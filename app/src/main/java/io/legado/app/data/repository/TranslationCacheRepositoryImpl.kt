package io.legado.app.data.repository

import io.legado.app.data.dao.TranslationCacheDao
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.TranslationCache
import io.legado.app.help.book.BookHelp
import io.legado.app.utils.MD5Utils
import java.io.File

interface TranslationCacheRepository {
    fun getCacheFile(book: Book, bookChapter: BookChapter, targetLanguage: String): File
    suspend fun readTranslation(book: Book, bookChapter: BookChapter, targetLanguage: String): String?
    suspend fun writeTranslation(book: Book, bookChapter: BookChapter, targetLanguage: String, content: String)
    suspend fun deleteTranslation(book: Book, bookChapter: BookChapter, targetLanguage: String)
    suspend fun deleteTranslationForBook(book: Book, targetLanguage: String)
    suspend fun deleteAllTranslation()
    fun getTranslationCacheSize(): Long
    fun computeContentHash(content: String): String
    fun computeCacheKey(bookUrl: String, chapterIndex: Int, chunkIndex: Int, targetLanguage: String): String
    suspend fun getCachedChunks(book: Book, bookChapter: BookChapter, targetLanguage: String, contentHash: String): List<TranslationCache>
    suspend fun getCachedChunk(cacheKey: String): TranslationCache?
    suspend fun saveChunk(translationCache: TranslationCache)
    suspend fun updateChunkStatus(cacheKey: String, status: Int, translatedContent: String?, errorMessage: String?)
    suspend fun clearChunkCacheForChapter(book: Book, bookChapter: BookChapter, targetLanguage: String)
    suspend fun clearChunkCacheForBook(book: Book, targetLanguage: String)
    suspend fun clearAllChunkCache()
}

class TranslationCacheRepositoryImpl(
    private val translationCacheDao: TranslationCacheDao
) : TranslationCacheRepository {

    private val cacheDir: File = File(BookHelp.cachePath)

    override fun getCacheFile(book: Book, bookChapter: BookChapter, targetLanguage: String): File {
        val bookFolder = File(cacheDir, book.getFolderName())
        val chapterFileName = bookChapter.getFileName()
        val translationFileName = "$chapterFileName.$targetLanguage.nb"
        return File(bookFolder, translationFileName)
    }

    override suspend fun readTranslation(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String
    ): String? {
        val cacheFile = getCacheFile(book, bookChapter, targetLanguage)
        return if (cacheFile.exists()) {
            val content = cacheFile.readText()
            if (content.isEmpty()) null else content
        } else {
            null
        }
    }

    override suspend fun writeTranslation(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        content: String
    ) {
        val cacheFile = getCacheFile(book, bookChapter, targetLanguage)
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeText(content)
    }

    override suspend fun deleteTranslation(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String
    ) {
        val cacheFile = getCacheFile(book, bookChapter, targetLanguage)
        cacheFile.delete()
        clearChunkCacheForChapter(book, bookChapter, targetLanguage)
    }

    override suspend fun deleteTranslationForBook(book: Book, targetLanguage: String) {
        val bookFolder = File(cacheDir, book.getFolderName())
        if (bookFolder.exists()) {
            bookFolder.listFiles()?.filter { it.name.endsWith(".$targetLanguage.nb") }?.forEach { it.delete() }
        }
        clearChunkCacheForBook(book, targetLanguage)
    }

    override suspend fun deleteAllTranslation() {
        clearAllChunkCache()
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

    override suspend fun getCachedChunks(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        contentHash: String
    ): List<TranslationCache> {
        return translationCacheDao.getByChapterAndHash(
            book.bookUrl,
            bookChapter.index,
            targetLanguage,
            contentHash
        )
    }

    override suspend fun getCachedChunk(cacheKey: String): TranslationCache? {
        return translationCacheDao.getByCacheKey(cacheKey)
    }

    override suspend fun saveChunk(translationCache: TranslationCache) {
        translationCacheDao.insert(translationCache)
    }

    override suspend fun updateChunkStatus(
        cacheKey: String,
        status: Int,
        translatedContent: String?,
        errorMessage: String?
    ) {
        if (translatedContent != null) {
            translationCacheDao.updateStatusAndContent(cacheKey, status, translatedContent)
        } else {
            translationCacheDao.updateStatusAndError(cacheKey, status, errorMessage)
        }
    }

    override suspend fun clearChunkCacheForChapter(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String
    ) {
        translationCacheDao.deleteByChapter(book.bookUrl, bookChapter.index, targetLanguage)
    }

    override suspend fun clearChunkCacheForBook(book: Book, targetLanguage: String) {
        translationCacheDao.deleteByBook(book.bookUrl, targetLanguage)
    }

    override suspend fun clearAllChunkCache() {
        translationCacheDao.deleteAll()
    }
}