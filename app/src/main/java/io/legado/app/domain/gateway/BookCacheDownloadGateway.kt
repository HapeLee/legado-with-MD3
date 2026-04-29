package io.legado.app.domain.gateway

import io.legado.app.model.cache.CacheDownloadRequest

interface BookCacheDownloadGateway {
    suspend fun start(request: CacheDownloadRequest)
    suspend fun start(requests: List<CacheDownloadRequest>)
    suspend fun start(bookUrl: String, chapterIndices: List<Int>)
    suspend fun start(bookUrl: String, startIndex: Int, endIndex: Int)
}
