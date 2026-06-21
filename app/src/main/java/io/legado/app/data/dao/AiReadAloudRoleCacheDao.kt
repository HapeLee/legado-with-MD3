package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.AiReadAloudRoleCache

@Dao
interface AiReadAloudRoleCacheDao {

    @Query("SELECT * FROM ai_read_aloud_role_caches WHERE cacheKey = :cacheKey LIMIT 1")
    fun get(cacheKey: String): AiReadAloudRoleCache?

    @Query("SELECT * FROM ai_read_aloud_role_caches WHERE bookUrl = :bookUrl")
    fun listByBook(bookUrl: String): List<AiReadAloudRoleCache>

    @Query(
        """
        SELECT * FROM ai_read_aloud_role_caches
        WHERE bookUrl = :bookUrl
          AND chapterIndex = :chapterIndex
          AND status = :status
        ORDER BY updatedAt DESC
        LIMIT 1
        """
    )
    fun latestByChapter(
        bookUrl: String,
        chapterIndex: Int,
        status: String = AiReadAloudRoleCache.STATUS_SUCCESS
    ): AiReadAloudRoleCache?

    @Query(
        """
        SELECT * FROM ai_read_aloud_role_caches
        WHERE bookUrl = :bookUrl
          AND chapterIndex = :chapterIndex
          AND segmentsJson != ''
        ORDER BY
          CASE WHEN status = 'success' THEN 0 ELSE 1 END,
          updatedAt DESC
        LIMIT 1
        """
    )
    fun latestUsableByChapter(
        bookUrl: String,
        chapterIndex: Int
    ): AiReadAloudRoleCache?

    @Query(
        """
        SELECT * FROM ai_read_aloud_role_caches
        WHERE bookUrl = :bookUrl
          AND chapterIndex = :chapterIndex
          AND contentHash = :contentHash
          AND status = 'success'
          AND segmentsJson != ''
          AND mode LIKE '%' || :preprocessVersion || '%'
        ORDER BY updatedAt DESC
        LIMIT 1
        """
    )
    fun latestSuccessByChapterContent(
        bookUrl: String,
        chapterIndex: Int,
        contentHash: String,
        preprocessVersion: String
    ): AiReadAloudRoleCache?

    @Query(
        """
        SELECT * FROM ai_read_aloud_role_caches
        WHERE bookUrl = :bookUrl
          AND chapterIndex = :chapterIndex
          AND contentHash = :contentHash
          AND status = 'success'
          AND segmentsJson != ''
          AND mode LIKE '%' || :preprocessVersion || '%'
        ORDER BY updatedAt DESC
        LIMIT 20
        """
    )
    fun successCandidatesByChapterContent(
        bookUrl: String,
        chapterIndex: Int,
        contentHash: String,
        preprocessVersion: String
    ): List<AiReadAloudRoleCache>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(cache: AiReadAloudRoleCache)

    @Query("DELETE FROM ai_read_aloud_role_caches WHERE bookUrl = :bookUrl AND chapterIndex = :chapterIndex")
    fun deleteByChapter(bookUrl: String, chapterIndex: Int)

    @Query("DELETE FROM ai_read_aloud_role_caches WHERE bookUrl = :bookUrl")
    fun deleteByBook(bookUrl: String)
}
