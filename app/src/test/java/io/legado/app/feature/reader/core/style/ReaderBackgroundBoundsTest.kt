package io.legado.app.feature.reader.core.style

import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderRect
import io.legado.app.feature.reader.core.model.ReaderTextBackgroundImage
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import org.junit.Assert.assertEquals
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
    ): ReaderElement.Text = ReaderElement.Text(
        bounds = ReaderRect(left, top, right, bottom),
        baselinePx = bottom - 8f,
        value = "字",
        style = baseStyle.copy(backgroundArgb = backgroundArgb, backgroundImage = backgroundImage),
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
        assertEquals(red, bands[0].colorArgb)
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
        assertEquals(listOf(red, blue, red), bands.map { it.colorArgb })
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
}
