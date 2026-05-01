package io.legado.app.model.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheDownloadQueueTest {

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

    private fun drain(queue: CacheDownloadQueue): List<Int> {
        val result = mutableListOf<Int>()
        while (true) {
            val next = queue.next("book", emptySet()) ?: break
            result.add(next.chapterIndex)
        }
        return result
    }
}
