package io.legado.app.model.translation

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.domain.gateway.TranslationCacheGateway
import io.legado.app.domain.gateway.TranslationSettingsGateway
import io.legado.app.domain.usecase.TranslateChapterUseCase
import io.legado.app.help.book.BookHelp
import io.legado.app.help.coroutine.Coroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.ConcurrentHashMap

object TranslationManager : KoinComponent {

    private val translationCacheGateway: TranslationCacheGateway by inject()
    private val translateChapterUseCase: TranslateChapterUseCase by inject()
    private val translationSettingsGateway: TranslationSettingsGateway by inject()

    /** Per-chapter task state flows: bookUrl+chapterIndex -> StateFlow (only for in-progress tasks) */
    private val _taskStateFlows =
        ConcurrentHashMap<TranslationChapterKey, MutableStateFlow<TranslationChapterState>>()

    private fun getChapterKey(book: Book, chapter: BookChapter): TranslationChapterKey {
        return TranslationChapterKey(book.bookUrl, chapter.index)
    }

    /**
     * Get task StateFlow for a chapter if translation is in progress.
     * Returns null if no in-progress translation exists.
     */
    fun getChapterTaskStateFlow(bookUrl: String, chapterIndex: Int): StateFlow<TranslationChapterState>? {
        val key = TranslationChapterKey(bookUrl, chapterIndex)
        return _taskStateFlows[key]?.takeIf { it.value.status == TranslationChapterStatus.Translating }
    }

    /**
     * Check if translated cache file exists for a chapter.
     */
    fun hasTranslatedCache(book: Book, chapter: BookChapter): Boolean {
        val cacheFile =
            translationCacheGateway.getCacheFile(book, chapter, currentTargetLanguage())
        return cacheFile.exists()
    }

    /**
     * Get finished cached translation for a chapter.
     */
    fun getCachedTranslation(book: Book, chapter: BookChapter): String? {
        val cacheFile =
            translationCacheGateway.getCacheFile(book, chapter, currentTargetLanguage())
        return if (cacheFile.exists()) cacheFile.readText() else null
    }

    /**
     * Start translation for a chapter.
     * - If translation is already in progress, return existing task flow.
     * - If cache already exists, do nothing and return null.
     * - If original content doesn't exist, return null.
     * The returned flow updates with mixedContent during translation.
     */
    @Synchronized
    fun startTranslation(
        book: Book,
        chapter: BookChapter,
        onTranslateStarted: () -> Unit = {}
    ): MutableStateFlow<TranslationChapterState>? {
        val key = getChapterKey(book, chapter)

        // Check if already translating
        _taskStateFlows[key]?.let { taskFlow ->
            if (taskFlow.value.status == TranslationChapterStatus.Translating) {
                return taskFlow
            }
        }

        // Skip if cache already exists
        if (hasTranslatedCache(book, chapter)) {
            return null
        }

        // Check if original content exists
        if (BookHelp.getContent(book, chapter) == null) {
            return null
        }

        // Create new task flow
        val taskFlow = MutableStateFlow(TranslationChapterState(key, status = TranslationChapterStatus.Idle))
        _taskStateFlows[key] = taskFlow

        // Start translation in background
        Coroutine.async {
            translateChapter(book, chapter, onTranslateStarted)
        }

        return taskFlow
    }

    private suspend fun translateChapter(
        book: Book,
        bookChapter: BookChapter,
        onTranslateStarted: () -> Unit
    ) = withContext(Dispatchers.IO) {
        val key = getChapterKey(book, bookChapter)
        val taskFlow = _taskStateFlows[key] ?: return@withContext

        taskFlow.update { it.copy(status = TranslationChapterStatus.Translating) }

        val result = translateChapterUseCase.execute(
            book = book,
            bookChapter = bookChapter,
            onProgress = { progress ->
                taskFlow.update {
                    it.copy(
                        currentChunk = progress.currentChunk,
                        totalChunks = progress.totalChunks,
                        mixedContent = progress.mixedContent
                    )
                }
            },
            onTranslateStarted = onTranslateStarted
        )

        result.onSuccess { content ->
            taskFlow.update {
                it.copy(
                    status = TranslationChapterStatus.Translated,
                    translatedContent = content,
                    mixedContent = null
                )
            }
        }.onFailure { error ->
            taskFlow.update {
                it.copy(
                    status = TranslationChapterStatus.Failed,
                    errorMessage = error.message ?: "Translation failed"
                )
            }
        }
    }

    /**
     * Clear state for a single chapter.
     */
    fun clearChapterState(bookUrl: String, chapterIndex: Int) {
        val key = TranslationChapterKey(bookUrl, chapterIndex)
        _taskStateFlows.remove(key)
    }

    /**
     * Clear all chapter states.
     */
    fun clearAllChapterStates() {
        _taskStateFlows.clear()
    }

    /**
     * Delete translation cache and state for a chapter.
     */
    suspend fun deleteTranslationCache(book: Book, bookChapter: BookChapter) {
        val targetLanguage = currentTargetLanguage()
        translationCacheGateway.deleteTranslation(
            book,
            bookChapter,
            targetLanguage
        )
        translationCacheGateway.clearChunkCacheForChapter(
            book,
            bookChapter,
            targetLanguage
        )
        clearChapterState(book.bookUrl, bookChapter.index)
    }

    private fun currentTargetLanguage(): String {
        return translationSettingsGateway.currentSettings.targetLanguage
    }

}
