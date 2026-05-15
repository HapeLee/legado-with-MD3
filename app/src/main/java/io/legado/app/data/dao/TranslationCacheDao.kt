package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.legado.app.data.entities.TranslationCache

@Dao
interface TranslationCacheDao {

    @Query("SELECT * FROM translationCache WHERE cacheKey = :cacheKey")
    suspend fun getByCacheKey(cacheKey: String): TranslationCache?

    @Query("SELECT * FROM translationCache WHERE bookUrl = :bookUrl AND chapterIndex = :chapterIndex AND targetLanguage = :targetLanguage ORDER BY chunkIndex")
    suspend fun getByChapter(bookUrl: String, chapterIndex: Int, targetLanguage: String): List<TranslationCache>

    @Query("SELECT * FROM translationCache WHERE bookUrl = :bookUrl AND chapterIndex = :chapterIndex AND targetLanguage = :targetLanguage AND originalContentHash = :contentHash ORDER BY chunkIndex")
    suspend fun getByChapterAndHash(bookUrl: String, chapterIndex: Int, targetLanguage: String, contentHash: String): List<TranslationCache>

    @Query("SELECT * FROM translationCache WHERE bookUrl = :bookUrl AND chapterIndex = :chapterIndex AND targetLanguage = :targetLanguage AND status = :status ORDER BY chunkIndex")
    suspend fun getByChapterAndStatus(bookUrl: String, chapterIndex: Int, targetLanguage: String, status: Int): List<TranslationCache>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg translationCache: TranslationCache)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(translationCaches: List<TranslationCache>)

    @Update
    suspend fun update(translationCache: TranslationCache)

    @Query("UPDATE translationCache SET status = :status, translatedChunkContent = :translatedContent, updateTime = :updateTime WHERE cacheKey = :cacheKey")
    suspend fun updateStatusAndContent(cacheKey: String, status: Int, translatedContent: String?, updateTime: Long = System.currentTimeMillis())

    @Query("UPDATE translationCache SET status = :status, errorMessage = :errorMessage, updateTime = :updateTime WHERE cacheKey = :cacheKey")
    suspend fun updateStatusAndError(cacheKey: String, status: Int, errorMessage: String?, updateTime: Long = System.currentTimeMillis())

    @Query("DELETE FROM translationCache WHERE bookUrl = :bookUrl AND chapterIndex = :chapterIndex AND targetLanguage = :targetLanguage")
    suspend fun deleteByChapter(bookUrl: String, chapterIndex: Int, targetLanguage: String)

    @Query("DELETE FROM translationCache WHERE bookUrl = :bookUrl AND targetLanguage = :targetLanguage")
    suspend fun deleteByBook(bookUrl: String, targetLanguage: String)

    @Query("DELETE FROM translationCache WHERE bookUrl = :bookUrl")
    suspend fun deleteByBook(bookUrl: String)

    @Query("DELETE FROM translationCache")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM translationCache WHERE bookUrl = :bookUrl AND chapterIndex = :chapterIndex AND targetLanguage = :targetLanguage AND status = :status")
    suspend fun countByChapterAndStatus(bookUrl: String, chapterIndex: Int, targetLanguage: String, status: Int): Int

    @Query("SELECT COUNT(*) FROM translationCache WHERE bookUrl = :bookUrl AND chapterIndex = :chapterIndex AND targetLanguage = :targetLanguage")
    suspend fun countByChapter(bookUrl: String, chapterIndex: Int, targetLanguage: String): Int
}