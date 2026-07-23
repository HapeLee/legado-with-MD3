package io.legado.app.ui.book.read.pageestimate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeuristicPageEstimatorTest {

    private val estimator = HeuristicPageEstimator()

    /**
     * 估算器现在返回**连续**页数，取整和下限由 [PageEstimateCalibration.apply] 负责 ——
     * 空章节只剩标题和章末留白那一点，走完取整仍是 1 页。
     */
    @Test
    fun `empty content occupies only the fixed chrome`() {
        val fraction = estimator.estimate(input(contentLength = 0))

        assertTrue("expected 0f..1f but was $fraction", fraction in 0f..1f)
        assertEquals(1, PageEstimateCalibration().apply(fraction))
    }

    @Test
    fun `larger text estimates more pages`() {
        val smallText = estimator.estimate(input(contentLength = 10_000, textSizePx = 24f))
        val largeText = estimator.estimate(input(contentLength = 10_000, textSizePx = 48f))

        assertTrue(largeText > smallText)
    }

    @Test
    fun `larger real font height estimates more pages`() {
        val compactFont = estimator.estimate(input(contentLength = 10_000, textHeightPx = 32f))
        val tallFont = estimator.estimate(input(contentLength = 10_000, textHeightPx = 48f))

        assertTrue(tallFont > compactFont)
    }

    /** 正文不足一页，但标题和章末留白把它顶过 1.0，取整后成为第 2 页。 */
    @Test
    fun `fixed chapter chrome can push content onto another page`() {
        val fraction = estimator.estimate(
            input(
                contentLength = 42,
                titleLength = 1,
                textSizePx = 10f,
                textHeightPx = 10f,
                lineSpacingPx = 0f,
                paragraphSpacingPx = 0f,
                contentWidthPx = 100,
                contentHeightPx = 100,
                titleTopSpacingPx = 10f,
                titleBottomSpacingPx = 10f,
                endPaddingPx = 20f,
            )
        )

        assertTrue("expected >1f but was $fraction", fraction > 1f)
        assertEquals(2, PageEstimateCalibration().apply(fraction))
    }

    private fun input(
        contentLength: Int,
        titleLength: Int = 10,
        textSizePx: Float = 32f,
        textHeightPx: Float = textSizePx * 1.2f,
        lineSpacingPx: Float = 8f,
        paragraphSpacingPx: Float = 4f,
        contentWidthPx: Int = 1080,
        contentHeightPx: Int = 1920,
        titleTopSpacingPx: Float = 20f,
        titleBottomSpacingPx: Float = 20f,
        endPaddingPx: Float = 20f,
    ) =
        ChapterPageEstimateInput(
            readerType = 0,
            titleLength = titleLength,
            includeTitle = true,
            contentLength = contentLength,
            textSizePx = textSizePx,
            textHeightPx = textHeightPx,
            lineSpacingPx = lineSpacingPx,
            paragraphSpacingPx = paragraphSpacingPx,
            titleTextSizePx = textSizePx * 1.25f,
            titleTextHeightPx = textHeightPx * 1.25f,
            titleLineSpacingPx = 0f,
            titleTopSpacingPx = titleTopSpacingPx,
            titleBottomSpacingPx = titleBottomSpacingPx,
            endPaddingPx = endPaddingPx,
            contentWidthPx = contentWidthPx,
            contentHeightPx = contentHeightPx,
        )
}
