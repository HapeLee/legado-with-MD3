package io.legado.app.data.repository.manga

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import io.legado.app.domain.gateway.MangaImageModel
import java.nio.FloatBuffer
import kotlin.math.roundToInt

internal data class MangaBubble(val bounds: Rect, val mask: BooleanArray, val tile: Rect) {
    fun contains(x: Int, y: Int): Boolean {
        if (!tile.contains(x, y)) return false
        val mx = ((x - tile.left).toLong() * 256 / tile.width()).toInt().coerceIn(0, 255)
        val my = ((y - tile.top).toLong() * 256 / tile.height()).toInt().coerceIn(0, 255)
        return mask[my * 256 + mx]
    }

}

/** CPU baseline. Native calls complete before tensors and sessions are closed. */
internal class MangaImageInference(private val models: MangaImageModelRepository) {
    suspend fun repair(pixels: IntArray, mask: BooleanArray, width: Int, height: Int, checkCancellation: () -> Unit): IntArray? =
        models.use(MangaImageModel.AOT) { session ->
            val size = 512
            val ratio = minOf(size.toFloat() / width, size.toFloat() / height)
            val scaledWidth = (width * ratio).roundToInt().coerceIn(1, size)
            val scaledHeight = (height * ratio).roundToInt().coerceIn(1, size)
            val image = FloatArray(3 * size * size)
            val erase = FloatArray(size * size)
            mask.indices.forEach { i -> if (mask[i]) {
                erase[(i / width * ratio).toInt().coerceIn(0, size - 1) * size + (i % width * ratio).toInt().coerceIn(0, size - 1)] = 1f
            } }
            for (y in 0 until scaledHeight) {
                checkCancellation()
                for (x in 0 until scaledWidth) {
                    val sx = (x / ratio).toInt().coerceIn(0, width - 1)
                    val sy = (y / ratio).toInt().coerceIn(0, height - 1)
                    val c = pixels[sy * width + sx]
                    val i = y * size + x
                    if (erase[i] == 0f) {
                        image[i] = (c shr 16 and 255) / 127.5f - 1f
                        image[i + size * size] = (c shr 8 and 255) / 127.5f - 1f
                        image[i + 2 * size * size] = (c and 255) / 127.5f - 1f
                    }
                }
            }
            val environment = OrtEnvironment.getEnvironment()
            OnnxTensor.createTensor(environment, FloatBuffer.wrap(image), longArrayOf(1, 3, 512, 512)).use { input ->
                OnnxTensor.createTensor(environment, FloatBuffer.wrap(erase), longArrayOf(1, 1, 512, 512)).use { maskInput ->
                    session.run(mapOf("image" to input, "mask" to maskInput)).use { result ->
                        val tensor = result[0] as OnnxTensor
                        check((tensor.info as TensorInfo).shape.contentEquals(longArrayOf(1, 3, 512, 512)))
                        val values = tensor.floatBuffer
                        pixels.copyOf().also { output ->
                            output.indices.forEach { i ->
                                if (i % width == 0) checkCancellation()
                                if (mask[i]) {
                                    val at = ((i / width * ratio).toInt().coerceIn(0, size - 1)) * size + (i % width * ratio).toInt().coerceIn(0, size - 1)
                                    fun channel(offset: Int): Int {
                                        val value = values.get(at + offset)
                                        check(value.isFinite()) { "Non-finite AOT output" }
                                        return ((value + 1) * 127.5f).roundToInt().coerceIn(0, 255)
                                    }
                                    output[i] = Color.rgb(channel(0), channel(size * size), channel(2 * size * size))
                                }
                            }
                        }
                    }
                }
            }
        }

