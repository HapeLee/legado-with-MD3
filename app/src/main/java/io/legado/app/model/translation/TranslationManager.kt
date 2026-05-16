package io.legado.app.model.translation

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.domain.usecase.TranslateChapterUseCase
import io.legado.app.help.book.BookHelp
import io.legado.app.ui.config.translation.TranslationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

object TranslationManager {

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
     * @param book The book
     * @param chapter The chapter
     * @param targetLanguage The target language code
     * @return The translated content if cached, null otherwise
     */
    fun getChapterTranslatedContent(book: Book, chapter: BookChapter, targetLanguage: String): String? {
        val cacheFile = getTranslationCacheFile(book, chapter, targetLanguage)
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

    private fun getTranslationCacheFile(book: Book, bookChapter: BookChapter, targetLanguage: String): java.io.File {
        val cacheDir = BookHelp.cachePath
        val bookFolder = java.io.File(cacheDir, book.getFolderName())
        val chapterFileName = bookChapter.getFileName()
        val translationFileName = "$chapterFileName.$targetLanguage.nb"
        return java.io.File(bookFolder, translationFileName)
    }

    suspend fun deleteTranslationCache(book: Book, bookChapter: BookChapter) {
        val cacheFile = getTranslationCacheFile(book, bookChapter, TranslationConfig.llmTargetLanguage)
        cacheFile.delete()
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