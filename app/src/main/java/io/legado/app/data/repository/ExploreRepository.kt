package io.legado.app.data.repository

import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.help.book.isNotShelf
import io.legado.app.help.source.exploreKinds
import io.legado.app.model.webBook.WebBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

interface ExploreRepository {
    fun getBookshelfItems(): Flow<List<SearchBook>>
    suspend fun getBookSource(url: String): BookSource?
    suspend fun exploreBook(source: BookSource, url: String, page: Int): Result<List<SearchBook>>
    suspend fun saveSearchBooks(books: List<SearchBook>)
    suspend fun getSourceExploreKinds(sourceUrl: String): List<ExploreKind>
}

class ExploreRepositoryImpl(
    private val appDb: AppDatabase
) : ExploreRepository {

    override fun getBookshelfItems(): Flow<List<SearchBook>> {
        return appDb.bookDao.flowAll().map { books ->
            books.filterNot { it.isNotShelf }
                .map { it.toSearchBook() }
        }
    }

    override suspend fun getBookSource(url: String): BookSource? {
        return appDb.bookSourceDao.getBookSource(url)
    }

    override suspend fun exploreBook(source: BookSource, url: String, page: Int): Result<List<SearchBook>> {
        return withContext(IO) {
            try {
                val books = WebBook.exploreBookSuspend(source, url, page)
                Result.success(books)
            } catch (e: Exception) {
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }

    override suspend fun getSourceExploreKinds(sourceUrl: String): List<ExploreKind> = withContext(IO) {
        val source = appDb.bookSourceDao.getBookSource(sourceUrl)
        return@withContext source?.exploreKinds() ?: emptyList()
    }

    override suspend fun saveSearchBooks(books: List<SearchBook>) {
        appDb.searchBookDao.insert(*books.toTypedArray())
    }
}