package io.legado.app.data.repository

import io.legado.app.data.dao.BookDao
import io.legado.app.domain.gateway.BookCacheDownloadGateway
import io.legado.app.help.book.isLocal
import io.legado.app.model.CacheBook
import io.legado.app.model.cache.CacheDownloadRequest
import io.legado.app.model.cache.ChapterSelection
import splitties.init.appCtx

class CacheBookDownloadRepository(
    private val bookDao: BookDao
) : BookCacheDownloadGateway {

    override suspend fun start(request: CacheDownloadRequest) {
        val book = bookDao.getBook(request.bookUrl) ?: return
        CacheBook.start(appCtx, request, isLocal = book.isLocal)
    }

    override suspend fun start(requests: List<CacheDownloadRequest>) {
        if (requests.isEmpty()) return
        CacheBook.start(appCtx, requests)
    }

    override suspend fun start(bookUrl: String, chapterIndices: List<Int>) {
        start(
            CacheDownloadRequest(
                bookUrl = bookUrl,
                selection = ChapterSelection.Indices(chapterIndices.toSet()),
            )
        )
    }

    override suspend fun start(bookUrl: String, startIndex: Int, endIndex: Int) {
        start(
            CacheDownloadRequest(
                bookUrl = bookUrl,
                selection = ChapterSelection.Range(startIndex, endIndex),
            )
        )
    }
}
