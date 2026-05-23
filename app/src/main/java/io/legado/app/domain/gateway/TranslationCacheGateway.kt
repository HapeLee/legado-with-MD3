package io.legado.app.domain.gateway

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.TranslationCache
import java.io.File

interface TranslationCacheGateway {
    fun getCacheFile(book: Book, bookChapter: BookChapter, targetLanguage: String): File
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
