package io.legado.app.domain.gateway

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.TranslationCache

interface TranslationCacheGateway {
    /**
     * 译文缓存文件的**路径**（非 `java.io.File`）。
     *
     * 领域网关属共享契约，按 AGENTS.md「新的共享领域契约不得暴露 Context/File/Uri/Room 类型」
     * 不得把 `java.io.File` 透出；调用方需要判断存在性或读内容时，走 `:core:platform` 的
     * [io.legado.app.core.platform.FileSystem] 契约。`File` 只出现在平台实现
     * （`TranslationCacheRepositoryImpl`）内部。
     */
    fun getCachePath(book: Book, bookChapter: BookChapter, targetLanguage: String): String
    suspend fun readTranslation(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String
    ): String?

    suspend fun writeTranslation(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        content: String
    )

    suspend fun deleteTranslation(book: Book, bookChapter: BookChapter, targetLanguage: String)
    suspend fun deleteTranslationForBook(book: Book, targetLanguage: String)
    suspend fun deleteAllTranslation()
    fun getTranslationCacheSize(): Long
    fun computeContentHash(content: String): String
    fun computeCacheKey(
        bookUrl: String,
        chapterIndex: Int,
        chunkIndex: Int,
        targetLanguage: String
    ): String

    suspend fun getCachedChunks(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        contentHash: String
    ): List<TranslationCache>

    suspend fun getCachedChunk(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        chunkIndex: Int
    ): TranslationCache?

    suspend fun saveChunk(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        chunkIndex: Int,
        originalChunkContent: String,
        originalContentHash: String,
        provider: String,
        status: Int,
        translatedContent: String?,
        errorMessage: String?
    )

    suspend fun clearChunkCacheForChapter(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String
    )

    suspend fun clearChunkCacheForBook(book: Book, targetLanguage: String)
    suspend fun clearAllChunkCache()
}
