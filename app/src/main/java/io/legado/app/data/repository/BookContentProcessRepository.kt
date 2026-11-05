package io.legado.app.data.repository

import io.legado.app.data.dao.BookContentProcessDao
import io.legado.app.data.entities.BookContentProcess
import io.legado.app.domain.gateway.BookContentProcessGateway
import io.legado.app.help.book.ContentProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class BookContentProcessRepository(
    private val dao: BookContentProcessDao,
) : BookContentProcessGateway {

    override suspend fun getForChapter(
        bookUrl: String,
        chapterIndex: Int?,
    ): List<BookContentProcess> = withContext(Dispatchers.IO) {
        dao.getForChapter(bookUrl, chapterIndex)
    }

    override fun flowForChapter(
        bookUrl: String,
        chapterIndex: Int?,
    ): Flow<List<BookContentProcess>> {
        return dao.flowForChapter(bookUrl, chapterIndex).flowOn(Dispatchers.IO)
    }

    override suspend fun nextOrder(bookUrl: String): Int = withContext(Dispatchers.IO) {
        dao.maxOrder(bookUrl) + 1
    }

    override suspend fun upsert(process: BookContentProcess) = withContext(Dispatchers.IO) {
        dao.upsert(process)
        ContentProcessor.invalidateSemanticContent(process.bookUrl, process.chapterIndex)
    }

    override suspend fun setEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        val process = dao.getById(id)
        dao.setEnabled(id, enabled)
        if (process != null) ContentProcessor.invalidateSemanticContent(process.bookUrl, process.chapterIndex)
    }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val process = dao.getById(id)
        dao.markDeleted(id)
        if (process != null) ContentProcessor.invalidateSemanticContent(process.bookUrl, process.chapterIndex)
    }
}
