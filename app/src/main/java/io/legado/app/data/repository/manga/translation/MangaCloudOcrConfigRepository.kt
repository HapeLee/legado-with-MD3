package io.legado.app.data.repository.manga.translation

import android.content.Context
import android.util.AtomicFile
import io.legado.app.data.security.MangaOcrCredentialCipher
import io.legado.app.domain.gateway.MangaCloudOcrGateway
import io.legado.app.domain.model.manga.MangaCloudOcrConfig
import io.legado.app.domain.model.manga.MangaCloudOcrProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal class MangaCloudOcrConfigRepository(
    context: Context,
    private val file: File = File(context.noBackupFilesDir, "manga-cloud-ocr.json"),
    cipher: MangaOcrCredentialCipher = MangaOcrCredentialCipher(),
    private val encrypt: (String) -> String = cipher::encrypt,
    private val decrypt: (String) -> String = cipher::decrypt,
) : MangaCloudOcrGateway {
    private val lock = Mutex()
    private val revision = MutableStateFlow(0L)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun read(): Map<MangaCloudOcrProvider, MangaCloudOcrConfig> {
        val atomic = AtomicFile(file)
        return if (!file.exists() && !File(file.path + ".bak").exists()) emptyMap()
        else json.decodeFromString(atomic.openRead().bufferedReader().use { it.readText() })
    }
    private fun MangaCloudOcrConfig.reveal() = copy(apiKey = decrypt(apiKey), secretKey = decrypt(secretKey))

    override fun observe(provider: MangaCloudOcrProvider) = revision.map { get(provider) }.distinctUntilChanged()

    override suspend fun get(provider: MangaCloudOcrProvider): MangaCloudOcrConfig = withContext(Dispatchers.IO) {
        lock.withLock { read()[provider]?.reveal() ?: MangaCloudOcrConfig() }
    }

    override suspend fun update(provider: MangaCloudOcrProvider, transform: (MangaCloudOcrConfig) -> MangaCloudOcrConfig) = withContext(Dispatchers.IO) {
        lock.withLock {
            val previous = read()
            val next = transform(previous[provider]?.reveal() ?: MangaCloudOcrConfig())
            require(next.apiKey.length <= 4096 && next.secretKey.length <= 4096 && next.endpoint.length <= 2048)
            val encoded = next.copy(apiKey = encrypt(next.apiKey.trim()), secretKey = encrypt(next.secretKey.trim()))
            if (File(file.path + ".bak").exists()) AtomicFile(file).openRead().close()
            val temporary = File.createTempFile(".${file.name}.", ".tmp", file.parentFile)
            try {
                FileOutputStream(temporary).use { output ->
                    output.write(json.encodeToString(previous + (provider to encoded)).toByteArray(Charsets.UTF_8))
                    output.fd.sync()
                }
                // Same checked atomic replacement as the manga cache; failures must propagate.
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally { temporary.delete() }
            revision.value += 1
            Unit
        }
    }
}
