package io.legado.app.domain.gateway

import io.legado.app.domain.model.readaloud.ChapterSpeechAnalysis
import io.legado.app.domain.model.readaloud.ChapterSpeechSegment

interface ChapterSpeechGateway {
    suspend fun getAnalysis(
        bookUrl: String,
        chapterIndex: Int,
        contentHash: String,
        resolverVersion: String,
    ): ChapterSpeechAnalysis?

    suspend fun upsertAnalysis(analysis: ChapterSpeechAnalysis)
    suspend fun saveAnalysis(
        analysis: ChapterSpeechAnalysis,
        segments: List<ChapterSpeechSegment>,
    )
    suspend fun getSegments(analysisId: String): List<ChapterSpeechSegment>
    suspend fun replaceSegments(analysisId: String, segments: List<ChapterSpeechSegment>)
    suspend fun deleteChapter(bookUrl: String, chapterIndex: Int)
}
