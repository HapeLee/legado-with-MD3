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

    private val _chapterStates = MutableStateFlow<Map<TranslationChapterKey, TranslationChapterState>>(emptyMap())
    val chapterStates: StateFlow<Map<TranslationChapterKey, TranslationChapterState>> = _chapterStates.asStateFlow()

    private val _isTranslationMode = MutableStateFlow(false)

    private var translatedChapterIndex: Int? = null
    private var translatedContent: String? = null

    private fun getChapterKey(book: Book, chapter: BookChapter): TranslationChapterKey {
        return TranslationChapterKey(book.bookUrl, chapter.index)
    }

    fun getChapterState(bookUrl: String, chapterIndex: Int): TranslationChapterState {
        val key = TranslationChapterKey(bookUrl, chapterIndex)
        return _chapterStates.value[key] ?: TranslationChapterState(key)
    }

    fun getChapterDisplayState(book: Book, chapter: BookChapter): TranslationDisplayState {
        return getChapterState(book.bookUrl, chapter.index).displayState
    }

    /**
     * Get translated content from cache for a specific chapter.
     */
    fun getChapterTranslatedContent(book: Book, chapter: BookChapter, targetLanguage: String): String? {
        val cacheFile = translationCacheRepository.getCacheFile(book, chapter, targetLanguage)
        return if (cacheFile.exists()) cacheFile.readText() else null
    }

    fun updateChapterDisplayState(book: Book, chapter: BookChapter, state: TranslationDisplayState) {
        val key = getChapterKey(book, chapter)
        _chapterStates.update { map ->
            map.toMutableMap().apply {
                val existing = get(key)
                if (existing != null) {
                    put(key, existing.copy(displayState = state))
                } else {
                    put(key, TranslationChapterState(key, displayState = state))
                }
            }
        }
        _isTranslationMode.value = state == TranslationDisplayState.Translated || state == TranslationDisplayState.Translating
    }

    fun updateChapterProgress(book: Book, chapter: BookChapter, current: Int, total: Int, mixedContent: String? = null) {
        val key = getChapterKey(book, chapter)
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
        _chapterStates.update { map ->
            map.toMutableMap().apply { remove(key) }
        }
    }

    fun clearAllChapterStates() {
        _chapterStates.value = emptyMap()
    }

    suspend fun translateChapter(
        book: Book,
        bookChapter: BookChapter,
        translateChapterUseCase: TranslateChapterUseCase
    ): Result<String> = withContext(Dispatchers.IO) {
        updateChapterDisplayState(book, bookChapter, TranslationDisplayState.Translating)

        val result = translateChapterUseCase.execute(
            book = book,
            bookChapter = bookChapter,
            targetLanguage = TranslationConfig.llmTargetLanguage,
            onProgress = { progress ->
                updateChapterProgress(book, bookChapter, progress.currentChunk, progress.totalChunks, progress.mixedContent)
            }
        )

        result.onSuccess { content ->
            translatedContent = content
            translatedChapterIndex = bookChapter.index
            updateChapterDisplayState(book, bookChapter, TranslationDisplayState.Translated)
            updateChapterResult(book, bookChapter, content, null)
        }.onFailure { error ->
            updateChapterDisplayState(book, bookChapter, TranslationDisplayState.Original)
            updateChapterResult(book, bookChapter, null, error.message ?: "Translation failed")
        }

        result
    }

    fun showOriginal(book: Book, bookChapter: BookChapter, onLoadOriginal: (suspend () -> String?)? = null) {
        clearChapterState(book.bookUrl, bookChapter.index)
        translatedChapterIndex = null
        translatedContent = null
        _isTranslationMode.value = false
    }

    suspend fun deleteTranslationCache(book: Book, bookChapter: BookChapter) {
        translationCacheRepository.deleteTranslation(book, bookChapter, TranslationConfig.llmTargetLanguage)
        if (translatedChapterIndex == bookChapter.index) {
            translatedChapterIndex = null
            translatedContent = null
            _isTranslationMode.value = false
        }
        clearChapterState(book.bookUrl, bookChapter.index)
    }

    fun reset() {
        translatedChapterIndex = null
        translatedContent = null
        _isTranslationMode.value = false
        clearAllChapterStates()
    }
}