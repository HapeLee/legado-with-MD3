package io.legado.app.domain.usecase

import io.legado.app.data.dao.BookChapterDao
import io.legado.app.data.dao.BookDao
import io.legado.app.data.dao.BookSourceDao
import io.legado.app.help.book.isLocal
import io.legado.app.model.SourceCallBack
import io.legado.app.model.localBook.LocalBook

class DeleteBooksUseCase(
    private val bookDao: BookDao,
    private val bookChapterDao: BookChapterDao,
    private val bookSourceDao: BookSourceDao
) {

    suspend fun execute(bookUrls: Set<String>, deleteOriginal: Boolean): List<String> {
        if (bookUrls.isEmpty()) return emptyList()
        val books = bookUrls.mapNotNull { bookDao.getBook(it) }
        books.forEach { book ->
            if (book.isLocal) {
                LocalBook.deleteBook(book, deleteOriginal)
            } else {
                val source = bookSourceDao.getBookSource(book.origin)
                SourceCallBack.callBackBook(SourceCallBack.DEL_BOOK_SHELF, source, book)
            }
            bookChapterDao.delByBook(book.bookUrl)
        }
        if (books.isNotEmpty()) {
            bookDao.delete(*books.toTypedArray())
        }
        return books.map { it.bookUrl }
    }
}
