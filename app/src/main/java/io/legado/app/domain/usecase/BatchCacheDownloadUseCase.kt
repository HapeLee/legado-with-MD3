package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.BookCacheDownloadGateway
import io.legado.app.domain.model.CacheableBook
import io.legado.app.domain.repository.BookDomainRepository
import io.legado.app.model.cache.CacheDownloadRequest
import io.legado.app.model.cache.CacheDownloadSource
import io.legado.app.model.cache.ChapterSelection

class BatchCacheDownloadUseCase(
    private val bookRepository: BookDomainRepository,
    private val bookCacheDownloadGateway: BookCacheDownloadGateway
) {

    suspend fun execute(
        bookUrls: Set<String>,
        downloadAllChapters: Boolean,
        skipAudioBooks: Boolean = false
    ): Int {
        if (bookUrls.isEmpty()) return 0
        var count = 0
        bookRepository.getCacheableBooks(bookUrls).forEach { book ->
            if (startIfNeeded(book, downloadAllChapters, skipAudioBooks)) {
                count++
            }
        }
        return count
    }

    private suspend fun startIfNeeded(
        book: CacheableBook,
        downloadAllChapters: Boolean,
        skipAudioBooks: Boolean
    ): Boolean {
        if (book.isLocal) return false
        if (skipAudioBooks && book.isAudio) return false
        val startIndex = if (downloadAllChapters) 0 else book.durChapterIndex
        val endIndex = book.lastChapterIndex
        if (endIndex < startIndex) return false
        bookCacheDownloadGateway.start(
            CacheDownloadRequest(
                bookUrl = book.bookUrl,
                selection = ChapterSelection.Range(startIndex, endIndex),
                source = CacheDownloadSource.Batch,
            )
        )
        return true
    }
}
