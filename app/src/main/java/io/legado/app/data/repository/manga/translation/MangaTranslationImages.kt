package io.legado.app.data.repository.manga.translation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import io.legado.app.domain.model.manga.*
import io.legado.app.domain.reader.MangaImageRegion
import io.legado.app.domain.reader.MangaRegionDecoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/** Image analysis and raster rendering have independent memory budgets and partitioning. */
internal suspend fun mangaAnalysisPreview(
    decoder: MangaRegionDecoder<Bitmap>, top: Int, bottom: Int, pixels: Int = 2 * 1024 * 1024, maxSide: Int = 3072,
): Bitmap {
    val width = decoder.imageSize.width
    val size = mangaTranslationPreview(width, bottom - top, pixels, maxSide)
    val decoded = withContext(NonCancellable) { decoder.decodeRegion(MangaImageRegion(0, top, width, bottom), size.sampleSize) }
    try {
        currentCoroutineContext().ensureActive()
        val preview = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        try {
            Canvas(preview).apply {
                drawColor(Color.WHITE)
                drawBitmap(decoded, null, Rect(0, 0, size.width, size.height), Paint(Paint.FILTER_BITMAP_FLAG))
            }
            return preview
        } catch (error: Throwable) { preview.recycle(); throw error }
    } finally { decoded.recycle() }
}

internal fun mangaMutableBitmap(decoded: Bitmap): Bitmap {
    if (decoded.isMutable && decoded.config == Bitmap.Config.ARGB_8888) return decoded
    return try { checkNotNull(decoded.copy(Bitmap.Config.ARGB_8888, true)) { "Cannot allocate a mutable comic region" } }
    finally { decoded.recycle() }
}

