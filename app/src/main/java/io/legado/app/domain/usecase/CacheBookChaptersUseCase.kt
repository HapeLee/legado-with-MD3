package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.BookCacheDownloadGateway
import io.legado.app.model.cache.CacheDownloadRequest
import io.legado.app.model.cache.CacheDownloadSource
import io.legado.app.model.cache.ChapterSelection

class CacheBookChaptersUseCase(
    private val bookCacheDownloadGateway: BookCacheDownloadGateway
) {

    suspend fun execute(bookUrl: String, chapterIndices: Iterable<Int>): Int {
        val indices = chapterIndices.distinct()
        if (indices.isEmpty()) return 0
        bookCacheDownloadGateway.start(
            CacheDownloadRequest(
                bookUrl = bookUrl,
                selection = ChapterSelection.Indices(indices.toSet()),
                source = CacheDownloadSource.Manual,
            )
        )
        return indices.size
    }

    suspend fun executeRange(bookUrl: String, startIndex: Int, endIndex: Int): Int {
        if (endIndex < startIndex) return 0
        bookCacheDownloadGateway.start(
            CacheDownloadRequest(
                bookUrl = bookUrl,
                selection = ChapterSelection.Range(startIndex, endIndex),
                source = CacheDownloadSource.Manual,
            )
        )
        return endIndex - startIndex + 1
    }
}
