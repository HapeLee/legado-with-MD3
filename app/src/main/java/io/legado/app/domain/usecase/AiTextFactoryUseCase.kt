package io.legado.app.domain.usecase

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
import io.legado.app.domain.model.ContentChunker
import io.legado.app.utils.MD5Utils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class AiTextFactoryUseCase(
    private val aiProfileGateway: AiProfileGateway,
    private val aiToolAwareGenerationUseCase: AiToolAwareGenerationUseCase,
    private val aiArtifactGateway: AiArtifactGateway,
    private val aiTaskManager: AiTaskManager,
) {

    data class Request(
        val bookUrl: String,
        val chapterIndex: Int? = null,
        val chapterTitle: String = "",
        val inputText: String,
        val taskType: String = AiTaskType.TEXT_FACTORY,
        val userInstruction: String,
        val referenceText: String = "",
        val maxCharsPerChunk: Int = DEFAULT_MAX_CHARS_PER_CHUNK,
        val skipCache: Boolean = false,
        val artifactContentHash: String? = null,
    )

    sealed interface StreamEvent {
        data class Content(val text: String) : StreamEvent
        data class Reasoning(val text: String) : StreamEvent
        data class Done(val text: String, val reasoning: String) : StreamEvent
    }

    fun observeTask(bookUrl: String, taskType: String, chapterIndex: Int?) =
        aiTaskManager.observeBookTask(bookUrl, taskType, chapterIndex)

    fun observeTaskById(taskId: String) = aiTaskManager.observeTask(taskId)

    suspend fun start(request: Request): String {
        val preset = resolvePreset(request.taskType) ?: error("No AI model configured")
        val systemPrompt = buildSystemPrompt(preset, request.userInstruction)
        val userInput =
            buildUserInput(request.chapterTitle, request.inputText, request.referenceText)
        val contentHash = request.artifactContentHash ?: MD5Utils.md5Encode(userInput)
        val promptHash = MD5Utils.md5Encode(
            systemPrompt + AiToolAwareGenerationUseCase.CACHE_PROMPT_VERSION,
        )
        val now = System.currentTimeMillis()
        val artifact = AiArtifact(
            id = request.buildArtifactId(contentHash, preset.model.id, now),
            taskType = request.taskType,
            bookUrl = request.bookUrl,
            chapterIndex = request.chapterIndex,
            contentHash = contentHash,
            promptHash = promptHash,
            modelProfileId = preset.model.id,
            createdAt = now,
            updatedAt = now,
        )
        return aiTaskManager.submit(artifact) {
            var output = ""
            executeStream(request).collect { event ->
                when (event) {
                    is StreamEvent.Content -> appendContent(event.text)
                    is StreamEvent.Reasoning -> appendReasoning(event.text)
                    is StreamEvent.Done -> output = event.text
                }
            }
            output
        }
    }

    suspend fun execute(request: Request): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(request.inputText.isNotBlank()) { "Input text is empty" }
            require(request.userInstruction.isNotBlank()) { "Instruction is empty" }

            val preset = resolvePreset(request.taskType)
                ?: error("No AI model configured")
            val systemPrompt = buildSystemPrompt(preset, request.userInstruction)
            val userInput = buildUserInput(
                chapterTitle = request.chapterTitle,
                text = request.inputText,
                referenceText = request.referenceText,
            )
            val contentHash = request.artifactContentHash ?: MD5Utils.md5Encode(userInput)
            val promptHash = MD5Utils.md5Encode(
                systemPrompt + AiToolAwareGenerationUseCase.CACHE_PROMPT_VERSION
            )

            if (!request.skipCache) {
                aiArtifactGateway.getCachedArtifact(
                    bookUrl = request.bookUrl,
                    chapterIndex = request.chapterIndex,
                    taskType = request.taskType,
                    contentHash = contentHash,
                    promptHash = promptHash,
                    modelProfileId = preset.model.id,
                )?.output?.let { return@runCatching it }
            }

            val chunks = ContentChunker.chunk(
                request.inputText,
                request.maxCharsPerChunk.coerceAtLeast(1000),
            )
            if (chunks.isEmpty()) error("Failed to chunk input text")

            val toolContext = request.toToolContext()
            val partialOutputs = chunks.map { chunk ->
                generate(
                    preset = preset,
                    systemPrompt = systemPrompt,
                    userContent = buildUserInput(
                        chapterTitle = request.chapterTitle,
                        text = chunk.content,
                        referenceText = request.referenceText,
                    ),
                    toolContext = toolContext,
                )
            }
            val output = if (partialOutputs.size == 1) {
                partialOutputs.first()
            } else {
                generate(
                    preset = preset,
                    systemPrompt = systemPrompt,
                    userContent = buildMergeUserInput(partialOutputs),
                    toolContext = toolContext,
                )
            }
            val now = System.currentTimeMillis()
            aiArtifactGateway.upsertArtifact(
                AiArtifact(
                    id = request.buildArtifactId(contentHash, preset.model.id, now),
                    taskType = request.taskType,
                    bookUrl = request.bookUrl,
                    chapterIndex = request.chapterIndex,
                    contentHash = contentHash,
                    promptHash = promptHash,
                    modelProfileId = preset.model.id,
                    status = AiArtifact.STATUS_SUCCESS,
                    output = output,
                    createdAt = now,
                    updatedAt = now,
                )
            )
            output
        }.onFailure { error ->
            if (error is CancellationException) throw error
        }
    }

    fun executeStream(request: Request): Flow<StreamEvent> = flow {
        require(request.inputText.isNotBlank()) { "Input text is empty" }
        require(request.userInstruction.isNotBlank()) { "Instruction is empty" }

        val preset = resolvePreset(request.taskType)
            ?: error("No AI model configured")
        val systemPrompt = buildSystemPrompt(preset, request.userInstruction)
        val userInput = buildUserInput(
            chapterTitle = request.chapterTitle,
            text = request.inputText,
            referenceText = request.referenceText,
        )
        val contentHash = request.artifactContentHash ?: MD5Utils.md5Encode(userInput)
        val promptHash = MD5Utils.md5Encode(
            systemPrompt + AiToolAwareGenerationUseCase.CACHE_PROMPT_VERSION
        )

        if (!request.skipCache) {
            aiArtifactGateway.getCachedArtifact(
                bookUrl = request.bookUrl,
                chapterIndex = request.chapterIndex,
                taskType = request.taskType,
                contentHash = contentHash,
                promptHash = promptHash,
                modelProfileId = preset.model.id,
            )?.output?.let { cached ->
                emit(StreamEvent.Content(cached))
                emit(StreamEvent.Done(cached, ""))
                return@flow
            }
        }

        val chunks = ContentChunker.chunk(
            request.inputText,
            request.maxCharsPerChunk.coerceAtLeast(1000),
        )
        if (chunks.isEmpty()) error("Failed to chunk input text")

        val outputBuilder = StringBuilder()
        val reasoningBuilder = StringBuilder()
        val toolContext = request.toToolContext()

        if (chunks.size == 1) {
            collectGenerateStream(
                preset = preset,
                systemPrompt = systemPrompt,
                userContent = userInput,
                toolContext = toolContext,
                outputBuilder = outputBuilder,
                reasoningBuilder = reasoningBuilder,
                emitEvent = { emit(it) },
            )
        } else {
            val partialOutputs = chunks.map { chunk ->
                generate(
                    preset = preset,
                    systemPrompt = systemPrompt,
                    userContent = buildUserInput(
                        chapterTitle = request.chapterTitle,
                        text = chunk.content,
                        referenceText = request.referenceText,
                    ),
                    toolContext = toolContext,
                )
            }
            val merged = generate(
                preset = preset,
                systemPrompt = systemPrompt,
                userContent = buildMergeUserInput(partialOutputs),
                toolContext = toolContext,
            )
            outputBuilder.append(merged)
            emit(StreamEvent.Content(merged))
        }

        val output = outputBuilder.toString().trim()
            .ifEmpty { error("AI returned empty text") }
        val reasoning = reasoningBuilder.toString()
        val now = System.currentTimeMillis()
        aiArtifactGateway.upsertArtifact(
            AiArtifact(
                id = request.buildArtifactId(contentHash, preset.model.id, now),
                taskType = request.taskType,
                bookUrl = request.bookUrl,
                chapterIndex = request.chapterIndex,
                contentHash = contentHash,
                promptHash = promptHash,
                modelProfileId = preset.model.id,
                status = AiArtifact.STATUS_SUCCESS,
                output = output,
                createdAt = now,
                updatedAt = now,
            )
        )
        emit(StreamEvent.Done(output, reasoning))
    }.flowOn(Dispatchers.IO)

    private suspend fun resolvePreset(taskType: String): AiTaskPresetConfig? {
        return aiProfileGateway.getTaskPreset(taskType)
            ?: aiProfileGateway.getTaskPreset(AiTaskType.TEXT_FACTORY)
            ?: aiProfileGateway.getTaskPreset(AiTaskType.CHAT)
            ?: aiProfileGateway.getTaskPreset(AiTaskType.SUMMARIZE_CHAPTER)
            ?: aiProfileGateway.getTaskPreset(AiTaskType.TRANSLATE_CHAPTER)
    }

    private fun buildSystemPrompt(
        preset: AiTaskPresetConfig,
        userInstruction: String,
    ): String {
        val basePrompt = preset.promptTemplate.ifBlank {
            AiPromptTemplate.DEFAULT_TEXT_FACTORY
        }
        return buildString {
            append(basePrompt.trim())
            append("\n\nOutput contract:\n")
            append("- Return only the final text that should replace the original \"Text to process\".\n")
            append("- Do not output the chapter title, book title, headings, subtitles, labels, or section names unless they already appear inside \"Text to process\" and must remain part of the body.\n")
            append("- Treat \"Chapter title\" and reference excerpts as context metadata, not as content to copy into the result.\n")
            append("- Do not add Markdown fences, bullet labels, explanations, summaries, notes, or prefaces.\n")
            append("- Preserve intentional paragraph breaks and ordinary prose formatting. Do not wrap the whole result in quotes.\n\n")
            append("User instruction:\n")
            append(userInstruction)
        }
    }

    private fun buildUserInput(
        chapterTitle: String,
        text: String,
        referenceText: String,
    ): String {
        return buildString {
            if (chapterTitle.isNotBlank()) {
                append("Chapter title: ")
                append(chapterTitle)
                append("\n\n")
            }
            if (referenceText.isNotBlank()) {
                append("Reference excerpts from other chapters. Use them only for continuity, names, relationships, timeline, and tone. Do not rewrite or summarize these excerpts:\n")
                append(referenceText)
                append("\n\n")
            }
            append("Text to process:\n")
            append(text)
        }
    }

    private fun buildMergeUserInput(partialOutputs: List<String>): String {
        return "Merge these processed text chunks into one coherent replacement text. " +
                "Return only the merged body text; do not add a chapter title, heading, label, or explanation.\n\n" +
                partialOutputs.joinToString("\n\n")
    }

    private suspend fun generate(
        preset: AiTaskPresetConfig,
        systemPrompt: String,
        userContent: String,
        toolContext: AiToolContext?,
    ): String {
        return aiToolAwareGenerationUseCase.generate(
            AiGenerateRequest(
                model = preset.model,
                messages = listOf(
                    AiMessage(AiMessageRole.SYSTEM, systemPrompt),
                    AiMessage(AiMessageRole.USER, userContent),
                ),
                params = preset.params,
                toolContext = toolContext,
            )
        )
    }

    private suspend fun collectGenerateStream(
        preset: AiTaskPresetConfig,
        systemPrompt: String,
        userContent: String,
        toolContext: AiToolContext?,
        outputBuilder: StringBuilder,
        reasoningBuilder: StringBuilder,
        emitEvent: suspend (StreamEvent) -> Unit,
    ) {
        aiToolAwareGenerationUseCase.generateStream(
            AiGenerateRequest(
                model = preset.model,
                messages = listOf(
                    AiMessage(AiMessageRole.SYSTEM, systemPrompt),
                    AiMessage(AiMessageRole.USER, userContent),
                ),
                params = preset.params,
                toolContext = toolContext,
            )
        ).collect { event ->
            when (event) {
                is AiStreamEvent.Content -> {
                    outputBuilder.append(event.text)
                    emitEvent(StreamEvent.Content(event.text))
                }

                is AiStreamEvent.Reasoning -> {
                    reasoningBuilder.append(event.text)
                    emitEvent(StreamEvent.Reasoning(event.text))
                }

                is AiStreamEvent.ToolCallDelta -> Unit
            }
        }
    }

    private fun Request.toToolContext(): AiToolContext {
        return AiToolContext(
            bookUrl = bookUrl,
            chapterIndex = chapterIndex,
            chapterTitle = chapterTitle.takeIf { it.isNotBlank() },
        )
    }

    private fun Request.buildArtifactId(
        contentHash: String,
        modelProfileId: String,
        createdAt: Long,
    ): String {
        val baseId = "${bookUrl}_${chapterIndex}_${taskType}_${contentHash}_${modelProfileId}"
        return if (skipCache) "${baseId}_$createdAt" else baseId
    }

    private companion object {
        const val DEFAULT_MAX_CHARS_PER_CHUNK = 8000
    }
}
