package io.legado.app.ui.book.source.manage

import io.legado.app.data.entities.BookSourcePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookSourceOrderTest {
    @Test
    fun `filtered ascending move preserves hidden slots`() {
        val result = reorderBookSources(sources(1, 2, 3, 4), listOf("C", "A"), true)
        assertEquals(listOf("C", "B", "A", "D"), displayedIds(result, true))
    }

    @Test
    fun `filtered descending move preserves hidden slots`() {
        val result = reorderBookSources(sources(1, 2, 3, 4), listOf("A", "C"), false)
        assertEquals(listOf("D", "A", "B", "C"), displayedIds(result, false))
    }

    @Test
    fun `descending move preserves stable ties outside the filter`() {
        val result = reorderBookSources(sources(0, 0, 0, 0), listOf("C", "A"), false)
        assertEquals(listOf("C", "B", "A", "D"), displayedIds(result, false))
        assertEquals(4, result.map { it.customOrder }.toSet().size)
    }

    @Test
    fun `ascending move gives duplicate imported orders distinct positions`() {
        val result = reorderBookSources(sources(0, 0, 0, 0), listOf("D", "C", "B", "A"), true)
        assertEquals(listOf("D", "C", "B", "A"), displayedIds(result, true))
        assertEquals(listOf(1, 2, 3, 4), result.map { it.customOrder })
    }

    @Test
    fun `deleted source in a pending order is ignored`() {
        val result = reorderBookSources(sources(1, 2, 3), listOf("C", "deleted", "A"), true)
        assertEquals(listOf("C", "B", "A"), displayedIds(result, true))
    }

    @Test
    fun `empty or entirely deleted pending order does not rewrite sources`() {
        assertTrue(reorderBookSources(sources(1, 2), emptyList(), true).isEmpty())
        assertTrue(reorderBookSources(sources(1, 2), listOf("deleted"), false).isEmpty())
    }

    private fun sources(vararg orders: Int) = orders.mapIndexed { index, order ->
        BookSourcePart(bookSourceUrl = ('A' + index).toString(), customOrder = order)
    }

    private fun displayedIds(parts: List<BookSourcePart>, ascending: Boolean) =
        (if (ascending) parts.sortedBy { it.customOrder } else parts.sortedByDescending { it.customOrder })
            .map { it.bookSourceUrl }
}
