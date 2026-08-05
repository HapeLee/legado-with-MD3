package io.legado.app.domain.usecase

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import org.junit.Assert.assertSame
import org.junit.Test

class ChangeSourceSearchEventTest {

    @Test
    fun `result keeps parsed book and toc for source replacement`() {
        val book = Book(
            bookUrl = "https://example.com/book",
            name = "Book",
            author = "Author",
            origin = "https://example.com",
        )
        val toc = listOf(
            BookChapter(
                bookUrl = book.bookUrl,
                url = "https://example.com/chapter/1",
                title = "Chapter 1",
            )
        )

        val event = ChangeSourceSearchEvent.Result(
            searchBook = book.toSearchBook(),
            book = book,
            toc = toc,
        )

        assertSame(book, event.book)
        assertSame(toc, event.toc)
    }
}
