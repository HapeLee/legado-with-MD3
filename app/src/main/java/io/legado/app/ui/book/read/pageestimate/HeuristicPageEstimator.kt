package io.legado.app.ui.book.read.pageestimate

import kotlin.math.ceil

class HeuristicPageEstimator(
    private val capacityScale: Float = 0.82f,
) : ChapterPageEstimator {

    override fun estimate(input: ChapterPageEstimateInput): Float {
        val glyphWidth = input.textSizePx * 0.95f
        val lineHeight = input.textHeightPx + input.lineSpacingPx
        val charsPerLine = input.contentWidthPx / glyphWidth.coerceAtLeast(1f)
        val linesPerPage = input.contentHeightPx / lineHeight.coerceAtLeast(1f)
        val capacity = charsPerLine * linesPerPage * capacityScale * calculateParagraphLoss(input)
        val bodyPageFraction = input.contentLength.coerceAtLeast(0) / capacity.coerceAtLeast(1f)
        val titlePageFraction = calculateTitleHeight(input) / input.contentHeightPx
        val endPaddingPageFraction = input.endPaddingPx / input.contentHeightPx

        return bodyPageFraction + titlePageFraction + endPaddingPageFraction
    }

    private fun calculateTitleHeight(input: ChapterPageEstimateInput): Float {
        if (!input.includeTitle) return 0f
        val titleGlyphWidth = input.titleTextSizePx * 0.95f
        val titleCharsPerLine = input.contentWidthPx / titleGlyphWidth.coerceAtLeast(1f)
        val titleLineCount = ceil(input.titleLength / titleCharsPerLine.coerceAtLeast(1f))
            .coerceAtLeast(1f)
        val titleLineHeight = input.titleTextHeightPx + input.titleLineSpacingPx
        return input.titleTopSpacingPx +
            titleLineCount * titleLineHeight.coerceAtLeast(1f) +
            input.titleBottomSpacingPx
    }

    private fun calculateParagraphLoss(input: ChapterPageEstimateInput): Float {
        val ratio = input.paragraphSpacingPx /
            (input.textHeightPx + input.lineSpacingPx).coerceAtLeast(1f)
        return (1f - ratio * 0.08f).coerceIn(0.7f, 1f)
    }
}
