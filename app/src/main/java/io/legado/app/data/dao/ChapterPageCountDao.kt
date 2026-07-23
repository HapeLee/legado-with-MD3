package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.ChapterPageCount

@Dao
interface ChapterPageCountDao {

    @Query("SELECT * FROM chapter_page_counts WHERE bookUrl = :bookUrl AND layoutKey = :layoutKey")
    suspend fun getByBookAndLayout(bookUrl: String, layoutKey: String): List<ChapterPageCount>

    @Query("SELECT * FROM chapter_page_counts WHERE bookUrl = :bookUrl AND chapterIndex = :chapterIndex AND layoutKey = :layoutKey LIMIT 1")
    suspend fun getChapterPageCount(bookUrl: String, chapterIndex: Int, layoutKey: String): ChapterPageCount?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg chapterPageCount: ChapterPageCount)

    @Query("DELETE FROM chapter_page_counts WHERE bookUrl = :bookUrl AND layoutKey != :currentLayoutKey")
    suspend fun deleteOldLayouts(bookUrl: String, currentLayoutKey: String)
}
