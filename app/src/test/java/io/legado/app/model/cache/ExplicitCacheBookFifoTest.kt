package io.legado.app.model.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplicitCacheBookFifoTest {

    @Test
    fun ensureAppendsNewBooksInOrder() {
        val fifo = ExplicitCacheBookFifo()
        fifo.ensure("a")
        fifo.ensure("b")
        fifo.ensure("a")

        assertEquals(listOf("a", "b"), fifo.snapshot())
    }

    @Test
    fun headWhereSkipsPausedBooks() {
        val fifo = ExplicitCacheBookFifo()
        fifo.ensure("a")
        fifo.ensure("b")
        fifo.ensure("c")

        val paused = setOf("a")
        assertEquals("b", fifo.headWhere { it !in paused })
    }

    @Test
    fun moveToTailOnResume() {
        val fifo = ExplicitCacheBookFifo()
        fifo.ensure("a")
        fifo.ensure("b")
        fifo.ensure("c")

        fifo.moveToTail("a")

        assertEquals(listOf("b", "c", "a"), fifo.snapshot())
        assertEquals("b", fifo.headWhere { true })
    }

    @Test
    fun removeDropsBookFromOrder() {
        val fifo = ExplicitCacheBookFifo()
        fifo.ensure("a")
        fifo.ensure("b")

        assertTrue(fifo.remove("a"))
        assertFalse(fifo.remove("a"))
        assertEquals(listOf("b"), fifo.snapshot())
        assertNull(fifo.headWhere { it == "a" })
    }

    @Test
    fun snapshotThenFilterOutsideAvoidsNestedModelLock() {
        // 与 CacheBook.startProcessJob 相同：先 snapshot，再在锁外按状态选队首
        val fifo = ExplicitCacheBookFifo()
        fifo.ensure("paused")
        fifo.ensure("ready")
        fifo.ensure("waiting")

        val order = fifo.snapshot()
        val launchable = setOf("ready", "waiting")
        val head = order.firstOrNull { it in launchable }

        assertEquals("ready", head)
        assertEquals(listOf("paused", "ready", "waiting"), order)
    }
}
