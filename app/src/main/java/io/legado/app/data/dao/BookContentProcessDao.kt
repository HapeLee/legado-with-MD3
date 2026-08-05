package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.BookContentProcess
import kotlinx.coroutines.flow.Flow

@Dao
interface BookContentProcessDao {

    @Query(
        """
        select * from book_content_processes
        where bookUrl = :bookUrl
          and (:chapterIndex is null or chapterIndex is null or chapterIndex = :chapterIndex)
          and status != ${BookContentProcess.STATUS_DELETED}
        order by sortOrder, createdAt
        """
    )
    suspend fun getForChapter(bookUrl: String, chapterIndex: Int?): List<BookContentProcess>

    @Query(
        """
        select * from book_content_processes
        where bookUrl = :bookUrl
          and (:chapterIndex is null or chapterIndex is null or chapterIndex = :chapterIndex)
          and status != ${BookContentProcess.STATUS_DELETED}
        order by sortOrder, createdAt
        """
    )
    fun getForChapterSync(bookUrl: String, chapterIndex: Int?): List<BookContentProcess>

    @Query(
        """
        select * from book_content_processes
        where bookUrl = :bookUrl
          and (:chapterIndex is null or chapterIndex is null or chapterIndex = :chapterIndex)
          and status != ${BookContentProcess.STATUS_DELETED}
        order by sortOrder, createdAt
        """
    )
    fun flowForChapter(bookUrl: String, chapterIndex: Int?): Flow<List<BookContentProcess>>

    @Query("select coalesce(max(sortOrder), 0) from book_content_processes where bookUrl = :bookUrl")
    suspend fun maxOrder(bookUrl: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(process: BookContentProcess)

    @Query("update book_content_processes set enabled = :enabled, updatedAt = :updatedAt where id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("update book_content_processes set status = ${BookContentProcess.STATUS_DELETED}, updatedAt = :updatedAt where id = :id")
    suspend fun markDeleted(id: String, updatedAt: Long = System.currentTimeMillis())
}
