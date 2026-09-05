package io.legado.app.feature.reader.core.model

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class ReaderNineSliceLayoutTest {
    @Test
    fun centerStaysOnTextWhileEightFrameCellsUseExpandedBounds() {
        val image = ReaderTextBackgroundImage(
            "frame.png", 3, 1f,
            ninePatchLeft = 0.2f,
            ninePatchRight = 0.3f,
            ninePatchTop = 0.1f,
            ninePatchBottom = 0.2f,
        )
        val content = ReaderRect(10f, 20f, 40f, 50f)
        val frame = ReaderRect(0f, 16f, 55f, 58f)

        val cells = ReaderNineSliceLayout.cells(50, 40, content, frame, image)

        assertEquals(9, cells.size)
        assertEquals(ReaderNineSliceCell(ReaderIntRect(10, 4, 35, 32), content), cells[4])
        assertEquals(frame.left, cells.first().destination.left, 0f)
        assertEquals(frame.top, cells.first().destination.top, 0f)
        assertEquals(frame.right, cells.last().destination.right, 0f)
        assertEquals(frame.bottom, cells.last().destination.bottom, 0f)
    }

    @Test
    fun collapsedFrameRowsAreOmittedInsteadOfDrawingInvertedRects() {
        val image = ReaderTextBackgroundImage("frame.png", 3, 1f)
        val content = ReaderRect(0f, 0f, 20f, 10f)

        val cells = ReaderNineSliceLayout.cells(10, 10, content, content, image)

        assertTrue(cells.all { it.destination.width > 0f && it.destination.height > 0f })
        assertEquals(1, cells.size)
    }
}
