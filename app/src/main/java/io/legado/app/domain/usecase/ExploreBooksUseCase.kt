package io.legado.app.domain.usecase

import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.gateway.ExploreBooksGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ExploreBooksUseCase(
    private val gateway: ExploreBooksGateway,
) {
    companion object {
        /** 排名类模块自动加载的最大书本数 */
        const val MAX_RANKING_BOOKS = 20

        /** 排名类模块自动加载的最大页数 */
        const val MAX_RANKING_PAGES = 3
    }

    suspend fun execute(
        sourceUrl: String,
        moduleUrl: String?,
        args: String?,
        page: Int = 1,
        key: String? = null,
    ): ExploreResult = withContext(Dispatchers.IO) {
        val request = resolveRequest(sourceUrl, moduleUrl, args, key)
        val books = gateway.exploreBooks(request.source, request.url, page, key = key)
        if (books.isNotEmpty()) gateway.saveSearchBooks(books)
        ExploreResult(request.url, books)
    }

    suspend fun executeForRanking(
        sourceUrl: String,
        moduleUrl: String?,
        args: String?
    ): List<SearchBook> = withContext(Dispatchers.IO) {
        val request = resolveRequest(sourceUrl, moduleUrl, args)
        var books = gateway.exploreBooks(request.source, request.url, page = 1)
        var page = 1
        while (books.size < MAX_RANKING_BOOKS && page < MAX_RANKING_PAGES) {
            page++
            val next = try {
                gateway.exploreBooks(request.source, request.url, page)
            } catch (_: Exception) {
                emptyList()
            }
            if (next.isEmpty()) break
            books = (books + next)
        }
        val result = books.take(MAX_RANKING_BOOKS)
        if (result.isNotEmpty()) gateway.saveSearchBooks(result)
        result
    }

    private suspend fun resolveRequest(
        sourceUrl: String,
        moduleUrl: String?,
        args: String?,
        key: String? = null,
    ): ExploreRequest {
        val base = gateway.getBookSource(sourceUrl)
            ?: throw SourceNotFound(sourceUrl)
        val source = args?.let { base.copy().also { s -> s.setTemporaryVariable(it) } } ?: base
        val url = moduleUrl
            ?: (if (key != null) source.searchUrl else null)
            ?: source.exploreUrl
            ?: throw NoExploreUrl(sourceUrl)
        return ExploreRequest(source, url)
    }

    data class ExploreResult(val resolvedUrl: String, val books: List<SearchBook>)

    private data class ExploreRequest(
        val source: BookSource,
        val url: String,
    )

    class SourceNotFound(url: String) : Exception("Source not found: ${url.take(60)}")
    class NoExploreUrl(url: String) : Exception("No explore URL for source: ${url.take(60)}")
}
