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
    var status: TranslationChapterStatus = TranslationChapterStatus.Idle,
    var currentChunk: Int = 0,
    var totalChunks: Int = 0,
    var mixedContent: String? = null,
    var translatedContent: String? = null,
    var errorMessage: String? = null
)