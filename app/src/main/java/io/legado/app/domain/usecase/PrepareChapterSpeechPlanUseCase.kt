package io.legado.app.domain.usecase

import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.domain.model.readaloud.CanonicalSpeechParagraph
import io.legado.app.domain.model.readaloud.ContentSplitPolicy
import io.legado.app.domain.model.readaloud.SpeechAnalysisMode
import io.legado.app.domain.model.readaloud.SpeechPlanItem
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
        analysisReasoningLevel: AiReasoningLevel = AiReasoningLevel.OFF,
        useMultiSpeaker: Boolean = true,
        policy: ContentSplitPolicy,
    ): List<SpeechPlanItem> {
        if (paragraphs.isEmpty()) return emptyList()
        val requestedMode = analysisMode
        val ruleVersion = ruleResolverVersion(policy)
        val resolverVersion = if (requestedMode == SpeechAnalysisMode.Rule) {
            ruleVersion
        } else {
            runCatching { refineSpeechWithAi.resolverVersion(bookUrl, requestedMode, policy) }
                .getOrDefault(ruleVersion)
        }
        val effectiveMode = if (isRuleResolverVersion(resolverVersion, policy)) {
            SpeechAnalysisMode.Rule
        } else {
            requestedMode
        }
        val analysis = analyzeChapterSpeech(
            bookUrl = bookUrl,
            chapterIndex = chapterIndex,
            paragraphs = paragraphs,
            resolverVersion = resolverVersion,
            policy = policy,
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
                    reasoningLevel = analysisReasoningLevel,
                    policy = policy,
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

/**
 * 纯规则分段的分析标识。
 *
 * 内容划分方式会改变切分结果，必须参与版本号，否则切换划分方式后会命中旧划分的分析缓存。
 */
fun ruleResolverVersion(policy: ContentSplitPolicy): String =
    "${RuleBasedSpeechSegmenter.VERSION}:${policy.identifier}"

/** 判断某个解析器版本是否就是该划分方式下的纯规则分段。 */
fun isRuleResolverVersion(resolverVersion: String, policy: ContentSplitPolicy): Boolean =
    resolverVersion == ruleResolverVersion(policy)
