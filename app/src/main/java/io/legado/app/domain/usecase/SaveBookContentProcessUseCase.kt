package io.legado.app.domain.usecase

import io.legado.app.data.entities.BookContentProcess
import io.legado.app.domain.gateway.BookContentProcessGateway
import io.legado.app.domain.model.BookContentProcessEngine
import io.legado.app.domain.model.TextProcessAction
import io.legado.app.domain.model.TextProcessAnchor
import io.legado.app.utils.GSON
import io.legado.app.utils.MD5Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class SaveBookContentProcessUseCase(
    private val bookContentProcessGateway: BookContentProcessGateway,
) {

    suspend fun saveReplacement(
        bookUrl: String,
        chapterIndex: Int,
        chapterPosition: Int,
        selectedText: String,
        contextBefore: String,
        contextAfter: String,
        replacementText: String,
        kind: String = BookContentProcess.KIND_AI_CLEAN,
        source: String = BookContentProcess.SOURCE_AI,
        aiArtifactId: String? = null,
        sourceContentHash: String? = null,
    ): Result<BookContentProcess> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedSelectedText = BookContentProcessEngine.normalizeProcessText(selectedText)
            require(normalizedSelectedText.isNotBlank()) { "Selected text is empty" }
            val normalizedReplacement = BookContentProcessEngine.normalizeProcessText(replacementText)
            require(normalizedSelectedText != normalizedReplacement) { "Replacement did not change text" }

            val anchor = TextProcessAnchor(
                chapterIndex = chapterIndex,
                chapterPosition = chapterPosition,
                selectedText = normalizedSelectedText,
                contextBefore = contextBefore,
                contextAfter = contextAfter,
                normalizedTextHash = MD5Utils.md5Encode(normalizedSelectedText),
            )
            val action = if (normalizedReplacement.isEmpty()) {
                TextProcessAction.delete()
            } else {
                TextProcessAction.replace(normalizedReplacement)
            }
            val now = System.currentTimeMillis()
            val process = BookContentProcess(
                id = UUID.randomUUID().toString(),
                bookUrl = bookUrl,
                chapterIndex = chapterIndex,
                kind = kind,
                stage = BookContentProcess.STAGE_CONTENT,
                target = BookContentProcess.TARGET_SELECTION,
                anchorJson = GSON.toJson(anchor),
                actionJson = GSON.toJson(action),
                source = source,
                aiArtifactId = aiArtifactId,
                sourceContentHash = sourceContentHash,
                sortOrder = bookContentProcessGateway.nextOrder(bookUrl),
                createdAt = now,
                updatedAt = now,
            )
            bookContentProcessGateway.upsert(process)
            process
        }
    }
}
