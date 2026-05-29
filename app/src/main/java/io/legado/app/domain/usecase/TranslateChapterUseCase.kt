package io.legado.app.domain.usecase

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.TranslationCache
import io.legado.app.domain.gateway.DictionaryGateway
import io.legado.app.domain.gateway.LlmGateway
import io.legado.app.domain.gateway.TranslationCacheGateway
import io.legado.app.domain.model.ContentChunker
import io.legado.app.domain.model.DictPair
import io.legado.app.domain.model.PartialTranslationAssembler
import io.legado.app.domain.model.RetryReason
import io.legado.app.domain.model.TextChunk
import io.legado.app.help.book.BookHelp
import io.legado.app.ui.config.translation.TranslationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class TranslateChapterUseCase(
    private val llmGateway: LlmGateway,
    private val translationCacheGateway: TranslationCacheGateway,
    private val dictionaryGateway: DictionaryGateway
) {

    data class TranslationProgress(
        val currentChunk: Int,
        val totalChunks: Int,
        val mixedContent: String? = null,
        val translatedChunkIndices: Set<Int> = emptySet()
    )

    companion object {
        private const val MAX_DICTIONARY_PAIRS = 80
    }

    private val dictionaryLock = Any()

    suspend fun execute(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        onProgress: (TranslationProgress) -> Unit,
        onTranslateStarted: () -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val originalContent = BookHelp.getContent(book, bookChapter)
                ?: return@withContext Result.failure(Exception("Failed to read original content"))

            val cachedTranslation =
                translationCacheGateway.readTranslation(book, bookChapter, targetLanguage)
            if (cachedTranslation != null) {
                onProgress(TranslationProgress(1, 1, cachedTranslation, emptySet()))
                return@withContext Result.success(cachedTranslation)
            }

            val contentHash = translationCacheGateway.computeContentHash(originalContent)

            // Load book dictionary for consistent terminology
            val bookDictionary = dictionaryGateway.getBookDictionaries(book)
            @Suppress("SENSELESS_COMPARISON")
            val dictionaries = (bookDictionary.pairs ?: emptyList()).toMutableList()

            // Callback to update dictionary pairs immediately (persist as soon as discovered)
            val onDictionaryUpdate: (List<DictPair>) -> Unit = { newPairs ->
                val merged = synchronized(dictionaryLock) {
                    mergeDictionaryPairs(dictionaries, newPairs)
                }
                if (merged) {
                    synchronized(dictionaryLock) {
                        dictionaryGateway.updateBookDic(book, dictionaries.toList())
                    }
                }
            }

            val chunks = ContentChunker.chunk(originalContent, TranslationConfig.llmMaxCharsPerChunk)
            if (chunks.isEmpty()) {
                return@withContext Result.failure(Exception("Failed to chunk content"))
            }

            val cachedChunks =
                translationCacheGateway.getCachedChunks(book, bookChapter, targetLanguage, contentHash)
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
                translationCacheGateway.writeTranslation(
                    book,
                    bookChapter,
                    targetLanguage,
                    mergedContent
                )
                onProgress(TranslationProgress(chunks.size, chunks.size, mergedContent, chunks.map { it.index }.toSet()))
                return@withContext Result.success(mergedContent)
            }

            onTranslateStarted()
            var translationError: Throwable? = null
            coroutineScope {
                val concurrentChunks = TranslationConfig.llmConcurrentChunks.coerceIn(1, 4)
                val chunkGroups = pendingChunks.chunked(concurrentChunks)

                for ((groupIndex, group) in chunkGroups.withIndex()) {
                    val results = group.map { chunk ->
                        async {
                            translateAndCacheChunk(chunk, book, bookChapter, targetLanguage, contentHash, dictionaries, onDictionaryUpdate)
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
                            translationError = result.exceptionOrNull() ?: Exception("Translation failed")
                            return@coroutineScope
                        }
                    }
                }
            }

            translationError?.let {
                return@withContext Result.failure(it)
            }

            if (translatedChunks.size != chunks.size) {
                return@withContext Result.failure(Exception("Translation incomplete"))
            }

            val allTranslatedChunks = chunks.sortedBy { it.index }.mapNotNull { chunk ->
                translatedChunks[chunk.index]?.let { content -> TextChunk(chunk.index, content, chunk.paragraphIndices) }
            }
            val mergedContent = ContentChunker.merge(allTranslatedChunks)
            translationCacheGateway.writeTranslation(book, bookChapter, targetLanguage, mergedContent)

            onProgress(TranslationProgress(chunks.size, chunks.size, mergedContent, chunks.map { it.index }.toSet()))
            Result.success(mergedContent)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Merge new pairs into existing list:
     * - If original exists, replace the translation
     * - If new, add to list
     * - Keep at most MAX_DICTIONARY_PAIRS
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

        // Keep early important names and the most recently discovered terms.
        if (existing.size > MAX_DICTIONARY_PAIRS) {
            val headCount = MAX_DICTIONARY_PAIRS / 2
            val tailCount = MAX_DICTIONARY_PAIRS - headCount
            val trimmed = existing.take(headCount) + existing.takeLast(tailCount)
            existing.clear()
            existing.addAll(trimmed)
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
        dictionaries: MutableList<DictPair>,
        onDictionaryUpdate: (List<DictPair>) -> Unit
    ): Result<String> {
        val existingCache =
            translationCacheGateway.getCachedChunk(book, bookChapter, targetLanguage, chunk.index)
        if (existingCache?.isSuccess == true && existingCache.translatedChunkContent != null) {
            return Result.success(existingCache.translatedChunkContent)
        }

        val result = translateChunkWithRetry(chunk, targetLanguage, dictionaries, onDictionaryUpdate)
        if (result.isSuccess) {
            translationCacheGateway.saveChunk(
                book, bookChapter, targetLanguage,
                chunk.index, chunk.content, contentHash,
                TranslationConfig.llmProvider,
                TranslationCache.STATUS_SUCCESS, result.getOrThrow(), null
            )
        } else {
            val errorMessage = result.exceptionOrNull()?.message ?: "Translation failed"
            translationCacheGateway.saveChunk(
                book, bookChapter, targetLanguage,
                chunk.index, chunk.content, contentHash,
                TranslationConfig.llmProvider,
                TranslationCache.STATUS_FAILED, null, errorMessage
            )
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
            val dictSnapshot = synchronized(dictionaryLock) { dictionaries.toList() }
            val result = llmGateway.translate(
                text = chunk.content,
                targetLanguage = targetLanguage,
                provider = TranslationConfig.llmProvider,
                baseUrl = TranslationConfig.llmBaseUrl,
                apiKey = TranslationConfig.llmApiKey,
                model = TranslationConfig.llmModel,
                prompt = TranslationConfig.llmPrompt,
                temperature = TranslationConfig.llmTemperature,
                dictionaries = dictSnapshot,
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
