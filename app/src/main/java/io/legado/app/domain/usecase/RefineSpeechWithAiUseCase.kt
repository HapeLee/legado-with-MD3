package io.legado.app.domain.usecase

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.gateway.BookKnowledgeGateway
import io.legado.app.domain.gateway.ChapterSpeechGateway
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiTaskPresetConfig
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.readaloud.CanonicalSpeechParagraph
import io.legado.app.domain.model.readaloud.ChapterSpeechAnalysisResult
import io.legado.app.domain.model.readaloud.ChapterSpeechSegment
import io.legado.app.domain.model.readaloud.SpeechAnalysisMode
import io.legado.app.domain.model.readaloud.SpeechAnalysisStatus
import io.legado.app.domain.model.readaloud.SpeechEmotion
import io.legado.app.domain.model.readaloud.SpeechIdentity
import io.legado.app.domain.model.readaloud.SpeechResolutionSource
import io.legado.app.domain.model.readaloud.SpeechRoleType
import io.legado.app.help.readaloud.segment.AiSpeechAtom
import io.legado.app.help.readaloud.segment.AiSpeechAtomizer
import io.legado.app.help.readaloud.segment.RuleBasedSpeechSegmenter
import io.legado.app.utils.GSON
import io.legado.app.utils.MD5Utils

