package io.legado.app.domain.usecase

import io.legado.app.domain.model.readaloud.CanonicalSpeechParagraph
import io.legado.app.domain.model.readaloud.SpeechPlanItem
import io.legado.app.domain.model.readaloud.SpeechAnalysisMode
import io.legado.app.help.readaloud.segment.RuleBasedSpeechSegmenter

/**
 * Builds the persisted speech plan used by a read-aloud session.
 *
 * Segmentation is pagination independent, while speaker resolution is refreshed when the
 * character database changes. Keeping the orchestration here prevents Android services from
 * knowing about the analysis cache or character repository.
 */
class PrepareChapterSpeechPlanUseCase(
    private val analyzeChapterSpeech: AnalyzeChapterSpeechUseCase,
    private val resolveLocalSpeakers: ResolveLocalSpeakersUseCase,
    private val refineSpeechWithAi: RefineSpeechWithAiUseCase,
    private val buildSpeechPlan: BuildSpeechPlanUseCase,
) {

    suspend operator fun invoke(
        bookUrl: String,
        chapterIndex: Int,
        paragraphs: List<CanonicalSpeechParagraph>,
        preferredDefaultVoiceId: String? = null,
        analysisMode: SpeechAnalysisMode = SpeechAnalysisMode.Rule,
        useMultiSpeaker: Boolean = true,
    ): List<SpeechPlanItem> {
        if (paragraphs.isEmpty()) return emptyList()
        val requestedMode = analysisMode
        val resolverVersion = if (requestedMode == SpeechAnalysisMode.Rule) {
            RuleBasedSpeechSegmenter.VERSION
        } else {
            runCatching { refineSpeechWithAi.resolverVersion(bookUrl, requestedMode) }
                .getOrDefault(RuleBasedSpeechSegmenter.VERSION)
        }
        val effectiveMode = if (resolverVersion == RuleBasedSpeechSegmenter.VERSION) {
            SpeechAnalysisMode.Rule
        } else {
            requestedMode
        }
        val analysis = analyzeChapterSpeech(
            bookUrl = bookUrl,
            chapterIndex = chapterIndex,
            paragraphs = paragraphs,
            resolverVersion = resolverVersion,
        )
        val locallyResolved = resolveLocalSpeakers(
            analysisResult = analysis,
            paragraphs = paragraphs,
        )
        val resolved = if (effectiveMode == SpeechAnalysisMode.Rule) {
            locallyResolved
        } else {
            runCatching {
                refineSpeechWithAi(
                    analysisResult = locallyResolved,
                    paragraphs = paragraphs,
                    mode = effectiveMode,
                )
            }.getOrDefault(locallyResolved)
        }
        return buildSpeechPlan(
            bookUrl = bookUrl,
            segments = resolved.segments,
            preferredDefaultVoiceId = preferredDefaultVoiceId,
            characterPerformances = resolved.characterPerformances.associateBy { it.characterId },
            useMultiSpeaker = useMultiSpeaker,
        )
    }
}
