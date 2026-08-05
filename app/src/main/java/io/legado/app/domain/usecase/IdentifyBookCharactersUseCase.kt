package io.legado.app.domain.usecase

import com.google.gson.JsonParser
import io.legado.app.data.entities.AiArtifact
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.domain.gateway.AiArtifactGateway
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.BookKnowledgeGateway
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.AiToolContext
import io.legado.app.utils.GSON
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.fromJsonArray
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import java.util.UUID

class IdentifyBookCharactersUseCase(
    private val aiProfileGateway: AiProfileGateway,
    private val aiToolAwareGenerationUseCase: AiToolAwareGenerationUseCase,
    private val aiArtifactGateway: AiArtifactGateway,
    private val aiTaskManager: AiTaskManager,
    private val bookKnowledgeGateway: BookKnowledgeGateway,
) {
    data class Candidate(
        val name: String,
        val aliases: List<String>,
        val voiceGender: String,
        val voiceAgeBand: String,
        val role: String,
        val personality: String,
        val summary: String,
        val evidence: String,
        val confidence: Float,
    )

    sealed interface Progress {
        data class Reasoning(val text: String) : Progress
        data class ToolCall(val name: String) : Progress
        data class Done(val candidates: List<Candidate>) : Progress
    }

    suspend fun identify(bookUrl: String): List<Candidate> {
        var candidates = emptyList<Candidate>()
        identifyStream(bookUrl).collect { progress ->
            if (progress is Progress.Done) candidates = progress.candidates
        }
        return candidates
    }

    suspend fun loadLatest(bookUrl: String): List<Candidate> {
        val artifact =
            aiArtifactGateway.observeBookArtifacts(bookUrl, AiTaskType.IDENTIFY_CHARACTERS)
                .first()
                .firstOrNull { it.status == AiArtifact.STATUS_SUCCESS && !it.output.isNullOrBlank() }
                ?: return emptyList()
        return decodeCandidates(artifact.output)
    }

    fun observeTask(bookUrl: String) = aiTaskManager.observeBookTask(
        bookUrl = bookUrl,
        taskType = AiTaskType.IDENTIFY_CHARACTERS,
    )

    suspend fun start(bookUrl: String): String {
        val identifyPreset = aiProfileGateway.getTaskPreset(AiTaskType.IDENTIFY_CHARACTERS)
        val preset = identifyPreset
            ?: aiProfileGateway.getTaskPreset(AiTaskType.CHAT)
            ?: error("No AI model configured for character identification")
        val prompt = identifyPreset?.promptTemplate?.takeIf(String::isNotBlank) ?: DEFAULT_PROMPT
        val now = System.currentTimeMillis()
        val contentHash = MD5Utils.md5Encode(bookUrl)
        val promptHash =
            MD5Utils.md5Encode(prompt + AiToolAwareGenerationUseCase.CACHE_PROMPT_VERSION)
        val artifact = AiArtifact(
            id = "${contentHash}_${AiTaskType.IDENTIFY_CHARACTERS}_${promptHash}_${preset.model.id}",
            taskType = AiTaskType.IDENTIFY_CHARACTERS,
            bookUrl = bookUrl,
            contentHash = contentHash,
            promptHash = promptHash,
            modelProfileId = preset.model.id,
            createdAt = now,
            updatedAt = now,
        )
        return aiTaskManager.submit(artifact) {
            var candidates = emptyList<Candidate>()
            identifyStream(bookUrl).collect { progress ->
                when (progress) {
                    is Progress.Reasoning -> appendReasoning(progress.text)
                    is Progress.ToolCall -> reportToolCall(progress.name)
                    is Progress.Done -> candidates = progress.candidates
                }
            }
            GSON.toJson(candidates)
        }
    }

    fun decodeCandidates(output: String?): List<Candidate> =
        output?.let { GSON.fromJsonArray<Candidate>(it).getOrNull() }.orEmpty()

    fun identifyStream(bookUrl: String): Flow<Progress> = flow {
        val identifyPreset = aiProfileGateway.getTaskPreset(AiTaskType.IDENTIFY_CHARACTERS)
        val preset = identifyPreset
            ?: aiProfileGateway.getTaskPreset(AiTaskType.CHAT)
            ?: error("No AI model configured for character identification")
        val prompt = identifyPreset?.promptTemplate?.takeIf(String::isNotBlank) ?: DEFAULT_PROMPT
        val response = StringBuilder()
        aiToolAwareGenerationUseCase.generateStream(
            AiGenerateRequest(
                model = preset.model,
                messages = listOf(
                    AiMessage(AiMessageRole.SYSTEM, prompt),
                    AiMessage(
                        AiMessageRole.USER,
                        "识别本书已下载章节中的人物。请先用工具读取已缓存章节，再只返回要求的 JSON。"
                    ),
                ),
                params = preset.params.copy(temperature = 0f),
                toolContext = AiToolContext(bookUrl = bookUrl),
            )
        ).collect { event ->
            when (event) {
                is io.legado.app.domain.gateway.AiStreamEvent.Content -> response.append(event.text)
                is io.legado.app.domain.gateway.AiStreamEvent.Reasoning -> emit(
                    Progress.Reasoning(
                        event.text
                    )
                )

                is io.legado.app.domain.gateway.AiStreamEvent.ToolCallDelta -> {
                    event.name?.takeIf(String::isNotBlank)?.let { emit(Progress.ToolCall(it)) }
                }
            }
        }
        val jsonStart = response.indexOf('{')
        val jsonEnd = response.lastIndexOf('}')
        require(jsonStart >= 0 && jsonEnd > jsonStart) {
            "Character identification did not return a JSON object"
        }
        val root = JsonParser.parseString(response.substring(jsonStart, jsonEnd + 1)).asJsonObject
        val candidates = root.getAsJsonArray("characters").map { element ->
            val item = element.asJsonObject
            Candidate(
                name = item.get("name")?.asString?.trim().orEmpty(),
                aliases = item.getAsJsonArray("aliases")?.map { it.asString.trim() }.orEmpty(),
                voiceGender = item.get("voiceGender")?.asString ?: "unknown",
                voiceAgeBand = item.get("voiceAgeBand")?.asString ?: "unknown",
                role = item.get("role")?.asString.orEmpty(),
                personality = item.get("personality")?.asString.orEmpty(),
                summary = item.get("summary")?.asString.orEmpty(),
                evidence = item.get("evidence")?.asString.orEmpty(),
                confidence = item.get("confidence")?.asFloat?.coerceIn(0f, 1f) ?: 0f,
            )
        }.filter { it.name.isNotBlank() && it.confidence >= MIN_CONFIDENCE }
            .distinctBy { it.name }
        val now = System.currentTimeMillis()
        val contentHash = MD5Utils.md5Encode(bookUrl)
        val promptHash =
            MD5Utils.md5Encode(prompt + AiToolAwareGenerationUseCase.CACHE_PROMPT_VERSION)
        aiArtifactGateway.upsertArtifact(
            AiArtifact(
                id = "${contentHash}_${AiTaskType.IDENTIFY_CHARACTERS}_${promptHash}_${preset.model.id}",
                taskType = AiTaskType.IDENTIFY_CHARACTERS,
                bookUrl = bookUrl,
                contentHash = contentHash,
                promptHash = promptHash,
                modelProfileId = preset.model.id,
                status = AiArtifact.STATUS_SUCCESS,
                output = GSON.toJson(candidates),
                createdAt = now,
                updatedAt = now,
            )
        )
        emit(Progress.Done(candidates))
    }

    suspend fun save(bookUrl: String, candidates: List<Candidate>) {
        candidates.forEach { candidate ->
            val existing = bookKnowledgeGateway.getCharacterProfile(bookUrl, candidate.name)
            bookKnowledgeGateway.upsertCharacterProfile(
                BookCharacterProfile(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    bookUrl = bookUrl,
                    name = candidate.name,
                    aliasesJson = GSON.toJson(
                        (GSON.fromJsonArray<String>(existing?.aliasesJson.orEmpty()).getOrNull()
                            .orEmpty()
                                + candidate.aliases)
                            .map(String::trim)
                            .filter(String::isNotBlank)
                            .distinct(),
                    ),
                    role = candidate.role,
                    voiceGender = candidate.voiceGender,
                    voiceAgeBand = candidate.voiceAgeBand,
                    personality = candidate.personality,
                    summary = candidate.summary,
                    source = BookCharacterProfile.SOURCE_AI,
                    confidence = candidate.confidence,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    companion object {
        private const val MIN_CONFIDENCE = 0.65f
        const val DEFAULT_PROMPT =
            """You identify stable fictional characters from downloaded local chapters. Use read-only tools to list and read cached chapters and inspect existing characters. Return JSON only: {\"characters\":[{\"name\":string,\"aliases\":[string],\"voiceGender\":\"male|female|unknown\",\"voiceAgeBand\":\"child|teen|young_adult|adult|elderly|unknown\",\"role\":\"male_lead|female_lead|male_supporting|female_supporting|\",\"personality\":string,\"summary\":string,\"evidence\":string,\"confidence\":number}]}. Do not include pronouns, generic titles, or one-off passers-by. Use unknown instead of guessing age or gender. Do not create duplicates of existing names or aliases."""
    }
}
