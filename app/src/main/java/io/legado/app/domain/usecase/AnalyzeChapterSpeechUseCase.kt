package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.ChapterSpeechGateway
import io.legado.app.domain.model.readaloud.CanonicalSpeechParagraph
import io.legado.app.domain.model.readaloud.ChapterSpeechAnalysis
import io.legado.app.domain.model.readaloud.ChapterSpeechAnalysisResult
import io.legado.app.domain.model.readaloud.ChapterSpeechSegment
import io.legado.app.domain.model.readaloud.SpeechAnalysisStatus
import io.legado.app.domain.model.readaloud.SpeechIdentity
import io.legado.app.domain.model.readaloud.SpeechRoleType
import io.legado.app.help.readaloud.segment.RuleBasedSpeechSegmenter

class AnalyzeChapterSpeechUseCase(
    private val chapterSpeechGateway: ChapterSpeechGateway,
) {

    suspend operator fun invoke(
        bookUrl: String,
        chapterIndex: Int,
        paragraphs: List<CanonicalSpeechParagraph>,
        resolverVersion: String = RuleBasedSpeechSegmenter.VERSION,
        now: Long = System.currentTimeMillis(),
    ): ChapterSpeechAnalysisResult {
        val contentHash = SpeechIdentity.chapterContentHash(paragraphs)
        val cached = chapterSpeechGateway.getAnalysis(
            bookUrl = bookUrl,
            chapterIndex = chapterIndex,
            contentHash = contentHash,
            resolverVersion = resolverVersion,
        )
        if (
            cached != null &&
            cached.status in setOf(SpeechAnalysisStatus.Success, SpeechAnalysisStatus.Partial)
        ) {
            return ChapterSpeechAnalysisResult(
                analysis = cached,
                segments = chapterSpeechGateway.getSegments(cached.id),
                fromCache = true,
            )
        }

        val analysisId = SpeechIdentity.analysisId(
            bookUrl = bookUrl,
            chapterIndex = chapterIndex,
            contentHash = contentHash,
            resolverVersion = resolverVersion,
        )
        val drafts = RuleBasedSpeechSegmenter.segment(paragraphs)
        val segments = drafts.map { draft ->
            ChapterSpeechSegment(
                id = SpeechIdentity.segmentId(
                    analysisId = analysisId,
                    paragraphIndex = draft.paragraphIndex,
                    start = draft.start,
                    end = draft.end,
                ),
                analysisId = analysisId,
                bookUrl = bookUrl,
                chapterIndex = chapterIndex,
                paragraphIndex = draft.paragraphIndex,
                start = draft.start,
                end = draft.end,
                chapterPosition = draft.chapterPosition,
                text = draft.text,
                roleType = draft.roleType,
                emotion = draft.emotion,
                confidence = draft.confidence,
                source = draft.source,
                createdAt = now,
                updatedAt = now,
            )
        }
        val status = if (segments.any { it.roleType.requiresSpeakerResolution }) {
            SpeechAnalysisStatus.Partial
        } else {
            SpeechAnalysisStatus.Success
        }
        val analysis = ChapterSpeechAnalysis(
            id = analysisId,
            bookUrl = bookUrl,
            chapterIndex = chapterIndex,
            contentHash = contentHash,
            resolverVersion = resolverVersion,
            characterRevision = "",
            status = status,
            createdAt = cached?.createdAt ?: now,
            updatedAt = now,
        )
        chapterSpeechGateway.saveAnalysis(analysis, segments)
        return ChapterSpeechAnalysisResult(analysis, segments, fromCache = false)
    }

    private val SpeechRoleType.requiresSpeakerResolution: Boolean
        get() = this == SpeechRoleType.Character || this == SpeechRoleType.Thought
}
