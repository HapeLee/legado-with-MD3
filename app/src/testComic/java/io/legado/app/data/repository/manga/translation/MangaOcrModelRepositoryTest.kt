package io.legado.app.data.repository.manga.translation

import android.app.Application
import io.legado.app.domain.model.manga.MangaLocalOcrEngine
import io.legado.app.domain.model.manga.MangaOcrModelPhase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
class MangaOcrModelRepositoryTest {
    @Test fun `multiple installed models reuse verified downloads and delete independently`() = runBlocking {
        val root = Files.createTempDirectory("manga-ocr-multiple").toFile()
        val other = MangaLocalOcrEngine.PADDLE_V6_SMALL
        val recBytes = "different recognizer".toByteArray()
        val recSpec = MangaOcrModelFile("rec.onnx", "https://example.invalid/other-rec", recBytes.size.toLong(),
            MessageDigest.getInstance("SHA-256").digest(recBytes).joinToString("") { "%02x".format(it.toInt() and 255) })
        val catalog = mapOf(engine to listOf(spec("det.onnx"), spec("rec.onnx")), other to listOf(spec("det.onnx"), recSpec))
        var downloads = 0
        fun repository() = MangaOcrModelRepository(RuntimeEnvironment.getApplication(), root, catalog) { file, target, _ ->
            downloads++
            target.writeBytes(if (file == recSpec) recBytes else payload)
        }
        try {
            val repo = repository()
            repo.download(engine); repo.download(other)
            assertEquals(3, downloads) // V6 detector was copied from verified local bytes.
            assertEquals(setOf(engine, other), repo.state.value.filterValues { it.phase == MangaOcrModelPhase.READY }.keys)
            repo.withInstalled(other) { assertArrayEquals(recBytes, File(it, "rec.onnx").readBytes()) }
            repo.delete(engine)
            assertEquals(MangaOcrModelPhase.READY, repo.state.value.getValue(other).phase)
            val restored = repository(); restored.refresh()
            assertEquals(MangaOcrModelPhase.MISSING, restored.state.value.getValue(engine).phase)
            assertEquals(MangaOcrModelPhase.READY, restored.state.value.getValue(other).phase)
            assertTrue(runCatching { restored.withInstalled(engine) { error("Missing model must not run") } }.isFailure)
            restored.withInstalled(other) { assertArrayEquals(recBytes, File(it, "rec.onnx").readBytes()) }
        } finally { root.deleteRecursively() }
    }

