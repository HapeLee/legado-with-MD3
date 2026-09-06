package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import io.legado.app.data.entities.ChapterSpeechAnalysisEntity
import io.legado.app.data.entities.ChapterSpeechSegmentEntity

@Dao
interface ChapterSpeechDao {

    @Query(
        "select * from chapter_speech_analysis where bookUrl = :bookUrl " +
            "and chapterIndex = :chapterIndex and contentHash = :contentHash " +
            "and resolverVersion = :resolverVersion limit 1"
    )
    suspend fun getAnalysis(
        bookUrl: String,
        chapterIndex: Int,
        contentHash: String,
        resolverVersion: String,
    ): ChapterSpeechAnalysisEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAnalysis(analysis: ChapterSpeechAnalysisEntity)

    @Query(
        "select * from chapter_speech_segments where analysisId = :analysisId " +
            "order by paragraphIndex, start, end"
    )
    suspend fun getSegments(analysisId: String): List<ChapterSpeechSegmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSegments(segments: List<ChapterSpeechSegmentEntity>)

    @Query("delete from chapter_speech_segments where analysisId = :analysisId")
    suspend fun deleteSegments(analysisId: String)

    @Transaction
    suspend fun replaceSegments(
        analysisId: String,
        segments: List<ChapterSpeechSegmentEntity>,
    ) {
        deleteSegments(analysisId)
        if (segments.isNotEmpty()) upsertSegments(segments)
    }

    @Transaction
    suspend fun saveAnalysis(
        analysis: ChapterSpeechAnalysisEntity,
        segments: List<ChapterSpeechSegmentEntity>,
    ) {
        upsertAnalysis(analysis)
        replaceSegments(analysis.id, segments)
    }

    @Query("delete from chapter_speech_segments where bookUrl = :bookUrl and chapterIndex = :chapterIndex")
    suspend fun deleteChapterSegments(bookUrl: String, chapterIndex: Int)

    @Query("delete from chapter_speech_analysis where bookUrl = :bookUrl and chapterIndex = :chapterIndex")
    suspend fun deleteChapterAnalyses(bookUrl: String, chapterIndex: Int)

    @Transaction
    suspend fun deleteChapter(bookUrl: String, chapterIndex: Int) {
        deleteChapterSegments(bookUrl, chapterIndex)
        deleteChapterAnalyses(bookUrl, chapterIndex)
    }
}
