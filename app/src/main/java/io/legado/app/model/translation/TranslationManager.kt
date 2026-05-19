package io.legado.app.model.translation

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.repository.TranslationCacheRepository
import io.legado.app.domain.usecase.TranslateChapterUseCase
import io.legado.app.ui.config.translation.TranslationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object TranslationManager : KoinComponent {

    private val translationCacheRepository: TranslationCacheRepository by inject()
    private val translateChapterUseCase: TranslateChapterUseCase by inject()

    /** All per-chapter states, keyed by TranslationChapterKey */
    private val _chapterStates = MutableStateFlow<Map<TranslationChapterKey, TranslationChapterState>>(emptyMap())
    val chapterStates: StateFlow<Map<TranslationChapterKey, TranslationChapterState>> = _chapterStates.asStateFlow()

    /** Per-chapter task state flows: bookUrl+chapterIndex -> StateFlow */
    private val _taskStateFlows = mutableMapOf<TranslationChapterKey, MutableStateFlow<TranslationChapterState>>()

    private fun getOrCreateTaskStateFlow(key: TranslationChapterKey): MutableStateFlow<TranslationChapterState> {
        return _taskStateFlows.getOrPut(key) {
            MutableStateFlow(TranslationChapterState(key))
        }
    }

    fun getChapterTaskStateFlow(bookUrl: String, chapterIndex: Int): StateFlow<TranslationChapterState>? {
        val key = TranslationChapterKey(bookUrl, chapterIndex)
        return _taskStateFlows[key]
    }

    private fun getChapterKey(book: Book, chapter: BookChapter): TranslationChapterKey {
        return TranslationChapterKey(book.bookUrl, chapter.index)
    }

    fun getChapterState(bookUrl: String, chapterIndex: Int): TranslationChapterState {
        val key = TranslationChapterKey(bookUrl, chapterIndex)
        return _chapterStates.value[key] ?: TranslationChapterState(key)
    }

    /**
     * Check if translated cache file exists for a chapter.
     */
    fun hasTranslatedCache(book: Book, chapter: BookChapter): Boolean {
        val cacheFile = translationCacheRepository.getCacheFile(book, chapter, TranslationConfig.llmTargetLanguage)
        return cacheFile.exists()
    }

    /**
     * Get translated content from cache for reading.
     * Returns mixed content if translation is in progress, else cached translation, else null.
     */
    fun getChapterContentForReading(book: Book, chapter: BookChapter): String? {
        val key = getChapterKey(book, chapter)
        // First check if there's an ongoing translation with mixed content
        val taskFlow = _taskStateFlows[key]
        if (taskFlow != null) {
            val state = taskFlow.value
            if (state.status == TranslationChapterStatus.Translating && !state.mixedContent.isNullOrEmpty()) {
                return state.mixedContent
            }
        }
        // Then check cached translation
        val cacheFile = translationCacheRepository.getCacheFile(book, chapter, TranslationConfig.llmTargetLanguage)
        return if (cacheFile.exists()) cacheFile.readText() else null
    }

    fun getChapterDisplayState(book: Book, chapter: BookChapter): TranslationChapterStatus {
        val key = getChapterKey(book, chapter)
        // Check if there's an ongoing task
        val taskFlow = _taskStateFlows[key]
        if (taskFlow != null) {
            val status = taskFlow.value.status
            if (status != TranslationChapterStatus.Idle) return status
        }
        // Check cached translation
        if (hasTranslatedCache(book, chapter)) {
            return TranslationChapterStatus.Translated
        }
        return TranslationChapterStatus.Idle
    }

    fun updateChapterProgress(book: Book, chapter: BookChapter, current: Int, total: Int, mixedContent: String? = null) {
        val key = getChapterKey(book, chapter)
        val taskFlow = getOrCreateTaskStateFlow(key)
        taskFlow.update { it.copy(currentChunk = current, totalChunks = total, mixedContent = mixedContent) }
        _chapterStates.update { map ->
            map.toMutableMap().apply {
                val existing = get(key)
                if (existing != null) {
                    put(key, existing.copy(currentChunk = current, totalChunks = total, mixedContent = mixedContent))
                } else {
                    put(key, TranslationChapterState(key, currentChunk = current, totalChunks = total, mixedContent = mixedContent))
                }
            }
        }
    }

    fun updateChapterResult(book: Book, chapter: BookChapter, content: String?, error: String?) {
        val key = getChapterKey(book, chapter)
        val taskFlow = _taskStateFlows[key]
        taskFlow?.update {
            it.copy(
                translatedContent = content,
                errorMessage = error,
                status = if (content != null) TranslationChapterStatus.Translated else TranslationChapterStatus.Failed
            )
        }
        _chapterStates.update { map ->
            map.toMutableMap().apply {
                val existing = get(key)
                if (existing != null) {
                    put(key, existing.copy(translatedContent = content, errorMessage = error))
                } else {
                    put(key, TranslationChapterState(key, translatedContent = content, errorMessage = error))
                }
            }
        }
    }

    fun clearChapterState(bookUrl: String, chapterIndex: Int) {
        val key = TranslationChapterKey(bookUrl, chapterIndex)
        _taskStateFlows.remove(key)
        _chapterStates.update { map ->
            map.toMutableMap().apply { remove(key) }
        }
    }

    fun clearAllChapterStates() {
        _taskStateFlows.values.forEach { it.value = TranslationChapterState(it.value.key) }
        _chapterStates.value = emptyMap()
    }

    suspend fun translateChapter(
        book: Book,
        bookChapter: BookChapter,
        translateChapterUseCase: TranslateChapterUseCase,
        onTranslateStarted: () -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val key = getChapterKey(book, bookChapter)
        val taskFlow = getOrCreateTaskStateFlow(key)
        taskFlow.update { it.copy(status = TranslationChapterStatus.Translating) }

        val result = translateChapterUseCase.execute(
            book = book,
            bookChapter = bookChapter,
            targetLanguage = TranslationConfig.llmTargetLanguage,
            onProgress = { progress ->
                updateChapterProgress(book, bookChapter, progress.currentChunk, progress.totalChunks, progress.mixedContent)
            },
            onTranslateStarted = onTranslateStarted
        )

        result.onSuccess { content ->
            taskFlow.update { it.copy(status = TranslationChapterStatus.Translated, translatedContent = content, mixedContent = null) }
            updateChapterResult(book, bookChapter, content, null)
        }.onFailure { error ->
            taskFlow.update { it.copy(status = TranslationChapterStatus.Failed, errorMessage = error.message ?: "Translation failed") }
            updateChapterResult(book, bookChapter, null, error.message ?: "Translation failed")
        }

        result
    }

    suspend fun deleteTranslationCache(book: Book, bookChapter: BookChapter) {
        translationCacheRepository.deleteTranslation(book, bookChapter, TranslationConfig.llmTargetLanguage)
        translationCacheRepository.clearChunkCacheForChapter(book, bookChapter, TranslationConfig.llmTargetLanguage)
        clearChapterState(book.bookUrl, bookChapter.index)
    }

    /**
     * Pre-translate the next chapter in the background.
     * Called after current chapter translation completes OR when opening a chapter that already has translation.
     * Only runs when reading in translation mode (caller's responsibility to check).
     */
    fun preTranslateNextChapter(book: Book, currentChapterIndex: Int) {
        val nextChapterIndex = currentChapterIndex + 1
        if (nextChapterIndex >= book.totalChapterNum) return

        // Check if next chapter already has original content
        val nextChapter = io.legado.app.data.appDb.bookChapterDao.getChapter(book.bookUrl, nextChapterIndex)
            ?: return
        val nextChapterKey = TranslationChapterKey(book.bookUrl, nextChapterIndex)

        // Skip if already translating or already has cache
        if (_taskStateFlows[nextChapterKey]?.value?.status == TranslationChapterStatus.Translating) return
        if (hasTranslatedCache(book, nextChapter)) return

        // Check if original content exists (we need this to translate)
        val originalContent = io.legado.app.help.book.BookHelp.getContent(book, nextChapter)
        if (originalContent == null) return

        // Start pre-translation silently in the background
        io.legado.app.help.coroutine.Coroutine.async {
            translateChapter(book, nextChapter, translateChapterUseCase) { }
        }
    }

    fun reset() {
        clearAllChapterStates()
    }
}