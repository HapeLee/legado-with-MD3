package io.legado.app.model.translation

/**
 * Key for per-chapter translation display state.
 */
data class TranslationChapterKey(
    val bookUrl: String,
    val chapterIndex: Int
)

/**
 * Display state for a chapter's translation mode.
 */
enum class TranslationDisplayState {
    Original,
    Translating,
    Translated
}

/**
 * Per-chapter translation state stored in TranslationManager.
 */
data class TranslationChapterState(
    val key: TranslationChapterKey,
    var displayState: TranslationDisplayState = TranslationDisplayState.Original,
    var currentChunk: Int = 0,
    var totalChunks: Int = 0,
    var mixedContent: String? = null
)