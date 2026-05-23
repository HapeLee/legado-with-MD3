package io.legado.app.model.translation

/**
 * Key for per-chapter translation display state.
 * Used as task key for looking up ongoing translation tasks in TranslationManager.
 */
data class TranslationChapterKey(
    val bookUrl: String,
    val chapterIndex: Int
)

/**
 * Per-chapter translation status.
 */
enum class TranslationChapterStatus {
    Idle,
    Translating,
    Translated,
    Failed
}

/**
 * Per-chapter translation state stored in TranslationManager.
 * Runtime-only, derived from translation cache on app restart.
 */
data class TranslationChapterState(
    val key: TranslationChapterKey,
    val status: TranslationChapterStatus = TranslationChapterStatus.Idle,
    val currentChunk: Int = 0,
    val totalChunks: Int = 0,
    val mixedContent: String? = null,
    val translatedContent: String? = null,
    val errorMessage: String? = null
)