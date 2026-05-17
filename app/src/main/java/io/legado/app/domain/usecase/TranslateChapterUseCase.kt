package io.legado.app.domain.usecase

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.TranslationCache
import io.legado.app.data.repository.TranslationCacheRepository
import io.legado.app.domain.gateway.LlmGateway
import io.legado.app.help.book.BookHelp
import io.legado.app.model.translation.ContentChunker
import io.legado.app.model.translation.DictPair
import io.legado.app.model.translation.PartialTranslationAssembler
import io.legado.app.model.translation.RetryReason
import io.legado.app.model.translation.TextChunk
import io.legado.app.model.translation.Translation
import io.legado.app.ui.config.translation.TranslationConfig
import io.legado.app.utils.MD5Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class TranslateChapterUseCase(
    private val llmGateway: LlmGateway,
    private val translationCacheRepository: TranslationCacheRepository
) {

    data class TranslationProgress(
        val currentChunk: Int,
        val totalChunks: Int,
        val mixedContent: String? = null,
        val translatedChunkIndices: Set<Int> = emptySet()
    )

    companion object {
        private const val MAX_DICTIONARY_PAIRS = 50
    }

    suspend fun execute(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        onProgress: (TranslationProgress) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val originalContent = BookHelp.getContent(book, bookChapter)
            ?: return@withContext Result.failure(Exception("Failed to read original content"))

        val cachedTranslation = translationCacheRepository.readTranslation(book, bookChapter, targetLanguage)
        if (cachedTranslation != null) {
            onProgress(TranslationProgress(1, 1, cachedTranslation, emptySet()))
            return@withContext Result.success(cachedTranslation)
        }

        val contentHash = translationCacheRepository.computeContentHash(originalContent)
        val chapterTitleMD5 = MD5Utils.md5Encode(bookChapter.title)

        // Load book dictionary for consistent terminology
        val bookDictionary = Translation.getBookDictionaries(book)
        val dictionaries = bookDictionary.pairs.toMutableList()
        var dictionaryChanged = false

        // Callback to update dictionary pairs
        val onDictionaryUpdate: (List<DictPair>) -> Unit = { newPairs ->
            if (mergeDictionaryPairs(dictionaries, newPairs)) {
                dictionaryChanged = true
            }
        }

        val chunks = ContentChunker.chunk(originalContent, TranslationConfig.llmMaxCharsPerChunk)
        if (chunks.isEmpty()) {
            return@withContext Result.failure(Exception("Failed to chunk content"))
        }

        val cachedChunks = translationCacheRepository.getCachedChunks(book, bookChapter, targetLanguage, contentHash)
        val cachedChunkMap = cachedChunks.filter { it.isSuccess }.associateBy { it.chunkIndex }

        val translatedChunks = mutableMapOf<Int, String>()
        val pendingChunks = mutableListOf<TextChunk>()

        // Load already cached chunks
        for (chunk in chunks) {
            val cached = cachedChunkMap[chunk.index]
            if (cached != null && cached.translatedChunkContent != null) {
                translatedChunks[chunk.index] = cached.translatedChunkContent
            } else {
                pendingChunks.add(chunk)
            }
        }

        // If we have partial cached chunks, report initial mixed content
        if (translatedChunks.isNotEmpty()) {
            val mixedContent = PartialTranslationAssembler.assemble(chunks, translatedChunks)
            onProgress(TranslationProgress(
                translatedChunks.size,
                chunks.size,
                mixedContent,
                translatedChunks.keys
            ))
        }

        if (pendingChunks.isEmpty()) {
            val sortedChunks = chunks.sortedBy { it.index }.mapNotNull { translatedChunks[it.index]?.let { content -> TextChunk(it.index, content, it.paragraphIndices) } }
            val mergedContent = ContentChunker.merge(sortedChunks)
            translationCacheRepository.writeTranslation(book, bookChapter, targetLanguage, mergedContent)
            translationCacheRepository.clearChunkCacheForChapter(book, bookChapter, targetLanguage)
            onProgress(TranslationProgress(chunks.size, chunks.size, mergedContent, chunks.map { it.index }.toSet()))
            return@withContext Result.success(mergedContent)
        }

        coroutineScope {
            val concurrentChunks = TranslationConfig.llmConcurrentChunks.coerceIn(1, 4)
            val chunkGroups = pendingChunks.chunked(concurrentChunks)

            for ((groupIndex, group) in chunkGroups.withIndex()) {
                val results = group.map { chunk ->
                    async {
                        translateAndCacheChunk(chunk, book, bookChapter, targetLanguage, contentHash, chapterTitleMD5, dictionaries, onDictionaryUpdate)
                    }
                }.awaitAll()

                for ((chunk, result) in group.zip(results)) {
                    if (result.isSuccess) {
                        translatedChunks[chunk.index] = result.getOrThrow()
                        val mixedContent = PartialTranslationAssembler.assemble(chunks, translatedChunks)
                        onProgress(TranslationProgress(
                            translatedChunks.size + cachedChunkMap.size,
                            chunks.size,
                            mixedContent,
                            translatedChunks.keys
                        ))
                    } else {
                        val error = result.exceptionOrNull() ?: Exception("Translation failed for chunk ${chunk.index}")
                        return@coroutineScope
                    }
                }
            }
        }

        if (translatedChunks.size != chunks.size) {
            return@withContext Result.failure(Exception("Translation incomplete: ${translatedChunks.size}/${chunks.size} chunks translated"))
        }

        val allTranslatedChunks = chunks.sortedBy { it.index }.mapNotNull { chunk ->
            translatedChunks[chunk.index]?.let { content -> TextChunk(chunk.index, content, chunk.paragraphIndices) }
        }
        val mergedContent = ContentChunker.merge(allTranslatedChunks)
        translationCacheRepository.writeTranslation(book, bookChapter, targetLanguage, mergedContent)
        translationCacheRepository.clearChunkCacheForChapter(book, bookChapter, targetLanguage)

        // Save updated dictionary if any changes were made
        if (dictionaryChanged) {
            Translation.updateBookDic(book, dictionaries)
        }

        onProgress(TranslationProgress(chunks.size, chunks.size, mergedContent, chunks.map { it.index }.toSet()))
        Result.success(mergedContent)
    }

    /**
     * Merge new pairs into existing list:
     * - If original exists, replace the translation
     * - If new, add to list
     * - Keep at most MAX_DICTIONARY_PAIRS (20)
     * @return true if any changes were made
     */
    private fun mergeDictionaryPairs(existing: MutableList<DictPair>, newPairs: List<DictPair>): Boolean {
        var changed = false
        for (newPair in newPairs) {
            val existingIndex = existing.indexOfFirst { it.original == newPair.original }
            if (existingIndex >= 0) {
                if (existing[existingIndex].translation != newPair.translation) {
                    existing[existingIndex] = newPair
                    changed = true
                }
            } else {
                existing.add(newPair)
                changed = true
            }
        }

        // Keep only the most recent MAX_DICTIONARY_PAIRS
        if (existing.size > MAX_DICTIONARY_PAIRS) {
            val sortedByRecency = existing.takeLast(MAX_DICTIONARY_PAIRS)
            existing.clear()
            existing.addAll(sortedByRecency)
            changed = true
        }
        return changed
    }

    private suspend fun translateAndCacheChunk(
        chunk: TextChunk,
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        contentHash: String,
        chapterTitleMD5: String,
        dictionaries: MutableList<DictPair>,
        onDictionaryUpdate: (List<DictPair>) -> Unit
    ): Result<String> {
        val cacheKey = translationCacheRepository.computeCacheKey(book.bookUrl, bookChapter.index, chunk.index, targetLanguage)
        val existingCache = translationCacheRepository.getCachedChunk(cacheKey)
        if (existingCache?.isSuccess == true && existingCache.translatedChunkContent != null) {
            return Result.success(existingCache.translatedChunkContent)
        }

        val translationCache = TranslationCache(
            cacheKey = cacheKey,
            bookUrl = book.bookUrl,
            chapterIndex = bookChapter.index,
            chapterTitleMD5 = chapterTitleMD5,
            originalContentHash = contentHash,
            targetLanguage = targetLanguage,
            provider = TranslationConfig.llmProvider,
            chunkIndex = chunk.index,
            originalChunkContent = chunk.content,
            translatedChunkContent = null,
            status = TranslationCache.STATUS_PENDING
        )
        translationCacheRepository.saveChunk(translationCache)

        val result = translateChunkWithRetry(chunk, targetLanguage, dictionaries, onDictionaryUpdate)
        if (result.isSuccess) {
            translationCacheRepository.updateChunkStatus(cacheKey, TranslationCache.STATUS_SUCCESS, result.getOrThrow(), null)
        } else {
            val errorMessage = result.exceptionOrNull()?.message ?: "Translation failed"
            translationCacheRepository.updateChunkStatus(cacheKey, TranslationCache.STATUS_FAILED, null, errorMessage)
        }
        return result
    }

    private suspend fun translateChunkWithRetry(
        chunk: TextChunk,
        targetLanguage: String,
        dictionaries: MutableList<DictPair>,
        onDictionaryUpdate: (List<DictPair>) -> Unit
    ): Result<String> {
        var lastError: Exception? = null
        var lastRetryReason: RetryReason? = null
        for (attempt in 0..TranslationConfig.llmRetryCount) {
            val result = llmGateway.translate(
                text = chunk.content,
                targetLanguage = targetLanguage,
                provider = TranslationConfig.llmProvider,
                baseUrl = TranslationConfig.llmBaseUrl,
                apiKey = TranslationConfig.llmApiKey,
                model = TranslationConfig.llmModel,
                prompt = TranslationConfig.llmPrompt,
                dictionaries = dictionaries.toList(),
                onUpdate = onDictionaryUpdate,
                retryReason = lastRetryReason
            )
            if (result.isSuccess) {
                return result
            }
            lastError = result.exceptionOrNull() as? Exception
            lastRetryReason = parseRetryReason(lastError)
        }
        return Result.failure(lastError ?: Exception("Translation failed after retries"))
    }

    private fun parseRetryReason(error: Exception?): RetryReason? {
        val message = error?.message ?: return null
        return when {
            message.contains("429") -> RetryReason.RATE_LIMIT
            message.contains("500") || message.contains("502") || message.contains("503") || message.contains("504") -> RetryReason.SERVER_ERROR
            message.contains("401") || message.contains("403") -> RetryReason.AUTH_ERROR
            message.contains("timeout", ignoreCase = true) -> RetryReason.TIMEOUT
            message.contains("HTTP") -> RetryReason.UNKNOWN
            else -> null
        }
    }
}