package io.legado.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchContentRepositoryTest {

    @Test
    fun `beginSearch remembers latest search parameters for the book`() {
        val repository = SearchContentRepository()

        repository.beginSearch(
            bookUrl = "book-1",
            query = "keyword",
            replaceEnabled = true,
            regexReplace = false,
        )

        val session = repository.getLastSession("book-1")
        assertEquals("keyword", session?.query)
        assertEquals(true, session?.replaceEnabled)
        assertEquals(false, session?.regexReplace)
        assertEquals(emptyList<Any>(), session?.results)
    }

    @Test
    fun `getLastSession does not return another book search`() {
        val repository = SearchContentRepository()
        repository.beginSearch(
            bookUrl = "book-1",
            query = "keyword",
            replaceEnabled = false,
            regexReplace = false,
        )

        assertNull(repository.getLastSession("book-2"))
    }
}
