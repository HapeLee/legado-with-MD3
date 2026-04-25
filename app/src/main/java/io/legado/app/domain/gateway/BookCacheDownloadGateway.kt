package io.legado.app.domain.gateway

interface BookCacheDownloadGateway {
    suspend fun start(bookUrl: String, chapterIndices: List<Int>)
}
