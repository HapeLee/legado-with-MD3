package io.legado.app.data.repository

import io.legado.app.data.dao.ChapterSpeechDao
import io.legado.app.data.entities.ChapterSpeechAnalysisEntity
import io.legado.app.data.entities.ChapterSpeechSegmentEntity
import io.legado.app.domain.gateway.ChapterSpeechGateway
import io.legado.app.domain.model.readaloud.ChapterSpeechAnalysis
import io.legado.app.domain.model.readaloud.ChapterSpeechSegment
import io.legado.app.domain.model.readaloud.SpeechAnalysisStatus
import io.legado.app.domain.model.readaloud.SpeechResolutionSource
import io.legado.app.domain.model.readaloud.SpeechRoleType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChapterSpeechRepository(
    private val dao: ChapterSpeechDao,
) : ChapterSpeechGateway {

    override suspend fun getAnalysis(
        bookUrl: String,
        chapterIndex: Int,
        contentHash: String,
        resolverVersion: String,
    ): ChapterSpeechAnalysis? = withContext(Dispatchers.IO) {
        dao.getAnalysis(bookUrl, chapterIndex, contentHash, resolverVersion)?.toDomain()
    }

    override suspend fun upsertAnalysis(analysis: ChapterSpeechAnalysis) =
        withContext(Dispatchers.IO) {
            dao.upsertAnalysis(analysis.toEntity())
        }

    override suspend fun saveAnalysis(
        analysis: ChapterSpeechAnalysis,
        segments: List<ChapterSpeechSegment>,
    ) = withContext(Dispatchers.IO) {
        require(segments.all { it.analysisId == analysis.id }) {
            "All speech segments must belong to analysis ${analysis.id}"
        }
        dao.saveAnalysis(analysis.toEntity(), segments.map(ChapterSpeechSegment::toEntity))
    }

    override suspend fun getSegments(analysisId: String): List<ChapterSpeechSegment> =
        withContext(Dispatchers.IO) {
            dao.getSegments(analysisId).map(ChapterSpeechSegmentEntity::toDomain)
        }

    override suspend fun replaceSegments(
        analysisId: String,
        segments: List<ChapterSpeechSegment>,
    ) = withContext(Dispatchers.IO) {
        require(segments.all { it.analysisId == analysisId }) {
            "All speech segments must belong to analysis $analysisId"
        }
        dao.replaceSegments(analysisId, segments.map(ChapterSpeechSegment::toEntity))
    }

    override suspend fun deleteChapter(bookUrl: String, chapterIndex: Int) =
        withContext(Dispatchers.IO) {
            dao.deleteChapter(bookUrl, chapterIndex)
        }
}

private fun ChapterSpeechAnalysisEntity.toDomain() = ChapterSpeechAnalysis(
    id = id,
    bookUrl = bookUrl,
    chapterIndex = chapterIndex,
    contentHash = contentHash,
    resolverVersion = resolverVersion,
    characterRevision = characterRevision,
    status = SpeechAnalysisStatus.fromStorage(status),
    error = error,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun ChapterSpeechAnalysis.toEntity() = ChapterSpeechAnalysisEntity(
    id = id,
    bookUrl = bookUrl,
    chapterIndex = chapterIndex,
    contentHash = contentHash,
    resolverVersion = resolverVersion,
    characterRevision = characterRevision,
    status = status.storageValue,
    error = error,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun ChapterSpeechSegmentEntity.toDomain() = ChapterSpeechSegment(
    id = id,
    analysisId = analysisId,
    bookUrl = bookUrl,
    chapterIndex = chapterIndex,
    paragraphIndex = paragraphIndex,
    start = start,
    end = end,
    chapterPosition = chapterPosition,
    text = text,
    roleType = SpeechRoleType.fromStorage(roleType),
    characterId = characterId,
    characterName = characterName,
    emotion = emotion,
    confidence = confidence,
    source = SpeechResolutionSource.fromStorage(source),
    userLocked = userLocked,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun ChapterSpeechSegment.toEntity() = ChapterSpeechSegmentEntity(
    id = id,
    analysisId = analysisId,
    bookUrl = bookUrl,
    chapterIndex = chapterIndex,
    paragraphIndex = paragraphIndex,
    start = start,
    end = end,
    chapterPosition = chapterPosition,
    text = text,
    roleType = roleType.storageValue,
    characterId = characterId,
    characterName = characterName,
    emotion = emotion,
    confidence = confidence,
    source = source.storageValue,
    userLocked = userLocked,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
