package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import io.legado.app.data.entities.ChapterPageCount

@Dao
interface ChapterPageCountDao {

    @Query("SELECT * FROM chapter_page_counts WHERE bookUrl = :bookUrl AND layoutKey = :layoutKey")
    suspend fun getByBookAndLayout(bookUrl: String, layoutKey: String): List<ChapterPageCount>

    @Query("SELECT * FROM chapter_page_counts WHERE bookUrl = :bookUrl AND chapterIndex = :chapterIndex AND layoutKey = :layoutKey LIMIT 1")
    suspend fun getChapterPageCount(bookUrl: String, chapterIndex: Int, layoutKey: String): ChapterPageCount?

    @Query("DELETE FROM chapter_page_counts WHERE bookUrl = :bookUrl AND layoutKey != :currentLayoutKey")
    suspend fun deleteOldLayouts(bookUrl: String, currentLayoutKey: String)

    @Query("DELETE FROM chapter_page_counts WHERE bookUrl = :bookUrl")
    suspend fun deleteByBook(bookUrl: String)

    @Update
    suspend fun update(chapterPageCount: ChapterPageCount)

    @Insert
    suspend fun insert(chapterPageCount: ChapterPageCount)
}
