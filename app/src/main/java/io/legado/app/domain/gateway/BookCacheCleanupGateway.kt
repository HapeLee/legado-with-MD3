package io.legado.app.domain.gateway

interface BookCacheCleanupGateway {
    fun clearAll()
    suspend fun clear(bookUrl: String): Boolean
}