internal suspend fun mangaAnalysisBands(decoder: MangaRegionDecoder<Bitmap>, language: String): List<MangaTranslationBand> {
    val (width, height) = decoder.imageSize
    if (mangaTranslationWholePage(width, height)) return listOf(MangaTranslationBand(0, height, 0, height))
    val target = max(1536, width * 5 / 2)
    val overlap = (width / 3).coerceIn(256, 1024)
    val cores = mutableListOf<Pair<Int, Int>>()
    var top = 0
    while (top < height) {
        currentCoroutineContext().ensureActive()
        val end = if (height - top <= target * 5 / 4) height else {
            val nominal = top + target
            val minimum = top + target * 3 / 5
            val maximum = min(height - 1, top + target * 5 / 4)
            val probeTop = max(top, minimum - width / 2)
            val probeBottom = min(height, maximum + width / 2)
            val preview = mangaAnalysisPreview(decoder, probeTop, probeBottom, 1024 * 1024, 2048)
            try {
                // Auxiliary local detection protects text blocks before any paid request. Unsupported
                // languages still use gutters; actual LOCAL recognition failures propagate later.
                val detected = if (language in setOf("ja", "zh", "ko", "en")) {
                    try { MangaLocalTextDetector.detect(preview, language) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { emptyList() }
                } else emptyList()
                val protected = detected.map {
                    val h = probeBottom - probeTop
                    (probeTop + (it.textBox.top * h / 1000).toInt() - 8).coerceAtLeast(0)..
                        (probeTop + kotlin.math.ceil(it.textBox.bottom * h / 1000).toInt() + 8).coerceAtMost(height)
                }
                val gaps = mangaBlankRows(preview).map {
                    (probeTop + it.first * (probeBottom - probeTop) / preview.height)..
                        (probeTop + it.last * (probeBottom - probeTop) / preview.height)
                }
                mangaTranslationBoundary(nominal, minimum, maximum, protected, gaps)
            } finally { preview.recycle() }
        }
        check(end > top) { "Translation partition made no progress" }
        cores += top to end
        top = end
    }
    return cores.map { (start, end) -> MangaTranslationBand(max(0, start - overlap), min(height, end + overlap), start, end) }
}

/** Flat near-white/near-black runs identify candidate gutters, not individual blank text lines. */
internal fun mangaBlankRows(bitmap: Bitmap): List<IntRange> {
    val result = mutableListOf<IntRange>()
    var start = -1
    val pixels = IntArray(bitmap.width)
    for (y in 0 until bitmap.height) {
        bitmap.getPixels(pixels, 0, bitmap.width, 0, y, bitmap.width, 1)
        var white = 0; var black = 0; var count = 0
        for (x in pixels.indices step max(1, bitmap.width / 128)) {
            val color = pixels[x]
            val light = (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000
            if (light >= 238) white++
            if (light <= 18) black++
            count++
        }
        val blank = max(white, black) >= count * 0.97f
        if (blank && start < 0) start = y
        if ((!blank || y == bitmap.height - 1) && start >= 0) {
            val end = if (blank) y else y - 1
            if (end - start >= 5) result += start..end
            start = -1
        }
    }
    return result
}

internal fun MangaTranslationBand.owns(region: MangaTranslatedRegion): Boolean = owns(region.textBox)

internal fun MangaTranslationBand.owns(box: MangaTextBox): Boolean {
    val center = top + (box.top + box.bottom) / 2 * (bottom - top) / 1000
    return center >= coreTop && center < coreBottom
}

internal fun MangaTranslatedRegion.pageRect(box: MangaTextBox, width: Int, band: MangaTranslationBand): Rect =
    box.pixels(width, band.bottom - band.top).apply { offset(0, band.top) }

internal fun MangaTranslatedRegion.pageLayoutRect(width: Int, band: MangaTranslationBand): Rect =
    layoutBox.layoutPixels(width, band.bottom - band.top).apply { offset(0, band.top) }

internal fun MangaTranslatedRegion.overlayRect(width: Int, height: Int, band: MangaTranslationBand, padding: Int = 4): Rect {
    val boxes = listOfNotNull(textBox, layoutBox, bubbleShape?.bounds) + eraseBoxes
    val rects = boxes.map { pageRect(it, width, band) } +
        MangaPageComposer.eraseRects(this, width, band.bottom - band.top).map { it.apply { offset(0, band.top) } }
    return Rect(max(0, rects.minOf { it.left } - padding), max(0, rects.minOf { it.top } - padding),
        min(width, rects.maxOf { it.right } + padding), min(height, rects.maxOf { it.bottom } + padding))
}

internal fun MangaTranslatedRegion.inOverlay(width: Int, band: MangaTranslationBand, rect: Rect): MangaTranslatedRegion {
    fun local(page: Rect): MangaTextBox {
        val pixels = Rect(page).apply { offset(-rect.left, -rect.top) }
        // Match the original pixel-aligned text area exactly. The tiny inward nudge prevents
        // Float round trips from flooring an integer left/top one pixel earlier in the ROI.
        return MangaTextBox(
            ((pixels.left + 0.01f) * 1000 / rect.width()).coerceIn(0f, 1000f),
            ((pixels.top + 0.01f) * 1000 / rect.height()).coerceIn(0f, 1000f),
            (pixels.right * 1000f / rect.width()).coerceIn(0f, 1000f),
            (pixels.bottom * 1000f / rect.height()).coerceIn(0f, 1000f),
        )
    }
    fun map(box: MangaTextBox): MangaTextBox = local(pageRect(box, width, band))
    val recognition = recognitionBox?.takeIf { Rect.intersects(pageRect(it, width, band), rect) }?.let(::map)
    return copy(textBox = map(textBox), layoutBox = local(pageLayoutRect(width, band)), eraseBoxes = eraseBoxes.map(::map), recognitionBox = recognition,
        bubbleShape = bubbleShape?.let { it.copy(bounds = map(it.bounds)) },
        sourceLines = sourceLines.filter { Rect.intersects(pageRect(it.bounds, width, band), rect) }.map { line ->
            val quad = line.quad?.let { source ->
                val local = FloatArray(8) { i -> if (i % 2 == 0) source.points[i / 2].x * width / 1000 - rect.left
                    else source.points[i / 2].y * (band.bottom - band.top) / 1000 + band.top - rect.top }
                // A clipped ROI cannot represent the full original quad. Keep the original
                // saved anchor intact and use the mapped rectangle for this render only.
                if ((0..3).all { local[it * 2] in 0f..rect.width().toFloat() && local[it * 2 + 1] in 0f..rect.height().toFloat() })
                    mangaSourceQuad(local, rect.width(), rect.height()) else null
            }
            line.copy(bounds = map(line.bounds), quad = quad)
        },
        sourceFontSize = sourceFontSize?.let { it * (band.bottom - band.top) / rect.height() },
        typesetting = typesetting?.let { it.copy(fontSize = it.fontSize * (band.bottom - band.top) / rect.height()) })
}

/** Only changed pixels are pasted back; an old source/layout rectangle must never clip cleanup. */
internal fun mangaMakeTransparentDelta(bitmap: Bitmap, before: IntArray) {
    require(bitmap.isMutable && bitmap.config == Bitmap.Config.ARGB_8888 && before.size == bitmap.width * bitmap.height)
    bitmap.setHasAlpha(true)
    val row = IntArray(bitmap.width)
    for (y in 0 until bitmap.height) {
        bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
        for (x in row.indices) if (row[x] == before[y * bitmap.width + x]) row[x] = 0
        bitmap.setPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
    }
}
