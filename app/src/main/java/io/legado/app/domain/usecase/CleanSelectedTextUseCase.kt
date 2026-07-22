package io.legado.app.domain.usecase

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.data.entities.AiArtifact
import io.legado.app.domain.gateway.AiArtifactGateway
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiPromptTemplate
import io.legado.app.domain.model.AiTaskPresetConfig
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.AiToolContext
import io.legado.app.utils.MD5Utils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class CleanSelectedTextUseCase(
    private val aiProfileGateway: AiProfileGateway,
    private val aiToolAwareGenerationUseCase: AiToolAwareGenerationUseCase,
    private val aiArtifactGateway: AiArtifactGateway,
    private val aiTaskManager: AiTaskManager,
) {

    sealed interface StreamEvent {
        data class Content(val text: String) : StreamEvent
        data class Reasoning(val text: String) : StreamEvent
        data class Done(
            val replacement: String,
            val rawText: String,
            val reasoning: String,
        ) : StreamEvent
    }

    fun observeTask(bookUrl: String, chapterIndex: Int) = aiTaskManager.observeBookTask(
        bookUrl = bookUrl,
        taskType = AiTaskType.CLEAN_SELECTION,
        chapterIndex = chapterIndex,
    )

    fun observeTaskById(taskId: String) = aiTaskManager.observeTask(taskId)

    suspend fun start(
        bookUrl: String,
        chapterIndex: Int,
        chapterTitle: String,
        selectedText: String,
        contextBefore: String,
        contextAfter: String,
    ): String {
        val preset = resolvePreset() ?: error("No AI model configured")
        val input = buildUserContent(
            chapterTitle = chapterTitle,
            selectedText = selectedText,
            contextBefore = contextBefore.takeLast(MAX_CONTEXT_CHARS),
            contextAfter = contextAfter.take(MAX_CONTEXT_CHARS),
        )
        val contentHash = MD5Utils.md5Encode(input)
        val promptHash = MD5Utils.md5Encode(
            AiPromptTemplate.DEFAULT_CLEAN_SELECTION + AiToolAwareGenerationUseCase.CACHE_PROMPT_VERSION,
        )
        val now = System.currentTimeMillis()
        val artifact = AiArtifact(
            id = "${bookUrl}_${chapterIndex}_${AiTaskType.CLEAN_SELECTION}_${contentHash}_${preset.model.id}",
            taskType = AiTaskType.CLEAN_SELECTION,
            bookUrl = bookUrl,
            chapterIndex = chapterIndex,
            contentHash = contentHash,
            promptHash = promptHash,
            modelProfileId = preset.model.id,
            createdAt = now,
            updatedAt = now,
        )
        return aiTaskManager.submit(artifact) {
            var replacement = ""
            executeStream(
                bookUrl,
                chapterIndex,
                chapterTitle,
                selectedText,
                contextBefore,
                contextAfter
            )
                .collect { event ->
                    when (event) {
                        is StreamEvent.Content -> appendContent(event.text)
                        is StreamEvent.Reasoning -> appendReasoning(event.text)
                        is StreamEvent.Done -> replacement = event.replacement
                    }
                }
            replacement
        }
    }

    suspend fun execute(
        bookUrl: String,
        chapterIndex: Int,
        chapterTitle: String,
        selectedText: String,
        contextBefore: String,
        contextAfter: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(selectedText.isNotBlank()) { "Selected text is empty" }
            require(selectedText.length <= MAX_SELECTED_TEXT_CHARS) {
                "Selected text is too long"
            }

            val preset = resolvePreset() ?: error("No AI model configured")
            val prompt = preset.promptTemplate.ifBlank { AiPromptTemplate.DEFAULT_CLEAN_SELECTION }
            val input = buildUserContent(
                chapterTitle = chapterTitle,
                selectedText = selectedText,
                contextBefore = contextBefore.takeLast(MAX_CONTEXT_CHARS),
                contextAfter = contextAfter.take(MAX_CONTEXT_CHARS),
            )
            val contentHash = MD5Utils.md5Encode(input)
            val promptHash = MD5Utils.md5Encode(
                prompt + AiToolAwareGenerationUseCase.CACHE_PROMPT_VERSION
            )

            aiArtifactGateway.getCachedArtifact(
                bookUrl = bookUrl,
                chapterIndex = chapterIndex,
                taskType = AiTaskType.CLEAN_SELECTION,
                contentHash = contentHash,
                promptHash = promptHash,
                modelProfileId = preset.model.id,
            )?.output?.let { return@runCatching it }

            val responseText = aiToolAwareGenerationUseCase.generate(
                AiGenerateRequest(
                    model = preset.model,
                    messages = listOf(
                        AiMessage(AiMessageRole.SYSTEM, prompt),
                        AiMessage(AiMessageRole.USER, input),
                    ),
                    params = preset.params.copy(
                        maxOutputTokens = preset.params.maxOutputTokens
                            ?.coerceAtMost(MAX_OUTPUT_TOKENS)
                            ?: MAX_OUTPUT_TOKENS,
                    ),
                    toolContext = AiToolContext(
                        bookUrl = bookUrl,
                        chapterIndex = chapterIndex,
                        chapterTitle = chapterTitle,
                    ),
                )
            )
            val replacement = parseCleanReplacement(responseText)
            val now = System.currentTimeMillis()
            aiArtifactGateway.upsertArtifact(
                AiArtifact(
                    id = "${bookUrl}_${chapterIndex}_${AiTaskType.CLEAN_SELECTION}_${contentHash}_${preset.model.id}",
                    taskType = AiTaskType.CLEAN_SELECTION,
                    bookUrl = bookUrl,
                    chapterIndex = chapterIndex,
                    contentHash = contentHash,
                    promptHash = promptHash,
                    modelProfileId = preset.model.id,
                    status = AiArtifact.STATUS_SUCCESS,
                    output = replacement,
                    createdAt = now,
                    updatedAt = now,
                )
            )
            replacement
        }.onFailure { error ->
            if (error is CancellationException) throw error
        }
    }

    fun executeStream(
        bookUrl: String,
        chapterIndex: Int,
        chapterTitle: String,
        selectedText: String,
        contextBefore: String,
        contextAfter: String,
    ): Flow<StreamEvent> = flow {
        require(selectedText.isNotBlank()) { "Selected text is empty" }
        require(selectedText.length <= MAX_SELECTED_TEXT_CHARS) {
            "Selected text is too long"
        }

        val preset = resolvePreset() ?: error("No AI model configured")
        val prompt = AiPromptTemplate.DEFAULT_CLEAN_SELECTION
        val input = buildUserContent(
            chapterTitle = chapterTitle,
            selectedText = selectedText,
            contextBefore = contextBefore.takeLast(MAX_CONTEXT_CHARS),
            contextAfter = contextAfter.take(MAX_CONTEXT_CHARS),
        )
        val contentHash = MD5Utils.md5Encode(input)
        val promptHash = MD5Utils.md5Encode(
            prompt + AiToolAwareGenerationUseCase.CACHE_PROMPT_VERSION
        )

        aiArtifactGateway.getCachedArtifact(
            bookUrl = bookUrl,
            chapterIndex = chapterIndex,
            taskType = AiTaskType.CLEAN_SELECTION,
            contentHash = contentHash,
            promptHash = promptHash,
            modelProfileId = preset.model.id,
        )?.output?.let { cached ->
            emit(StreamEvent.Content(cached))
            emit(StreamEvent.Done(cached, cached, ""))
            return@flow
        }

        val rawBuilder = StringBuilder()
        val reasoningBuilder = StringBuilder()
        aiToolAwareGenerationUseCase.generateStream(
            AiGenerateRequest(
                model = preset.model,
                messages = listOf(
                    AiMessage(AiMessageRole.SYSTEM, prompt),
                    AiMessage(AiMessageRole.USER, input),
                ),
                params = preset.params.copy(
                    maxOutputTokens = preset.params.maxOutputTokens
                        ?.coerceAtMost(MAX_OUTPUT_TOKENS)
                        ?: MAX_OUTPUT_TOKENS,
                ),
                toolContext = AiToolContext(
                    bookUrl = bookUrl,
                    chapterIndex = chapterIndex,
                    chapterTitle = chapterTitle,
                ),
            )
        ).collect { event ->
            when (event) {
                is AiStreamEvent.Content -> {
                    rawBuilder.append(event.text)
                    emit(StreamEvent.Content(event.text))
                }

                is AiStreamEvent.Reasoning -> {
                    reasoningBuilder.append(event.text)
                    emit(StreamEvent.Reasoning(event.text))
                }

                is AiStreamEvent.ToolCallDelta -> Unit
            }
        }

        val rawText = rawBuilder.toString()
        val replacement = parseCleanReplacement(rawText)
        val now = System.currentTimeMillis()
        aiArtifactGateway.upsertArtifact(
            AiArtifact(
                id = "${bookUrl}_${chapterIndex}_${AiTaskType.CLEAN_SELECTION}_${contentHash}_${preset.model.id}",
                taskType = AiTaskType.CLEAN_SELECTION,
                bookUrl = bookUrl,
                chapterIndex = chapterIndex,
                contentHash = contentHash,
                promptHash = promptHash,
                modelProfileId = preset.model.id,
                status = AiArtifact.STATUS_SUCCESS,
                output = replacement,
                createdAt = now,
                updatedAt = now,
            )
        )
        emit(
            StreamEvent.Done(
                replacement = replacement,
                rawText = rawText,
                reasoning = reasoningBuilder.toString(),
            )
        )
    }.flowOn(Dispatchers.IO)

    private suspend fun resolvePreset(): AiTaskPresetConfig? {
        return aiProfileGateway.getTaskPreset(AiTaskType.CLEAN_SELECTION)
            ?: aiProfileGateway.getTaskPreset(AiTaskType.SUMMARIZE_CHAPTER)
            ?: aiProfileGateway.getTaskPreset(AiTaskType.CHAT)
            ?: aiProfileGateway.getTaskPreset(AiTaskType.TRANSLATE_CHAPTER)
    }

    private fun buildUserContent(
        chapterTitle: String,
        selectedText: String,
        contextBefore: String,
        contextAfter: String,
    ): String {
        return JsonObject().apply {
            addProperty("chapter_title", chapterTitle)
            addProperty("context_before", contextBefore)
            addProperty("selected_text", selectedText)
            addProperty("context_after", contextAfter)
        }.toString()
    }

    private companion object {
        const val MAX_SELECTED_TEXT_CHARS = 6000
        const val MAX_CONTEXT_CHARS = 1000
        const val MAX_OUTPUT_TOKENS = 1200
    }
}

internal fun parseCleanReplacement(rawResponse: String): String {
    val trimmed = rawResponse.trim()
    val withoutFence = if (trimmed.startsWith("```")) {
        trimmed
            .substringAfter('\n', missingDelimiterValue = trimmed.removePrefix("```"))
            .substringBeforeLast("```")
            .trim()
    } else {
        trimmed
    }
    val objectStart = withoutFence.indexOf('{')
    val objectEnd = withoutFence.lastIndexOf('}')
    require(objectStart >= 0 && objectEnd > objectStart) {
        "AI returned an invalid replacement"
    }
    val root = JsonParser.parseString(
        withoutFence.substring(objectStart, objectEnd + 1)
    ).asJsonObject
    val replacement = root.get("replacement")
    require(
        replacement != null &&
                !replacement.isJsonNull &&
                replacement.isJsonPrimitive &&
                replacement.asJsonPrimitive.isString
    ) {
        "AI returned an invalid replacement"
    }
    return replacement.asString
}