    @Test fun `socket failure caused by cancellation resets model state without reporting download failure`() = runBlocking {
        val root = Files.createTempDirectory("manga-ocr-test").toFile()
        try {
            val started = CompletableDeferred<Unit>()
            val repo = MangaOcrModelRepository(RuntimeEnvironment.getApplication(), root, mapOf(engine to listOf(spec("det.onnx")))) { _, target, _ ->
                target.writeText("partial"); started.complete(Unit)
                try { awaitCancellation() } finally { throw java.io.IOException("cancelled socket") }
            }
            val task = launch { repo.download(engine) }; started.await(); task.cancelAndJoin()
            assertEquals(MangaOcrModelPhase.MISSING, repo.state.value.getValue(engine).phase)
            assertFalse(File(directory(root), "det.onnx.part").exists())
        } finally { root.deleteRecursively() }
    }
    private val engine = MangaLocalOcrEngine.PADDLE_V5
    private fun directory(root: File) = File(root, MangaPaddleModelCatalog.directory(engine))
    private val payload = "verified model".toByteArray()
    private fun spec(name: String) = MangaOcrModelFile(name, "https://example.invalid/$name", payload.size.toLong(),
        MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it.toInt() and 255) })

    @Test fun `verified set survives repository recreation and deletion does not touch unrelated data`() = runBlocking {
        val root = Files.createTempDirectory("manga-ocr-test").toFile()
        try {
            var calls = 0
            fun repository() = MangaOcrModelRepository(RuntimeEnvironment.getApplication(), root,
                mapOf(engine to listOf(spec("det.onnx"), spec("rec.onnx")))) { _, target, progress ->
                calls++; target.writeBytes(payload); progress(payload.size.toLong())
            }
            val repo = repository()
            repo.refresh(); assertEquals(MangaOcrModelPhase.MISSING, repo.state.value.getValue(engine).phase)
            assertTrue(runCatching { repo.withInstalled(engine) { error("must not reach runtime") } }.isFailure)
            repo.download(engine)
            assertEquals(MangaOcrModelPhase.READY, repo.state.value.getValue(engine).phase)
            assertEquals(payload.size * 2L, repo.state.value.getValue(engine).downloadedBytes)
            val restored = repository(); restored.refresh(); restored.download(engine)
            assertEquals(2, calls)
            assertEquals(directory(root), restored.withInstalled(engine) { it })
            File(root, "unrelated.txt").writeText("keep")
            restored.delete(engine)
            assertEquals(MangaOcrModelPhase.MISSING, restored.state.value.getValue(engine).phase)
            assertEquals("keep", File(root, "unrelated.txt").readText())
        } finally { root.deleteRecursively() }
    }

    @Test fun `corrupt or partial bytes never become installed and retry repairs the set`() = runBlocking {
        val root = Files.createTempDirectory("manga-ocr-test").toFile()
        try {
            var corrupt = true
            val repo = MangaOcrModelRepository(RuntimeEnvironment.getApplication(), root, mapOf(engine to listOf(spec("det.onnx")))) { _, target, _ ->
                target.writeBytes(if (corrupt) ByteArray(payload.size) else payload)
            }
            assertTrue(runCatching { repo.download(engine) }.isFailure)
            assertEquals(MangaOcrModelPhase.FAILED, repo.state.value.getValue(engine).phase)
            assertFalse(File(directory(root), "det.onnx").exists()); assertFalse(File(directory(root), "det.onnx.part").exists())
            corrupt = false; repo.download(engine)
            File(directory(root), "det.onnx").writeBytes(ByteArray(payload.size))
            repo.refresh(); assertEquals(MangaOcrModelPhase.MISSING, repo.state.value.getValue(engine).phase)
            repo.download(engine); assertEquals(MangaOcrModelPhase.READY, repo.state.value.getValue(engine).phase)
        } finally { root.deleteRecursively() }
    }

    @Test fun `cancelling a download cleans partial bytes and allows immediate retry`() = runBlocking {
        val root = Files.createTempDirectory("manga-ocr-test").toFile()
        try {
            val started = CompletableDeferred<Unit>(); val wait = CompletableDeferred<Unit>()
            var block = true
            val repo = MangaOcrModelRepository(RuntimeEnvironment.getApplication(), root, mapOf(engine to listOf(spec("det.onnx")))) { _, target, _ ->
                target.writeText("partial")
                if (block) { started.complete(Unit); wait.await() }
                target.writeBytes(payload)
            }
            val task = launch { repo.download(engine) }; started.await(); task.cancelAndJoin()
            assertEquals(MangaOcrModelPhase.MISSING, repo.state.value.getValue(engine).phase)
            assertFalse(File(directory(root), "det.onnx.part").exists())
            block = false; repo.download(engine)
            assertEquals(MangaOcrModelPhase.READY, repo.state.value.getValue(engine).phase)
        } finally { root.deleteRecursively() }
    }

    @Test fun `delete waits until the native consumer releases its files`() = runBlocking {
        val root = Files.createTempDirectory("manga-ocr-test").toFile()
        try {
            val repo = MangaOcrModelRepository(RuntimeEnvironment.getApplication(), root, mapOf(engine to listOf(spec("det.onnx")))) { _, target, _ -> target.writeBytes(payload) }
            repo.download(engine)
            val started = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
            val consumer = launch { repo.withInstalled(engine) { started.complete(Unit); finish.await(); assertTrue(File(it, "det.onnx").exists()) } }
            started.await()
            val deletion = launch { repo.delete(engine) }
            assertFalse(deletion.isCompleted)
            finish.complete(Unit); consumer.join(); deletion.join()
            assertFalse(File(directory(root), "det.onnx").exists())
        } finally { root.deleteRecursively() }
    }
}
