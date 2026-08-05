package io.legado.app.model.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheDownloadAdmissionQueueTest {

    @Test
    fun queuesNewBookWhenActiveLimitIsReached() {
        val queue = CacheDownloadAdmissionQueue(maxActiveBooks = 2)
        val request = request("c")

        assertTrue(queue.shouldQueue(request, setOf("a", "b")))

        queue.add(request)

        assertNull(queue.pollReady(setOf("a", "b")))
        assertEquals(request, queue.pollReady(setOf("a")))
    }

    @Test
    fun fifoExclusiveOnlyOneNewBookWhenMaxActiveIsOne() {
        val queue = CacheDownloadAdmissionQueue(maxActiveBooks = 1)
        assertTrue(queue.shouldQueue(request("b"), setOf("a")))
        assertFalse(queue.shouldQueue(request("a"), setOf("a")))

        queue.add(request("b"))
        queue.add(request("c"))

        assertNull(queue.pollReady(setOf("a")))
        assertEquals(request("b"), queue.pollReady(emptySet()))
        assertEquals(request("c"), queue.pollReady(emptySet()))
    }

    @Test
    fun activeBookRequestCanBypassLimit() {
        val queue = CacheDownloadAdmissionQueue(maxActiveBooks = 1)
        val activeRequest = request("a")

        queue.add(request("b"))
        queue.add(activeRequest)

        assertEquals(activeRequest, queue.pollReady(setOf("a")))
    }

    @Test
    fun removeBookDropsPendingRequestsForBook() {
        val queue = CacheDownloadAdmissionQueue(maxActiveBooks = 1)

        queue.add(request("a"))
        queue.add(request("b"))
        queue.add(request("a"))

        assertTrue(queue.removeBook("a"))
        assertFalse(queue.removeBook("a"))
        assertEquals(request("b"), queue.pollReady(emptySet()))
        assertNull(queue.pollReady(emptySet()))
    }

    @Test
    fun drainAllReturnsFifoOrderAndClearsQueue() {
        val queue = CacheDownloadAdmissionQueue(maxActiveBooks = 1)
        val a = request("a")
        val b = request("b")
        val c = request("c")
        queue.add(a)
        queue.add(b)
        queue.add(c)

        assertEquals(listOf(a, b, c), queue.drainAll())
        assertTrue(queue.isEmpty())
        assertEquals(emptyList<CacheDownloadRequest>(), queue.drainAll())
    }

    private fun request(bookUrl: String): CacheDownloadRequest {
        return CacheDownloadRequest(
            bookUrl = bookUrl,
            selection = ChapterSelection.Single(0),
            source = CacheDownloadSource.Batch,
        )
    }
}
