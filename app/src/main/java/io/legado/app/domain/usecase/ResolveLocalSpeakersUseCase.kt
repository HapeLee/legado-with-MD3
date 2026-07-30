package io.legado.app.domain.usecase

import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.domain.gateway.BookKnowledgeGateway
import io.legado.app.domain.gateway.ChapterSpeechGateway
import io.legado.app.domain.model.readaloud.CanonicalSpeechParagraph
import io.legado.app.domain.model.readaloud.ChapterSpeechAnalysisResult
import io.legado.app.domain.model.readaloud.CharacterPerformanceProfile
import io.legado.app.domain.model.readaloud.SpeakerCharacter
import io.legado.app.domain.model.readaloud.SpeechAnalysisStatus
import io.legado.app.domain.model.readaloud.SpeechIdentity
import io.legado.app.domain.model.readaloud.SpeechResolutionSource
import io.legado.app.domain.model.readaloud.SpeechRoleType
import io.legado.app.help.readaloud.resolve.LocalCharacterSpeakerResolver
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ResolveLocalSpeakersUseCase(
    private val bookKnowledgeGateway: BookKnowledgeGateway,
    private val chapterSpeechGateway: ChapterSpeechGateway,
) {

    suspend operator fun invoke(
        analysisResult: ChapterSpeechAnalysisResult,
        paragraphs: List<CanonicalSpeechParagraph>,
        now: Long = System.currentTimeMillis(),
    ): ChapterSpeechAnalysisResult {
        val profiles = withContext(Dispatchers.IO) {
            bookKnowledgeGateway.getCharacterProfiles(analysisResult.analysis.bookUrl, 200)
                .filter { it.status == BookCharacterProfile.STATUS_ACTIVE }
        }
        val characters = profiles.map { profile ->
            SpeakerCharacter(
                id = profile.id,
                name = profile.name,
                aliases = GSON.fromJsonArray<String>(profile.aliasesJson).getOrNull().orEmpty(),
                role = profile.role,
                voiceGender = profile.voiceGender,
                voiceAgeBand = profile.voiceAgeBand,
                updatedAt = profile.updatedAt,
            )
        }
        val characterPerformances = profiles.map { profile ->
            CharacterPerformanceProfile(
                characterId = profile.id,
                role = profile.role,
                voiceGender = profile.voiceGender,
                voiceAgeBand = profile.voiceAgeBand,
                personality = profile.personality,
                updatedAt = profile.updatedAt,
            )
        }
        val characterRevision = buildString {
            append(LocalCharacterSpeakerResolver.VERSION)
            append(':')
            append(SpeechIdentity.characterRevision(characters))
        }
        if (analysisResult.analysis.characterRevision == characterRevision) {
            return analysisResult.copy(
                fromCache = true,
                characterPerformances = characterPerformances,
            )
        }

        val resetSegments = analysisResult.segments.map { segment ->
            if (segment.source == SpeechResolutionSource.Local && !segment.userLocked) {
                segment.copy(
                    characterId = null,
                    characterName = "",
                    confidence = minOf(segment.confidence, 0.72f),
                    source = SpeechResolutionSource.Rule,
                )
            } else {
                segment
            }
        }
        val resolved = LocalCharacterSpeakerResolver.resolve(
            paragraphs = paragraphs,
            segments = resetSegments,
            characters = characters,
        )
        val status = if (resolved.any { it.needsSpeakerResolution }) {
            SpeechAnalysisStatus.Partial
        } else {
            SpeechAnalysisStatus.Success
        }
        val analysis = analysisResult.analysis.copy(
            characterRevision = characterRevision,
            status = status,
            updatedAt = now,
        )
        chapterSpeechGateway.saveAnalysis(analysis, resolved)
        return ChapterSpeechAnalysisResult(
            analysis = analysis,
            segments = resolved,
            fromCache = false,
            characterPerformances = characterPerformances,
        )
    }

    private val io.legado.app.domain.model.readaloud.ChapterSpeechSegment.needsSpeakerResolution: Boolean
        get() = characterId == null &&
            (roleType == SpeechRoleType.Character || roleType == SpeechRoleType.Thought)
}
