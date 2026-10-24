package io.legado.app.feature.reader.core.style

import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderRect
import io.legado.app.feature.reader.core.model.ReaderTextBackgroundImage
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderBackgroundBoundsTest {

    private val baseStyle = ReaderTextStyle(colorArgb = 0xFF000000.toInt(), fontSizePx = 40f)

    private fun textElement(
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
        backgroundArgb: Int?,
        backgroundImage: ReaderTextBackgroundImage? = null,
        borderColorArgb: Int? = null,
        borderWidthPx: Float = 0f,
        borderRadiusPx: Float = 0f,
    ): ReaderElement.Text = ReaderElement.Text(
        bounds = ReaderRect(left, top, right, bottom),
        baselinePx = bottom - 8f,
        value = "字",
        style = baseStyle.copy(
            backgroundArgb = backgroundArgb,
            backgroundImage = backgroundImage,
            borderColorArgb = borderColorArgb,
            borderWidthPx = borderWidthPx,
            borderRadiusPx = borderRadiusPx,
        ),
        selected = false,
        emphasized = false,
        chapterPosition = 0,
    )

    private val bubble = ReaderTextBackgroundImage(source = "bubble.png", fit = 3, scale = 1f)

    @Test fun mergesAdjacentSameColorBoxesOnTheSameLine() {
        val red = 0xFFAA0000.toInt()
        val bands = listOf(
            textElement(0f, 40f, 0f, 50f, red),
            textElement(42f, 82f, 0f, 50f, red),
            textElement(84f, 124f, 0f, 50f, red),
        ).mergeBackgroundBounds()
        assertEquals(1, bands.size)
        assertEquals(red, bands[0].fillColorArgb)
        assertEquals(ReaderRect(0f, 0f, 124f, 50f), bands[0].bounds)
    }

    @Test fun differentColorStartsANewBand() {
        val red = 0xFFAA0000.toInt()
        val blue = 0xFF0000AA.toInt()
        val bands = listOf(
            textElement(0f, 40f, 0f, 50f, red),
            textElement(42f, 82f, 0f, 50f, blue),
            textElement(84f, 124f, 0f, 50f, red),
        ).mergeBackgroundBounds()
        assertEquals(listOf(red, blue, red), bands.map { it.fillColorArgb })
    }

    @Test fun differentLineStartsANewBand() {
        val red = 0xFFAA0000.toInt()
        val bands = listOf(
            textElement(0f, 40f, 0f, 50f, red),
            textElement(0f, 40f, 60f, 110f, red),
        ).mergeBackgroundBounds()
        assertEquals(2, bands.size)
    }

    @Test fun gapBeyondToleranceStartsANewBand() {
        val red = 0xFFAA0000.toInt()
        val bands = listOf(
            textElement(0f, 40f, 0f, 50f, red),
            textElement(200f, 240f, 0f, 50f, red),
        ).mergeBackgroundBounds()
        assertEquals(2, bands.size)
    }

    @Test fun unstyledElementsAreSkipped() {
        val red = 0xFFAA0000.toInt()
        val bands = listOf(
            textElement(0f, 40f, 0f, 50f, null),
            textElement(42f, 82f, 0f, 50f, red),
        ).mergeBackgroundBounds()
        assertEquals(1, bands.size)
        assertEquals(ReaderRect(42f, 0f, 82f, 50f), bands[0].bounds)
    }

    /** 带背景图的那一列只画图：色块是按行盒画的直边矩形，压在九宫格气泡上会切出直边。 */
    @Test fun backgroundImageSuppressesTheColorBand() {
        val bands = listOf(
            textElement(0f, 40f, 0f, 50f, 0xFFAA0000.toInt(), bubble),
        ).mergeBackgroundBounds()
        assertEquals(emptyList<ReaderBackgroundBand>(), bands)
    }

    /** 隔断：气泡两侧的同色色块不许跨过气泡并成一条，否则色条会从气泡中间穿过去。 */
    @Test fun backgroundImageBreaksTheColorBand() {
        val red = 0xFFAA0000.toInt()
        val bands = listOf(
            textElement(0f, 40f, 0f, 50f, red),
            textElement(42f, 82f, 0f, 50f, red, bubble),
            textElement(84f, 124f, 0f, 50f, red),
        ).mergeBackgroundBounds()
        assertEquals(2, bands.size)
        assertEquals(ReaderRect(0f, 0f, 40f, 50f), bands[0].bounds)
        assertEquals(ReaderRect(84f, 0f, 124f, 50f), bands[1].bounds)
    }

    @Test fun emptyInputYieldsNoBands() {
        assertEquals(emptyList<ReaderBackgroundBand>(), emptyList<ReaderElement.Text>().mergeBackgroundBounds())
    }

    /** 只设边框不设背景色：照样成带，只是没有填充。粗细为 0 视为没设边框。 */
    @Test fun borderOnlyElementYieldsAFillLessBand() {
        val green = 0xFF00AA00.toInt()
        val bands = listOf(
            textElement(0f, 40f, 0f, 50f, null, borderColorArgb = green, borderWidthPx = 2f),
        ).mergeBackgroundBounds()
        assertEquals(1, bands.size)
        assertNull(bands[0].fillColorArgb)
        assertEquals(green, bands[0].borderColorArgb)
        assertEquals(2f, bands[0].borderWidthPx)
    }

    @Test fun zeroWidthBorderIsIgnored() {
        val bands = listOf(
            textElement(
                0f, 40f, 0f, 50f, null,
                borderColorArgb = 0xFF00AA00.toInt(), borderWidthPx = 0f,
            ),
        ).mergeBackgroundBounds()
        assertEquals(emptyList<ReaderBackgroundBand>(), bands)
    }

    /** 同行同色的两段，边框任一项不同就不许并成一条。 */
    @Test fun differentBorderStartsANewBand() {
        val red = 0xFFAA0000.toInt()
        val green = 0xFF00AA00.toInt()
        val blue = 0xFF0000AA.toInt()
        val bands = listOf(
            textElement(0f, 40f, 0f, 50f, red, borderColorArgb = green, borderWidthPx = 2f),
            textElement(42f, 82f, 0f, 50f, red, borderColorArgb = blue, borderWidthPx = 2f),
        ).mergeBackgroundBounds()
        assertEquals(2, bands.size)
        assertEquals(green, bands[0].borderColorArgb)
        assertEquals(blue, bands[1].borderColorArgb)

        val radiusBands = listOf(
            textElement(0f, 40f, 0f, 50f, red, borderColorArgb = green, borderWidthPx = 2f),
            textElement(
                42f, 82f, 0f, 50f, red,
                borderColorArgb = green, borderWidthPx = 2f, borderRadiusPx = 4f,
            ),
        ).mergeBackgroundBounds()
        assertEquals(2, radiusBands.size)

        val widthBands = listOf(
            textElement(0f, 40f, 0f, 50f, red, borderColorArgb = green, borderWidthPx = 2f),
            textElement(42f, 82f, 0f, 50f, red, borderColorArgb = green, borderWidthPx = 3f),
        ).mergeBackgroundBounds()
        assertEquals(2, widthBands.size)
    }

    @Test fun sameBorderMergesIntoOneBand() {
        val red = 0xFFAA0000.toInt()
        val green = 0xFF00AA00.toInt()
        val bands = listOf(
            textElement(0f, 40f, 0f, 50f, red, borderColorArgb = green, borderWidthPx = 2f),
            textElement(42f, 82f, 0f, 50f, red, borderColorArgb = green, borderWidthPx = 2f),
        ).mergeBackgroundBounds()
        assertEquals(1, bands.size)
        assertEquals(ReaderRect(0f, 0f, 82f, 50f), bands[0].bounds)
    }

    /** 一段有边框一段没有：颜色虽然相同，边框键不同，不许并成一条。 */
    @Test fun borderedAndUnborderedDoNotMerge() {
        val red = 0xFFAA0000.toInt()
        val bands = listOf(
            textElement(0f, 40f, 0f, 50f, red, borderColorArgb = 0xFF00AA00.toInt(), borderWidthPx = 2f),
            textElement(42f, 82f, 0f, 50f, red),
        ).mergeBackgroundBounds()
        assertEquals(2, bands.size)
    }
}
