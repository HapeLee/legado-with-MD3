package io.legado.app.data.repository

import io.legado.app.data.dao.BookSourceDao
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class BookSourceRepository(private val bookSourceDao: BookSourceDao) {

    fun flowAll(): Flow<List<BookSourcePart>> {
        return bookSourceDao.flowAll()
    }

    fun flowEnabled(): Flow<List<BookSourcePart>> {
        return bookSourceDao.flowEnabled()
    }

    fun flowHomepageModules(): Flow<List<BookSource>> {
        return bookSourceDao.flowHomepageModules()
    }

    fun flowExploreSources(): Flow<List<BookSource>> {
        return bookSourceDao.flowExploreSources()
    }

    suspend fun getBookSource(sourceUrl: String): BookSource? {
        return withContext(Dispatchers.IO) {
            bookSourceDao.getBookSource(sourceUrl)
        }
    }

    fun getBookSourceSync(sourceUrl: String): BookSource? {
        return bookSourceDao.getBookSource(sourceUrl)
    }

    suspend fun getBookSourceAddBook(baseUrl: String): BookSource? {
        return withContext(Dispatchers.IO) {
            bookSourceDao.getBookSourceAddBook(baseUrl)
        }
    }

    suspend fun getHasBookUrlPattern(): List<BookSourcePart> {
        return withContext(Dispatchers.IO) {
            bookSourceDao.hasBookUrlPattern
        }
    }

    suspend fun getAllEnabledPart(): List<BookSourcePart> {
        return withContext(Dispatchers.IO) {
            bookSourceDao.allEnabledPart
        }
    }
}
