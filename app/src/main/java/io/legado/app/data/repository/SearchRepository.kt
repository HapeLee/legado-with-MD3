package io.legado.app.data.repository

import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.SearchKeyword
import io.legado.app.domain.gateway.BookSearchGateway
import io.legado.app.domain.model.BookSearchScope
import io.legado.app.domain.usecase.BookShelfKey
import io.legado.app.help.book.isNotShelf
import io.legado.app.ui.main.bookshelf.BookShelfItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
}

class SearchRepositoryImpl(
    private val appDb: AppDatabase,
) : SearchRepository, BookSearchGateway {

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

    override suspend fun getBookSourceParts(scope: BookSearchScope): List<BookSourcePart> =
        withContext(Dispatchers.IO) {
            val selectedSources = linkedSetOf<BookSourcePart>()
            when {
                scope.isAll -> selectedSources.addAll(appDb.bookSourceDao.allEnabledPart)
                scope.isSource -> scope.sourceUrls.forEach { sourceUrl ->
                    appDb.bookSourceDao.getBookSourcePart(sourceUrl)?.let { selectedSources.add(it) }
                }

                else -> scope.groupNames.forEach { groupName ->
                    selectedSources.addAll(appDb.bookSourceDao.getEnabledPartByGroup(groupName))
                }
            }

            if (selectedSources.isEmpty()) {
                appDb.bookSourceDao.allEnabledPart
            } else {
                selectedSources.toList().sortedBy { it.customOrder }
            }
        }

    override suspend fun getBookSource(sourceUrl: String) = withContext(Dispatchers.IO) {
        appDb.bookSourceDao.getBookSource(sourceUrl)
    }

    override suspend fun saveSearchBooks(books: List<SearchBook>) = withContext(Dispatchers.IO) {
        if (books.isNotEmpty()) {
            appDb.searchBookDao.insert(books)
        }
    }
}
