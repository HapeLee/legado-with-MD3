package io.legado.app.help.book

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.concurrent.atomic.AtomicBoolean

/** BookHelp 图片文件的发布边界；临时文件永远不作为可读图片暴露。 */
internal class BookImageFileStore(
    private val readAccessTime: (File) -> Long = File::lastModified,
    private val validate: (File) -> Boolean,
) {
    companion object {
        const val ONLINE_MARKER_DIRECTORY = ".online"
        const val DOWNLOAD_INDEX_DIRECTORY = ".downloads"
    }
    private class Entry {
        val mutex = Mutex()
        var users = 0
    }

    private data class OnlineCandidate(
        val file: File,
        val marker: File,
        val accessTime: Long,
        val size: Long
    )

    private val entries = mutableMapOf<String, Entry>()
    private val evictionLock = Any()

    /** 读取租约与写入共用保护登记，close 可在任意线程重复调用。 */
    fun pin(file: File): Closeable {
        val key = file.absolutePath
        val entry = synchronized(entries) {
            entries.getOrPut(key, ::Entry).also { it.users++ }
        }
        val closed = AtomicBoolean()
        return Closeable {
            if (closed.compareAndSet(false, true)) synchronized(entries) {
                if (--entry.users == 0) entries.remove(key)
            }
        }
    }

    private fun onlineMarker(file: File) = File(File(file.parentFile, ONLINE_MARKER_DIRECTORY), file.name)

    fun isOnline(file: File): Boolean = onlineMarker(file).isFile

    private fun downloadIndex(file: File) = File(File(file.parentFile, DOWNLOAD_INDEX_DIRECTORY), file.name)

    fun isExplicitDownload(file: File): Boolean = downloadIndex(file).isFile

    fun hasDownloadChapter(file: File, chapterKey: String): Boolean = try {
        val properties = java.util.Properties()
        downloadIndex(file).inputStream().use(properties::load)
        properties.getProperty("version") == "1" && properties.getProperty("chapter.$chapterKey") == "explicit"
    } catch (_: IOException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    }

    /** 每张原图记录引用它的显式下载章节；失败的部分下载同样保留，完成状态仍验证原图。 */
    fun retainDownload(file: File, chapterKey: String) = synchronized(entries) {
        val index = downloadIndex(file)
        val properties = java.util.Properties()
        if (index.isFile) try {
            index.inputStream().use(properties::load)
        } catch (_: IllegalArgumentException) {
            // 损坏索引仍保护原图，本次显式下载恢复可验证的引用。
        }
        properties.setProperty("version", "1")
        properties.setProperty("chapter.$chapterKey", "explicit")
        val parent = requireNotNull(index.parentFile)
        if (!parent.isDirectory && !parent.mkdirs()) throw IOException("无法创建下载用途索引")
        val temp = File.createTempFile("${file.name}.", ".tmp", parent)
        try {
            FileOutputStream(temp).use {
                properties.store(it, null)
                it.fd.sync()
            }
            Files.move(temp.toPath(), index.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
            setOnline(file, false)
        } finally {
            temp.delete()
        }
    }

    /** 未分类的历史文件不自动纳入预算；现有章节下载可将在线文件升级为保留文件。 */
    fun setOnline(file: File, online: Boolean) {
        if (online && isExplicitDownload(file)) return
        val marker = onlineMarker(file)
        if (online) {
            requireNotNull(marker.parentFile).mkdirs()
            if (!marker.exists() && !marker.createNewFile()) throw IOException("无法登记在线图片")
            marker.setLastModified(System.currentTimeMillis())
        } else if (marker.exists() && !marker.delete()) {
            throw IOException("无法保留图片缓存")
        }
    }

    fun touch(file: File) {
        onlineMarker(file).takeIf(File::isFile)?.setLastModified(System.currentTimeMillis())
    }

    /** 只淘汰明确登记的在线文件；活动读取/写入可暂时超过预算，释放后再次整理。 */
    fun trimOnlineCache(root: File, maxBytes: Long) = synchronized(evictionLock) {
        require(maxBytes >= 0)
        val candidates = root.walkTopDown().filter {
            it.isFile && it.parentFile?.name == ONLINE_MARKER_DIRECTORY
        }
            .map { marker ->
                val file = File(requireNotNull(marker.parentFile).parentFile, marker.name)
                // touch/下载可以并发修改文件；比较器只能读取本次枚举的固定值。
                OnlineCandidate(file, marker, readAccessTime(marker), file.length())
            }
            .filter { !isExplicitDownload(it.file) }
            .toList().sortedBy { it.accessTime }
        var total = candidates.sumOf { it.size }
        for ((file, marker, _, size) in candidates) {
            if (!marker.isFile || isExplicitDownload(file)) {
                total -= size
                continue
            }
            if (total <= maxBytes && file.exists()) continue
            if (deleteIfIdle(file) {
                    // 下载可能已在枚举之后升级保留用途，必须在删除前复核。
                    if (!marker.isFile || isExplicitDownload(file)) false
                    else if (!file.exists() || file.delete()) {
                        marker.delete()
                        true
                    } else false
                }) total -= size
        }
    }

    /** 与获取写入锁原子协调；目录包含正在写入或等待写入的文件时整次清理跳过。 */
    fun deleteIfIdle(file: File, delete: (File) -> Boolean = File::delete): Boolean =
        synchronized(entries) {
            val path = file.absolutePath
            val prefix = path + File.separator
            if (entries.keys.any { it == path || it.startsWith(prefix) }) {
                false
            } else {
                delete(file)
            }
        }

    /** 同时保护迁移的源和目标，不能移动瓦片仍通过路径打开的目录。 */
    fun moveIfIdle(source: File, target: File, move: (File, File) -> Boolean): Boolean =
        deleteIfIdle(source) { deleteIfIdle(target) { move(source, target) } }

    /** users 包括等待者，最后一位退出后才回收，避免同一文件同时出现两把锁。 */
    suspend fun <T> withFileLock(file: File, block: suspend () -> T): T {
        val key = file.absolutePath
        val entry = synchronized(entries) {
            entries.getOrPut(key, ::Entry).also { it.users++ }
        }
        try {
            return entry.mutex.withLock { block() }
        } finally {
            synchronized(entries) {
                if (--entry.users == 0) entries.remove(key)
            }
        }
    }

    fun isValid(file: File): Boolean = try {
        file.isFile && file.length() > 0 && validate(file)
    } catch (_: IOException) {
        // 外部存储不可读或检查期间被清理，不能当作有效缓存。
        false
    }

    /** 输入流由调用方关闭；失败保留原来的有效文件，不降级为直接复制到目标。 */
    fun write(file: File, input: InputStream, ensureActive: () -> Unit = {}) {
        // 同步 writeImage 也经过此入口，不能只保护 suspend saveImage 的写入。
        val key = file.absolutePath
        val entry = synchronized(entries) {
            entries.getOrPut(key, ::Entry).also { it.users++ }
        }
        try {
            writeToTemporaryFile(file, input, ensureActive)
        } finally {
            synchronized(entries) {
                if (--entry.users == 0) entries.remove(key)
            }
        }
    }

    private fun writeToTemporaryFile(file: File, input: InputStream, ensureActive: () -> Unit) {
        val parent = file.absoluteFile.parentFile ?: throw IOException("图片目录不存在")
        if (!parent.isDirectory && !parent.mkdirs()) throw IOException("无法创建图片目录: $parent")
        val temp = File.createTempFile("${file.name}.", ".tmp", parent)
        try {
            FileOutputStream(temp).use { output ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    ensureActive()
                    val count = input.read(buffer)
                    if (count == -1) break
                    output.write(buffer, 0, count)
                }
                output.fd.sync()
            }
            ensureActive()
            if (!isValid(temp)) throw IOException("图片数据无效")
            ensureActive()
            // 同目录原子替换：外部存储若不支持则报错，不暴露复制中的半文件。
            Files.move(temp.toPath(), file.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            temp.delete()
        }
    }
}
