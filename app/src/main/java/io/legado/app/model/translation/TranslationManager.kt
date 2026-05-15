package io.legado.app.model.translation

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.repository.TranslationCacheRepository
import io.legado.app.domain.usecase.TranslateChapterUseCase
import io.legado.app.help.book.BookHelp
import io.legado.app.model.ReadBook
import io.legado.app.ui.config.translation.TranslationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

object TranslationManager {

    private val _chapterStates = mutableMapOf<TranslationChapterKey, TranslationChapterState>()

    private val _translationState = MutableStateFlow<TranslationState>(TranslationState.Idle)
    val translationState: StateFlow<TranslationState> = _translationState

    private val _isTranslationMode = MutableStateFlow(false)
    val isTranslationMode: StateFlow<Boolean> = _isTranslationMode

    private var translatedChapterIndex: Int? = null
    private var translatedContent: String? = null

    sealed class TranslationState {
        data object Idle : TranslationState()
        data class Translating(val currentChunk: Int, val totalChunks: Int, val mixedContent: String? = null) : TranslationState()
        data class Finished(val content: String) : TranslationState()
        data class Error(val message: String) : TranslationState()
    }

    private fun getChapterKey(book: Book, chapter: BookChapter): TranslationChapterKey {
        return TranslationChapterKey(book.bookUrl, chapter.index)
    }

    fun getChapterState(bookUrl: String, chapterIndex: Int): TranslationChapterState {
        val key = TranslationChapterKey(bookUrl, chapterIndex)
        return _chapterStates.getOrPut(key) { TranslationChapterState(key) }
    }

    fun getChapterDisplayState(book: Book, chapter: BookChapter): TranslationDisplayState {
        val key = getChapterKey(book, chapter)
        return _chapterStates.getOrPut(key) { TranslationChapterState(key) }.displayState
    }

    fun updateChapterDisplayState(book: Book, chapter: BookChapter, state: TranslationDisplayState) {
        val key = getChapterKey(book, chapter)
        val chapterState = _chapterStates.getOrPut(key) { TranslationChapterState(key) }
        chapterState.displayState = state
        // Sync isTranslationMode for this chapter
        _isTranslationMode.value = state == TranslationDisplayState.Translated || state == TranslationDisplayState.Translating
    }

    fun updateChapterProgress(book: Book, chapter: BookChapter, current: Int, total: Int, mixedContent: String? = null) {
        val key = getChapterKey(book, chapter)
        val chapterState = _chapterStates.getOrPut(key) { TranslationChapterState(key) }
        chapterState.currentChunk = current
        chapterState.totalChunks = total
        chapterState.mixedContent = mixedContent
        _translationState.value = TranslationState.Translating(current, total, mixedContent)
    }

    fun clearChapterState(bookUrl: String, chapterIndex: Int) {
        val key = TranslationChapterKey(bookUrl, chapterIndex)
        _chapterStates.remove(key)
    }

    fun clearAllChapterStates() {
        _chapterStates.clear()
    }

    suspend fun translateChapter(
        book: Book,
        bookChapter: BookChapter,
        translateChapterUseCase: TranslateChapterUseCase
    ): Result<String> = withContext(Dispatchers.IO) {
        updateChapterDisplayState(book, bookChapter, TranslationDisplayState.Translating)
        _translationState.value = TranslationState.Translating(0, 1)

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
            _translationState.value = TranslationState.Finished(content)
        }.onFailure { error ->
            updateChapterDisplayState(book, bookChapter, TranslationDisplayState.Original)
            _translationState.value = TranslationState.Error(error.message ?: "Translation failed")
        }

        result
    }

    fun showOriginal(book: Book, bookChapter: BookChapter, onLoadOriginal: (suspend () -> String?)? = null) {
        clearChapterState(book.bookUrl, bookChapter.index)
        translatedChapterIndex = null
        translatedContent = null
        _isTranslationMode.value = false
        _translationState.value = TranslationState.Idle
    }

    fun switchToTranslation(book: Book, bookChapter: BookChapter): Boolean {
        val cachedTranslation = BookHelp.getContent(book, bookChapter)?.let {
            val cacheFile = getTranslationCacheFile(book, bookChapter, TranslationConfig.llmTargetLanguage)
            if (cacheFile.exists()) {
                cacheFile.readText()
            } else null
        }
        if (cachedTranslation != null) {
            translatedContent = cachedTranslation
            translatedChapterIndex = bookChapter.index
            updateChapterDisplayState(book, bookChapter, TranslationDisplayState.Translated)
            _translationState.value = TranslationState.Finished(cachedTranslation)
            return true
        }
        return false
    }

    private fun getTranslationCacheFile(book: Book, bookChapter: BookChapter, targetLanguage: String): java.io.File {
        val cacheDir = BookHelp.cachePath
        val bookFolder = java.io.File(cacheDir, book.getFolderName())
        val chapterFileName = bookChapter.getFileName()
        val translationFileName = "$chapterFileName.$targetLanguage.nb"
        return java.io.File(bookFolder, translationFileName)
    }

    fun isTranslationCached(book: Book, bookChapter: BookChapter): Boolean {
        val cacheFile = getTranslationCacheFile(book, bookChapter, TranslationConfig.llmTargetLanguage)
        return cacheFile.exists()
    }

    fun getTranslatedContent(): String? = translatedContent

    fun isCurrentChapterTranslated(): Boolean {
        return translatedChapterIndex != null && translatedContent != null
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
        _translationState.value = TranslationState.Idle
        clearAllChapterStates()
    }
}