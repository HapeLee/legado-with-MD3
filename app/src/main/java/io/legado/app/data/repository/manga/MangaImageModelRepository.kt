package io.legado.app.data.repository.manga

import android.app.Application
import android.net.Uri
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import java.nio.FloatBuffer
import io.legado.app.domain.gateway.*
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.okHttpClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

internal data class MangaModelArtifact(val url: String?, val hash: String, val sizeLimit: Long)

/** Weights live outside evictable image cache. All inference and installation leases are serialized. */
class MangaImageModelRepository(private val application: Application) : MangaImageModelGateway {
    private val lock = Mutex()
    private val operations = MangaImageModel.entries.associateWith { Mutex() }
    private val root get() = File(application.filesDir, "manga-image-models").apply { mkdirs() }
    private fun pointer(model: MangaImageModel) = File(root, "${model.name}.current")
    private fun installedHash(model: MangaImageModel) = pointer(model).takeIf { it.isFile }?.readText()?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }
    private fun file(model: MangaImageModel): File = installedHash(model)?.let { File(root, "${model.name}-$it.onnx") }
        ?: File(root, "${model.name.lowercase()}.onnx")
    private val mutableStates = MutableStateFlow(MangaImageModel.entries.associateWith { model ->
        MangaModelState(model, if (file(model).isFile) MangaModelStatus.READY else MangaModelStatus.MISSING,
            bytes = file(model).length())
    })
    override val states = mutableStates.asStateFlow()
    private val generationCounter = java.util.concurrent.atomic.AtomicLong()
    val generation: Long get() = generationCounter.get()

    internal fun artifact(model: MangaImageModel) = when (model) {
        MangaImageModel.CTD -> MangaModelArtifact(
            "https://github.com/zyddnys/manga-image-translator/releases/download/beta-0.3/comictextdetector.pt.onnx",
            "1a86ace74961413cbd650002e7bb4dcec4980ffa21b2f19b86933372071d718f", 110L * 1024 * 1024)
        MangaImageModel.BUBBLE -> MangaModelArtifact(
            "https://huggingface.co/mednasserallah/manga109-segmentation-bubble-onnx/resolve/main/manga109_segmentation_bubble_1024.onnx",
            "593cae61f4c9ffc773fee34d55c28069390d19415c24dc04600f983311618ea3", 16L * 1024 * 1024)
        // AOT is enabled only after a reproducible export has a pinned content identity.
        MangaImageModel.AOT -> MangaModelArtifact(null, "", 32L * 1024 * 1024)
    }
    private fun state(model: MangaImageModel, status: MangaModelStatus, bytes: Long = 0, total: Long = 0, error: String? = null) {
        val previous = mutableStates.value[model]?.status
        mutableStates.update { it + (model to MangaModelState(model, status, bytes, total, error)) }
        if (previous != status) generationCounter.incrementAndGet()
    }

    override suspend fun download(model: MangaImageModel) = install(model) { temporary ->
        val url = requireNotNull(artifact(model).url) { "Deployment artifact is not available" }
        val callRef = java.util.concurrent.atomic.AtomicReference<okhttp3.Call?>()
        coroutineScope {
            val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    continuation.invokeOnCancellation { callRef.get()?.cancel() }
                }
            }
            try {
                okHttpClient.newCallResponse(onCall = { callRef.set(it) }) { url(url) }.use { response ->
                    check(response.isSuccessful) { "HTTP ${response.code}" }
                    response.body.byteStream().use { copy(model, it, temporary, response.body.contentLength()) }
                }
            } finally { watcher.cancelAndJoin() }
        }
    }

    override suspend fun import(model: MangaImageModel, locator: String) = install(model) { temporary ->
        checkNotNull(application.contentResolver.openInputStream(Uri.parse(locator))).use { copy(model, it, temporary, -1) }
    }

    private suspend fun copy(model: MangaImageModel, input: InputStream, target: File, total: Long) {
        val limit = artifact(model).sizeLimit
        require(total <= limit) { "Model exceeds supported size" }
        check(root.usableSpace > (if (total > 0) total else limit) + 16L * 1024 * 1024) { "Insufficient storage" }
        target.outputStream().use { output ->
            val buffer = ByteArray(64 * 1024)
            var count = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                count += read
                check(count <= limit) { "Model exceeds supported size" }
                output.write(buffer, 0, read)
                state(model, MangaModelStatus.INSTALLING, count, total)
            }
        }
    }

    private suspend fun install(model: MangaImageModel, write: suspend (File) -> Unit) = withContext(Dispatchers.IO) {
        operations.getValue(model).withLock {
            val temporary = File(root, "${model.name}.installing")
            try {
                state(model, MangaModelStatus.INSTALLING)
                write(temporary)
                state(model, MangaModelStatus.VERIFYING, temporary.length(), temporary.length())
                val digest = MessageDigest.getInstance("SHA-256")
                temporary.inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                    }
                }
                val contentHash = digest.digest().joinToString("") { "%02x".format(it) }
                if (model != MangaImageModel.AOT) check(contentHash == artifact(model).hash) { "Model SHA-256 mismatch" }
                lock.withLock {
                    currentCoroutineContext().ensureActive()
                    open(temporary) { session ->
                        validate(model, session)
                    }
                    currentCoroutineContext().ensureActive()
                    val previous = file(model)
                    val deployed = File(root, "${model.name}-$contentHash.onnx")
                    val nextPointer = File(root, "${model.name}.pointer.tmp")
                    try {
                        nextPointer.writeText(contentHash)
                        Files.move(temporary.toPath(), deployed.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                        Files.move(nextPointer.toPath(), pointer(model).toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                    } catch (error: Throwable) {
                        if (installedHash(model) != contentHash) deployed.delete()
                        throw error
                    } finally { nextPointer.delete() }
                    if (previous != deployed) previous.delete()
                    generationCounter.incrementAndGet()
                }
                state(model, MangaModelStatus.READY, file(model).length())
            } catch (error: CancellationException) {
                state(model, if (file(model).isFile) MangaModelStatus.READY else MangaModelStatus.MISSING, file(model).length())
                throw error
            } catch (error: Exception) {
                state(model, if (file(model).isFile) MangaModelStatus.READY else MangaModelStatus.FAILED,
                    file(model).length(), error = error.message)
            } finally { temporary.delete() }
        }
    }

    override suspend fun delete(model: MangaImageModel) = withContext(Dispatchers.IO) {
        operations.getValue(model).withLock {
            state(model, MangaModelStatus.DELETING, file(model).length())
            generationCounter.incrementAndGet()
            try {
                withContext(NonCancellable) { lock.withLock {
                    check(!file(model).exists() || file(model).delete()) { "Cannot delete model" }
                    File(root, "${model.name}.installing").delete()
                    pointer(model).delete()
                } }
                state(model, MangaModelStatus.MISSING)
            } catch (error: Exception) {
                state(model, MangaModelStatus.FAILED, file(model).length(), error = error.message)
                if (error is CancellationException) throw error
            }
        }
    }

    internal suspend fun <T> use(model: MangaImageModel, action: (OrtSession) -> T): T? = withContext(Dispatchers.IO) {
        lock.withLock {
            if (states.value[model]?.status != MangaModelStatus.READY) return@withLock null
            open(file(model), action).also { currentCoroutineContext().ensureActive() }
        }
    }
    private fun <T> open(file: File, action: (OrtSession) -> T): T = OrtSession.SessionOptions().use { options ->
        options.setIntraOpNumThreads(2)
        OrtEnvironment.getEnvironment().createSession(file.absolutePath, options).use(action)
    }
    private fun validate(model: MangaImageModel, session: OrtSession) {
        fun shape(name: String, expected: LongArray) {
            val info = session.inputInfo[name]?.info as? TensorInfo
            check(info != null && info.type == OnnxJavaType.FLOAT && info.shape.contentEquals(expected)) { "Unsupported input contract: $name" }
        }
        if (model == MangaImageModel.AOT) {
            check(session.inputNames == setOf("image", "mask") && session.outputNames == setOf("repaired")) { "Unsupported AOT contract" }
            shape("image", longArrayOf(1, 3, 512, 512))
            shape("mask", longArrayOf(1, 1, 512, 512))
            val metadata = session.metadata.customMetadata
            check(metadata["legado.contract"] == "manga-aot-512-v1" &&
                metadata["legado.checkpoint.sha256"] == "878d541c68648969bc1b042a6e997f3a58e49b6c07c5636ad55130736977149f") { "Use the Legado AOT deployment exporter" }
        } else {
            check(session.inputNames.size == 1) { "Unsupported image input" }
            shape(session.inputNames.single(), longArrayOf(1, 3, 1024, 1024))
        }
        check(session.outputInfo.isNotEmpty()) { "Missing outputs" }
        val outputShapes = session.outputInfo.values.map { (it.info as? TensorInfo)?.shape?.toList() }
        when (model) {
            MangaImageModel.CTD -> check(outputShapes.any { it?.size == 4 && it[1] == 1L } &&
                outputShapes.any { it?.size == 4 && it[1] == 2L }) { "Unsupported CTD segmentation outputs" }
            MangaImageModel.BUBBLE -> check(outputShapes == listOf(listOf(1L, 37L, 21504L), listOf(1L, 32L, 256L, 256L))) { "Unsupported bubble outputs" }
            MangaImageModel.AOT -> check(outputShapes == listOf(listOf(1L, 3L, 512L, 512L))) { "Unsupported AOT output" }
        }
        val tensors = mutableMapOf<String, OnnxTensor>()
        try {
            session.inputInfo.forEach { (name, node) ->
                val info = node.info as TensorInfo
                val length = info.shape.fold(1L) { a, b -> a * b }
                check(length in 1..(3L * 1024 * 1024)) { "Unsupported input allocation" }
                tensors[name] = OnnxTensor.createTensor(OrtEnvironment.getEnvironment(), FloatBuffer.allocate(length.toInt()), info.shape)
            }
            session.run(tensors).use { result ->
                for (i in 0 until result.size()) {
                    val values = (result[i] as OnnxTensor).floatBuffer
                    while (values.hasRemaining()) check(values.get().isFinite()) { "Non-finite model output" }
                }
            }
        } finally { tensors.values.forEach { it.close() } }
    }
    internal fun identity(model: MangaImageModel) = if (states.value[model]?.status == MangaModelStatus.READY)
        "${model.name}:${installedHash(model) ?: artifact(model).hash}" else "${model.name}:basic"
    internal fun identity() = MangaImageModel.entries.joinToString(":") { identity(it) }
}
