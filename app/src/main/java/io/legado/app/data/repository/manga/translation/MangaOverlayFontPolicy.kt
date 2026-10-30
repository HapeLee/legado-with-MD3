// SPDX-License-Identifier: Apache-2.0
// Adapted from ciddwd/overlay-translator, commit 64a7aa2c9605c88dd35bdeea42ab5c0f1c18ce5e.
// Modifications: Legado package, geometry types and bounded Android pipeline integration.
package io.legado.app.data.repository.manga.translation

import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.ceil
import kotlin.math.roundToInt

internal const val TRANSLATION_TARGET_GLYPH_RATIO = 1f
internal const val TRANSLATION_MIN_GLYPH_RATIO = .85f
internal data class MangaGlyphSizeRange(val minPx: Int, val targetPx: Int)

internal fun translationSourceGlyphHeight(
    lineHeightsPx: List<Int>,
    fallbackPx: Int,
): Float = translationSourceGlyphHeight(lineHeightsPx, fallbackPx, 0)

internal fun translationSourceGlyphHeight(
    lineHeightsPx: List<Int>,
    fallbackPx: Int,
    sourceGlyphCount: Int,
    sourceLineLengthsPx: List<Int> = emptyList(),
    maximumHeightPx: Float = Float.POSITIVE_INFINITY,
): Float {
    val heights = lineHeightsPx.filter { it > 0 }.sorted()
    if (heights.isEmpty()) return minOf(fallbackPx.coerceAtLeast(1).toFloat(), maximumHeightPx.coerceAtLeast(1f))
    val middle = heights.size / 2
    val thickness = if (heights.size % 2 == 0) heights[middle - 1] / 2f + heights[middle] / 2f
        else heights[middle].toFloat()
    // A detector column includes margins and sometimes ruby; its width is not a glyph size.
    // The amount of source text along those columns supplies an independent size ceiling.
    val lengths = sourceLineLengthsPx.filter { it > 0 }
    val alongText = if (sourceGlyphCount > 0 && lengths.isNotEmpty()) {
        lengths.sumOf { it.toLong() }.toFloat() / sourceGlyphCount / 1.05f
    } else Float.POSITIVE_INFINITY
    return minOf(thickness, alongText, maximumHeightPx).coerceAtLeast(1f)
}

/** Render-only policy: converts visible source ink height to a font size, not an OCR geometry. */
internal fun translationGlyphSizeRange(
    sourceHeightPx: Float,
    referenceInkHeightPx: Float,
    referenceTextSizePx: Float = 100f,
    constrainToBounds: Boolean = false,
    measuredInk: Boolean = false,
): MangaGlyphSizeRange? {
    if (!sourceHeightPx.isFinite() || sourceHeightPx <= 0f ||
        !referenceInkHeightPx.isFinite() || referenceInkHeightPx <= 0f ||
        !referenceTextSizePx.isFinite() || referenceTextSizePx <= 0f) return null
    // Detection rectangles include line margins. Treat their height as a font-size ceiling;
    // compensating for a font's shorter ink bounds magnifies the translation and adds wraps.
    val calibration = referenceTextSizePx / referenceInkHeightPx
    val target = sourceHeightPx * TRANSLATION_TARGET_GLYPH_RATIO *
        (if (measuredInk) calibration else minOf(1f, calibration))
    if (!target.isFinite() || target > Int.MAX_VALUE / 2f) return null
    val maxPx = target.roundToInt().coerceAtLeast(1)
    // Bounds affect available layout space, never the shared readable font-size floor.
    // Fixed areas may shrink for longer translations, while short translations retain the
    // calibrated source target instead of growing merely because the bubble has spare room.
    val minPx = ceil(if (constrainToBounds) sourceHeightPx * .35f
        else maxPx * TRANSLATION_MIN_GLYPH_RATIO).toInt().coerceIn(1, maxPx)
    return MangaGlyphSizeRange(minPx, maxPx)
}

/** Measure the current translation with its actual typeface; punctuation alone is not a font sample. */
internal fun measureTranslationGlyphRange(
    paint: Paint,
    text: CharSequence,
    sourceHeightPx: Float,
    constrainToBounds: Boolean = false,
    measuredInk: Boolean = false,
): MangaGlyphSizeRange? {
    val sample = buildString {
        var index = 0
        var count = 0
        while (index < text.length && count < 128) {
            val codePoint = Character.codePointAt(text, index)
            if (Character.isLetterOrDigit(codePoint)) {
                appendCodePoint(codePoint)
                count++
            }
            index += Character.charCount(codePoint)
        }
    }.ifEmpty { "国Hg" }
    val probe = Paint(paint).apply { textSize = 100f }
    val bounds = Rect()
    probe.getTextBounds(sample, 0, sample.length, bounds)
    return translationGlyphSizeRange(
        sourceHeightPx, bounds.height().toFloat(), constrainToBounds = constrainToBounds, measuredInk = measuredInk,
    )
}
