package io.legado.app.data.repository

import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.SearchKeyword
import io.legado.app.domain.usecase.BookShelfKey
import io.legado.app.help.book.isNotShelf
import io.legado.app.model.webBook.SearchModel
import io.legado.app.ui.book.search.SearchScope
import io.legado.app.ui.main.bookshelf.BookShelfItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

interface SearchRepository {
    val enabledGroups: Flow<List<String>>
    val enabledSources: Flow<List<BookSourcePart>>
    val bookshelfKeys: Flow<Set<BookShelfKey>>

    fun searchBookshelf(query: String): Flow<List<BookShelfItem>>
    fun searchHistory(query: String): Flow<List<SearchKeyword>>

    suspend fun saveSearchKeyword(keyword: String)
    suspend fun deleteSearchKeyword(item: SearchKeyword)
    suspend fun clearSearchKeywords()

    fun createSearchSession(scopeProvider: () -> SearchScope): SearchSession
}

sealed interface SearchSessionEvent {
    data object Started : SearchSessionEvent

    data class Progress(
        val books: List<SearchBook>,
        val processedSources: Int,
        val totalSources: Int,
    ) : SearchSessionEvent

    data class Finished(
        val isEmpty: Boolean,
        val hasMore: Boolean,
    ) : SearchSessionEvent

    data class Canceled(val throwable: Throwable? = null) : SearchSessionEvent
}

interface SearchSession {
    val events: Flow<SearchSessionEvent>
    fun search(searchId: Long, keyword: String)
    fun stop()
    fun pause()
    fun resume()
    fun close()
}

class SearchRepositoryImpl(
    private val appDb: AppDatabase,
) : SearchRepository {

    override val enabledGroups: Flow<List<String>> = appDb.bookSourceDao.flowEnabledGroups()
    override val enabledSources: Flow<List<BookSourcePart>> = appDb.bookSourceDao.flowEnabled()

    override val bookshelfKeys: Flow<Set<BookShelfKey>> = appDb.bookDao.flowBookShelf().map { books ->
        books.filterNot { it.isNotShelf }
            .map { book -> BookShelfKey(book.name, book.author, book.bookUrl) }
            .toSet()
    }

    override fun searchBookshelf(query: String): Flow<List<BookShelfItem>> {
        val keyword = query.trim()
        return if (keyword.isBlank()) {
            flowOf(emptyList())
        } else {
            appDb.bookDao.flowBookShelfSearch(keyword)
        }
    }

    override fun searchHistory(query: String): Flow<List<SearchKeyword>> {
        val keyword = query.trim()
        return if (keyword.isBlank()) {
            appDb.searchKeywordDao.flowByTime()
        } else {
            appDb.searchKeywordDao.flowSearch(keyword)
        }
    }

    override suspend fun saveSearchKeyword(keyword: String) = withContext(Dispatchers.IO) {
        val key = keyword.trim()
        if (key.isBlank()) return@withContext

        appDb.searchKeywordDao.get(key)?.let { history ->
            history.usage += 1
            history.lastUseTime = System.currentTimeMillis()
            appDb.searchKeywordDao.update(history)
        } ?: appDb.searchKeywordDao.insert(SearchKeyword(word = key, usage = 1))
    }

    override suspend fun deleteSearchKeyword(item: SearchKeyword) = withContext(Dispatchers.IO) {
        appDb.searchKeywordDao.delete(item)
    }

    override suspend fun clearSearchKeywords() = withContext(Dispatchers.IO) {
        appDb.searchKeywordDao.deleteAll()
    }

    override fun createSearchSession(scopeProvider: () -> SearchScope): SearchSession {
        return SearchSessionImpl(scopeProvider)
    }

    private class SearchSessionImpl(
        scopeProvider: () -> SearchScope,
    ) : SearchSession {

        private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val _events = MutableSharedFlow<SearchSessionEvent>(extraBufferCapacity = 64)
        override val events: Flow<SearchSessionEvent> = _events.asSharedFlow()

        private val searchModel = SearchModel(sessionScope, object : SearchModel.CallBack {
            override fun getSearchScope(): SearchScope = scopeProvider()

            override fun onSearchStart() {
                _events.tryEmit(SearchSessionEvent.Started)
            }

            override fun onSearchSuccess(
                searchBooks: List<SearchBook>,
                processedSources: Int,
                totalSources: Int,
            ) {
                _events.tryEmit(
                    SearchSessionEvent.Progress(
                        books = searchBooks.toList(),
                        processedSources = processedSources,
                        totalSources = totalSources,
                    )
                )
            }

            override fun onSearchFinish(isEmpty: Boolean, hasMore: Boolean) {
                _events.tryEmit(SearchSessionEvent.Finished(isEmpty, hasMore))
            }

            override fun onSearchCancel(exception: Throwable?) {
                _events.tryEmit(SearchSessionEvent.Canceled(exception))
            }
        })

        override fun search(searchId: Long, keyword: String) {
            searchModel.search(searchId, keyword)
        }

        override fun stop() {
            searchModel.cancelSearch()
        }

        override fun pause() {
            searchModel.pause()
        }

        override fun resume() {
            searchModel.resume()
        }

        override fun close() {
            searchModel.close()
            sessionScope.cancel()
        }
    }
}