class RefineSpeechWithAiUseCase(
    private val aiProfileGateway: AiProfileGateway,
    private val aiTextGateway: AiTextGateway,
    private val bookKnowledgeGateway: BookKnowledgeGateway,
    private val chapterSpeechGateway: ChapterSpeechGateway,
) {

    suspend fun resolverVersion(bookUrl: String, mode: SpeechAnalysisMode): String {
        if (mode == SpeechAnalysisMode.Rule) return RuleBasedSpeechSegmenter.VERSION
        val preset = resolvePreset()
        val profiles = activeProfiles(bookUrl)
        val characterRevision = profiles
            .sortedBy(BookCharacterProfile::id)
            .joinToString("|") { "${it.id}:${it.updatedAt}" }
        val promptHash = MD5Utils.md5Encode(systemPrompt(preset, mode))
        return listOf(
            RuleBasedSpeechSegmenter.VERSION,
            VERSION,
            mode.storageValue,
            preset.model.id,
            promptHash,
            MD5Utils.md5Encode(characterRevision),
        ).joinToString(":")
    }

    suspend operator fun invoke(
        analysisResult: ChapterSpeechAnalysisResult,
        paragraphs: List<CanonicalSpeechParagraph>,
        mode: SpeechAnalysisMode,
        now: Long = System.currentTimeMillis(),
    ): ChapterSpeechAnalysisResult {
        if (mode == SpeechAnalysisMode.Rule) return analysisResult
        if (
            mode == SpeechAnalysisMode.AiUnderstanding &&
            analysisResult.fromCache &&
            analysisResult.segments.all { it.userLocked || it.source == SpeechResolutionSource.Ai }
        ) return analysisResult
        val profiles = activeProfiles(analysisResult.analysis.bookUrl)
        val preset = resolvePreset()
        val refined = when (mode) {
            SpeechAnalysisMode.Rule -> analysisResult.segments
            SpeechAnalysisMode.RuleWithAi -> completeRuleSegments(
                analysisResult = analysisResult,
                profiles = profiles,
                preset = preset,
                now = now,
            )
            SpeechAnalysisMode.AiUnderstanding -> {
                if (analysisResult.segments.any(ChapterSpeechSegment::userLocked)) {
                    completeRuleSegments(analysisResult, profiles, preset, now)
                } else {
                    understandAtoms(analysisResult, paragraphs, profiles, preset, now)
                }
            }
        }
        val status = if (refined.any { segment ->
                segment.characterId == null && segment.roleType in setOf(
                    SpeechRoleType.Character,
                    SpeechRoleType.Thought,
                )
            }) {
            SpeechAnalysisStatus.Partial
        } else {
            SpeechAnalysisStatus.Success
        }
        val analysis = analysisResult.analysis.copy(status = status, error = "", updatedAt = now)
        chapterSpeechGateway.saveAnalysis(analysis, refined)
        return analysisResult.copy(
            analysis = analysis,
            segments = refined,
            fromCache = false,
        )
    }

    private suspend fun completeRuleSegments(
        analysisResult: ChapterSpeechAnalysisResult,
        profiles: List<BookCharacterProfile>,
        preset: AiTaskPresetConfig,
        now: Long,
    ): List<ChapterSpeechSegment> {
        val candidates = analysisResult.segments.filter { segment ->
            !segment.userLocked && segment.source != SpeechResolutionSource.Ai && (
                segment.confidence < HYBRID_CONFIDENCE_THRESHOLD ||
                    segment.characterId == null && segment.roleType in setOf(
                        SpeechRoleType.Character,
                        SpeechRoleType.Thought,
                    )
                )
        }
        if (candidates.isEmpty()) return analysisResult.segments
        val updates = linkedMapOf<String, AiSegmentDecision>()
        candidates.chunkByTextLength(MAX_CHUNK_CHARS) { it.text }.forEach { chunk ->
            val payload = mapOf(
                "characters" to profiles.map { it.toPromptMap() },
                "segments" to chunk.map { segment ->
                    mapOf(
                        "segmentId" to segment.id,
                        "text" to segment.text,
                        "roleType" to segment.roleType.storageValue,
                        "characterId" to segment.characterId,
                        "emotion" to segment.emotion,
                        "confidence" to segment.confidence,
                    )
                },
            )
            parseSegmentDecisions(generate(preset, SpeechAnalysisMode.RuleWithAi, payload))
                .forEach { decision ->
                    require(decision.segmentId in chunk.map(ChapterSpeechSegment::id)) {
                        "AI returned an unknown segmentId: ${decision.segmentId}"
                    }
                    require(updates.put(decision.segmentId, decision) == null) {
                        "AI returned duplicate segmentId: ${decision.segmentId}"
                    }
                    require(decision.characterId == null || profiles.any { it.id == decision.characterId }) {
                        "AI returned an unknown characterId: ${decision.characterId}"
                    }
                }
            require(updates.keys.containsAll(chunk.map(ChapterSpeechSegment::id))) {
                "AI did not return every requested segment"
            }
        }
        val profilesById = profiles.associateBy(BookCharacterProfile::id)
        return analysisResult.segments.map { segment ->
            if (segment !in candidates) return@map segment
            val decision = updates[segment.id]
            val roleType = decision?.roleType ?: segment.roleType
            val character = decision?.characterId?.let(profilesById::get)
            segment.copy(
                roleType = roleType,
                characterId = if (roleType == SpeechRoleType.Narrator) null else character?.id,
                characterName = if (roleType == SpeechRoleType.Narrator) "" else character?.name.orEmpty(),
                emotion = decision?.emotion ?: segment.emotion,
                confidence = decision?.confidence ?: segment.confidence,
                source = SpeechResolutionSource.Ai,
                updatedAt = now,
            )
        }
    }

    private suspend fun understandAtoms(
        analysisResult: ChapterSpeechAnalysisResult,
        paragraphs: List<CanonicalSpeechParagraph>,
        profiles: List<BookCharacterProfile>,
        preset: AiTaskPresetConfig,
        now: Long,
    ): List<ChapterSpeechSegment> {
        val atoms = paragraphs.flatMap(AiSpeechAtomizer::atomize)
        if (atoms.isEmpty()) return analysisResult.segments
        val profilesById = profiles.associateBy(BookCharacterProfile::id)
        val result = mutableListOf<ChapterSpeechSegment>()
        atoms.chunkByTextLength(MAX_CHUNK_CHARS) { it.text }.forEach { chunk ->
            val payload = mapOf(
                "characters" to profiles.map { it.toPromptMap() },
                "atoms" to chunk.map { atom ->
                    mapOf("atomId" to atom.id, "text" to atom.text)
                },
            )
            val groups = parseAtomGroups(generate(preset, SpeechAnalysisMode.AiUnderstanding, payload))
            validateCoverage(chunk, groups)
            require(groups.all { group ->
                group.characterId == null || profilesById.containsKey(group.characterId)
            }) { "AI returned an unknown characterId" }
            val atomsById = chunk.associateBy(AiSpeechAtom::id)
            groups.forEach { group ->
                val groupedAtoms = group.atomIds.map(atomsById::getValue)
                require(groupedAtoms.map(AiSpeechAtom::paragraphIndex).distinct().size == 1) {
                    "AI cannot merge atoms across paragraphs"
                }
                val first = groupedAtoms.first()
                val last = groupedAtoms.last()
                val paragraph = paragraphs.first { it.index == first.paragraphIndex }
                val character = group.characterId?.let(profilesById::get)
                result += ChapterSpeechSegment(
                    id = SpeechIdentity.segmentId(
                        analysisId = analysisResult.analysis.id,
                        paragraphIndex = first.paragraphIndex,
                        start = first.start,
                        end = last.end,
                    ),
                    analysisId = analysisResult.analysis.id,
                    bookUrl = analysisResult.analysis.bookUrl,
                    chapterIndex = analysisResult.analysis.chapterIndex,
                    paragraphIndex = first.paragraphIndex,
                    start = first.start,
                    end = last.end,
                    chapterPosition = paragraph.chapterPosition + first.start,
                    text = paragraph.text.substring(first.start, last.end),
                    roleType = group.roleType,
                    characterId = if (group.roleType == SpeechRoleType.Narrator) null else character?.id,
                    characterName = if (group.roleType == SpeechRoleType.Narrator) "" else character?.name.orEmpty(),
                    emotion = group.emotion,
                    confidence = group.confidence,
                    source = SpeechResolutionSource.Ai,
                    createdAt = now,
                    updatedAt = now,
                )
            }
        }
        return result.sortedWith(compareBy(ChapterSpeechSegment::paragraphIndex, ChapterSpeechSegment::start))
    }

    private suspend fun generate(
        preset: AiTaskPresetConfig,
        mode: SpeechAnalysisMode,
        payload: Any,
    ): String = aiTextGateway.generate(
        AiGenerateRequest(
            model = preset.model,
            messages = listOf(
                AiMessage(AiMessageRole.SYSTEM, systemPrompt(preset, mode)),
                AiMessage(AiMessageRole.USER, GSON.toJson(payload)),
            ),
            params = preset.params.copy(temperature = 0f),
        )
    ).getOrThrow().text

    private suspend fun resolvePreset(): AiTaskPresetConfig =
        aiProfileGateway.getTaskPreset(AiTaskType.ANALYZE_SPEECH)
            ?: aiProfileGateway.getTaskPreset(AiTaskType.CHAT)
            ?: error("No AI model configured for speech analysis")

    private suspend fun activeProfiles(bookUrl: String): List<BookCharacterProfile> =
        bookKnowledgeGateway.getCharacterProfiles(bookUrl, 200)
            .filter { it.status == BookCharacterProfile.STATUS_ACTIVE }

    private fun systemPrompt(preset: AiTaskPresetConfig, mode: SpeechAnalysisMode): String {
        val custom = preset.promptTemplate.takeIf {
            preset.taskType == AiTaskType.ANALYZE_SPEECH && it.isNotBlank()
        }
        return buildString {
            append(custom ?: DEFAULT_PROMPT)
            append("\nReturn only one JSON object. Never rewrite text and never invent IDs.")
            if (mode == SpeechAnalysisMode.RuleWithAi) {
                append("\nReturn {\"segments\":[{\"segmentId\":string,\"roleType\":")
                append("\"narrator|character|thought|unknown\",\"characterId\":string|null,")
                append("\"emotion\":\"neutral|cheerful|sad|angry|fearful|surprised|disgusted|whispering|calm\",")
                append("\"confidence\":number}]}. Return one decision for every input segment.")
            } else {
                append("\nGroup every atom exactly once and in input order. Groups cannot cross paragraphs.")
                append("\nReturn {\"segments\":[{\"atomIds\":[string],\"roleType\":")
                append("\"narrator|character|thought|unknown\",\"characterId\":string|null,")
                append("\"emotion\":\"neutral|cheerful|sad|angry|fearful|surprised|disgusted|whispering|calm\",")
                append("\"confidence\":number}]}.")
            }
        }
    }

    private fun parseSegmentDecisions(text: String): List<AiSegmentDecision> =
        parseRoot(text).getAsJsonArray("segments").map { element ->
            val item = element.asJsonObject
            AiSegmentDecision(
                segmentId = item.requiredString("segmentId"),
                roleType = item.roleType(),
                characterId = item.optionalString("characterId"),
                emotion = item.emotion(),
                confidence = item.confidence(),
            )
        }

    private fun parseAtomGroups(text: String): List<AiAtomGroup> =
        parseRoot(text).getAsJsonArray("segments").map { element ->
            val item = element.asJsonObject
            AiAtomGroup(
                atomIds = item.getAsJsonArray("atomIds").map { it.asString },
                roleType = item.roleType(),
                characterId = item.optionalString("characterId"),
                emotion = item.emotion(),
                confidence = item.confidence(),
            )
        }

    private fun parseRoot(text: String): JsonObject {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        require(start >= 0 && end > start) { "AI returned invalid JSON" }
        return JsonParser.parseString(text.substring(start, end + 1)).asJsonObject
    }

    private fun JsonObject.roleType(): SpeechRoleType {
        val value = requiredString("roleType")
        require(value in SpeechRoleType.entries.map(SpeechRoleType::storageValue)) {
            "AI returned invalid roleType: $value"
        }
        return SpeechRoleType.fromStorage(value)
    }

    private fun JsonObject.emotion(): String =
        SpeechEmotion.fromStorage(optionalString("emotion").orEmpty()).storageValue

    private fun JsonObject.confidence(): Float =
        get("confidence")?.takeUnless { it.isJsonNull }?.asFloat?.coerceIn(0f, 1f) ?: 0f

    private fun JsonObject.requiredString(name: String): String =
        optionalString(name)?.takeIf(String::isNotBlank) ?: error("AI response misses $name")

    private fun JsonObject.optionalString(name: String): String? =
        get(name)?.takeUnless { it.isJsonNull }?.asString

    private fun validateCoverage(atoms: List<AiSpeechAtom>, groups: List<AiAtomGroup>) {
        require(groups.isNotEmpty()) { "AI returned no speech groups" }
        val returned = groups.flatMap(AiAtomGroup::atomIds)
        require(returned == atoms.map(AiSpeechAtom::id)) {
            "AI atom coverage is incomplete, duplicated, or out of order"
        }
    }

    private fun BookCharacterProfile.toPromptMap(): Map<String, Any?> = mapOf(
        "characterId" to id,
        "name" to name,
        "aliasesJson" to aliasesJson,
        "role" to role,
        "voiceGender" to voiceGender,
        "voiceAgeBand" to voiceAgeBand,
        "personality" to personality,
    )

    private fun <T> List<T>.chunkByTextLength(
        maxLength: Int,
        text: (T) -> String,
    ): List<List<T>> {
        if (isEmpty()) return emptyList()
        val result = mutableListOf<MutableList<T>>()
        var current = mutableListOf<T>()
        var length = 0
        forEach { item ->
            val itemLength = text(item).length
            if (current.isNotEmpty() && length + itemLength > maxLength) {
                result += current
                current = mutableListOf()
                length = 0
            }
            current += item
            length += itemLength
        }
        if (current.isNotEmpty()) result += current
        return result
    }

    private data class AiSegmentDecision(
        val segmentId: String,
        val roleType: SpeechRoleType,
        val characterId: String?,
        val emotion: String,
        val confidence: Float,
    )

    private data class AiAtomGroup(
        val atomIds: List<String>,
        val roleType: SpeechRoleType,
        val characterId: String?,
        val emotion: String,
        val confidence: Float,
    )

    companion object {
        const val VERSION = "ai-speech-analysis-v1"
        private const val MAX_CHUNK_CHARS = 6_000
        private const val HYBRID_CONFIDENCE_THRESHOLD = 0.75f
        private const val DEFAULT_PROMPT =
            "Analyze fiction speech for text-to-speech. Distinguish narration, spoken dialogue, " +
                "internal thought and unknown speech. Resolve speakers only from the supplied " +
                "character IDs, infer emotion conservatively, and use null when uncertain."
    }
}
