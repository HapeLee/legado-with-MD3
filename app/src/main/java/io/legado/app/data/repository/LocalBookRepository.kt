package io.legado.app.data.repository

import io.legado.app.data.dao.BookDao
import io.legado.app.domain.gateway.LocalBookGateway
import io.legado.app.model.localBook.LocalBook

class LocalBookRepository(
    private val bookDao: BookDao
) : LocalBookGateway {
    override suspend fun deleteBook(bookUrl: String, deleteOriginal: Boolean) {
        val book = bookDao.getBook(bookUrl) ?: return
        LocalBook.deleteBook(book, deleteOriginal)
    }
}
