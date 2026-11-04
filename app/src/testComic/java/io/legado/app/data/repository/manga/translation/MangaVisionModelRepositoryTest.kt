package io.legado.app.data.repository.manga.translation

import android.app.Application
import io.legado.app.data.repository.manga.engine.MangaLocalEngineUnavailableException
import io.legado.app.domain.model.manga.MangaOcrModelPhase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class MangaVisionModelRepositoryTest {
    @Test fun `full keeps the bundled detector usable without network`() = runBlocking {
        val root = Files.createTempDirectory("manga-vision-bundled").toFile()
        try {
            var downloads = 0
            val repo = MangaVisionModelRepository(RuntimeEnvironment.getApplication(), root) { _, target, _ ->
                downloads++
                target.writeText("network weight must not be used in full")
            }
            repo.refresh()
            assumeTrue("full 未内置气泡权重时跳过", repo.state.value.phase == MangaOcrModelPhase.READY)
            assertEquals(0, downloads)
            val model = repo.requireModel()
            assertEquals(MangaVisionModelCatalog.BYTES, model.length())
            assertEquals(root, model.parentFile)
            repo.refresh()
            assertEquals(MangaOcrModelPhase.READY, repo.state.value.phase)
            assertEquals(0, downloads)
        } finally { root.deleteRecursively() }
    }

    @Test fun `missing bundled weight falls back to an explicit download`() = runBlocking {
        val root = Files.createTempDirectory("manga-vision-download").toFile()
        val payload = "downloaded detector".toByteArray()
        val spec = MangaOcrModelFile("detector-test.onnx", "https://example.invalid/detector",
            payload.size.toLong(), MessageDigest.getInstance("SHA-256").digest(payload)
                .joinToString("") { "%02x".format(it.toInt() and 255) })
        try {
            var downloads = 0
            val repo = MangaVisionModelRepository(RuntimeEnvironment.getApplication(), root, spec) { _, target, _ ->
                downloads++
                target.writeBytes(payload)
            }
            repo.refresh()
            assertEquals(MangaOcrModelPhase.MISSING, repo.state.value.phase)
            assertThrows(MangaLocalEngineUnavailableException::class.java) { runBlocking { repo.requireModel() } }
            repo.download()
            assertEquals(1, downloads)
            assertEquals(MangaOcrModelPhase.READY, repo.state.value.phase)
            assertEquals(spec.bytes, repo.requireModel().length())
        } finally { root.deleteRecursively() }
    }

    @Test fun `corrupt download reports failure and removes the partial file`() = runBlocking {
        val root = Files.createTempDirectory("manga-vision-corrupt").toFile()
        val spec = MangaOcrModelFile("detector-test.onnx", "https://example.invalid/detector", 10,
            "0000000000000000000000000000000000000000000000000000000000000000")
        try {
            val repo = MangaVisionModelRepository(RuntimeEnvironment.getApplication(), root, spec) { _, target, _ ->
                target.writeText("short")
            }
            assertThrows(IllegalStateException::class.java) { runBlocking { repo.download() } }
            assertEquals(MangaOcrModelPhase.FAILED, repo.state.value.phase)
            assertFalse(File(root, "${spec.name}.part").exists())
            assertFalse(File(root, spec.name).exists())
        } finally { root.deleteRecursively() }
    }

    @Test fun `deleting the weight reports missing until the bundled copy is restored`() = runBlocking {
        val root = Files.createTempDirectory("manga-vision-delete").toFile()
        try {
            val repo = MangaVisionModelRepository(RuntimeEnvironment.getApplication(), root)
            repo.refresh()
            assumeTrue("full 未内置气泡权重时跳过", repo.state.value.phase == MangaOcrModelPhase.READY)
            repo.delete()
            assertEquals(MangaOcrModelPhase.MISSING, repo.state.value.phase)
            assertTrue(root.listFiles().isNullOrEmpty())
        } finally { root.deleteRecursively() }
    }
}
