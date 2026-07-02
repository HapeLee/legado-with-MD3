package io.legado.app.data.repository

import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.domain.gateway.ExploreBooksGateway
import io.legado.app.help.source.SourceHelp
import io.legado.app.help.source.exploreKinds
import io.legado.app.model.webBook.WebBook
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

interface ExploreRepository {
    fun getBookshelfItems(): Flow<List<SearchBook>>
    fun getExploreGroups(): Flow<List<String>>
    fun getExploreSources(query: String, selectedGroup: String): Flow<List<BookSourcePart>>
    suspend fun getBookSource(sourceUrl: String): BookSource?
    suspend fun getSourceExploreKinds(sourceUrl: String): List<ExploreKind>
    suspend fun topSource(bookSource: BookSourcePart)
    suspend fun deleteSource(sourceUrl: String)
}

class ExploreRepositoryImpl(
    private val appDb: AppDatabase
) : ExploreRepository, ExploreBooksGateway {

    override fun getBookshelfItems(): Flow<List<SearchBook>> {
        return appDb.bookDao.flowBookShelf().map { books ->
            books.filterNot { it.isNotShelf }
                .map {
                    SearchBook(
                        bookUrl = it.bookUrl,
                        name = it.name,
                        author = it.author,
                        originName = it.originName,
                        type = it.type,
                        coverUrl = it.coverUrl,
                        latestChapterTitle = it.latestChapterTitle
                    )
                }
        }
    }

    override fun getExploreGroups(): Flow<List<String>> {
        return appDb.bookSourceDao.flowExploreGroups()
    }

    override fun getExploreSources(
        query: String,
        selectedGroup: String
    ): Flow<List<BookSourcePart>> {
        return when {
            query.isNotBlank() -> {
                if (query.startsWith("group:")) {
                    appDb.bookSourceDao.flowGroupExplore(query.substringAfter("group:"))
                } else {
                    appDb.bookSourceDao.flowExplore(query)
                }
            }

            selectedGroup.isNotBlank() -> {
                appDb.bookSourceDao.flowGroupExplore(selectedGroup)
            }

            else -> {
                appDb.bookSourceDao.flowExplore()
            }
        }
    }

    override suspend fun getBookSource(sourceUrl: String): BookSource? {
        return appDb.bookSourceDao.getBookSource(sourceUrl)
    }

    override suspend fun exploreBooks(
        bookSource: BookSource,
        url: String,
        page: Int,
        key: String?
    ): List<SearchBook> {
        return WebBook.exploreBookSuspend(bookSource, url, page, key = key, isSearch = key != null)
    }

    override suspend fun saveSearchBooks(books: List<SearchBook>) {
        if (books.isNotEmpty()) {
            appDb.searchBookDao.insert(books)
        }
    }

    override suspend fun getSourceExploreKinds(sourceUrl: String): List<ExploreKind> = withContext(IO) {
        val source = appDb.bookSourceDao.getBookSource(sourceUrl)
        return@withContext source?.exploreKinds() ?: emptyList()
    }

    override suspend fun topSource(bookSource: BookSourcePart) {
        val minOrder = appDb.bookSourceDao.minOrder
        appDb.bookSourceDao.upOrder(bookSource.copy(customOrder = minOrder - 1))
    }

    override suspend fun deleteSource(sourceUrl: String) {
        SourceHelp.deleteBookSource(sourceUrl)
    }
}
