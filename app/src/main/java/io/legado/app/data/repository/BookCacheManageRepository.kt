package io.legado.app.data.repository

import io.legado.app.data.dao.BookChapterDao
import io.legado.app.data.dao.BookDao
import io.legado.app.data.dao.BookGroupDao
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.model.BookChapterCacheInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class BookCacheManageRepository(
    private val bookDao: BookDao,
    private val bookChapterDao: BookChapterDao,
    private val bookGroupDao: BookGroupDao,
) {

    fun flowBooks(): Flow<List<Book>> = bookDao.flowAll().flowOn(Dispatchers.IO)

    fun flowSelectableGroups(): Flow<List<BookGroup>> =
        bookGroupDao.flowSelect().flowOn(Dispatchers.IO)

    suspend fun getAllBooks(): List<Book> = withContext(Dispatchers.IO) {
        bookDao.all
    }

    suspend fun getBook(bookUrl: String): Book? = withContext(Dispatchers.IO) {
        bookDao.getBook(bookUrl)
    }

    suspend fun getChapterCount(bookUrl: String): Int = withContext(Dispatchers.IO) {
        bookChapterDao.getChapterCount(bookUrl)
    }

    suspend fun getVolumeCount(bookUrl: String): Int = withContext(Dispatchers.IO) {
        bookChapterDao.getVolumeCount(bookUrl)
    }

    suspend fun getChapterCacheInfo(bookUrl: String): List<BookChapterCacheInfo> =
        withContext(Dispatchers.IO) {
            bookChapterDao.getChapterCacheInfoList(bookUrl)
        }
}
