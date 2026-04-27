package io.legado.app.model.webBook

import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.exception.NoStackTraceException
import io.legado.app.ui.config.otherConfig.OtherConfig
import io.legado.app.ui.book.search.SearchScope
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.mapParallelSafe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import splitties.init.appCtx
import java.util.concurrent.Executors
import kotlin.coroutines.coroutineContext
import kotlin.math.min

class SearchModel(private val scope: CoroutineScope, private val callBack: CallBack) {
    private companion object {
        const val MAX_RETAINED_SEARCH_RESULTS = 1000
    }

    val threadCount = OtherConfig.threadCount
    private var searchPool: ExecutorCoroutineDispatcher? = null
    private var mSearchId = 0L
    private var searchPage = 1
    private var searchKey: String = ""
    private var bookSourceParts = emptyList<BookSourcePart>()
    private val equalBooks = LinkedHashMap<SearchBookKey, SearchBook>()
    private val containsBooks = LinkedHashMap<SearchBookKey, SearchBook>()
    private val otherBooks = LinkedHashMap<SearchBookKey, SearchBook>()
    private var searchJob: Job? = null
    private var workingState = MutableStateFlow(true)
    private var resultLimitReached = false

    private fun initSearchPool() {
        searchPool?.close()
        searchPool = Executors
            .newFixedThreadPool(min(threadCount, AppConst.MAX_THREAD)).asCoroutineDispatcher()
    }

    fun search(searchId: Long, key: String) {
        if (searchId != mSearchId) {
            if (key.isEmpty()) {
                return
            }
            searchKey = key
            if (mSearchId != 0L) {
                close()
            }
            clearSearchBooks()
            bookSourceParts = callBack.getSearchScope().getBookSourceParts()
            if (bookSourceParts.isEmpty()) {
                callBack.onSearchCancel(NoStackTraceException("启用书源为空"))
                return
            }
            mSearchId = searchId
            searchPage = 1
            initSearchPool()
        } else {
            searchPage++
        }
        startSearch()
    }

    private fun startSearch() {
        val precision = appCtx.getPrefBoolean(PreferKey.precisionSearch)
        var hasMore = false
        val totalParts = bookSourceParts.size
        var processedParts = 0
        searchJob = scope.launch(searchPool!!) {
            flow {
                for (bs in bookSourceParts) {
                    bs.getBookSource()?.let {
                        emit(it)
                    }
                    workingState.first { it }
                }
            }.onStart {
                callBack.onSearchStart()
            }.mapParallelSafe(threadCount) {
                withTimeout(30000L) {
                    WebBook.searchBookAwait(
                        it, searchKey, searchPage,
                        filter = { name, author ->
                            !precision || name.contains(searchKey) ||
                                    author.contains(searchKey)
                        })
                }
            }.onEach { items ->
                for (book in items) {
                    book.releaseHtmlData()
                }
                hasMore = hasMore || items.isNotEmpty()
                if (items.isNotEmpty()) {
                    appDb.searchBookDao.insert(items)
                }
                val change = mergeItems(items, precision)
                currentCoroutineContext().ensureActive()
                processedParts++
                callBack.onSearchSuccess(
                    upsertBooks = change.upsertBooks,
                    removedBookUrls = change.removedBookUrls,
                    resultCount = searchBookCount(),
                    processedSources = processedParts,
                    totalSources = totalParts,
                )
            }.onCompletion {
                if (it == null) {
                    callBack.onSearchFinish(
                        isEmpty = searchBookCount() == 0,
                        hasMore = hasMore && !resultLimitReached,
                    )
                }
            }.catch {
                AppLog.put("书源搜索出错\n${it.localizedMessage}", it)
            }.collect()
        }
    }

    private suspend fun mergeItems(newDataS: List<SearchBook>, precision: Boolean): SearchBookChange {
        if (newDataS.isEmpty()) {
            return SearchBookChange()
        }

        val upsertBooks = arrayListOf<SearchBook>()
        val removedBookUrls = linkedSetOf<String>()
        newDataS.forEach { nBook ->
            coroutineContext.ensureActive()
            val bucket = classifyBucket(nBook, precision) ?: return@forEach
            val key = SearchBookKey(nBook.name, nBook.author)
            val currentBook = bucket[key]
            if (currentBook == null) {
                bucket[key] = nBook
                upsertBooks.add(nBook)
            } else {
                currentBook.addOrigin(nBook.origin)
                upsertBooks.add(currentBook)
            }
            trimSearchBooks()?.let { removed ->
                removedBookUrls.add(removed.bookUrl)
                upsertBooks.removeAll { it.bookUrl == removed.bookUrl }
            }
        }
        return SearchBookChange(upsertBooks, removedBookUrls.toList())
    }

    private fun classifyBucket(
        book: SearchBook,
        precision: Boolean,
    ): LinkedHashMap<SearchBookKey, SearchBook>? {
        return when {
            book.name == searchKey || book.author == searchKey -> equalBooks
            book.name.contains(searchKey) || book.author.contains(searchKey) -> containsBooks
            !precision -> otherBooks
            else -> null
        }
    }

    private fun trimSearchBooks(): SearchBook? {
        if (searchBookCount() <= MAX_RETAINED_SEARCH_RESULTS) {
            return null
        }
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

    private fun searchBookCount(): Int {
        return equalBooks.size + containsBooks.size + otherBooks.size
    }

    private fun clearSearchBooks() {
        equalBooks.clear()
        containsBooks.clear()
        otherBooks.clear()
        resultLimitReached = false
    }

    fun cancelSearch() {
        close()
        callBack.onSearchCancel()
    }

    fun close() {
        searchJob?.cancel()
        searchPool?.close()
        searchPool = null
        mSearchId = 0L
    }

    fun pause() {
        workingState.value = false
    }

    fun resume() {
        workingState.value = true
    }

    interface CallBack {
        fun getSearchScope(): SearchScope
        suspend fun onSearchStart()
        suspend fun onSearchSuccess(
            upsertBooks: List<SearchBook>,
            removedBookUrls: List<String>,
            resultCount: Int,
            processedSources: Int,
            totalSources: Int,
        )
        suspend fun onSearchFinish(isEmpty: Boolean, hasMore: Boolean)
        fun onSearchCancel(exception: Throwable? = null)
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
