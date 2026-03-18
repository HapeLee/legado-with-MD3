package io.legado.app.data.repository

import androidx.lifecycle.asFlow
import io.legado.app.data.dao.BookGroupDao
import io.legado.app.data.entities.BookGroup
import kotlinx.coroutines.flow.Flow

class BookGroupRepository(private val bookGroupDao: BookGroupDao) {

    fun flowAll(): Flow<List<BookGroup>> {
        return bookGroupDao.flowAll()
    }

    fun flowShow(): Flow<List<BookGroup>> {
        return bookGroupDao.show.asFlow()
    }

    suspend fun update(vararg bookGroup: BookGroup) {
        bookGroupDao.update(*bookGroup)
    }

    suspend fun insert(vararg bookGroup: BookGroup) {
        bookGroupDao.insert(*bookGroup)
    }

    suspend fun delete(vararg bookGroup: BookGroup) {
        bookGroupDao.delete(*bookGroup)
    }

    suspend fun getUnusedId(): Long {
        return bookGroupDao.getUnusedId()
    }

    fun getMaxOrder(): Int {
        return bookGroupDao.maxOrder
    }

    suspend fun getByID(id: Long): BookGroup? {
        return bookGroupDao.getByID(id)
    }

    suspend fun clearCover(groupId: Long) {
        bookGroupDao.clearCover(groupId)
    }
}
