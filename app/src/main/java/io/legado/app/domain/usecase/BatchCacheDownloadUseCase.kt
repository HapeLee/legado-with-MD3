package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.BookCacheDownloadGateway
import io.legado.app.domain.model.CacheableBook
import io.legado.app.domain.repository.BookDomainRepository

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
        val indices = if (downloadAllChapters) {
            (0..book.lastChapterIndex).toList()
        } else {
            (book.durChapterIndex..book.lastChapterIndex).toList()
        }
        if (indices.isEmpty()) return false
        bookCacheDownloadGateway.start(book.bookUrl, indices)
        return true
    }
}
