package io.legado.app.data.repository

import io.legado.app.data.dao.BookDao
import io.legado.app.domain.gateway.BookCacheCleanupGateway
import io.legado.app.help.book.BookHelp

class BookCacheCleanupRepository(
    private val bookDao: BookDao
) : BookCacheCleanupGateway {

    override fun clearAll() {
        BookHelp.clearCache()
    }

    override suspend fun clear(bookUrl: String): Boolean {
        val book = bookDao.getBook(bookUrl) ?: return false
        BookHelp.clearCache(book)
        return true
    }
}
