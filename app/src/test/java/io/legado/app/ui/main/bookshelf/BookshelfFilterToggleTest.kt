package io.legado.app.ui.main.bookshelf

import org.junit.Assert.assertEquals
import org.junit.Test

class BookshelfFilterToggleTest {
    @Test
    fun `repeating the filter action opens then closes it`() {
        var isSearch = false
        val intents = mutableListOf<BookshelfIntent>()
        repeat(2) {
            toggleBookshelfFilter(isSearch, 1L, 1L) { intent ->
                intents += intent
                if (intent is BookshelfIntent.SetSearchMode) isSearch = intent.active
            }
        }
        assertEquals(
            listOf(BookshelfIntent.SetSearchMode(true), BookshelfIntent.SetSearchMode(false)),
            intents,
        )
        assertEquals(false, isSearch)
    }

    @Test
    fun `closing the filter restores the group of the current tab`() {
        val intents = mutableListOf<BookshelfIntent>()
        toggleBookshelfFilter(true, 2L, 1L, intents::add)
        assertEquals(
            listOf(BookshelfIntent.SetSearchMode(false), BookshelfIntent.ChangeGroup(1L)),
            intents,
        )
    }

    @Test
    fun `opening the filter preserves its selected group`() {
        val intents = mutableListOf<BookshelfIntent>()
        toggleBookshelfFilter(false, 2L, 1L, intents::add)
        assertEquals(listOf(BookshelfIntent.SetSearchMode(true)), intents)
    }
}
