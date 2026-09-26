package io.legado.app.help.book

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/** BookHelp 图片文件的发布边界；临时文件永远不作为可读图片暴露。 */
internal class BookImageFileStore(private val validate: (File) -> Boolean) {
    private class Entry {
        val mutex = Mutex()
        var users = 0
    }

    private val entries = mutableMapOf<String, Entry>()

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
