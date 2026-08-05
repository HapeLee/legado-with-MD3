package io.legado.app.data.repository

import io.legado.app.data.dao.BookmarkDao
import io.legado.app.data.entities.Bookmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class BookmarkRepository(
    private val dao: BookmarkDao,
) {

    fun flowAll(): Flow<List<Bookmark>> = dao.flowAll().flowOn(Dispatchers.IO)

    fun flowByBook(bookName: String, bookAuthor: String): Flow<List<Bookmark>> =
        dao.flowByBook(bookName, bookAuthor).flowOn(Dispatchers.IO)

    suspend fun getAll(): List<Bookmark> = withContext(Dispatchers.IO) {
        dao.all
    }

    suspend fun getByBook(bookName: String, bookAuthor: String): List<Bookmark> =
        withContext(Dispatchers.IO) {
            dao.getByBook(bookName, bookAuthor)
        }

    suspend fun save(bookmark: Bookmark) = withContext(Dispatchers.IO) {
        dao.insert(bookmark)
    }

    suspend fun saveAll(bookmarks: List<Bookmark>) = withContext(Dispatchers.IO) {
        dao.insert(*bookmarks.toTypedArray())
    }

    suspend fun delete(bookmark: Bookmark) = withContext(Dispatchers.IO) {
        dao.delete(bookmark)
    }
}
