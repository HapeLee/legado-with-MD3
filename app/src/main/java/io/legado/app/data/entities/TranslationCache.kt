package io.legado.app.data.entities

import androidx.annotation.Keep

/**
 * Chunk-level translation cache record stored in .chunks.jsonl files.
 * File path already contains bookUrl/chapterIndex/targetLanguage — chunkIndex is the key.
 */
@Keep
data class TranslationCache(
    val chunkIndex: Int,
    val originalChunkContent: String,
    val translatedChunkContent: String?,
    val status: Int = STATUS_PENDING,
    val errorMessage: String? = null,
    val originalContentHash: String,
    val provider: String = ""
) {
    companion object {
        const val STATUS_PENDING = 0
        const val STATUS_TRANSLATING = 1
        const val STATUS_SUCCESS = 2
        const val STATUS_FAILED = 3
    }

    val isSuccess: Boolean get() = status == STATUS_SUCCESS
    val isFailed: Boolean get() = status == STATUS_FAILED
    val isPending: Boolean get() = status == STATUS_PENDING
}