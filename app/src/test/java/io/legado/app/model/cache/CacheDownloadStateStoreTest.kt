package io.legado.app.model.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheDownloadStateStoreTest {

    @Test
    fun queueAndRunningCountsAreDerivedFromBooks() {
        val store = CacheDownloadStateStore()

        store.updateBookQueue("a", waitingCount = 2, runningIndices = setOf(1))
        store.updateBookQueue("b", waitingCount = 3, runningIndices = setOf(4, 5))

        val state = store.state
        assertTrue(state.isRunning)
        assertEquals(5, state.totalWaiting)
        assertEquals(3, state.totalRunning)
    }

    @Test
    fun successAndFailureUpdateBookState() {
        val store = CacheDownloadStateStore()

        store.updateBookQueue("a", waitingCount = 0, runningIndices = setOf(1, 2))
        store.markFailed("a", 1)
        store.markSuccess("a", 2)

        val bookState = store.state.books.getValue("a")
        assertEquals(setOf(1), bookState.failedIndices)
        assertEquals(emptySet<Int>(), bookState.runningIndices)
        assertEquals(1, bookState.successCount)
        assertEquals(1, store.state.totalFailure)
        assertEquals(1, store.state.totalSuccess)
    }

    @Test
    fun duplicateSuccessDoesNotInflateCount() {
        val store = CacheDownloadStateStore()

        store.markSuccess("a", 1)
        store.markSuccess("a", 1)

        assertEquals(1, store.state.books.getValue("a").successCount)
        assertEquals(1, store.state.totalSuccess)
    }

    @Test
    fun removeBookRecalculatesRunningState() {
        val store = CacheDownloadStateStore()

        store.updateBookQueue("a", waitingCount = 1, runningIndices = emptySet())
        store.removeBook("a")

        assertFalse(store.state.isRunning)
        assertEquals(0, store.state.totalWaiting)
        assertEquals(emptyMap<String, CacheBookDownloadState>(), store.state.books)
    }
}
