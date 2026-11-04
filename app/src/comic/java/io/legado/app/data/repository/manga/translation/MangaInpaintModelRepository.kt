package io.legado.app.data.repository.manga.translation

import android.content.Context
import io.legado.app.R
import io.legado.app.domain.gateway.MangaInpaintModelGateway
import io.legado.app.domain.model.manga.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

internal data class MangaInpaintModelFile(val url: String, val bytes: Long, val sha256: String)

internal object MangaInpaintModelCatalog {
    val files = mapOf(
        MangaInpaintEngine.MI_GAN to MangaInpaintModelFile(
            "https://huggingface.co/andraniksargsyan/migan/resolve/406830d0fa60666da0071c342ad2fbc8f30c5c64/migan_pipeline_v2.onnx",
            28_079_181, "6f1f3530a1a2324b19752018ce756088b07973cda8d7d890034ace5c8a48c40b"),
        MangaInpaintEngine.LAMA to MangaInpaintModelFile(
            "https://huggingface.co/Carve/LaMa-ONNX/resolve/c3c0c9e468934d62e79c329e35d82dd09ff8c444/lama_fp32.onnx",
            208_044_816, "1faef5301d78db7dda502fe59966957ec4b79dd64e16f03ed96913c7a4eb68d6"),
    )
    fun directory(engine: MangaInpaintEngine) = engine.cacheIdentity.lowercase()
}

/** One lock covers download, deletion and native use. Verified files are cached only for this
 * repository's private storage lifetime; refresh always rehashes and mutations invalidate it.
 */
internal class MangaInpaintModelRepository(
    private val context: Context,
    private val root: File = File(context.noBackupFilesDir, "manga-inpaint"),
    private val catalog: Map<MangaInpaintEngine, MangaInpaintModelFile> = MangaInpaintModelCatalog.files,
    private val fetch: suspend (MangaInpaintModelFile, File, (Long) -> Unit) -> Unit = ::downloadMangaInpaintFile,
) : MangaInpaintModelGateway {
    private val lock = Mutex()
    private val verified = mutableMapOf<MangaInpaintEngine, Pair<Long, Long>>()
    private val mutableState = MutableStateFlow(catalog.mapValues { (_, f) -> MangaInpaintModelState(totalBytes = f.bytes) })
    override val state = mutableState.asStateFlow()
    private fun file(engine: MangaInpaintEngine) = File(root, "${MangaInpaintModelCatalog.directory(engine)}/model.onnx")
    private fun spec(engine: MangaInpaintEngine) = requireNotNull(catalog[engine]) { "Unknown inpainting model" }
    private fun publish(engine: MangaInpaintEngine, value: MangaInpaintModelState) = mutableState.update { it + (engine to value) }
    private suspend fun valid(target: File, spec: MangaInpaintModelFile): Boolean {
        if (!target.isFile || target.length() != spec.bytes) return false
        val digest = MessageDigest.getInstance("SHA-256")
        target.inputStream().buffered().use { input ->
            val bytes = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val size = input.read(bytes); if (size < 0) break
                digest.update(bytes, 0, size)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == spec.sha256
    }
    private suspend fun installed(engine: MangaInpaintEngine, rehash: Boolean = false): Boolean {
        val target = file(engine)
        val stamp = target.length() to target.lastModified()
        if (!rehash && target.isFile && verified[engine] == stamp) return true
        verified.remove(engine)
        return valid(target, spec(engine)).also { if (it) verified[engine] = stamp }
    }
    private fun status(engine: MangaInpaintEngine, ready: Boolean) = MangaInpaintModelState(
        if (ready) MangaInpaintModelPhase.READY else MangaInpaintModelPhase.MISSING,
        if (ready) spec(engine).bytes else 0, spec(engine).bytes)

    override suspend fun refresh() = withContext(Dispatchers.IO) {
        lock.withLock { catalog.keys.forEach { publish(it, status(it, installed(it, rehash = true))) } }
    }
    override suspend fun download(engine: MangaInpaintEngine) = withContext(Dispatchers.IO) {
        lock.withLock {
            val spec = spec(engine); val target = file(engine); val part = File(target.parentFile, "model.onnx.part")
            verified.remove(engine)
            try {
                check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs()) { "Cannot create inpainting model directory" }
                publish(engine, MangaInpaintModelState(MangaInpaintModelPhase.DOWNLOADING, totalBytes = spec.bytes))
                if (!installed(engine)) {
                    fetch(spec, part) { count -> publish(engine, MangaInpaintModelState(MangaInpaintModelPhase.DOWNLOADING,
                        count.coerceIn(0, spec.bytes), spec.bytes)) }
                    check(valid(part, spec)) { context.getString(R.string.manga_inpaint_integrity_failed) }
                    currentCoroutineContext().ensureActive()
                    Files.move(part.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                    verified[engine] = target.length() to target.lastModified()
                }
                publish(engine, status(engine, true))
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) { publish(engine, status(engine, installed(engine))) }
                throw cancelled
            } catch (failure: Exception) {
                if (!currentCoroutineContext().isActive) {
                    withContext(NonCancellable) { publish(engine, status(engine, installed(engine))) }
                    currentCoroutineContext().ensureActive()
                }
                publish(engine, MangaInpaintModelState(MangaInpaintModelPhase.FAILED, totalBytes = spec.bytes, error = failure.message?.take(300)))
                throw failure
            } finally { part.delete() }
        }
    }
    override suspend fun delete(engine: MangaInpaintEngine) = withContext(Dispatchers.IO) {
        lock.withLock {
            spec(engine); verified.remove(engine)
            val target = file(engine)
            for (owned in listOf(target, File(target.parentFile, "model.onnx.part")))
                check(!owned.exists() || owned.delete()) { "Cannot delete inpainting model" }
            publish(engine, status(engine, false))
        }
    }
    suspend fun <T> withInstalled(engine: MangaInpaintEngine, block: suspend (File) -> T): T = withContext(Dispatchers.IO) {
        lock.withLock {
            val ready = installed(engine)
            publish(engine, status(engine, ready))
            check(ready) { context.getString(R.string.manga_inpaint_download_required) }
            block(file(engine))
        }
    }
}

private val inpaintDownloadClient by lazy { OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(45, TimeUnit.SECONDS).callTimeout(20, TimeUnit.MINUTES).build() }

private suspend fun downloadMangaInpaintFile(spec: MangaInpaintModelFile, target: File, progress: (Long) -> Unit) = coroutineScope {
    val call = inpaintDownloadClient.newCall(Request.Builder().url(spec.url).build())
    val watcher = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) { try { awaitCancellation() } finally { call.cancel() } }
    try {
        call.execute().use { response ->
            check(response.isSuccessful) { "Inpainting model download: HTTP ${response.code}" }
            response.body.byteStream().use { input -> target.outputStream().buffered().use { output ->
                val buffer = ByteArray(64 * 1024); var count = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buffer); if (n < 0) break
                    count += n; check(count <= spec.bytes) { "Inpainting model exceeds expected size" }
                    output.write(buffer, 0, n); progress(count)
                }
            } }
        }
    } finally { watcher.cancel() }
}
