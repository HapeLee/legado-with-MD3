package io.legado.app.data.repository.manga.translation

import ai.onnxruntime.*
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import io.legado.app.R
import io.legado.app.domain.model.manga.MangaInpaintEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt
import kotlin.math.floor

/** One CPU session at a time, never two sets of weights resident after switching engines.
 * Native calls cannot be interrupted, so retain input/file leases until the call finishes.
 */
internal class MangaLocalInpainter(private val context: Context, private val models: MangaInpaintModelRepository) {
    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var release: Job? = null
    private var session: OrtSession? = null
    private var activeEngine: MangaInpaintEngine? = null

    suspend fun requireReady(engine: MangaInpaintEngine) = useModel(engine) { Unit }

    private suspend fun <T> useModel(engine: MangaInpaintEngine, block: (OrtSession) -> T): T = lock.withLock {
        require(engine.downloadable)
        release?.cancel()
        try {
            models.withInstalled(engine) { file ->
                val result = withContext(NonCancellable + Dispatchers.Default) { block(open(engine, file)) }
                currentCoroutineContext().ensureActive()
                result
            }
        } catch (error: OrtException) {
            session?.close(); session = null; activeEngine = null
            throw IllegalStateException(context.getString(R.string.manga_inpaint_runtime_unavailable) + " " + error.message.orEmpty().take(200), error)
        } catch (error: LinkageError) {
            throw IllegalStateException(context.getString(R.string.manga_inpaint_runtime_unavailable), error)
        } finally {
            release = scope.launch {
                delay(30_000)
                lock.withLock { session?.close(); session = null; activeEngine = null }
            }
        }
    }