    private fun <T> image(session: OrtSession, bitmap: Bitmap, centered: Boolean, action: (OrtSession.Result, Float, Int, Int) -> T): T {
        val size = 1024
        val ratio = minOf(size.toFloat() / bitmap.width, size.toFloat() / bitmap.height)
        val width = (bitmap.width * ratio).roundToInt()
        val height = (bitmap.height * ratio).roundToInt()
        val dx = if (centered) (size - width) / 2 else 0
        val dy = if (centered) (size - height) / 2 else 0
        val canvasBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size)
        try {
            Canvas(canvasBitmap).apply {
                drawColor(if (centered) Color.rgb(114, 114, 114) else Color.BLACK)
                drawBitmap(bitmap, null, Rect(dx, dy, dx + width, dy + height), Paint(Paint.FILTER_BITMAP_FLAG))
            }
            canvasBitmap.getPixels(pixels, 0, size, 0, 0, size, size)
        } finally { canvasBitmap.recycle() }
        val values = FloatArray(3 * pixels.size)
        pixels.indices.forEach { i ->
            values[i] = (pixels[i] shr 16 and 255) / 255f
            values[i + pixels.size] = (pixels[i] shr 8 and 255) / 255f
            values[i + pixels.size * 2] = (pixels[i] and 255) / 255f
        }
        return OnnxTensor.createTensor(OrtEnvironment.getEnvironment(), FloatBuffer.wrap(values), longArrayOf(1, 3, size.toLong(), size.toLong())).use { input ->
            session.run(mapOf(session.inputNames.single() to input)).use { action(it, ratio, dx, dy) }
        }
    }

    suspend fun textMask(bitmap: Bitmap, checkCancellation: () -> Unit): BooleanArray? = models.use(MangaImageModel.CTD) { session ->
        image(session, bitmap, false) { result, ratio, _, _ ->
            val tensor = (0 until result.size()).map { result[it] as OnnxTensor }.single {
                val shape = (it.info as TensorInfo).shape
                shape.size == 4 && shape[1] == 1L
            }
            val shape = (tensor.info as TensorInfo).shape
            val h = shape[2].toInt(); val w = shape[3].toInt()
            val data = tensor.floatBuffer
            BooleanArray(bitmap.width * bitmap.height) { i ->
                if (i % bitmap.width == 0) checkCancellation()
                val x = ((i % bitmap.width) * ratio * w / 1024).toInt().coerceIn(0, w - 1)
                val y = ((i / bitmap.width) * ratio * h / 1024).toInt().coerceIn(0, h - 1)
                data.get(y * w + x) > .3f
            }
        }
    }

    suspend fun textLines(bitmap: Bitmap, checkCancellation: () -> Unit): List<Rect>? = models.use(MangaImageModel.CTD) { session ->
        image(session, bitmap, false) { result, ratio, _, _ ->
            val tensor = (0 until result.size()).map { result[it] as OnnxTensor }.single {
                val shape = (it.info as TensorInfo).shape
                shape.size == 4 && shape[1] == 2L
            }
            val shape = (tensor.info as TensorInfo).shape
            val h = shape[2].toInt(); val w = shape[3].toInt()
            val data = tensor.floatBuffer
            val visited = BooleanArray(w * h)
            val queue = IntArray(w * h)
            val boxes = mutableListOf<Rect>()
            for (start in visited.indices) {
                if (start % w == 0) checkCancellation()
                if (visited[start] || data.get(start) < .3f) continue
                var head = 0; var tail = 1
                queue[0] = start; visited[start] = true
                var left = w; var top = h; var right = 0; var bottom = 0; var confidence = 0.0
                while (head < tail) {
                    if (head % 4096 == 0) checkCancellation()
                    val at = queue[head++]; val x = at % w; val y = at / w
                    left = minOf(left, x); top = minOf(top, y); right = maxOf(right, x + 1); bottom = maxOf(bottom, y + 1)
                    confidence += data.get(at)
                    for (next in intArrayOf(if (x > 0) at - 1 else -1, if (x + 1 < w) at + 1 else -1,
                        if (y > 0) at - w else -1, if (y + 1 < h) at + w else -1)) {
                        if (next >= 0 && !visited[next] && data.get(next) >= .3f) { visited[next] = true; queue[tail++] = next }
                    }
                }
                if (tail < 4 || confidence / tail < .6) continue
                val scaleX = 1024f / w / ratio; val scaleY = 1024f / h / ratio
                val padding = maxOf(3, (minOf(right - left, bottom - top) * maxOf(scaleX, scaleY) * .5f).roundToInt())
                val box = Rect((left * scaleX).toInt() - padding, (top * scaleY).toInt() - padding,
                    (right * scaleX).roundToInt() + padding, (bottom * scaleY).roundToInt() + padding)
                if (box.intersect(0, 0, bitmap.width, bitmap.height) && !box.isEmpty) boxes += box
                check(boxes.size <= 512) { "Too many CTD lines in one tile" }
            }
            boxes
        }
    }

    suspend fun bubbles(bitmap: Bitmap, top: Int, checkCancellation: () -> Unit): List<MangaBubble> = models.use(MangaImageModel.BUBBLE) { session ->
        image(session, bitmap, true) { result, ratio, dx, dy ->
            val proposals = result[0] as OnnxTensor
            val prototypes = result[1] as OnnxTensor
            val shape = (proposals.info as TensorInfo).shape
            check(shape.contentEquals(longArrayOf(1, 37, 21504))) { "Unsupported bubble head" }
            check((prototypes.info as TensorInfo).shape.contentEquals(longArrayOf(1, 32, 256, 256))) { "Unsupported mask prototypes" }
            val head = proposals.floatBuffer; val proto = prototypes.floatBuffer
            val count = shape[2].toInt()
            fun value(channel: Int, anchor: Int) = head.get(channel * count + anchor)
            val selected = mutableListOf<Rect>()
            val output = mutableListOf<MangaBubble>()
            val anchors = (0 until count).filter { value(4, it) > .5f }.sortedByDescending { value(4, it) }.take(256)
            for (a in anchors) {
                checkCancellation()
                val cx = value(0, a); val cy = value(1, a); val bw = value(2, a); val bh = value(3, a)
                val box = Rect(((cx - bw / 2 - dx) / ratio).roundToInt(), ((cy - bh / 2 - dy) / ratio).roundToInt(),
                    ((cx + bw / 2 - dx) / ratio).roundToInt(), ((cy + bh / 2 - dy) / ratio).roundToInt())
                if (box.left < 0 || box.top < 0 || box.right > bitmap.width || box.bottom > bitmap.height || box.isEmpty) continue
                if (selected.any { other ->
                        val intersection = Rect(box)
                        if (!intersection.intersect(other)) false else {
                            val area = intersection.width().toLong() * intersection.height()
                            area.toDouble() / (box.width().toLong() * box.height() + other.width().toLong() * other.height() - area) > .5
                        }
                    }) continue
                selected += box
                val mask = BooleanArray(256 * 256)
                for (y in 0 until 256) {
                    checkCancellation()
                    val py = y * bitmap.height / 256
                    for (x in 0 until 256) {
                        val px = x * bitmap.width / 256
                        if (!box.contains(px, py)) continue
                        val mx = ((px * ratio + dx) / 4).toInt().coerceIn(0, 255)
                        val my = ((py * ratio + dy) / 4).toInt().coerceIn(0, 255)
                        var logit = 0f
                        for (c in 0 until 32) logit += value(5 + c, a) * proto.get(c * 65536 + my * 256 + mx)
                        mask[y * 256 + x] = logit > 0
                    }
                }
                box.offset(0, top)
                output += MangaBubble(box, mask, Rect(0, top, bitmap.width, top + bitmap.height))
                if (output.size >= 64) break
            }
            output
        }
    } ?: emptyList()
}
