package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.BgmSceneMark

/**
 * 正文背景音乐场景标记的读写。
 *
 * 只有「按章取」「存一条」「删」三种动作：朗读时先取本章全部标记，再按当前段落序号
 * 决定放哪条背景音乐，所以不做逐条查询。
 */
@Dao
interface BgmSceneDao {

    @Query("SELECT * FROM bgm_scene_marks WHERE bookUrl = :bookUrl AND chapterIndex = :chapterIndex ORDER BY paragraphOrdinal")
    suspend fun getChapter(bookUrl: String, chapterIndex: Int): List<BgmSceneMark>

    @Query("SELECT COUNT(*) FROM bgm_scene_marks WHERE bookUrl = :bookUrl")
    suspend fun countForBook(bookUrl: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(mark: BgmSceneMark)

    @Query("DELETE FROM bgm_scene_marks WHERE bookUrl = :bookUrl AND chapterIndex = :chapterIndex AND paragraphOrdinal = :ordinal")
    suspend fun delete(bookUrl: String, chapterIndex: Int, ordinal: Int)

    @Query("DELETE FROM bgm_scene_marks WHERE bookUrl = :bookUrl AND chapterIndex = :chapterIndex")
    suspend fun deleteChapter(bookUrl: String, chapterIndex: Int)

    @Query("DELETE FROM bgm_scene_marks WHERE bookUrl = :bookUrl")
    suspend fun deleteForBook(bookUrl: String)
}
