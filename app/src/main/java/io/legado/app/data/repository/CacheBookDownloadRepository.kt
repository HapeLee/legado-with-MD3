package io.legado.app.data.repository

import io.legado.app.data.dao.BookDao
import io.legado.app.domain.gateway.BookCacheDownloadGateway
import io.legado.app.model.CacheBook
import splitties.init.appCtx

class CacheBookDownloadRepository(
    private val bookDao: BookDao
) : BookCacheDownloadGateway {

    override suspend fun start(bookUrl: String, chapterIndices: List<Int>) {
        val book = bookDao.getBook(bookUrl) ?: return
        CacheBook.start(appCtx, book, chapterIndices)
    }
}
