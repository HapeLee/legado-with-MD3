package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.BookCacheDownloadGateway

class CacheBookChaptersUseCase(
    private val bookCacheDownloadGateway: BookCacheDownloadGateway
) {

    suspend fun execute(bookUrl: String, chapterIndices: Iterable<Int>): Int {
        val indices = chapterIndices.distinct()
        if (indices.isEmpty()) return 0
        bookCacheDownloadGateway.start(bookUrl, indices)
        return indices.size
    }
}
