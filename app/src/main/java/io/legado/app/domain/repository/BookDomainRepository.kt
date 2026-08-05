package io.legado.app.domain.repository

import io.legado.app.domain.model.BookGroupAssignment
import io.legado.app.domain.model.CacheableBook
import io.legado.app.domain.model.DeletableBook

interface BookDomainRepository {
    suspend fun getCacheableBooks(bookUrls: Set<String>): List<CacheableBook>
    suspend fun getDeletableBooks(bookUrls: Set<String>): List<DeletableBook>
    suspend fun getBookGroupAssignments(bookUrls: Set<String>): List<BookGroupAssignment>
    suspend fun updateBookGroups(assignments: List<BookGroupAssignment>)
    suspend fun removeGroupFromBooks(groupId: Long)
    suspend fun deleteBooks(bookUrls: Set<String>)
    suspend fun deleteChaptersByBook(bookUrl: String)
}
