package io.legado.app.domain.usecase

import io.legado.app.data.entities.AiArtifact
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.domain.gateway.AiArtifactGateway
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiTaskPresetConfig
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.ContentChunker
import io.legado.app.help.book.BookHelp
import io.legado.app.utils.MD5Utils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GenerateChapterSummaryUseCase(
    private val aiProfileGateway: AiProfileGateway,
    private val aiTextGateway: AiTextGateway,
    private val aiArtifactGateway: AiArtifactGateway
) {

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
            val promptHash = MD5Utils.md5Encode(preset.promptTemplate)
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

            val partialSummaries = chunks.map { chunk ->
                generate(
                    preset = preset,
                    userContent = "Chapter title: ${bookChapter.title}\n\nText:\n${chunk.content}"
                )
            }
            val summary = if (partialSummaries.size == 1) {
                partialSummaries.first()
            } else {
                generate(
                    preset = preset,
                    userContent = "Merge these partial summaries into one chapter summary:\n\n${partialSummaries.joinToString("\n\n")}"
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

    private suspend fun resolvePreset(): AiTaskPresetConfig? {
        return aiProfileGateway.getTaskPreset(AiTaskType.SUMMARIZE_CHAPTER)
    }

    private suspend fun generate(
        preset: AiTaskPresetConfig,
        userContent: String
    ): String {
        val response = aiTextGateway.generate(
            AiGenerateRequest(
                model = preset.model,
                messages = listOf(
                    AiMessage(AiMessageRole.SYSTEM, preset.promptTemplate),
                    AiMessage(AiMessageRole.USER, userContent)
                ),
                params = preset.params
            )
        )
        return response.getOrThrow().text.trim()
            .ifEmpty { error("AI returned an empty chapter summary") }
    }

    private companion object {
        const val DEFAULT_MAX_CHARS_PER_CHUNK = 8000
    }
}
