package io.legado.app.data.repository.manga.translation

import android.app.Application
import io.legado.app.domain.model.manga.*
import kotlinx.coroutines.*
import org.junit.Assert.*
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
class MangaInpaintModelRepositoryTest {
    private val bytes = "verified inpainting model".toByteArray()
    private val spec = MangaInpaintModelFile("https://example.invalid/weights", bytes.size.toLong(),
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) })
    private val engine = MangaInpaintEngine.MI_GAN

    @Test fun `downloaded engines verify persist and delete independently without implicit fetch`() = runBlocking {
        val root = Files.createTempDirectory("inpaint-models").toFile()
        var calls = 0
        fun repository() = MangaInpaintModelRepository(RuntimeEnvironment.getApplication(), root,
            mapOf(engine to spec, MangaInpaintEngine.LAMA to spec)) { _, target, progress -> calls++; target.writeBytes(bytes); progress(bytes.size.toLong()) }
        try {
            val repo = repository(); repo.refresh()
            assertEquals(MangaInpaintModelPhase.MISSING, repo.state.value.getValue(engine).phase)
            assertTrue(runCatching { repo.withInstalled(engine) { fail("not downloaded") } }.isFailure)
            assertEquals(0, calls)
            repo.download(engine); repo.download(MangaInpaintEngine.LAMA)
            val restored = repository(); restored.refresh()
            restored.withInstalled(engine) { assertArrayEquals(bytes, it.readBytes()) }
            restored.download(engine); assertEquals(2, calls)
            File(root, "unrelated").writeText("keep")
            restored.delete(engine)
            assertEquals(MangaInpaintModelPhase.MISSING, restored.state.value.getValue(engine).phase)
            assertEquals(MangaInpaintModelPhase.READY, restored.state.value.getValue(MangaInpaintEngine.LAMA).phase)
            assertEquals("keep", File(root, "unrelated").readText())
        } finally { root.deleteRecursively() }
    }

    @Test fun `bad checksum never publishes weights or leaves a partial file`() = runBlocking {
        val root = Files.createTempDirectory("inpaint-integrity").toFile()
        try {
            val repo = MangaInpaintModelRepository(RuntimeEnvironment.getApplication(), root, mapOf(engine to spec)) { _, file, _ ->
                file.writeBytes(ByteArray(bytes.size))
            }
            assertTrue(runCatching { repo.download(engine) }.isFailure)
            assertEquals(MangaInpaintModelPhase.FAILED, repo.state.value.getValue(engine).phase)
            assertFalse(File(root, "${MangaInpaintModelCatalog.directory(engine)}/model.onnx").exists())
            assertFalse(root.walkTopDown().any { it.name.endsWith(".part") })
        } finally { root.deleteRecursively() }
    }

    @Test fun `cancellation resets state and releases the partial download`() = runBlocking {
        val root = Files.createTempDirectory("inpaint-cancel").toFile()
        try {
            val started = CompletableDeferred<Unit>()
            val repo = MangaInpaintModelRepository(RuntimeEnvironment.getApplication(), root, mapOf(engine to spec)) { _, file, _ ->
                file.writeText("partial"); started.complete(Unit)
                try { awaitCancellation() } finally { throw java.io.IOException("cancelled socket") }
            }
            val task = launch { repo.download(engine) }; started.await(); task.cancelAndJoin()
            assertEquals(MangaInpaintModelPhase.MISSING, repo.state.value.getValue(engine).phase)
            assertFalse(root.walkTopDown().any { it.name.endsWith(".part") })
        } finally { root.deleteRecursively() }
    }

    @Test fun `deletion waits for the native model lease`() = runBlocking {
        val root = Files.createTempDirectory("inpaint-lease").toFile()
        try {
            val repo = MangaInpaintModelRepository(RuntimeEnvironment.getApplication(), root, mapOf(engine to spec)) { _, file, _ -> file.writeBytes(bytes) }
            repo.download(engine)
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            val use = launch { repo.withInstalled(engine) { entered.complete(Unit); release.await(); assertTrue(it.isFile) } }
            entered.await()
            val deletion = launch { repo.delete(engine) }; yield(); assertFalse(deletion.isCompleted)
            release.complete(Unit); use.join(); deletion.join()
            assertEquals(MangaInpaintModelPhase.MISSING, repo.state.value.getValue(engine).phase)
        } finally { root.deleteRecursively() }
    }
}