    private fun open(engine: MangaInpaintEngine, file: File): OrtSession {
        if (activeEngine != engine) { session?.close(); session = null; activeEngine = engine }
        return session ?: OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(2); options.setInterOpNumThreads(1)
            options.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
            OrtEnvironment.getEnvironment().createSession(file.absolutePath, options).also { session = it }
        }
    }

    suspend fun erase(bitmap: Bitmap, mask: BooleanArray, engine: MangaInpaintEngine) {
        require(bitmap.isMutable && mask.size == bitmap.width * bitmap.height && mask.size <= 4 * 1024 * 1024)
        if (mask.none { it }) return
        val repaired = useModel(engine) { session -> infer(bitmap, mask, engine, session) }
        // Cancellation is observed before changing caller pixels.
        currentCoroutineContext().ensureActive()
        bitmap.setPixels(repaired, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    private fun infer(bitmap: Bitmap, mask: BooleanArray, engine: MangaInpaintEngine, session: OrtSession): IntArray {
        val input = mangaInpaintInput(bitmap, mask)
        val pixels = input.pixels; val holes = input.mask
        val env = OrtEnvironment.getEnvironment()
        val shape = longArrayOf(1, 3, 512, 512); val maskShape = longArrayOf(1, 1, 512, 512)
        fun tensor(channels: Int): OnnxTensor {
            val isMask = channels == 1
            if (engine == MangaInpaintEngine.MI_GAN) {
                val bytes = ByteBuffer.allocateDirect(512 * 512 * channels)
                if (isMask) holes.forEach { bytes.put(if (it) 0.toByte() else 255.toByte()) }
                else for (shift in listOf(16, 8, 0)) pixels.forEach { bytes.put((it ushr shift and 255).toByte()) }
                bytes.flip()
                return OnnxTensor.createTensor(env, bytes, if (isMask) maskShape else shape, OnnxJavaType.UINT8)
            }
            val floats = ByteBuffer.allocateDirect(512 * 512 * channels * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            if (isMask) holes.forEach { floats.put(if (it) 1f else 0f) }
            else for (shift in listOf(16, 8, 0)) pixels.forEach { floats.put((it ushr shift and 255) / 255f) }
            floats.flip()
            return OnnxTensor.createTensor(env, floats, if (isMask) maskShape else shape)
        }
        val output = IntArray(512 * 512)
        tensor(3).use { image -> tensor(1).use { maskTensor ->
            session.run(mapOf("image" to image, "mask" to maskTensor)).use { results ->
                val result = results[0] as OnnxTensor
                check(result.info.shape.contentEquals(shape)) { "Unexpected inpainting output dimensions" }
                val plane = 512 * 512
                if (engine == MangaInpaintEngine.MI_GAN) {
                    val buffer = result.byteBuffer
                    for (i in output.indices) output[i] = android.graphics.Color.rgb(buffer.get(i).toInt() and 255,
                        buffer.get(plane + i).toInt() and 255, buffer.get(2 * plane + i).toInt() and 255)
                } else {
                    val buffer = result.floatBuffer
                    fun channel(i: Int): Int = buffer.get(i).let { value ->
                        check(value.isFinite()) { "Non-finite inpainting output" }; value.roundToInt().coerceIn(0, 255)
                    }
                    for (i in output.indices) output[i] = android.graphics.Color.rgb(channel(i), channel(plane + i), channel(2 * plane + i))
                }
            }
        } }
        val scaled = Bitmap.createBitmap(output, 512, 512, Bitmap.Config.ARGB_8888)
        try {
            val unpadded = Bitmap.createBitmap(scaled, input.left, input.top, input.width, input.height)
            try {
                val originalSize = Bitmap.createScaledBitmap(unpadded, bitmap.width, bitmap.height, true)
                try {
                    val original = IntArray(mask.size).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
                    val values = IntArray(mask.size).also { originalSize.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
                    return mangaCompositeInpainting(original, values, mask)
                } finally { if (originalSize !== unpadded) originalSize.recycle() }
            } finally { if (unpadded !== scaled) unpadded.recycle() }
        } finally { scaled.recycle() }
    }
}

internal data class MangaInpaintInput(val pixels: IntArray, val mask: BooleanArray, val left: Int, val top: Int, val width: Int, val height: Int)

/** Preserve aspect ratio, edge-pad, and max-pool the mask so downscaling cannot lose small strokes. */
internal fun mangaInpaintInput(bitmap: Bitmap, mask: BooleanArray): MangaInpaintInput {
    require(mask.size == bitmap.width * bitmap.height)
    val scale = minOf(512f / bitmap.width, 512f / bitmap.height)
    val width = (bitmap.width * scale).roundToInt().coerceIn(1, 512)
    val height = (bitmap.height * scale).roundToInt().coerceIn(1, 512)
    val left = (512 - width) / 2; val top = (512 - height) / 2
    val resized = Bitmap.createScaledBitmap(bitmap, width, height, true)
    val source = IntArray(width * height)
    try { resized.getPixels(source, 0, width, 0, 0, width, height) } finally { if (resized !== bitmap) resized.recycle() }
    val pixels = IntArray(512 * 512) { index -> source[(index / 512 - top).coerceIn(0, height - 1) * width + (index % 512 - left).coerceIn(0, width - 1)] }
    val holes = BooleanArray(pixels.size)
    // RGB uses bilinear filtering: include all contributing source pixels. Nearest-only masks
    // expose blurred lettering to the model as supposedly intact background context.
    for (y in 0 until height) for (x in 0 until width) {
        val sx = floor((x + .5) * bitmap.width / width - .5).toInt()
        val sy = floor((y + .5) * bitmap.height / height - .5).toInt()
        holes[(y + top) * 512 + x + left] = (0..1).any { dy -> (0..1).any { dx ->
            mask[(sy + dy).coerceIn(0, bitmap.height - 1) * bitmap.width + (sx + dx).coerceIn(0, bitmap.width - 1)]
        } }
    }
    mask.forEachIndexed { index, selected -> if (selected)
        holes[(index / bitmap.width * height / bitmap.height + top) * 512 + index % bitmap.width * width / bitmap.width + left] = true
    }
    return MangaInpaintInput(pixels, holes, left, top, width, height)
}

internal fun mangaCompositeInpainting(original: IntArray, repaired: IntArray, mask: BooleanArray): IntArray {
    require(original.size == repaired.size && original.size == mask.size)
    return IntArray(original.size) { i -> if (mask[i]) (original[i] and 0xff000000.toInt()) or (repaired[i] and 0x00ffffff) else original[i] }
}
