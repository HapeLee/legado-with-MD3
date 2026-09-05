package io.legado.app.feature.reader.core.selection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderSelectionCoordinatesTest {
    private val lines = listOf(
        ReaderSelectionLine(10, listOf(1, 2, 1)),
        ReaderSelectionLine(15, listOf(1, 1)),
    )

    @Test
    fun mapsChapterOffsetsToLayoutColumns() {
        assertEquals(ReaderSelectionCoordinate(0, 0), ReaderSelectionCoordinateMapper.find(lines, 10))
        assertEquals(ReaderSelectionCoordinate(0, 1), ReaderSelectionCoordinateMapper.find(lines, 12))
        assertEquals(ReaderSelectionCoordinate(1, 0), ReaderSelectionCoordinateMapper.find(lines, 15))
    }

    @Test
    fun doesNotBridgeParagraphGapOrPageBoundary() {
        assertNull(ReaderSelectionCoordinateMapper.find(lines, 14))
        assertNull(ReaderSelectionCoordinateMapper.find(lines, 17))
    }
}
