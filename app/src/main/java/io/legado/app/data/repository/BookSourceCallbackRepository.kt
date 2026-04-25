package io.legado.app.data.repository

import io.legado.app.data.dao.BookDao
import io.legado.app.data.dao.BookSourceDao
import io.legado.app.domain.gateway.BookSourceCallbackGateway
import io.legado.app.model.SourceCallBack

class BookSourceCallbackRepository(
    private val bookDao: BookDao,
    private val bookSourceDao: BookSourceDao
) : BookSourceCallbackGateway {
    override suspend fun onDeleteFromShelf(bookUrl: String) {
        val book = bookDao.getBook(bookUrl) ?: return
        val source = bookSourceDao.getBookSource(book.origin)
        SourceCallBack.callBackBook(SourceCallBack.DEL_BOOK_SHELF, source, book)
    }
}
