package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.legado.app.data.entities.ChapterRoleAssignment
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterRoleAssignmentDao {

    @Query(
        """
        SELECT * FROM chapter_role_assignments
        WHERE bookUrl = :bookUrl AND chapterIndex = :chapterIndex
        ORDER BY quoteOrdinal
        """
    )
    suspend fun getForChapter(bookUrl: String, chapterIndex: Int): List<ChapterRoleAssignment>

    @Query(
        """
        SELECT * FROM chapter_role_assignments
        WHERE bookUrl = :bookUrl
        ORDER BY chapterIndex, quoteOrdinal
        """
    )
    suspend fun getForBook(bookUrl: String): List<ChapterRoleAssignment>

    /** 这本书里**分配过角色**的章节号（目录页那枚多角色图标用）。 */
    @Query(
        """
        SELECT DISTINCT chapterIndex FROM chapter_role_assignments
        WHERE bookUrl = :bookUrl
        """
    )
    suspend fun getAssignedChapters(bookUrl: String): List<Int>

    /** 同上，但订阅式：目录页的状态图标要在分配完当场亮起来。 */
    @Query(
        """
        SELECT DISTINCT chapterIndex FROM chapter_role_assignments
        WHERE bookUrl = :bookUrl
        """
    )
    fun flowAssignedChapters(bookUrl: String): Flow<List<Int>>

    @Query(
        """
        SELECT * FROM chapter_role_assignments
        WHERE bookUrl = :bookUrl AND chapterIndex = :chapterIndex AND quoteOrdinal = :ordinal
        """
    )
    suspend fun getOne(
        bookUrl: String,
        chapterIndex: Int,
        ordinal: Int,
    ): ChapterRoleAssignment?

    @Upsert
    suspend fun upsert(assignment: ChapterRoleAssignment)

    /** 角色改名/换池后同步所有引用它的分配行冗余字段（胶囊与标记全局更新）。 */
    @Query(
        """
        UPDATE chapter_role_assignments
        SET characterName = :name, voicePoolLabel = :poolLabel, updatedAt = :now
        WHERE bookUrl = :bookUrl AND characterId = :characterId
        """
    )
    suspend fun updateForCharacter(
        bookUrl: String,
        characterId: String,
        name: String,
        poolLabel: String,
        now: Long,
    )

    /** 重新分配前清空本章既有分配。 */
    @Query(
        """
        DELETE FROM chapter_role_assignments
        WHERE bookUrl = :bookUrl AND chapterIndex = :chapterIndex
        """
    )
    suspend fun deleteChapter(bookUrl: String, chapterIndex: Int)

    @Query(
        """
        DELETE FROM chapter_role_assignments
        WHERE bookUrl = :bookUrl AND chapterIndex = :chapterIndex AND quoteOrdinal = :ordinal
        """
    )
    suspend fun deleteOne(bookUrl: String, chapterIndex: Int, ordinal: Int)

    @Query("DELETE FROM chapter_role_assignments WHERE bookUrl = :bookUrl AND characterId = :characterId")
    suspend fun deleteForCharacter(bookUrl: String, characterId: String)

    /** 分配表 tab：每个角色被分配的句子数。 */
    @Query(
        """
        SELECT characterId AS characterId, COUNT(*) AS count
        FROM chapter_role_assignments
        GROUP BY characterId
        """
    )
    suspend fun countByCharacter(): List<CharacterAssignmentCount>
}

/** countByCharacter 投影行。 */
data class CharacterAssignmentCount(
    val characterId: String,
    val count: Int,
)
