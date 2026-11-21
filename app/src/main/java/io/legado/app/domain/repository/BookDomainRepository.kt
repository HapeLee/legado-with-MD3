package io.legado.app.domain.repository

import io.legado.app.domain.model.BookGroupAssignment
import io.legado.app.domain.model.CacheableBook
import io.legado.app.domain.model.DeletableBook
import kotlinx.coroutines.flow.Flow

interface BookDomainRepository {
    /** 系列名 → 独立封面路径；没有独立封面的系列不在 map 里 */
    fun flowSeriesCovers(): Flow<Map<String, String>>
    suspend fun getCacheableBooks(bookUrls: Set<String>): List<CacheableBook>
    suspend fun getDeletableBooks(bookUrls: Set<String>): List<DeletableBook>
    suspend fun getBookGroupAssignments(bookUrls: Set<String>): List<BookGroupAssignment>
    suspend fun updateBookGroups(assignments: List<BookGroupAssignment>)
    suspend fun removeGroupFromBooks(groupId: Long)
    suspend fun deleteBooks(bookUrls: Set<String>)
    suspend fun deleteChaptersByBook(bookUrl: String)

    // ── 书架折叠收纳（同系列多卷） ──

    suspend fun getSeriesNames(): List<String>
    suspend fun getMaxSeriesOrder(seriesName: String): Int
    suspend fun assignSeries(bookUrls: List<String>, seriesName: String, startOrder: Int)
    suspend fun ensureSeries(seriesName: String)
    suspend fun setSeriesCover(seriesName: String, cover: String?)
    suspend fun renameSeries(oldName: String, newName: String)
    suspend fun dissolveSeries(seriesName: String)
    suspend fun removeBooksFromSeries(bookUrls: Set<String>)
}
