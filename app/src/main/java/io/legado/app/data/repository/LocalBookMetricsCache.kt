package io.legado.app.data.repository

import io.legado.app.domain.model.LocalBookshelfSort
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * 本地书架排序指标（文件大小）的双层缓存：内存 + 应用缓存目录 JSON。
 *
 * - Key 与校验戳（stamp）均采用 SHA-256 摘要，避免私密书籍路径或原始文件名以明文落入 cacheDir。
 * - 刷新与单书失效在同一个同步锁内推进代次（epoch），丢弃失效前发出的慢读取回填结果。
 * - 已知非负字节数落盘，跨冷启动复用；「测不到/未知」状态仅记在内存中，重启或刷新后可重试。
 */
internal class LocalBookMetricsCache(
    private val cacheFile: File,
) {
    private companion object {
        const val CACHE_VERSION = 2
        const val SHA256_HEX_LENGTH = 64
        const val KEY_VERSION = "version"
        const val KEY_FILE_SIZE = "fileSize"
        const val KEY_VALUE = "v"
        const val KEY_STAMP = "s"
    }

    internal data class CachedEntry(
        val value: Long,
        val stampDigest: String,
        val updatedAtEpoch: Long = 0L,
    )

    private data class UnknownEntry(
        val stampDigest: String,
        val updatedAtEpoch: Long,
    )

    private sealed interface DiskLoadResult {
        data object Absent : DiskLoadResult
        data object Invalidated : DiskLoadResult
        data class Loaded(val entries: Map<String, CachedEntry>) : DiskLoadResult
    }

    internal sealed interface Lookup {
        data class Hit(val value: Long) : Lookup
        data object KnownMissing : Lookup
        /**
         * @param token 发起读取时的全局代次，写回时用于丢弃失效前的慢读取。
         * @param generation 该书当前生效的刷新代次（全局刷新或单书失效中较新者），
         * 同一本书在同一代次内的并发读取以它作为共享键。
         */
        data class Miss(val token: Long, val generation: Long) : Lookup
    }

    private val lock = Any()
    private val persistLock = Any()

    private var loaded = false
    private var dirty = false
    private var clearedFileSizesBeforeLoad = false

    private var epoch = 0L
    private var clearEpoch = 0L
    private val keyInvalidatedEpoch = HashMap<String, Long>()

    private val fileSizes = HashMap<String, CachedEntry>()
    private val unknownFileSizes = HashMap<String, UnknownEntry>()

    fun ensureLoaded() {
        synchronized(lock) {
            if (loaded) return
        }
        val diskResult = synchronized(persistLock) {
            readFromDisk()
        }
        synchronized(lock) {
            if (loaded) return
            when (diskResult) {
                DiskLoadResult.Absent -> Unit
                DiskLoadResult.Invalidated -> dirty = true
                is DiskLoadResult.Loaded -> {
                    if (!clearedFileSizesBeforeLoad) {
                        for ((k, v) in diskResult.entries) {
                            if (keyInvalidatedEpoch.containsKey(k)) {
                                dirty = true
                            } else {
                                fileSizes.putIfAbsent(k, v)
                            }
                        }
                    } else if (diskResult.entries.isNotEmpty()) {
                        dirty = true
                    }
                }
            }
            loaded = true
        }
    }

    fun currentEpoch(): Long = synchronized(lock) { epoch }

    fun isDirty(): Boolean = synchronized(lock) { dirty }

    fun isTokenValid(bookUrl: String, token: Long): Boolean {
        val key = sha256Hex(bookUrl)
        return synchronized(lock) {
            isTokenValidLocked(key, token)
        }
    }

    fun lookupFileSize(bookUrl: String, stamp: String): Lookup {
        val key = sha256Hex(bookUrl)
        val stampDigest = sha256Hex(stamp)
        synchronized(lock) {
            val entry = fileSizes[key]
            if (entry != null && entry.stampDigest == stampDigest) return Lookup.Hit(entry.value)
            if (unknownFileSizes[key]?.stampDigest == stampDigest) return Lookup.KnownMissing
            val generation = maxOf(clearEpoch, keyInvalidatedEpoch[key] ?: 0L)
            return Lookup.Miss(token = epoch, generation = generation)
        }
    }

    fun putFileSize(bookUrl: String, stamp: String, value: Long?, token: Long): Boolean {
        val key = sha256Hex(bookUrl)
        val stampDigest = sha256Hex(stamp)
        synchronized(lock) {
            if (!isTokenValidLocked(key, token)) return false
            if (value != null && value >= 0L) {
                unknownFileSizes.remove(key)
                val current = fileSizes[key]
                if (current == null || current.value != value || current.stampDigest != stampDigest) {
                    fileSizes[key] = CachedEntry(value, stampDigest, updatedAtEpoch = epoch)
                    dirty = true
                }
            } else {
                if (fileSizes.remove(key) != null) {
                    dirty = true
                }
                unknownFileSizes[key] = UnknownEntry(stampDigest, updatedAtEpoch = epoch)
            }
            return true
        }
    }

    /** @return 失效后该书生效的新代次；代次更旧的在途读取都应作废。 */
    fun invalidateFileSize(bookUrl: String): Long {
        val key = sha256Hex(bookUrl)
        synchronized(lock) {
            epoch += 1
            keyInvalidatedEpoch[key] = epoch
            if (fileSizes.remove(key) != null) {
                dirty = true
            }
            unknownFileSizes.remove(key)
            return epoch
        }
    }

    fun prune(activeBookUrls: Set<String>, maxEpoch: Long = Long.MAX_VALUE) {
        val activeHashes = HashSet<String>(activeBookUrls.size * 2)
        for (url in activeBookUrls) {
            activeHashes.add(sha256Hex(url))
        }
        synchronized(lock) {
            val sizeIterator = fileSizes.entries.iterator()
            while (sizeIterator.hasNext()) {
                val entry = sizeIterator.next()
                if (entry.key !in activeHashes && entry.value.updatedAtEpoch <= maxEpoch) {
                    sizeIterator.remove()
                    dirty = true
                }
            }
            val unknownIterator = unknownFileSizes.entries.iterator()
            while (unknownIterator.hasNext()) {
                val entry = unknownIterator.next()
                if (entry.key !in activeHashes && entry.value.updatedAtEpoch <= maxEpoch) {
                    unknownIterator.remove()
                }
            }
            // 单书失效水位不随书籍移出书架而清理：否则失效前发出、仍阻塞在 SAF 里的慢读取
            // 返回时会因水位消失被误判为有效而回填旧值，同路径重导时共享代次也会倒退。
            // 水位只在整体刷新推进 clearEpoch 时统一清空，进程内手动重导的条目数量很小。
        }
    }

    /** @return 刷新后的新代次；代次更旧的在途读取都应作废。 */
    fun clear(sort: Int): Long {
        if (sort != LocalBookshelfSort.FILE_SIZE) return currentEpoch()
        synchronized(lock) {
            epoch += 1
            clearEpoch = epoch
            keyInvalidatedEpoch.clear()
            if (!loaded) {
                clearedFileSizesBeforeLoad = true
                dirty = true
            }
            if (fileSizes.isNotEmpty()) {
                fileSizes.clear()
                dirty = true
            }
            unknownFileSizes.clear()
            return epoch
        }
    }

    fun persistIfDirty() {
        synchronized(persistLock) {
            val snapshot: Map<String, CachedEntry> = synchronized(lock) {
                if (!dirty) return
                dirty = false
                HashMap(fileSizes)
            }
            writeToDisk(snapshot)
        }
    }

    private fun isTokenValidLocked(key: String, token: Long): Boolean {
        if (token < clearEpoch) return false
        val invalidatedAt = keyInvalidatedEpoch[key]
        if (invalidatedAt != null && token < invalidatedAt) return false
        return true
    }

    private fun readFromDisk(): DiskLoadResult {
        if (!cacheFile.isFile) return DiskLoadResult.Absent
        return try {
            val root = JSONObject(cacheFile.readText(Charsets.UTF_8))
            if (root.optInt(KEY_VERSION, -1) != CACHE_VERSION) {
                deleteCacheFileQuietly()
                return DiskLoadResult.Invalidated
            }
            val parsed = parseSection(root.optJSONObject(KEY_FILE_SIZE))
            if (parsed == null) {
                deleteCacheFileQuietly()
                DiskLoadResult.Invalidated
            } else {
                DiskLoadResult.Loaded(parsed)
            }
        } catch (_: Exception) {
            deleteCacheFileQuietly()
            DiskLoadResult.Invalidated
        }
    }

    private fun parseSection(section: JSONObject?): Map<String, CachedEntry>? {
        if (section == null) return emptyMap()
        val result = HashMap<String, CachedEntry>(section.length())
        val keys = section.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (!isSha256Hex(key)) return null
            val item = section.optJSONObject(key) ?: return null
            val value = item.optLong(KEY_VALUE, -1L)
            val stampDigest = item.optString(KEY_STAMP, "")
            if (value < 0L || !isSha256Hex(stampDigest)) return null
            result[key] = CachedEntry(value = value, stampDigest = stampDigest, updatedAtEpoch = 0L)
        }
        return result
    }

    private fun writeToDisk(sizes: Map<String, CachedEntry>) {
        try {
            cacheFile.parentFile?.mkdirs()
            val root = JSONObject().apply {
                put(KEY_VERSION, CACHE_VERSION)
                put(KEY_FILE_SIZE, encodeSection(sizes))
            }
            val tmp = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
            tmp.writeText(root.toString(), Charsets.UTF_8)
            try {
                Files.move(
                    tmp.toPath(),
                    cacheFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: Exception) {
                Files.move(tmp.toPath(), cacheFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (_: Exception) {
            // 磁盘满或无权限时静默退化为内存缓存，不影响主流程
        }
    }

    private fun deleteCacheFileQuietly() {
        try {
            Files.deleteIfExists(cacheFile.toPath())
        } catch (_: Exception) {
            cacheFile.delete()
        }
    }

    private fun encodeSection(entries: Map<String, CachedEntry>): JSONObject {
        val obj = JSONObject()
        for ((key, entry) in entries) {
            obj.put(
                key,
                JSONObject().apply {
                    put(KEY_VALUE, entry.value)
                    put(KEY_STAMP, entry.stampDigest)
                }
            )
        }
        return obj
    }

    private fun isSha256Hex(value: String): Boolean {
        if (value.length != SHA256_HEX_LENGTH) return false
        for (ch in value) {
            val isDigit = ch in '0'..'9'
            val isLowerHex = ch in 'a'..'f'
            if (!isDigit && !isLowerHex) return false
        }
        return true
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        val hex = CharArray(digest.size * 2)
        val digits = "0123456789abcdef"
        for (i in digest.indices) {
            val v = digest[i].toInt() and 0xFF
            hex[i * 2] = digits[v ushr 4]
            hex[i * 2 + 1] = digits[v and 0x0F]
        }
        return String(hex)
    }
}
