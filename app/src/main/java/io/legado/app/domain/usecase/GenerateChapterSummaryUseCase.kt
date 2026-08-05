package io.legado.app.domain.usecase

import io.legado.app.data.entities.AiArtifact
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.domain.gateway.AiArtifactGateway
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiTaskPresetConfig
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.AiToolContext
import io.legado.app.domain.model.ContentChunker
import io.legado.app.help.book.BookHelp
import io.legado.app.utils.MD5Utils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class GenerateChapterSummaryUseCase(
    private val aiProfileGateway: AiProfileGateway,
    private val aiToolAwareGenerationUseCase: AiToolAwareGenerationUseCase,
    private val aiArtifactGateway: AiArtifactGateway,
    private val aiTaskManager: AiTaskManager,
) {

    sealed interface StreamEvent {
        data class Content(val text: String) : StreamEvent
        data class Reasoning(val text: String) : StreamEvent
        data class Done(val text: String, val reasoning: String) : StreamEvent
    }

    fun observeTask(bookUrl: String, chapterIndex: Int) = aiTaskManager.observeBookTask(
        bookUrl = bookUrl,
        taskType = AiTaskType.SUMMARIZE_CHAPTER,
        chapterIndex = chapterIndex,
    )

    suspend fun start(
        book: Book,
        bookChapter: BookChapter,
        contentOverride: String? = null,
        maxCharsPerChunk: Int = DEFAULT_MAX_CHARS_PER_CHUNK,
    ): String {
        val content = contentOverride ?: BookHelp.getContent(book, bookChapter)
        ?: error("Failed to read chapter content")
        val preset = resolvePreset() ?: error("No AI model configured for chapter summary")
        val contentHash = MD5Utils.md5Encode(content)
        val promptHash = MD5Utils.md5Encode(
            preset.promptTemplate + AiToolAwareGenerationUseCase.CACHE_PROMPT_VERSION,
        )
        val now = System.currentTimeMillis()
        val artifact = AiArtifact(
            id = "${book.bookUrl}_${bookChapter.index}_${AiTaskType.SUMMARIZE_CHAPTER}_${contentHash}_${preset.model.id}",
            taskType = AiTaskType.SUMMARIZE_CHAPTER,
            bookUrl = book.bookUrl,
            chapterIndex = bookChapter.index,
            contentHash = contentHash,
            promptHash = promptHash,
            modelProfileId = preset.model.id,
            createdAt = now,
            updatedAt = now,
        )
        return aiTaskManager.submit(artifact) {
            var summary = ""
            executeStream(book, bookChapter, content, maxCharsPerChunk).collect { event ->
                when (event) {
                    is StreamEvent.Content -> appendContent(event.text)
                    is StreamEvent.Reasoning -> appendReasoning(event.text)
                    is StreamEvent.Done -> summary = event.text
                }
            }
            summary.ifBlank { error("AI returned an empty chapter summary") }
        }
    }

    suspend fun execute(
        book: Book,
        bookChapter: BookChapter,
        contentOverride: String? = null,
        maxCharsPerChunk: Int = DEFAULT_MAX_CHARS_PER_CHUNK
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val content = contentOverride ?: BookHelp.getContent(book, bookChapter)
                ?: error("Failed to read chapter content")
            val preset = resolvePreset() ?: error("No AI model configured for chapter summary")
            val contentHash = MD5Utils.md5Encode(content)
            val promptHash = MD5Utils.md5Encode(
                preset.promptTemplate + AiToolAwareGenerationUseCase.CACHE_PROMPT_VERSION
            )
            aiArtifactGateway.getCachedArtifact(
                bookUrl = book.bookUrl,
                chapterIndex = bookChapter.index,
                taskType = AiTaskType.SUMMARIZE_CHAPTER,
                contentHash = contentHash,
                promptHash = promptHash,
                modelProfileId = preset.model.id
            )?.output?.let { return@runCatching it }

            val chunks = ContentChunker.chunk(content, maxCharsPerChunk)
            if (chunks.isEmpty()) error("Failed to chunk chapter content")

            val toolContext = book.toToolContext(bookChapter)
            val partialSummaries = chunks.map { chunk ->
                generate(
                    preset = preset,
                    userContent = "Chapter title: ${bookChapter.title}\n\nText:\n${chunk.content}",
                    toolContext = toolContext,
                )
            }
            val summary = if (partialSummaries.size == 1) {
                partialSummaries.first()
            } else {
                generate(
                    preset = preset,
                    userContent = "Merge these partial summaries into one chapter summary:\n\n${partialSummaries.joinToString("\n\n")}",
                    toolContext = toolContext,
                )
            }
            val now = System.currentTimeMillis()
            aiArtifactGateway.upsertArtifact(
                AiArtifact(
                    id = "${book.bookUrl}_${bookChapter.index}_${AiTaskType.SUMMARIZE_CHAPTER}_${contentHash}_${preset.model.id}",
                    taskType = AiTaskType.SUMMARIZE_CHAPTER,
                    bookUrl = book.bookUrl,
                    chapterIndex = bookChapter.index,
                    contentHash = contentHash,
                    promptHash = promptHash,
                    modelProfileId = preset.model.id,
                    status = AiArtifact.STATUS_SUCCESS,
                    output = summary,
                    createdAt = now,
                    updatedAt = now
                )
            )
            summary
        }.onFailure { error ->
            if (error is CancellationException) throw error
        }
    }

    fun executeStream(
        book: Book,
        bookChapter: BookChapter,
        contentOverride: String? = null,
        maxCharsPerChunk: Int = DEFAULT_MAX_CHARS_PER_CHUNK
    ): Flow<StreamEvent> = flow {
        val content = contentOverride ?: BookHelp.getContent(book, bookChapter)
            ?: error("Failed to read chapter content")
        val preset = resolvePreset() ?: error("No AI model configured for chapter summary")
        val contentHash = MD5Utils.md5Encode(content)
        val promptHash = MD5Utils.md5Encode(
            preset.promptTemplate + AiToolAwareGenerationUseCase.CACHE_PROMPT_VERSION
        )
        aiArtifactGateway.getCachedArtifact(
            bookUrl = book.bookUrl,
            chapterIndex = bookChapter.index,
            taskType = AiTaskType.SUMMARIZE_CHAPTER,
            contentHash = contentHash,
            promptHash = promptHash,
            modelProfileId = preset.model.id
        )?.output?.let { cached ->
            emit(StreamEvent.Content(cached))
            emit(StreamEvent.Done(cached, ""))
            return@flow
        }

        val chunks = ContentChunker.chunk(content, maxCharsPerChunk)
        if (chunks.isEmpty()) error("Failed to chunk chapter content")

        val summaryBuilder = StringBuilder()
        val reasoningBuilder = StringBuilder()
        val toolContext = book.toToolContext(bookChapter)
        if (chunks.size == 1) {
            collectGenerateStream(
                preset = preset,
                userContent = "Chapter title: ${bookChapter.title}\n\nText:\n${content}",
                toolContext = toolContext,
                outputBuilder = summaryBuilder,
                reasoningBuilder = reasoningBuilder,
                emitEvent = { emit(it) },
            )
        } else {
            val partialSummaries = chunks.map { chunk ->
                generate(
                    preset = preset,
                    userContent = "Chapter title: ${bookChapter.title}\n\nText:\n${chunk.content}",
                    toolContext = toolContext,
                )
            }
            collectGenerateStream(
                preset = preset,
                userContent = "Merge these partial summaries into one chapter summary:\n\n${partialSummaries.joinToString("\n\n")}",
                toolContext = toolContext,
                outputBuilder = summaryBuilder,
                reasoningBuilder = reasoningBuilder,
                emitEvent = { emit(it) },
            )
        }

        val summary = summaryBuilder.toString().trim()
            .ifEmpty { error("AI returned an empty chapter summary") }
        val reasoning = reasoningBuilder.toString()
        val now = System.currentTimeMillis()
        aiArtifactGateway.upsertArtifact(
            AiArtifact(
                id = "${book.bookUrl}_${bookChapter.index}_${AiTaskType.SUMMARIZE_CHAPTER}_${contentHash}_${preset.model.id}",
                taskType = AiTaskType.SUMMARIZE_CHAPTER,
                bookUrl = book.bookUrl,
                chapterIndex = bookChapter.index,
                contentHash = contentHash,
                promptHash = promptHash,
                modelProfileId = preset.model.id,
                status = AiArtifact.STATUS_SUCCESS,
                output = summary,
                createdAt = now,
                updatedAt = now
            )
        )
        emit(StreamEvent.Done(summary, reasoning))
    }.flowOn(Dispatchers.IO)

    private suspend fun resolvePreset(): AiTaskPresetConfig? {
        return aiProfileGateway.getTaskPreset(AiTaskType.SUMMARIZE_CHAPTER)
    }

    private suspend fun generate(
        preset: AiTaskPresetConfig,
        userContent: String,
        toolContext: AiToolContext,
    ): String {
        return aiToolAwareGenerationUseCase.generate(
            AiGenerateRequest(
                model = preset.model,
                messages = listOf(
                    AiMessage(AiMessageRole.SYSTEM, preset.promptTemplate),
                    AiMessage(AiMessageRole.USER, userContent)
                ),
                params = preset.params,
                toolContext = toolContext,
            )
        )
    }

    private suspend fun collectGenerateStream(
        preset: AiTaskPresetConfig,
        userContent: String,
        toolContext: AiToolContext,
        outputBuilder: StringBuilder,
        reasoningBuilder: StringBuilder,
        emitEvent: suspend (StreamEvent) -> Unit,
    ) {
        aiToolAwareGenerationUseCase.generateStream(
            AiGenerateRequest(
                model = preset.model,
                messages = listOf(
                    AiMessage(AiMessageRole.SYSTEM, preset.promptTemplate),
                    AiMessage(AiMessageRole.USER, userContent)
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

    private fun Book.toToolContext(bookChapter: BookChapter): AiToolContext {
        return AiToolContext(
            bookUrl = bookUrl,
            bookName = name,
            chapterIndex = bookChapter.index,
            chapterTitle = bookChapter.title,
        )
    }

    private companion object {
        const val DEFAULT_MAX_CHARS_PER_CHUNK = 8000
    }
}
