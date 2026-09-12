package io.legado.app.feature.reader.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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

    @Test
    fun rawNinePatchGuideBorderIsExcludedFromEverySourceCell() {
        val image = ReaderTextBackgroundImage(
            "frame.9.png", 3, 1f,
            ninePatchLeft = 0.2f,
            ninePatchRight = 0.3f,
            ninePatchTop = 0.1f,
            ninePatchBottom = 0.2f,
        )

        val cells = ReaderNineSliceLayout.cells(
            bitmapWidth = 52,
            bitmapHeight = 42,
            content = ReaderRect(10f, 4f, 35f, 32f),
            frame = ReaderRect(0f, 0f, 50f, 40f),
            image = image,
        )

        assertEquals(ReaderIntRect(1, 1, 11, 5), cells.first().source)
        assertEquals(ReaderIntRect(36, 33, 51, 41), cells.last().source)
    }

    @Test
    fun fixedCornersKeepOneUniformConfiguredScale() {
        val image = ReaderTextBackgroundImage(
            "frame.png", 3, 0.5f,
            ninePatchLeft = 0.2f,
            ninePatchRight = 0.3f,
            ninePatchTop = 0.1f,
            ninePatchBottom = 0.2f,
        ).withBitmapSize(50, 40)
        val content = ReaderRect(5f, 2f, 30f, 30f)
        val frame = ReaderRect(
            content.left - image.contentInsetLeftPx,
            content.top - image.contentInsetTopPx,
            content.right + image.contentInsetRightPx,
            content.bottom + image.contentInsetBottomPx,
        )

        val topLeft = ReaderNineSliceLayout.cells(50, 40, content, frame, image).first()

        assertEquals(topLeft.source.right - topLeft.source.left, 10)
        assertEquals(topLeft.source.bottom - topLeft.source.top, 4)
        assertEquals(5f, topLeft.destination.width, 0f)
        assertEquals(2f, topLeft.destination.height, 0f)
    }

    @Test
    fun fractionalMarginsRoundBackToTheirOriginalPixelBoundaries() {
        val image = ReaderTextBackgroundImage(
            "frame.9.png", 3, 1f,
            ninePatchLeft = 7f / 31f,
            ninePatchRight = 9f / 31f,
            ninePatchTop = 5f / 29f,
            ninePatchBottom = 8f / 29f,
        )

        val cells = ReaderNineSliceLayout.cells(
            bitmapWidth = 33,
            bitmapHeight = 31,
            content = ReaderRect(7f, 5f, 22f, 21f),
            frame = ReaderRect(0f, 0f, 31f, 29f),
            image = image,
        )

        assertEquals(ReaderIntRect(1, 1, 8, 6), cells.first().source)
        assertEquals(ReaderIntRect(23, 22, 32, 30), cells.last().source)
    }
}
