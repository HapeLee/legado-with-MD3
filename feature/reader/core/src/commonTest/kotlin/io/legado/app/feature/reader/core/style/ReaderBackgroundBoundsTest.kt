package io.legado.app.feature.reader.core.style

import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderRect
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import kotlin.test.assertEquals
import kotlin.test.Test

class ReaderBackgroundBoundsTest {

    private val baseStyle = ReaderTextStyle(colorArgb = 0xFF000000.toInt(), fontSizePx = 40f)

    private fun textElement(
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
        backgroundArgb: Int?,
    ): ReaderElement.Text = ReaderElement.Text(
        bounds = ReaderRect(left, top, right, bottom),
        baselinePx = bottom - 8f,
        value = "字",
        style = baseStyle.copy(backgroundArgb = backgroundArgb),
        selected = false,
        emphasized = false,
        chapterPosition = 0,
    )

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

    @Test fun emptyInputYieldsNoBands() {
        assertEquals(emptyList<ReaderBackgroundBand>(), emptyList<ReaderElement.Text>().mergeBackgroundBounds())
    }
}
