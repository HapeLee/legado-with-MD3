package io.legado.app.data.repository

import io.legado.app.data.dao.BookMarkingDao
import io.legado.app.data.entities.BookMarking
import io.legado.app.domain.gateway.BookMarkingGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class BookMarkingRepository(
    private val dao: BookMarkingDao,
) : BookMarkingGateway {

    override suspend fun getByBook(
        bookName: String,
        bookAuthor: String,
        chapterIndex: Int?,
    ): List<BookMarking> = withContext(Dispatchers.IO) {
        dao.getByBook(bookName, bookAuthor, chapterIndex)
    }

    override fun flowByBook(
        bookName: String,
        bookAuthor: String,
    ): Flow<List<BookMarking>> = dao.flowByBook(bookName, bookAuthor).flowOn(Dispatchers.IO)

    override suspend fun getById(id: String): BookMarking? = withContext(Dispatchers.IO) {
        dao.getById(id)
    }

    override suspend fun upsert(bookMarking: BookMarking) = withContext(Dispatchers.IO) {
        dao.upsert(bookMarking)
    }

    override suspend fun setEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        dao.setEnabled(id, enabled)
    }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        dao.delete(id)
    }
}
