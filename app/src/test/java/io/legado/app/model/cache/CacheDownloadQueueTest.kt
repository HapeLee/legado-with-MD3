package io.legado.app.model.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheDownloadQueueTest {

    @Test
    fun explicitUpgradeWaitsForRunningPreloadForSingleAndRange() {
        for (selection in listOf(ChapterSelection.Single(3), ChapterSelection.Range(3, 4))) {
            val queue = CacheDownloadQueue()
            queue.enqueue(CacheDownloadRequest("book", ChapterSelection.Single(3), CacheDownloadSource.ReadPreload))
            assertEquals(3, queue.next("book", emptySet())?.chapterIndex)
            queue.enqueue(CacheDownloadRequest("book", selection, CacheDownloadSource.Manual))
            val next = queue.next("book", setOf(3))
            if (selection is ChapterSelection.Range) assertEquals(4, next?.chapterIndex)
            else assertNull(next)
            assertTrue(queue.isWaiting(3))
            assertEquals(1, queue.waitingCount())
            assertFalse(queue.hasLaunchableChapter(setOf(3)))
            assertTrue(queue.hasLaunchableChapter(emptySet()))
            assertNull(queue.next("book", setOf(3)))
            assertEquals(3, queue.next("book", emptySet())?.chapterIndex)
            assertTrue(queue.isExplicitDownload(3))
        }
    }

    @Test
    fun explicitPurposeSurvivesReadPreloadRetryAndPrioritization() {
        val queue = CacheDownloadQueue()
        queue.enqueue(CacheDownloadRequest("book", ChapterSelection.Range(2, 4), CacheDownloadSource.Manual))
        queue.enqueue(CacheDownloadRequest("book", ChapterSelection.Single(3), CacheDownloadSource.ReadPreload))
        queue.prioritize(3)
        assertEquals(3, queue.next("book", emptySet())?.chapterIndex)
        queue.enqueue(ChapterSelection.Single(3))
        assertTrue(queue.isExplicitDownload(3))
        queue.enqueue(CacheDownloadRequest("book", ChapterSelection.Single(8), CacheDownloadSource.ReadPreload))
        assertFalse(queue.isExplicitDownload(8))
        queue.clear()
        assertFalse(queue.isExplicitDownload(3))
    }

    @Test
    fun rangeReturnsChaptersLazily() {
        val queue = CacheDownloadQueue()

        queue.enqueue(ChapterSelection.Range(2, 4))

        assertEquals(3, queue.waitingCount())
        assertEquals(2, queue.next("book", emptySet())?.chapterIndex)
        assertEquals(2, queue.waitingCount())
        assertEquals(3, queue.next("book", emptySet())?.chapterIndex)
        assertEquals(4, queue.next("book", emptySet())?.chapterIndex)
        assertNull(queue.next("book", emptySet()))
    }

    @Test
    fun removeChapterSkipsChapterInsideRange() {
        val queue = CacheDownloadQueue()

        queue.enqueue(ChapterSelection.Range(0, 3))
        assertTrue(queue.removeChapter(1))

        assertEquals(listOf(0, 2, 3), drain(queue))
        assertFalse(queue.isWaiting(1))
    }

    @Test
    fun nextDoesNotReturnRunningChapter() {
        val queue = CacheDownloadQueue()

        queue.enqueue(ChapterSelection.Indices(setOf(1, 2)))

        assertEquals(2, queue.next("book", setOf(1))?.chapterIndex)
        assertNull(queue.next("book", setOf(1)))
        assertEquals(1, queue.next("book", emptySet())?.chapterIndex)
        assertNull(queue.next("book", emptySet()))
    }

    @Test
    fun explicitRequeueCanRetryConsumedChapter() {
        val queue = CacheDownloadQueue()

        queue.enqueue(ChapterSelection.Range(1, 1))
        assertEquals(1, queue.next("book", emptySet())?.chapterIndex)
        queue.enqueue(ChapterSelection.Single(1))

        assertEquals(1, queue.next("book", emptySet())?.chapterIndex)
    }

    @Test
    fun reEnqueueRangeRestoresRemovedChapter() {
        val queue = CacheDownloadQueue()

        queue.enqueue(ChapterSelection.Range(0, 2))
        assertTrue(queue.removeChapter(1))
        assertEquals(0, queue.next("book", emptySet())?.chapterIndex)
        queue.enqueue(ChapterSelection.Range(1, 1))

        assertEquals(1, queue.next("book", emptySet())?.chapterIndex)
        assertEquals(2, queue.next("book", emptySet())?.chapterIndex)
    }

    @Test
    fun prioritizeMovesChapterAheadOfFailedRetry() {
        val queue = CacheDownloadQueue()
        queue.enqueue(ChapterSelection.Range(0, 20))
        queue.enqueue(ChapterSelection.Single(10))
        queue.prioritize(13)

        assertEquals(13, queue.next("book", emptySet())?.chapterIndex)
        assertEquals(10, queue.next("book", emptySet())?.chapterIndex)
        assertEquals(0, queue.next("book", emptySet())?.chapterIndex)
    }

    @Test
    fun waitingIndicesListsIndicesBeforeRangeRemainder() {
        val queue = CacheDownloadQueue()
        queue.enqueue(ChapterSelection.Range(0, 3))
        queue.enqueue(ChapterSelection.Single(9))

        assertEquals(listOf(9, 0, 1, 2, 3), queue.waitingIndices())
        assertEquals(5, queue.waitingCount())
    }

    private fun drain(queue: CacheDownloadQueue): List<Int> {
        val result = mutableListOf<Int>()
        while (true) {
            val next = queue.next("book", emptySet()) ?: break
            result.add(next.chapterIndex)
        }
        return result
    }
}
