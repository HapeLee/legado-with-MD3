// SPDX-License-Identifier: Apache-2.0
// Processing order and Otsu reference: overlay-translator BitmapPreprocessor (Apache-2.0).
package io.legado.app.data.repository.manga.translation

import android.graphics.Bitmap
import io.legado.app.domain.model.manga.MangaOcrOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

internal data class MangaOcrInputSize(val width: Int, val height: Int)

/** Bound optional working copies without changing the page/tile ownership or normalized frame. */
internal fun mangaOcrInputSize(width: Int, height: Int, upscale2x: Boolean): MangaOcrInputSize {
    require(width > 0 && height > 0)
    val ratio = minOf(if (upscale2x) 2.0 else 1.0, 4096.0 / maxOf(width, height), sqrt(4_194_304.0 / (width.toLong() * height)))
    return MangaOcrInputSize((width * ratio).toInt().coerceAtLeast(1), (height * ratio).toInt().coerceAtLeast(1))
}

/** Returns only after native consumers release the input, including after cancellation. */
internal suspend fun <T> withMangaOcrInput(source: Bitmap, options: MangaOcrOptions, block: suspend (Bitmap) -> T): T {
    if (!options.hasPreprocessing) return block(source)
    return withContext(Dispatchers.Default) {
        val prepared = mangaOcrPreprocess(source, options)
        try { currentCoroutineContext().ensureActive(); block(prepared) } finally { prepared.recycle() }
    }
}

/** The caller always owns source; on failure every allocated working bitmap is released. */
internal suspend fun mangaOcrPreprocess(source: Bitmap, options: MangaOcrOptions): Bitmap {
    val size = mangaOcrInputSize(source.width, source.height, options.upscale2x)
    var current: Bitmap? = null
    try {
        current = if (size.width != source.width || size.height != source.height)
            Bitmap.createScaledBitmap(source, size.width, size.height, true)
        else source.copy(Bitmap.Config.ARGB_8888, true)
        checkNotNull(current)
        if (!options.invert && !options.binarize) return current
        if (!current.isMutable) {
            val mutable = current.copy(Bitmap.Config.ARGB_8888, true)
            current.recycle()
            current = checkNotNull(mutable)
        }
        val row = IntArray(current.width)
        fun luma(pixel: Int): Int {
            val a = pixel ushr 24
            val r = ((pixel ushr 16 and 255) * a + 255 * (255 - a) + 127) / 255
            val g = ((pixel ushr 8 and 255) * a + 255 * (255 - a) + 127) / 255
            val b = ((pixel and 255) * a + 255 * (255 - a) + 127) / 255
            val gray = (299 * r + 587 * g + 114 * b) / 1000
            return if (options.invert) 255 - gray else gray
        }
        val histogram = IntArray(256)
        if (options.binarize) for (y in 0 until current.height) {
            currentCoroutineContext().ensureActive()
            current.getPixels(row, 0, current.width, 0, y, current.width, 1)
            row.forEach { histogram[luma(it)]++ }
        }
        val threshold = if (options.binarize) mangaOtsuThreshold(histogram) else 0
        for (y in 0 until current.height) {
            currentCoroutineContext().ensureActive()
            current.getPixels(row, 0, current.width, 0, y, current.width, 1)
            for (x in row.indices) {
                val pixel = row[x]
                row[x] = if (options.binarize) {
                    if (luma(pixel) > threshold) -1 else 0xff000000.toInt()
                } else (pixel and 0xff000000.toInt()) or ((pixel xor 0x00ffffff) and 0x00ffffff)
            }
            current.setPixels(row, 0, current.width, 0, y, current.width, 1)
        }
        return current
    } catch (error: Throwable) { current?.takeIf { it !== source }?.recycle(); throw error }
}

internal fun mangaOtsuThreshold(histogram: IntArray): Int {
    require(histogram.size == 256 && histogram.all { it >= 0 })
    val total = histogram.sumOf { it.toLong() }
    val sum = histogram.indices.sumOf { it * histogram[it].toLong() }
    var background = 0L; var backgroundSum = 0L
    var bestVariance = -1.0; var threshold = 127
    for (t in histogram.indices) {
        background += histogram[t]
        backgroundSum += t * histogram[t].toLong()
        if (background == 0L) continue
        val foreground = total - background
        if (foreground == 0L) break
        val delta = backgroundSum.toDouble() / background - (sum - backgroundSum).toDouble() / foreground
        val variance = background.toDouble() * foreground * delta * delta
        if (variance > bestVariance) { bestVariance = variance; threshold = t }
    }
    // A uniform image must remain uniform; do not invent black text on a plain white page.
    return threshold
}
