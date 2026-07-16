package io.legado.app.service

import org.junit.Assert.assertEquals
import org.junit.Test

class TTSReadAloudProgressTest {

    @Test
    fun nextParagraphAdvancesFromCurrentRangePosition() {
        assertEquals(
            201,
            nextParagraphPosition(
                currentPosition = 180,
                paragraphLength = 100,
                paragraphStartPosition = 80,
            )
        )
    }

    @Test
    fun nextParagraphPreservesInitialSelectionOffset() {
        assertEquals(
            201,
            nextParagraphPosition(
                currentPosition = 120,
                paragraphLength = 100,
                paragraphStartPosition = 20,
            )
        )
    }

    @Test
    fun rangeProgressUsesTheUtteranceStartSnapshot() {
        assertEquals(
            340,
            currentRangePosition(
                utteranceStartPosition = 200,
                rangeStart = 140,
            )
        )
    }

    @Test
    fun longParagraphRangeCanCrossSeveralPages() {
        val pageStarts = listOf(0, 100, 200, 300, 400)

        assertEquals(
            3,
            findReadAloudPageIndex(
                currentPageIndex = 0,
                chapterPosition = 350,
                pageCount = pageStarts.size,
                pageStart = pageStarts::get,
            )
        )
    }

    @Test
    fun pageBoundaryChangesAreUsedAfterRelayout() {
        var pageStarts = listOf(0, 100, 200, 300)
        val pageStart = { index: Int -> pageStarts[index] }

        assertEquals(
            2,
            findReadAloudPageIndex(0, 250, pageStarts.size, pageStart)
        )

        pageStarts = listOf(0, 180, 360)

        assertEquals(
            1,
            findReadAloudPageIndex(0, 250, pageStarts.size, pageStart)
        )
    }

    @Test
    fun exactPageBoundaryWaitsForPlaybackToEnterThePage() {
        val pageStarts = listOf(0, 100, 200)

        assertEquals(
            0,
            findReadAloudPageIndex(0, 100, pageStarts.size, pageStarts::get)
        )
        assertEquals(
            1,
            findReadAloudPageIndex(0, 101, pageStarts.size, pageStarts::get)
        )
    }
}
