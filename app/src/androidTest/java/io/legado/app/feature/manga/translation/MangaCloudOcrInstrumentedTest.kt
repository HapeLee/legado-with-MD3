package io.legado.app.feature.manga.translation

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.data.repository.manga.translation.MangaCloudOcrConfigRepository
import io.legado.app.data.repository.manga.translation.tencentOcrRequest
import io.legado.app.domain.model.manga.MangaCloudOcrConfig
import io.legado.app.domain.model.manga.MangaCloudOcrProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** No network or user credentials. Exercise actual Android Keystore and HMAC providers. */
@RunWith(AndroidJUnit4::class)
class MangaCloudOcrInstrumentedTest {
    @Test fun credentialsAreEncryptedAndProvidersSurviveRecreation() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val root = File(app.cacheDir, "manga-cloud-ocr-test-${UUID.randomUUID()}").also { check(it.mkdir()) }
        val file = File(root, "test.json")
        try {
            val repo = MangaCloudOcrConfigRepository(app, file)
            repo.update(MangaCloudOcrProvider.BAIDU) { it.copy(apiKey = "fixture-baidu", secretKey = "fixture-baidu-secret") }
            repo.update(MangaCloudOcrProvider.TENCENT) { it.copy(apiKey = "fixture-tencent", secretKey = "fixture-tencent-secret") }
            val restored = MangaCloudOcrConfigRepository(app, file)
            assertEquals("fixture-baidu", restored.get(MangaCloudOcrProvider.BAIDU).apiKey)
            assertEquals("fixture-tencent-secret", restored.get(MangaCloudOcrProvider.TENCENT).secretKey)
            assertFalse(file.readText().contains("fixture-"))
        } finally {
            listOf("test.json", "test.json.new", "test.json.bak").forEach { File(root, it).delete() }
            check(root.delete())
        }
    }

    @Test fun tencentSignatureMatchesIndependentVector() {
        val request = tencentOcrRequest("aGVsbG8=", "ja", MangaCloudOcrConfig(apiKey = "fake-id", secretKey = "fake-secret"), 1551113065)
        assertTrue(request.header("Authorization")!!.endsWith("Signature=9704630c82a0d12dfeebee8c96e186b853861027b43c8fccd88d32a0b69d2d23"))
    }
}
