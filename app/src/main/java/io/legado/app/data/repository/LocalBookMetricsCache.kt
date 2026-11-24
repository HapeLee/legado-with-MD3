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
 * - Key 采用 bookUrl 的 SHA-256 摘要，避免私密书籍路径或文件名以明文落入 cacheDir。
 * - 已知非负字节数落盘，跨冷启动复用，避免每次重启重新扫描本地文件与 SAF。
 * - 「测不到/未知」状态仅记在内存中，重启或手动刷新后可重新尝试。
 */
internal class LocalBookMetricsCache(
    private val cacheFile: File,
) {
    private companion object {
        const val CACHE_VERSION = 1
        const val KEY_VERSION = "version"
        const val KEY_FILE_SIZE = "fileSize"
        const val KEY_VALUE = "v"
        const val KEY_STAMP = "s"
    }

    internal data class CachedEntry(
        val value: Long,
        val stamp: String,
    )

    internal sealed interface Lookup {
        data class Hit(val value: Long) : Lookup
        data object KnownMissing : Lookup
        data object Miss : Lookup
    }

    private val lock = Any()
    private val persistLock = Any()

    private var loaded = false
    private var dirty = false
    private var clearedFileSizesBeforeLoad = false

    private val fileSizes = HashMap<String, CachedEntry>()
    private val unknownFileSizes = HashMap<String, String>()

    fun ensureLoaded() {
        synchronized(lock) {
            if (loaded) return
        }
        val parsed = readFromDisk()
        synchronized(lock) {
            if (loaded) return
            if (parsed != null) {
                if (!clearedFileSizesBeforeLoad) {
                    for ((k, v) in parsed) {
                        fileSizes.putIfAbsent(k, v)
                    }
                } else if (parsed.isNotEmpty()) {
                    dirty = true
                }
            }
            loaded = true
        }
    }

    fun lookupFileSize(bookUrl: String, stamp: String): Lookup {
        val key = hashUrl(bookUrl)
        synchronized(lock) {
            val entry = fileSizes[key]
            if (entry != null && entry.stamp == stamp) return Lookup.Hit(entry.value)
            if (unknownFileSizes[key] == stamp) return Lookup.KnownMissing
            return Lookup.Miss
        }
    }

    fun putFileSize(bookUrl: String, stamp: String, value: Long?) {
        val key = hashUrl(bookUrl)
        synchronized(lock) {
            if (value != null && value >= 0L) {
                unknownFileSizes.remove(key)
                val next = CachedEntry(value, stamp)
                if (fileSizes[key] != next) {
                    fileSizes[key] = next
                    dirty = true
                }
            } else {
                if (fileSizes.remove(key) != null) {
                    dirty = true
                }
                unknownFileSizes[key] = stamp
            }
        }
    }

    fun prune(activeBookUrls: Set<String>) {
        val activeHashes = HashSet<String>(activeBookUrls.size * 2)
        for (url in activeBookUrls) {
            activeHashes.add(hashUrl(url))
        }
        synchronized(lock) {
            if (fileSizes.keys.retainAll(activeHashes)) dirty = true
            unknownFileSizes.keys.retainAll(activeHashes)
        }
    }

    fun clear(sort: Int) {
        if (sort != LocalBookshelfSort.FILE_SIZE) return
        synchronized(lock) {
            if (!loaded) clearedFileSizesBeforeLoad = true
            if (fileSizes.isNotEmpty()) {
                fileSizes.clear()
                dirty = true
            }
            unknownFileSizes.clear()
        }
    }

    fun persistIfDirty() {
        val snapshot: Map<String, CachedEntry> = synchronized(lock) {
            if (!dirty) return
            dirty = false
            HashMap(fileSizes)
        }
        synchronized(persistLock) {
            writeToDisk(snapshot)
        }
    }

    private fun readFromDisk(): Map<String, CachedEntry>? {
        return try {
            if (!cacheFile.isFile) return null
            val root = JSONObject(cacheFile.readText(Charsets.UTF_8))
            if (root.optInt(KEY_VERSION, -1) != CACHE_VERSION) return null
            parseSection(root.optJSONObject(KEY_FILE_SIZE))
        } catch (_: Exception) {
            null
        }
    }

    private fun parseSection(section: JSONObject?): Map<String, CachedEntry> {
        if (section == null) return emptyMap()
        val result = HashMap<String, CachedEntry>(section.length())
        val keys = section.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val item = section.optJSONObject(key) ?: continue
            val value = item.optLong(KEY_VALUE, -1L)
            val stamp = item.optString(KEY_STAMP, "")
            if (value >= 0L && stamp.isNotEmpty()) {
                result[key] = CachedEntry(value, stamp)
            }
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

    private fun encodeSection(entries: Map<String, CachedEntry>): JSONObject {
        val obj = JSONObject()
        for ((key, entry) in entries) {
            obj.put(
                key,
                JSONObject().apply {
                    put(KEY_VALUE, entry.value)
                    put(KEY_STAMP, entry.stamp)
                }
            )
        }
        return obj
    }

    private fun hashUrl(bookUrl: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bookUrl.toByteArray(Charsets.UTF_8))
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
