package io.legado.app.domain.usecase

import io.legado.app.constant.AppLog
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.gateway.BookSearchGateway
import io.legado.app.domain.model.BookSearchScope
import io.legado.app.exception.NoStackTraceException
import io.legado.app.model.webBook.WebBook
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.coroutineContext

data class BookSearchRequest(
    val keyword: String,
    val page: Int,
    val scope: BookSearchScope,
    val precision: Boolean,
    val concurrency: Int,
)

sealed interface SearchRunEvent {
    data object Started : SearchRunEvent

    data class Progress(
        val upsertBooks: List<SearchBook>,
        val removedBookUrls: List<String>,
        val resultCount: Int,
        val processedSources: Int,
        val totalSources: Int,
    ) : SearchRunEvent

    data class Finished(
        val isEmpty: Boolean,
        val hasMore: Boolean,
    ) : SearchRunEvent
}

class BookSearchControl {
    private val isResumed = MutableStateFlow(true)

    fun pause() {
        isResumed.value = false
    }

    fun resume() {
        isResumed.value = true
    }

    suspend fun awaitResumed() {
        isResumed.first { it }
    }
}

class SearchBooksUseCase(
    private val gateway: BookSearchGateway,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun execute(
        request: BookSearchRequest,
        control: BookSearchControl,
    ): Flow<SearchRunEvent> = flow {
        val keyword = request.keyword.trim()
        if (keyword.isBlank()) return@flow

        val sourceParts = gateway.getBookSourceParts(request.scope)
        if (sourceParts.isEmpty()) {
            throw NoStackTraceException("启用书源为空")
        }

        val searchableSources = coroutineScope {
            sourceParts.map { part ->
                async(Dispatchers.IO) {
                    val source = gateway.getBookSource(part.bookSourceUrl) ?: return@async null
                    if (source.bookSourceType != 0 || source.searchUrl.isNullOrBlank()) {
                        return@async null
                    }
                    SearchableSource(part, source)
                }
            }.awaitAll().filterNotNull()
        }
        if (searchableSources.isEmpty()) {
            throw NoStackTraceException("可搜索书源为空")
        }

        val merger = SearchResultMerger(keyword, request.precision)
        val concurrency = request.concurrency.coerceAtLeast(1)
        var hasMore = false
        var processedSources = 0
        var failedSources = 0
        var firstFailureMessage: String? = null

        emit(SearchRunEvent.Started)

        searchableSources.asFlow()
            .flatMapMerge(concurrency) { searchableSource ->
                flow {
                    control.awaitResumed()
                    emit(searchSource(searchableSource, keyword, request.page, request.precision))
                }.flowOn(Dispatchers.IO)
            }
            .collect { result ->
                currentCoroutineContext().ensureActive()
                processedSources++
                when (result) {
                    is SourceSearchResult.Found -> {
                        result.books.forEach { it.releaseHtmlData() }
                        hasMore = hasMore || (result.supportsNextPage && result.books.isNotEmpty())
                        if (result.books.isNotEmpty()) {
                            gateway.saveSearchBooks(result.books)
                        }
                        val change = merger.merge(result.books)
                        emit(
                            SearchRunEvent.Progress(
                                upsertBooks = change.upsertBooks,
                                removedBookUrls = change.removedBookUrls,
                                resultCount = merger.count,
                                processedSources = processedSources,
                                totalSources = searchableSources.size,
                            )
                        )
                    }

                    is SourceSearchResult.Failed -> {
                        failedSources++
                        if (firstFailureMessage.isNullOrBlank()) {
                            firstFailureMessage = result.throwable.localizedMessage
                        }
                        AppLog.put("书源搜索出错\n${result.throwable.localizedMessage}", result.throwable)
                        emit(
                            SearchRunEvent.Progress(
                                upsertBooks = emptyList(),
                                removedBookUrls = emptyList(),
                                resultCount = merger.count,
                                processedSources = processedSources,
                                totalSources = searchableSources.size,
                            )
                        )
                    }
                }
            }

        if (merger.count == 0 && failedSources == searchableSources.size) {
            val error = firstFailureMessage?.takeIf { it.isNotBlank() } ?: "全部书源搜索失败"
            throw NoStackTraceException(error)
        }

        emit(
            SearchRunEvent.Finished(
                isEmpty = merger.count == 0,
                hasMore = hasMore && !merger.resultLimitReached,
            )
        )
    }.flowOn(Dispatchers.IO)

    private suspend fun searchSource(
        searchableSource: SearchableSource,
        keyword: String,
        page: Int,
        precision: Boolean,
    ): SourceSearchResult {
        return try {
            val source = searchableSource.source
            val supportsSearchPage = source.supportsSearchPage()
            if (page > 1 && !supportsSearchPage) {
                return SourceSearchResult.Found(emptyList())
            }
            val books = withTimeout(30000L) {
                WebBook.searchBookAwait(
                    source,
                    keyword,
                    page,
                    filter = { name, author ->
                        !precision ||
                            name.contains(keyword, ignoreCase = true) ||
                            author.contains(keyword, ignoreCase = true)
                    }
                )
            }
            SourceSearchResult.Found(books, supportsSearchPage)
        } catch (exception: Throwable) {
            coroutineContext.ensureActive()
            if (exception is CancellationException) throw exception
            SourceSearchResult.Failed(exception)
        }
    }



    private data class SearchableSource(
        val part: BookSourcePart,
        val source: BookSource,
    )
    private sealed interface SourceSearchResult {
        data class Found(
            val books: List<SearchBook>,
            val supportsNextPage: Boolean = false,
        ) : SourceSearchResult

        data class Failed(val throwable: Throwable) : SourceSearchResult
    }

    private class SearchResultMerger(
        private val keyword: String,
        private val precision: Boolean,
    ) {
        private companion object {
            const val MAX_RETAINED_SEARCH_RESULTS = 1000
        }

        private val equalBooks = LinkedHashMap<SearchBookKey, SearchBook>()
        private val containsBooks = LinkedHashMap<SearchBookKey, SearchBook>()
        private val otherBooks = LinkedHashMap<SearchBookKey, SearchBook>()
        var resultLimitReached = false
            private set

        val count: Int
            get() = equalBooks.size + containsBooks.size + otherBooks.size

        suspend fun merge(newBooks: List<SearchBook>): SearchBookChange {
            if (newBooks.isEmpty()) return SearchBookChange()

            val upsertBooks = arrayListOf<SearchBook>()
            val removedBookUrls = linkedSetOf<String>()
            newBooks.forEach { newBook ->
                coroutineContext.ensureActive()
                val bucket = classifyBucket(newBook) ?: return@forEach
                val key = SearchBookKey(newBook.name, newBook.author)
                val currentBook = bucket[key]
                if (currentBook == null) {
                    bucket[key] = newBook
                    upsertBooks.add(newBook)
                } else {
                    currentBook.addOrigin(newBook.origin)
                    upsertBooks.add(currentBook)
                }
                trimSearchBooks()?.let { removed ->
                    removedBookUrls.add(removed.bookUrl)
                    upsertBooks.removeAll { it.bookUrl == removed.bookUrl }
                }
            }
            return SearchBookChange(upsertBooks, removedBookUrls.toList())
        }

        private fun classifyBucket(book: SearchBook): LinkedHashMap<SearchBookKey, SearchBook>? {
            return when {
                book.name.equals(keyword, ignoreCase = true) ||
                    book.author.equals(keyword, ignoreCase = true) -> equalBooks
                book.name.contains(keyword, ignoreCase = true) ||
                    book.author.contains(keyword, ignoreCase = true) -> containsBooks
                !precision -> otherBooks
                else -> null
            }
        }

        private fun trimSearchBooks(): SearchBook? {
            if (count <= MAX_RETAINED_SEARCH_RESULTS) return null
            resultLimitReached = true
            return removeLast(otherBooks)
                ?: removeLowestOrigin(containsBooks)
                ?: removeLowestOrigin(equalBooks)
        }

        private fun removeLast(bucket: LinkedHashMap<SearchBookKey, SearchBook>): SearchBook? {
            val key = bucket.keys.lastOrNull() ?: return null
            return bucket.remove(key)
        }

        private fun removeLowestOrigin(bucket: LinkedHashMap<SearchBookKey, SearchBook>): SearchBook? {
            val key = bucket.entries.minByOrNull { it.value.origins.size }?.key ?: return null
            return bucket.remove(key)
        }
    }

    private data class SearchBookKey(
        val name: String,
        val author: String,
    )

    private data class SearchBookChange(
        val upsertBooks: List<SearchBook> = emptyList(),
        val removedBookUrls: List<String> = emptyList(),
    )
}

internal fun BookSource.supportsSearchPage(): Boolean {
    val url = searchUrl.orEmpty()
    if (url.isBlank()) return false

    if (searchPageScriptPattern.findAll(url).any { searchPageTokenPattern.containsMatchIn(it.value) }) {
        return true
    }

    val staticUrl = searchPageScriptPattern.replace(url, "")
    return searchPageGroupPattern.containsMatchIn(staticUrl)
}

private val searchPageScriptPattern = Regex(
    pattern = """\{\{[\s\S]*?\}\}|<js>[\s\S]*?</js>|@js:[\s\S]*""",
    option = RegexOption.IGNORE_CASE,
)
private val searchPageTokenPattern = Regex("""(?<![A-Za-z0-9_])page(?![A-Za-z0-9_])""")
private val searchPageGroupPattern = Regex("""<[^<>]+>""")
