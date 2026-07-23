package io.legado.app.ui.book.read.pageestimate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WholeBookPageIndexTest {

    @Test
    fun `query uses cumulative page counts`() {
        val index = WholeBookPageIndex(3)
        index.initialize(intArrayOf(3, 5, 2))

        val state = requireNotNull(index.getState(chapterIndex = 1, localPageIndex = 2))

        assertEquals(6, state.currentPage)
        assertEquals(10, state.totalPages)
        assertEquals(3, state.chapterPage)
        assertEquals(5, state.chapterPageCount)
        assertTrue(state.estimated)
    }

    @Test
    fun `correction shifts current and following cumulative totals`() {
        val index = WholeBookPageIndex(3)
        index.initialize(intArrayOf(3, 5, 2))

        assertTrue(index.correct(chapterIndex = 1, realPageCount = 8))

        val corrected = requireNotNull(index.getState(chapterIndex = 1, localPageIndex = 0))
        val following = requireNotNull(index.getState(chapterIndex = 2, localPageIndex = 0))
        assertEquals(13, corrected.totalPages)
        assertEquals(8, corrected.chapterPageCount)
        assertEquals(12, following.currentPage)
        assertTrue(corrected.currentChapterExact)
        assertFalse(corrected.allPreviousChaptersExact)
    }

    @Test
    fun `estimated length update shifts suffix without marking chapter exact`() {
        val index = WholeBookPageIndex(3)
        index.initialize(intArrayOf(3, 5, 2))

        assertTrue(index.updateEstimatedPageCount(chapterIndex = 1, estimatedPageCount = 8))

        val updated = requireNotNull(index.getState(chapterIndex = 1, localPageIndex = 0))
        assertEquals(13, updated.totalPages)
        assertEquals(8, updated.chapterPageCount)
        assertFalse(updated.currentChapterExact)
        assertTrue(updated.estimated)
    }

    @Test
    fun `estimated length update cannot replace exact chapter`() {
        val index = WholeBookPageIndex(1)
        index.initialize(intArrayOf(3))
        index.correct(chapterIndex = 0, realPageCount = 5)

        assertFalse(index.updateEstimatedPageCount(chapterIndex = 0, estimatedPageCount = 8))
        assertEquals(5, index.getState(chapterIndex = 0, localPageIndex = 0)?.totalPages)
    }

    @Test
    fun `all exact chapters clear estimated flag`() {
        val index = WholeBookPageIndex(2)
        index.initialize(intArrayOf(2, 2))

        index.correct(1, 2)
        index.correct(0, 2)

        val state = requireNotNull(index.getState(1, 0))
        assertFalse(state.estimated)
        assertTrue(state.allPreviousChaptersExact)
    }

    @Test
    fun `invalidating changed exact chapter restores estimated suffix`() {
        val index = WholeBookPageIndex(2)
        index.initialize(intArrayOf(7, 3), exactChapters = setOf(0))

        assertTrue(index.invalidateExact(chapterIndex = 0, estimatedPageCount = 4))

        val state = requireNotNull(index.getState(0, 0))
        assertEquals(7, state.totalPages)
        assertEquals(4, state.chapterPageCount)
        assertFalse(state.currentChapterExact)
        assertTrue(state.estimated)
    }
}
