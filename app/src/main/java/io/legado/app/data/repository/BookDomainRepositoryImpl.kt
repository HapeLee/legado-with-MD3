package io.legado.app.data.repository

import io.legado.app.data.dao.BookChapterDao
import io.legado.app.data.dao.BookDao
import io.legado.app.data.dao.BookSeriesDao
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSeries
import io.legado.app.domain.model.BookGroupAssignment
import io.legado.app.domain.model.CacheableBook
import io.legado.app.domain.model.DeletableBook
import io.legado.app.domain.repository.BookDomainRepository
import io.legado.app.help.book.isLocal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BookDomainRepositoryImpl(
    private val bookDao: BookDao,
    private val bookChapterDao: BookChapterDao,
    private val bookSeriesDao: BookSeriesDao,
) : BookDomainRepository {

    override fun flowSeriesCovers(): Flow<Map<String, String>> {
        return bookSeriesDao.flowAll().map { seriesList ->
            buildMap {
                seriesList.forEach { series ->
                    series.cover?.takeIf { it.isNotBlank() }?.let { put(series.name, it) }
                }
            }
        }
    }

    private suspend fun getBooks(bookUrls: Set<String>): List<Book> {
        if (bookUrls.isEmpty()) return emptyList()
        return bookUrls.mapNotNull { bookDao.getBook(it) }
    }

    override suspend fun getCacheableBooks(bookUrls: Set<String>): List<CacheableBook> {
        if (bookUrls.isEmpty()) return emptyList()
        return bookDao.getCacheableBooks(bookUrls)
    }

    override suspend fun getDeletableBooks(bookUrls: Set<String>): List<DeletableBook> {
        return getBooks(bookUrls).map { book ->
            DeletableBook(
                bookUrl = book.bookUrl,
                origin = book.origin,
                isLocal = book.isLocal
            )
        }
    }

    override suspend fun getBookGroupAssignments(bookUrls: Set<String>): List<BookGroupAssignment> {
        return getBooks(bookUrls).map { book ->
            BookGroupAssignment(
                bookUrl = book.bookUrl,
                group = book.group
            )
        }
    }

    override suspend fun updateBookGroups(assignments: List<BookGroupAssignment>) {
        if (assignments.isEmpty()) return
        val groups = assignments.associateBy { it.bookUrl }
        val books = getBooks(groups.keys).mapNotNull { book ->
            groups[book.bookUrl]?.let { assignment ->
                if (book.group == assignment.group) null else book.copy(group = assignment.group)
            }
        }
        if (books.isNotEmpty()) {
            bookDao.update(*books.toTypedArray())
        }
    }

    override suspend fun removeGroupFromBooks(groupId: Long) {
        bookDao.removeGroup(groupId)
    }

    override suspend fun deleteBooks(bookUrls: Set<String>) {
        val books = getBooks(bookUrls)
        if (books.isNotEmpty()) {
            bookDao.delete(*books.toTypedArray())
        }
    }

    override suspend fun deleteChaptersByBook(bookUrl: String) {
        bookChapterDao.delByBook(bookUrl)
    }

    override suspend fun getSeriesNames(): List<String> = bookDao.getAllSeriesNames()

    override suspend fun getMaxSeriesOrder(seriesName: String): Int = bookDao.maxSeriesOrder(seriesName)

    override suspend fun assignSeries(bookUrls: List<String>, seriesName: String, startOrder: Int) {
        if (bookUrls.isEmpty()) return
        bookDao.assignSeries(bookUrls, seriesName, startOrder)
    }

    override suspend fun ensureSeries(seriesName: String) {
        if (seriesName.isBlank()) return
        if (bookSeriesDao.get(seriesName) == null) {
            bookSeriesDao.upsert(BookSeries(name = seriesName))
        }
    }

    override suspend fun setSeriesCover(seriesName: String, cover: String?) {
        if (seriesName.isBlank()) return
        val normalized = cover?.takeIf { it.isNotBlank() }
        if (bookSeriesDao.get(seriesName) == null) {
            bookSeriesDao.upsert(BookSeries(name = seriesName, cover = normalized))
        } else {
            bookSeriesDao.updateCover(seriesName, normalized)
        }
    }

    override suspend fun renameSeries(oldName: String, newName: String) {
        if (oldName == newName) return
        // 两张表各一条 SQL：books 决定归属，book_series 只带封面，先后失败只会留下孤立封面行
        bookDao.renameSeries(oldName, newName)
        bookSeriesDao.rename(oldName, newName)
    }

    override suspend fun dissolveSeries(seriesName: String) {
        bookDao.clearSeries(seriesName)
        bookSeriesDao.delete(seriesName)
    }

    override suspend fun removeBooksFromSeries(bookUrls: Set<String>) {
        if (bookUrls.isEmpty()) return
        bookDao.clearSeriesForBooks(bookUrls.toList())
    }
}
