package io.legado.app.domain.usecase

import io.legado.app.data.dao.BookDao
import io.legado.app.data.entities.Book
import io.legado.app.help.book.BookHelp

class ClearBookCacheUseCase(
    private val bookDao: BookDao
) {

    fun execute(book: Book): String {
        BookHelp.clearCache(book)
        return book.bookUrl
    }

    suspend fun execute(bookUrls: Set<String>): List<String> {
        if (bookUrls.isEmpty()) return emptyList()
        return bookUrls.mapNotNull { bookUrl ->
            bookDao.getBook(bookUrl)?.let { execute(it) }
        }
    }
}
