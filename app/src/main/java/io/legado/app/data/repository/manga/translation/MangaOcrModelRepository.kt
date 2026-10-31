package io.legado.app.data.repository.manga.translation

import android.content.Context
import io.legado.app.R
import io.legado.app.domain.gateway.MangaOcrModelGateway
import io.legado.app.domain.model.manga.MangaOcrModelPhase
import io.legado.app.domain.model.manga.MangaOcrModelState
import io.legado.app.domain.model.manga.MangaLocalOcrEngine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

internal data class MangaOcrModelFile(val name: String, val url: String, val bytes: Long, val sha256: String)

/** The same lock protects download, native use and deletion; inference owns no persistent sessions. */
internal class MangaOcrModelRepository(
    private val context: Context,
    private val root: File = File(context.noBackupFilesDir, "manga-ocr"),
    private val catalogs: Map<MangaLocalOcrEngine, List<MangaOcrModelFile>> = MangaPaddleModelCatalog.files,
    private val fetch: suspend (MangaOcrModelFile, File, (Long) -> Unit) -> Unit = ::downloadMangaOcrFile,
) : MangaOcrModelGateway {
    private val lock = Mutex()
    private val mutableState = MutableStateFlow(catalogs.mapValues { (_, files) -> MangaOcrModelState(totalBytes = files.sumOf { it.bytes }) })
    override val state = mutableState.asStateFlow()

    private fun folder(engine: MangaLocalOcrEngine) = File(root, MangaPaddleModelCatalog.directory(engine))
    private fun files(engine: MangaLocalOcrEngine) = requireNotNull(catalogs[engine]) { "Unknown OCR model set" }
    private fun publish(engine: MangaLocalOcrEngine, status: MangaOcrModelState) {
        mutableState.update { it + (engine to status) }
    }
    private suspend fun valid(spec: MangaOcrModelFile, file: File): Boolean {
        if (!file.isFile || file.length() != spec.bytes) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == spec.sha256
    }

    private suspend fun installed(engine: MangaLocalOcrEngine): Boolean = files(engine).all { valid(it, File(folder(engine), it.name)) }
    private fun status(engine: MangaLocalOcrEngine, ready: Boolean): MangaOcrModelState {
        val total = files(engine).sumOf { it.bytes }
        return MangaOcrModelState(if (ready) MangaOcrModelPhase.READY else MangaOcrModelPhase.MISSING,
            if (ready) total else 0, total)
    }

    override suspend fun refresh() = withContext(Dispatchers.IO) {
        lock.withLock { catalogs.keys.forEach { publish(it, status(it, installed(it))) } }
    }

    override suspend fun download(engine: MangaLocalOcrEngine) = withContext(Dispatchers.IO) {
        lock.withLock {
            val root = folder(engine)
            val files = files(engine)
            val total = files.sumOf { it.bytes }
            try {
                check(root.isDirectory || root.mkdirs()) { "Cannot create OCR model directory" }
                var completed = 0L
                publish(engine, MangaOcrModelState(MangaOcrModelPhase.DOWNLOADING, 0, total))
                for (spec in files) {
                    if (!valid(spec, File(root, spec.name))) {
                        val temp = File(root, "${spec.name}.part")
                        try {
                            // Separate owned copies make deletion independent; reuse verified bytes
                            // from another installed set instead of downloading V6's detector again.
                            val reusable = catalogs.entries.firstNotNullOfOrNull { (other, otherFiles) ->
                                if (other == engine) null else otherFiles.firstOrNull { it.sha256 == spec.sha256 && it.bytes == spec.bytes }
                                    ?.let { File(folder(other), it.name) }?.takeIf { valid(spec, it) }
                            }
                            if (reusable != null) {
                                reusable.inputStream().use { input -> temp.outputStream().use { output ->
                                    val buffer = ByteArray(64 * 1024)
                                    while (true) {
                                        currentCoroutineContext().ensureActive()
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        output.write(buffer, 0, count)
                                    }
                                } }
                            } else fetch(spec, temp) { count -> publish(engine, MangaOcrModelState(
                                MangaOcrModelPhase.DOWNLOADING, completed + count.coerceIn(0, spec.bytes), total)) }
                            check(valid(spec, temp)) { context.getString(R.string.manga_ocr_integrity_failed) }
                            currentCoroutineContext().ensureActive()
                            Files.move(temp.toPath(), File(root, spec.name).toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                        } finally { temp.delete() }
                    }
                    completed += spec.bytes
                    publish(engine, MangaOcrModelState(MangaOcrModelPhase.DOWNLOADING, completed, total))
                }
                publish(engine, status(engine, true))
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) { publish(engine, status(engine, installed(engine))) }
                throw cancelled
            } catch (error: Exception) {
                // A cancelled socket reports IOException; preserve cancellation and reset state.
                if (!currentCoroutineContext().isActive) {
                    withContext(NonCancellable) { publish(engine, status(engine, installed(engine))) }
                    currentCoroutineContext().ensureActive()
                }
                publish(engine, MangaOcrModelState(MangaOcrModelPhase.FAILED, totalBytes = total, error = error.message?.take(300)))
                throw error
            }
        }
    }

    override suspend fun delete(engine: MangaLocalOcrEngine) = withContext(Dispatchers.IO) {
        lock.withLock {
            // Only known app-owned filenames; never remove caller-selected directories recursively.
            files(engine).forEach { spec -> listOf(spec.name, "${spec.name}.part").forEach { name ->
                val file = File(folder(engine), name)
                check(!file.exists() || file.delete()) { "Cannot delete OCR model file" }
            } }
            publish(engine, status(engine, false))
        }
    }

    suspend fun <T> withInstalled(engine: MangaLocalOcrEngine, block: suspend (File) -> T): T = withContext(Dispatchers.IO) {
        lock.withLock {
            val ready = installed(engine)
            publish(engine, status(engine, ready))
            check(ready) { context.getString(R.string.manga_ocr_download_required) }
            block(folder(engine))
        }
    }
}

private val mangaModelClient by lazy {
    OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.MINUTES).build()
}

private suspend fun downloadMangaOcrFile(spec: MangaOcrModelFile, target: File, progress: (Long) -> Unit) = coroutineScope {
    val call = mangaModelClient.newCall(Request.Builder().url(spec.url).build())
    // Cancel socket I/O immediately even while blocked in a synchronous read on Dispatchers.IO.
    val watcher = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
        try { awaitCancellation() } finally { call.cancel() }
    }
    try {
        call.execute().use { response ->
            check(response.isSuccessful) { "OCR model download: HTTP ${response.code}" }
            response.body.byteStream().use { input -> target.outputStream().buffered().use { out ->
                val buffer = ByteArray(64 * 1024)
                var count = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buffer)
                    if (n < 0) break
                    count += n
                    check(count <= spec.bytes) { "OCR model exceeds expected size" }
                    out.write(buffer, 0, n)
                    progress(count)
                }
            } }
        }
    } finally { watcher.cancel() }
}
