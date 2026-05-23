package io.legado.app.data.repository

import com.google.gson.Gson
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.TranslationCache
import io.legado.app.domain.gateway.TranslationCacheGateway
import io.legado.app.help.book.BookHelp
import io.legado.app.utils.MD5Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class TranslationCacheRepositoryImpl : TranslationCacheGateway {

    private val cacheDir: File = File(BookHelp.cachePath)
    private val gson = Gson()

    override fun getCacheFile(book: Book, bookChapter: BookChapter, targetLanguage: String): File {
        val bookFolder = File(cacheDir, book.getFolderName())
        // getFileName() returns "{index}-{titleMD5}.nb", remove .nb to avoid double extension
        val chapterFileName = bookChapter.getFileName().removeSuffix(".nb")
        val translationFileName = "$chapterFileName.$targetLanguage.nb"
        return File(bookFolder, translationFileName)
    }

    private fun getChunkFile(book: Book, bookChapter: BookChapter, targetLanguage: String): File {
        val bookFolder = File(cacheDir, book.getFolderName())
        val chapterFileName = bookChapter.getFileName().removeSuffix(".nb")
        return File(bookFolder, "$chapterFileName.$targetLanguage.chunks.jsonl")
    }

    override suspend fun readTranslation(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String
    ): String? = withContext(Dispatchers.IO) {
        val cacheFile = getCacheFile(book, bookChapter, targetLanguage)
        if (cacheFile.exists()) {
            val content = cacheFile.readText()
            content.ifEmpty { null }
        } else {
            null
        }
    }

    override suspend fun writeTranslation(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        content: String
    ) = withContext(Dispatchers.IO) {
        val cacheFile = getCacheFile(book, bookChapter, targetLanguage)
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeText(content)
    }

    override suspend fun deleteTranslation(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String
    ) = withContext(Dispatchers.IO) {
        val cacheFile = getCacheFile(book, bookChapter, targetLanguage)
        cacheFile.delete()
        clearChunkCacheForChapter(book, bookChapter, targetLanguage)
        Unit
    }

    override suspend fun deleteTranslationForBook(book: Book, targetLanguage: String) = withContext(Dispatchers.IO) {
        val bookFolder = File(cacheDir, book.getFolderName())
        if (bookFolder.exists()) {
            bookFolder.listFiles()?.filter { it.name.endsWith(".$targetLanguage.nb") }?.forEach { it.delete() }
        }
        clearChunkCacheForBook(book, targetLanguage)
        Unit
    }

    override suspend fun deleteAllTranslation() = withContext(Dispatchers.IO) {
        clearAllChunkCache()
        Unit
    }

    override fun getTranslationCacheSize(): Long {
        var totalSize = 0L
        cacheDir.listFiles()?.forEach { bookFolder ->
            bookFolder.listFiles()?.filter { it.name.endsWith(".nb") && it.name.contains(".") }?.forEach { file ->
                totalSize += file.length()
            }
        }
        return totalSize
    }

    override fun computeContentHash(content: String): String {
        return MD5Utils.md5Encode(content)
    }

    override fun computeCacheKey(
        bookUrl: String,
        chapterIndex: Int,
        chunkIndex: Int,
        targetLanguage: String
    ): String {
        return "${bookUrl}_${chapterIndex}_${chunkIndex}_$targetLanguage"
    }

    private suspend fun readAllChunks(book: Book, bookChapter: BookChapter, targetLanguage: String): Map<Int, TranslationCache> = withContext(Dispatchers.IO) {
        val chunkFile = getChunkFile(book, bookChapter, targetLanguage)
        if (!chunkFile.exists()) return@withContext emptyMap()
        val result = mutableMapOf<Int, TranslationCache>()
        chunkFile.forEachLine { line ->
            try {
                val chunk = gson.fromJson(line, TranslationCache::class.java)
                result[chunk.chunkIndex] = chunk
            } catch (_: Exception) {
                // skip malformed line
            }
        }
        result
    }

    override suspend fun getCachedChunks(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        contentHash: String
    ): List<TranslationCache> = withContext(Dispatchers.IO) {
        val allChunks = readAllChunks(book, bookChapter, targetLanguage)
        allChunks.values
            .filter { it.originalContentHash == contentHash && it.isSuccess }
            .sortedBy { it.chunkIndex }
    }

    override suspend fun getCachedChunk(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        chunkIndex: Int
    ): TranslationCache? = withContext(Dispatchers.IO) {
        val allChunks = readAllChunks(book, bookChapter, targetLanguage)
        allChunks[chunkIndex]
    }

    override suspend fun saveChunk(
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
    ) = withContext(Dispatchers.IO) {
        val chunk = TranslationCache(
            chunkIndex = chunkIndex,
            originalChunkContent = originalChunkContent,
            translatedChunkContent = translatedContent,
            status = status,
            errorMessage = errorMessage,
            originalContentHash = originalContentHash,
            provider = provider
        )
        val chunkFile = getChunkFile(book, bookChapter, targetLanguage)
        chunkFile.parentFile?.mkdirs()
        chunkFile.appendText(gson.toJson(chunk) + "\n")
    }

    override suspend fun clearChunkCacheForChapter(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String
    ) = withContext(Dispatchers.IO) {
        val chunkFile = getChunkFile(book, bookChapter, targetLanguage)
        chunkFile.delete()
        Unit
    }

    override suspend fun clearChunkCacheForBook(book: Book, targetLanguage: String) = withContext(Dispatchers.IO) {
        val bookFolder = File(cacheDir, book.getFolderName())
        if (bookFolder.exists()) {
            bookFolder.listFiles()?.filter { it.name.endsWith(".$targetLanguage.chunks.jsonl") }?.forEach { it.delete() }
        }
        Unit
    }

    override suspend fun clearAllChunkCache() = withContext(Dispatchers.IO) {
        cacheDir.listFiles()?.forEach { bookFolder ->
            bookFolder.listFiles()?.filter { it.name.endsWith(".chunks.jsonl") }?.forEach { it.delete() }
        }
        Unit
    }
}