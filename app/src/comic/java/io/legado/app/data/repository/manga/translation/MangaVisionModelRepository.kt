package io.legado.app.data.repository.manga.translation

import android.content.Context
import io.legado.app.data.repository.manga.engine.MangaLocalEngineUnavailableException
import io.legado.app.domain.gateway.MangaVisionModelGateway
import io.legado.app.domain.model.manga.MangaOcrModelPhase
import io.legado.app.domain.model.manga.MangaVisionModelState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * 气泡检测权重的来源与校验。
 *
 * full 仍可内置权重：存在 `assets/manga-vision/<name>` 时首次使用复制到私有目录，开箱离线可用；
 * 内置权重移除后（`core` 变体天然没有）自动回落到下载，沿用字节数 + SHA-256 + 原子替换。
 * 缺失时 [requireModel] 抛能力缺失异常，排版退化为按文字块分组，不阻断云端或 AI 流程。
 */
internal class MangaVisionModelRepository(
    private val context: Context,
    private val root: File = File(context.noBackupFilesDir, "manga-vision"),
    private val spec: MangaOcrModelFile = MangaVisionModelCatalog.file,
    private val fetch: suspend (MangaOcrModelFile, File, (Long) -> Unit) -> Unit = ::downloadMangaOcrFile,
) : MangaVisionModelGateway {
    private val lock = Mutex()
    private val mutableState = MutableStateFlow(MangaVisionModelState(totalBytes = spec.bytes))
    override val state = mutableState.asStateFlow()
    private var verified: File? = null

    private fun target() = File(root, spec.name)

    /**
     * full 内置权重：存在时复制到私有目录，保持拆包前的开箱离线能力；core 没有该资源，
     * 这里直接返回 null 并回落到下载路径。调用方必须已持有 [lock]。
     */
    private fun seeded(): File? {
        val file = target()
        if (valid(file)) return file
        val input = try {
            context.assets.open("manga-vision/${spec.name}")
        } catch (_: FileNotFoundException) {
            return null
        }
        if (!root.isDirectory && !root.mkdirs()) return null
        val temp = File(root, "${spec.name}.part")
        return try {
            input.use { source -> temp.outputStream().buffered().use { source.copyTo(it) } }
            if (!valid(temp)) return null
            Files.move(temp.toPath(), file.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            file
        } catch (_: java.io.IOException) {
            null
        } finally {
            temp.delete()
        }
    }

    private fun valid(file: File): Boolean {
        if (!file.isFile || file.length() != spec.bytes) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == spec.sha256
    }

    private fun publish(file: File?) {
        mutableState.value = MangaVisionModelState(
            if (file != null) MangaOcrModelPhase.READY else MangaOcrModelPhase.MISSING,
            if (file != null) spec.bytes else 0,
            spec.bytes,
        )
    }

    override suspend fun refresh() = withContext(Dispatchers.IO) {
        lock.withLock { publish(seeded()?.also { verified = it }) }
    }

    override suspend fun download() = withContext(Dispatchers.IO) {
        lock.withLock {
            try {
                seeded()?.let { verified = it; publish(it); return@withLock }
                check(spec.url.isNotBlank()) { context.getString(io.legado.app.R.string.manga_vision_model_unavailable) }
                check(root.isDirectory || root.mkdirs()) { "Cannot create manga vision model directory" }
                val file = target()
                if (!valid(file)) {
                    mutableState.value = MangaVisionModelState(MangaOcrModelPhase.DOWNLOADING, 0, spec.bytes)
                    val temp = File(root, "${spec.name}.part")
                    try {
                        fetch(spec, temp) { count ->
                            mutableState.value = MangaVisionModelState(
                                MangaOcrModelPhase.DOWNLOADING, count.coerceIn(0, spec.bytes), spec.bytes)
                        }
                        check(valid(temp)) { context.getString(io.legado.app.R.string.manga_ocr_integrity_failed) }
                        currentCoroutineContext().ensureActive()
                        Files.move(temp.toPath(), file.toPath(),
                            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                    } finally { temp.delete() }
                }
                verified = file
                publish(file)
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) { publish(target().takeIf { valid(it) }) }
                throw cancelled
            } catch (error: Exception) {
                if (!currentCoroutineContext().isActive) {
                    withContext(NonCancellable) { publish(target().takeIf { valid(it) }) }
                    currentCoroutineContext().ensureActive()
                }
                mutableState.value = MangaVisionModelState(
                    MangaOcrModelPhase.FAILED, totalBytes = spec.bytes, error = error.message?.take(300))
                throw error
            }
        }
    }

    override suspend fun delete() = withContext(Dispatchers.IO) {
        lock.withLock {
            verified = null
            listOf(spec.name, "${spec.name}.part").forEach { name ->
                val file = File(root, name)
                check(!file.exists() || file.delete()) { "Cannot delete manga vision model" }
            }
            publish(null)
        }
    }

    /** 供推理使用；文件缺失或校验失败时以能力缺失表达，调用方据此降级排版。 */
    suspend fun requireModel(): File = withContext(Dispatchers.IO) {
        lock.withLock {
            verified?.takeIf { it.isFile && it.length() == spec.bytes }?.let { return@withLock it }
            val file = seeded() ?: throw MangaLocalEngineUnavailableException(
                context.getString(io.legado.app.R.string.manga_vision_model_unavailable))
            verified = file
            publish(file)
            file
        }
    }
}

/** 权重元数据。上传检测模型到项目自有发布渠道后填入 [url]，字节数与 sha256 已固化。 */
internal object MangaVisionModelCatalog {
    const val MODEL = "detector-v4-s_int8.onnx"
    const val BYTES = 11_120_765L
    const val SHA256 = "5fe9e4f576e49d4e7e8b0e029d6d3cdc252abd4694113e1cae120e62c931ea79"

    /** 待配置：留空时下载会明确报错，不会静默失败。 */
    const val URL = ""

    val file = MangaOcrModelFile(MODEL, URL, BYTES, SHA256)
}
