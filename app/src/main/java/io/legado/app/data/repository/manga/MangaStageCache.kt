package io.legado.app.data.repository.manga

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Optional persistent intermediates. A failed/cache-evicted entry never becomes a pipeline result. */
internal class MangaStageCache(private val root: File, private val quota: Long = 64L * 1024 * 1024) {
    companion object {
        const val MAX_ENTRY = 32 * 1024 * 1024
        fun key(vararg parts: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            parts.forEach {
                val bytes = it.toByteArray(Charsets.UTF_8)
                digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.size).array()); digest.update(bytes)
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
        private fun checksum(payload: ByteArray) = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }
    }
    init {
        root.mkdirs()
        root.listFiles()?.filter { it.name.endsWith(".tmp") }?.forEach { it.delete() }
    }
    private fun file(stage: String, key: String): File {
        require(stage.matches(Regex("[a-z-]+")) && key.matches(Regex("[0-9a-f]{64}")))
        return File(root, "$stage-$key.bin")
    }
    fun read(stage: String, key: String): ByteArray? {
        val file = file(stage, key)
        if (!file.isFile) return null
        return try {
            require(file.length() in 65..(MAX_ENTRY + 65L))
            val bytes = file.readBytes()
            val hash = bytes.copyOfRange(0, 64).toString(Charsets.US_ASCII)
            val payload = bytes.copyOfRange(65, bytes.size)
            require(bytes[64] == '\n'.code.toByte() && hash == checksum(payload))
            file.setLastModified(System.currentTimeMillis())
            payload
        } catch (_: Exception) { file.delete(); null }
    }
    fun remove(stage: String, key: String) {
        file(stage, key).delete()
    }
    suspend fun write(stage: String, identity: String, payload: ByteArray) {
        currentCoroutineContext().ensureActive()
        if (payload.size > MAX_ENTRY || payload.size + 65 > quota) return
        val target = file(stage, identity)
        val temporary = File(root, target.name + ".tmp")
        try {
            val entries = root.listFiles()?.filter { it.isFile && it != target }?.sortedBy { it.lastModified() }.orEmpty()
            var used = entries.sumOf { it.length() } + target.length()
            // Include both the old entry and the replacement until the atomic rename.
            for (entry in entries) {
                if (used + payload.size + 65 <= quota) break
                val length = entry.length()
                if (entry.delete()) used -= length
            }
            if (used + payload.size + 65 > quota || root.usableSpace < payload.size + 1024 * 1024) return
            temporary.outputStream().use {
                it.write(checksum(payload).toByteArray(Charsets.US_ASCII))
                it.write('\n'.code); it.write(payload)
            }
            currentCoroutineContext().ensureActive()
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (error: CancellationException) { throw error }
        catch (_: java.io.IOException) { /* Cache storage is optional; final-image writes remain strict. */ }
        finally { temporary.delete() }
    }
    inline fun <reified T> json(stage: String, key: String): T? = read(stage, key)?.let {
        try { Json.decodeFromString<T>(it.toString(Charsets.UTF_8)) }
        catch (_: Exception) { remove(stage, key); null }
    }
    suspend inline fun <reified T> writeJson(stage: String, key: String, value: T) = write(stage, key, Json.encodeToString(value).toByteArray())
}
