package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.ExactChapterPageCountEntity

@Dao
interface ExactChapterPageCountDao {

    @Query(
        "select * from exact_chapter_page_counts " +
            "where bookId = :bookId and layoutSignature = :layoutSignature " +
            "and engineVersion = :engineVersion"
    )
    suspend fun getByLayout(
        bookId: String,
        layoutSignature: Long,
        engineVersion: Int,
    ): List<ExactChapterPageCountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ExactChapterPageCountEntity)

    @Query("delete from exact_chapter_page_counts where bookId = :bookId and chapterId = :chapterId")
    suspend fun deleteChapter(bookId: String, chapterId: String)
}
