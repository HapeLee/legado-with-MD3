package io.legado.app.data.repository.manga.translation

import android.graphics.Bitmap
import android.graphics.Rect
import io.legado.app.domain.model.manga.*
import io.legado.app.domain.reader.MangaImageRegion
import io.legado.app.domain.reader.MangaRegionDecoder
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.floor

/** Unrotated crops in the decoder's displayed EXIF-corrected frame. No preview pixel rounding. */
internal data class MangaCropFrame(val id: String, val regionId: String, val bounds: MangaImageRegion,
    val bandWidth: Int, val bandHeight: Int) {
    fun hint(box: MangaTextBox): MangaTextBox = MangaTextBox(
        ((box.left * bandWidth / 1000 - bounds.left) * 1000 / bounds.width).coerceIn(0f, 999f),
        ((box.top * bandHeight / 1000 - bounds.top) * 1000 / bounds.height).coerceIn(0f, 999f),
        ((box.right * bandWidth / 1000 - bounds.left) * 1000 / bounds.width).coerceIn(1f, 1000f),
        ((box.bottom * bandHeight / 1000 - bounds.top) * 1000 / bounds.height).coerceIn(1f, 1000f))


}

internal fun mangaCropFrame(region: MangaTranslatedRegion, width: Int, height: Int, index: Int): MangaCropFrame {
    val source = region.textBox
    val x = maxOf((source.right - source.left) * .3f, 12_000f / width)
    val y = maxOf((source.bottom - source.top) * .2f, 12_000f / height)
    val container = region.aiGeometry?.containerBox
    val l = minOf(source.left - x, container?.left ?: source.left) * width / 1000
    val t = minOf(source.top - y, container?.top ?: source.top) * height / 1000
    val r = maxOf(source.right + x, container?.right ?: source.right) * width / 1000
    val b = maxOf(source.bottom + y, container?.bottom ?: source.bottom) * height / 1000
    return MangaCropFrame("crop-$index", region.id, MangaImageRegion(floor(l).toInt().coerceIn(0, width - 1),
        floor(t).toInt().coerceIn(0, height - 1), ceil(r).toInt().coerceIn(1, width), ceil(b).toInt().coerceIn(1, height)), width, height)
}

internal suspend fun <T> withMangaOriginalCrop(decoder: MangaRegionDecoder<Bitmap>, band: MangaTranslationBand,
    frame: MangaCropFrame, block: suspend (Bitmap) -> T): T {
    val bounds = frame.bounds
    val size = mangaTranslationPreview(bounds.width, bounds.height, 1024 * 1024, 2048)
    val bitmap = withContext(NonCancellable) { decoder.decodeRegion(bounds.copy(top = bounds.top + band.top, bottom = bounds.bottom + band.top), size.sampleSize) }
    try {
        currentCoroutineContext().ensureActive()
        val prepared = if (bitmap.width == size.width && bitmap.height == size.height) bitmap
            else Bitmap.createScaledBitmap(bitmap, size.width, size.height, true)
        try { return block(prepared) } finally { if (prepared !== bitmap) prepared.recycle() }
    } finally { bitmap.recycle() }
}

/** Measured on original ROI pixels, not the downsampled page preview or repaired image. */
internal suspend fun measureMangaOriginalFonts(decoder: MangaRegionDecoder<Bitmap>, band: MangaTranslationBand,
    plan: MangaTranslationPlan): MangaTranslationPlan {
    var budget = 8L * 1024 * 1024
    val regions = plan.regions.mapIndexed { i, region ->
        if (region.typesetting != null || region.fontMeasurement != null || budget <= 0) return@mapIndexed region
        val frame = mangaCropFrame(region.copy(aiGeometry = null), decoder.imageSize.width, band.bottom - band.top, i)
        val predicted = mangaTranslationPreview(frame.bounds.width, frame.bounds.height, 1024 * 1024, 2048)
        val area = predicted.width.toLong() * predicted.height
        if (area > budget) return@mapIndexed region
        budget -= area
        withMangaOriginalCrop(decoder, band, frame) { bitmap ->
            val local = region.copy(textBox = frame.hint(region.textBox), layoutBox = frame.hint(region.textBox),
                eraseBoxes = region.eraseBoxes.map(frame::hint), sourceLines = emptyList())
            val samples = region.aiGeometry?.glyphSamples.orEmpty().filter {
                it.role == MangaGlyphRole.BODY && it.text.none { character -> character in "ぁぃぅぇぉっゃゅょゎァィゥェォッャュョヮヵヶｧｨｩｪｫｯｬｭｮ" } &&
                    it.text.codePoints().filter { point -> Character.isLetter(point) }.count() == 1L
            }.mapNotNull { sample ->
                val r = frame.hint(sample.bounds).pixels(bitmap.width, bitmap.height)
                val bg = MangaPageComposer.background(bitmap, r)
                var top = r.bottom; var bottom = r.top; var count = 0
                for (y in r.top until r.bottom) {
                    currentCoroutineContext().ensureActive()
                    for (x in r.left until r.right) {
                        if (mangaInkContrast(bitmap.getPixel(x, y), bg, 48)) { top = minOf(top, y); bottom = maxOf(bottom, y + 1); count++ }
                    }
                }
                if (count < 3 || bottom <= top || count > r.width().toLong() * r.height() * .8) null else (bottom - top).toFloat()
            }.sorted()
            val source = local.textBox.pixels(bitmap.width, bitmap.height)
            val bg = MangaPageComposer.background(bitmap, source)
            var ink = 0; var matching = 0; var edge = 0
            for (y in source.top until source.bottom) {
                currentCoroutineContext().ensureActive()
                for (x in source.left until source.right) if (mangaInkContrast(bitmap.getPixel(x, y), bg, 48)) ink++
            }
            // Reject texture and blank ROIs instead of recording a heuristic as measured evidence.
            val outer = Rect(source).apply { inset(-2, -2); intersect(0, 0, bitmap.width, bitmap.height) }
            for (x in outer.left until outer.right) for (y in listOf(outer.top, outer.bottom - 1)) {
                edge++; if (!mangaInkContrast(bitmap.getPixel(x, y), bg, 32)) matching++
            }
            for (y in outer.top until outer.bottom) for (x in listOf(outer.left, outer.right - 1)) {
                edge++; if (!mangaInkContrast(bitmap.getPixel(x, y), bg, 32)) matching++
            }
            val reliable = ink >= 3 && ink < source.width().toLong() * source.height() * .65 && matching >= edge * .85f
            if (samples.isEmpty() && !reliable) region else {
                val glyph = if (samples.isNotEmpty()) samples[samples.size / 2] else MangaPageComposer.sourceSize(bitmap, local)
                val normalized = glyph * frame.bounds.height / bitmap.height * 1000 / frame.bandHeight
                region.copy(sourceFontSize = normalized, fontMeasurement = MangaFontMeasurement(normalized,
                    samples.size.coerceAtLeast(1), if (samples.isNotEmpty()) MangaFontEvidence.ORIGINAL_GLYPHS else MangaFontEvidence.ORIGINAL_INK))
            }
        }
    }
    return plan.copy(regions = regions)
}
