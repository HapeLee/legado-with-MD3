package io.legado.app.domain.gateway

import io.legado.app.data.entities.BookContentProcess
import kotlinx.coroutines.flow.Flow

interface BookContentProcessGateway {
    suspend fun getForChapter(bookUrl: String, chapterIndex: Int?): List<BookContentProcess>
    fun flowForChapter(bookUrl: String, chapterIndex: Int?): Flow<List<BookContentProcess>>
    suspend fun nextOrder(bookUrl: String): Int
    suspend fun upsert(process: BookContentProcess)
    suspend fun setEnabled(id: String, enabled: Boolean)
    suspend fun delete(id: String)
}
