package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.BookCacheCleanupGateway

class ClearBookCacheUseCase(
    private val bookCacheCleanupGateway: BookCacheCleanupGateway
) {

    fun executeAll() {
        bookCacheCleanupGateway.clearAll()
    }

    suspend fun execute(bookUrl: String): String? {
        return if (bookCacheCleanupGateway.clear(bookUrl)) bookUrl else null
    }

    suspend fun execute(bookUrls: Set<String>): List<String> {
        if (bookUrls.isEmpty()) return emptyList()
        return bookUrls.mapNotNull { execute(it) }
    }
}
