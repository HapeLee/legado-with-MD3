package io.legado.app.domain.usecase

import androidx.annotation.Keep
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.TranslationCache
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.gateway.DictionaryGateway
import io.legado.app.domain.gateway.TranslationCacheGateway
import io.legado.app.domain.gateway.TranslationSettingsGateway
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiTaskPresetConfig
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.ContentChunker
import io.legado.app.domain.model.DictPair
import io.legado.app.domain.model.PartialTranslationAssembler
import io.legado.app.domain.model.RetryReason
import io.legado.app.domain.model.TextChunk
import io.legado.app.domain.model.TranslationConstants
import io.legado.app.domain.model.TranslationConstants.OUTPUT_FORMAT
import io.legado.app.help.book.BookHelp
import io.legado.app.help.http.newCallStrResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.utils.GSON
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.URLEncoder

class TranslateChapterUseCase(
    private val aiTextGateway: AiTextGateway,
    private val translationCacheGateway: TranslationCacheGateway,
    private val dictionaryGateway: DictionaryGateway,
    private val aiProfileGateway: AiProfileGateway,
    private val translationSettingsGateway: TranslationSettingsGateway,
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
        onProgress: (TranslationProgress) -> Unit,
        onTranslateStarted: () -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val settings = translationSettingsGateway.currentSettings
            val provider = settings.provider
            val preset = if (provider == TranslationConstants.PROVIDER_APP_AI) {
                resolveTranslationPreset()
                    ?: return@withContext Result.failure(Exception("No AI translation preset configured"))
            } else {
                null
            }
            val targetLanguage = settings.targetLanguage

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
            val dictionaries = bookDictionary.pairs.toMutableList()

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

            val chunks = ContentChunker.chunk(
                originalContent,
                settings.maxCharsPerChunk.coerceAtLeast(1000)
            )
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
                val concurrentChunks = settings.concurrentChunks.coerceIn(1, 4)
                val chunkGroups = pendingChunks.chunked(concurrentChunks)

                for ((groupIndex, group) in chunkGroups.withIndex()) {
                    val results = group.map { chunk ->
                        async {
                            translateAndCacheChunk(
                                chunk, book, bookChapter, targetLanguage, contentHash, provider,
                                preset, dictionaries, onDictionaryUpdate, settings.retryCount
                            )
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
        provider: String,
        preset: AiTaskPresetConfig?,
        dictionaries: MutableList<DictPair>,
        onDictionaryUpdate: (List<DictPair>) -> Unit,
        retryCount: Int,
    ): Result<String> {
        val existingCache =
            translationCacheGateway.getCachedChunk(book, bookChapter, targetLanguage, chunk.index)
        if (existingCache?.isSuccess == true && existingCache.translatedChunkContent != null) {
            return Result.success(existingCache.translatedChunkContent)
        }

        val result = translateChunkWithRetry(
            chunk, targetLanguage, provider, preset, dictionaries, onDictionaryUpdate, retryCount
        )
        if (result.isSuccess) {
            translationCacheGateway.saveChunk(
                book, bookChapter, targetLanguage,
                chunk.index, chunk.content, contentHash,
                provider,
                TranslationCache.STATUS_SUCCESS, result.getOrThrow(), null
            )
        } else {
            val errorMessage = result.exceptionOrNull()?.message ?: "Translation failed"
            translationCacheGateway.saveChunk(
                book, bookChapter, targetLanguage,
                chunk.index, chunk.content, contentHash,
                provider,
                TranslationCache.STATUS_FAILED, null, errorMessage
            )
        }
        return result
    }

    private suspend fun translateChunkWithRetry(
        chunk: TextChunk,
        targetLanguage: String,
        provider: String,
        preset: AiTaskPresetConfig?,
        dictionaries: MutableList<DictPair>,
        onDictionaryUpdate: (List<DictPair>) -> Unit,
        retryCount: Int,
    ): Result<String> {
        var lastError: Exception? = null
        var lastRetryReason: RetryReason? = null
        for (attempt in 0..retryCount.coerceIn(0, 5)) {
            val dictSnapshot = synchronized(dictionaryLock) { dictionaries.toList() }
            val result = when (provider) {
                TranslationConstants.PROVIDER_GOOGLE -> translateWithGoogle(chunk.content, targetLanguage)
                TranslationConstants.PROVIDER_APP_AI -> translateWithAiGateway(
                    text = chunk.content,
                    targetLanguage = targetLanguage,
                    preset = preset ?: return Result.failure(Exception("No AI translation preset configured")),
                    dictionaries = dictSnapshot,
                    onUpdate = onDictionaryUpdate,
                    retryReason = lastRetryReason
                )
                else -> Result.failure(IllegalArgumentException("Unknown translation provider: $provider"))
            }
            if (result.isSuccess) {
                return result
            }
            lastError = result.exceptionOrNull() as? Exception
            lastRetryReason = parseRetryReason(lastError)
        }
        return Result.failure(lastError ?: Exception("Translation failed after retries"))
    }

    private suspend fun resolveTranslationPreset(): AiTaskPresetConfig? {
        return aiProfileGateway.getTaskPreset(AiTaskType.TRANSLATE_CHAPTER)
    }

    private suspend fun translateWithGoogle(text: String, targetLanguage: String): Result<String> {
        val encodedText = URLEncoder.encode(text, Charsets.UTF_8.name())
        val url =
            "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$targetLanguage&dj=1&dt=t&ie=UTF-8&q=$encodedText"
        val response = okHttpClient.newCallStrResponse {
            url(url)
        }
        return if (response.isSuccessful()) {
            runCatching {
                val json = GSON.fromJson(response.body, GoogleTranslateResponse::class.java)
                json?.sentences?.mapNotNull { it.trans }?.joinToString("").orEmpty()
            }.fold(
                onSuccess = { translatedText ->
                    if (translatedText.isNotEmpty()) {
                        Result.success(translatedText)
                    } else {
                        Result.failure(Exception("Empty translation result"))
                    }
                },
                onFailure = { Result.failure(it) }
            )
        } else {
            Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
        }
    }

    private suspend fun translateWithAiGateway(
        text: String,
        targetLanguage: String,
        preset: AiTaskPresetConfig,
        dictionaries: List<DictPair>,
        onUpdate: ((List<DictPair>) -> Unit)?,
        retryReason: RetryReason?
    ): Result<String> {
        if (targetLanguage == "en" && isMostlyEnglish(text)) {
            return Result.success(text)
        }
        if (targetLanguage == "zh" && isMostlyChinese(text)) {
            return Result.success(text)
        }
        if (preset.model.provider.baseUrl.isBlank() ||
            preset.model.provider.apiKey.isBlank() ||
            preset.model.modelId.isBlank()
        ) {
            return Result.failure(IllegalArgumentException("AI model configuration is incomplete"))
        }

        val dictionaryInstruction = buildDictionaryInstruction(dictionaries)
        val retryInstruction = buildRetryInstruction(retryReason)
        val systemPrompt = buildSystemPrompt(
            prompt = preset.promptTemplate,
            targetLanguage = targetLanguage,
            dictionaryInstruction = dictionaryInstruction,
            retryInstruction = retryInstruction,
            outputFormat = OUTPUT_FORMAT
        )
        val params = preset.params.copy(
            temperature = preset.params.temperature
                ?: preset.model.defaultParams.temperature
                ?: TranslationConstants.DEFAULT_TEMPERATURE
        )
        val result = aiTextGateway.generate(
            AiGenerateRequest(
                model = preset.model,
                messages = listOf(
                    AiMessage(AiMessageRole.SYSTEM, systemPrompt),
                    AiMessage(AiMessageRole.USER, "Translate the following text:\n\n$text")
                ),
                params = params
            )
        )
        if (result.isFailure) {
            return Result.failure(result.exceptionOrNull() ?: Exception("Translation failed"))
        }
        val rawContent = result.getOrThrow().text
        if (rawContent.isBlank()) {
            return Result.failure(Exception("Empty translation result"))
        }
        val parseResult = parseLlmOutput(rawContent, dictionaries)
        if (parseResult.extractedPairs.isNotEmpty()) {
            onUpdate?.invoke(parseResult.extractedPairs.take(10))
        }
        val finalText = if (targetLanguage == "zh") {
            filterHighEnglishParagraphs(parseResult.translatedText)
        } else {
            parseResult.translatedText
        }
        return Result.success(finalText)
    }

    private data class ParseOutputResult(
        val translatedText: String,
        val extractedPairs: List<DictPair>
    )

    private fun parseLlmOutput(
        rawOutput: String,
        existingDictionaries: List<DictPair>
    ): ParseOutputResult {
        val existingOriginals = existingDictionaries.map { it.original }.toSet()
        var dictionarySection: String? = null
        var resultSection: String? = null
        var currentSection: String? = null

        for (line in rawOutput.split('\n')) {
            val trimmedLine = line.trim()
            when {
                trimmedLine.startsWith("[dictionary]", ignoreCase = true) -> currentSection = "dictionary"
                trimmedLine.startsWith("[result]", ignoreCase = true) -> currentSection = "result"
                currentSection == "dictionary" -> dictionarySection = (dictionarySection ?: "") + line + "\n"
                currentSection == "result" -> resultSection = (resultSection ?: "") + line + "\n"
            }
        }

        val extractedPairs = dictionarySection
            ?.let { parseDictionarySection(it, existingOriginals) }
            .orEmpty()
        val translatedText = resultSection?.trim() ?: rawOutput.trim()
        return ParseOutputResult(translatedText, extractedPairs)
    }

    private fun parseDictionarySection(
        section: String,
        existingOriginals: Set<String>
    ): List<DictPair> {
        val pairs = mutableListOf<DictPair>()
        for (line in section.split('\n')) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) continue
            if (trimmedLine.startsWith("[") || trimmedLine.startsWith("dictionary", ignoreCase = true)) {
                continue
            }

            val separator = when {
                trimmedLine.contains(" -> ") -> " -> "
                trimmedLine.contains(" ->") -> " ->"
                trimmedLine.contains("-> ") -> "-> "
                trimmedLine.contains("->") -> "->"
                trimmedLine.contains(" : ") -> " : "
                trimmedLine.contains(": ") -> ": "
                trimmedLine.contains(" :") -> " :"
                trimmedLine.contains(":") -> ":"
                else -> null
            }
            if (separator != null) {
                val parts = trimmedLine.split(separator, limit = 2)
                if (parts.size == 2) {
                    val original = parts[0].trim()
                    val translation = parts[1].trim()
                    if (original !in existingOriginals && original.isNotEmpty() && translation.isNotEmpty()) {
                        pairs.add(DictPair(original, translation))
                        if (pairs.size >= 10) break
                    }
                }
            }
        }
        return pairs
    }

    private fun buildDictionaryInstruction(dictionaries: List<DictPair>): String {
        if (dictionaries.isEmpty()) return ""
        val terms = dictionaries.joinToString("\n") { "${it.original} -> ${it.translation}" }
        return """

Terminology Dictionary (use these exact translations):
$terms
"""
    }

    private fun buildRetryInstruction(retryReason: RetryReason?): String {
        return when (retryReason) {
            RetryReason.EMPTY_RESPONSE -> "\nPrevious attempt returned empty content. Return only the required formatted translation."
            RetryReason.PARSE_ERROR -> "\nPrevious attempt used an invalid format. Follow the [dictionary] and [result] format exactly."
            RetryReason.RATE_LIMIT,
            RetryReason.SERVER_ERROR,
            RetryReason.AUTH_ERROR,
            RetryReason.TIMEOUT,
            RetryReason.NETWORK_ERROR,
            RetryReason.UNKNOWN,
            RetryReason.PERMANENT_FAILURE,
            null -> ""
        }
    }

    private fun buildSystemPrompt(
        prompt: String,
        targetLanguage: String,
        dictionaryInstruction: String,
        retryInstruction: String,
        outputFormat: String
    ): String {
        return buildString {
            append(prompt)
            append("\nTarget language: ").append(getLanguageDisplayName(targetLanguage))
            if (dictionaryInstruction.isNotEmpty()) {
                append(dictionaryInstruction)
            }
            if (retryInstruction.isNotEmpty()) {
                append(retryInstruction)
            }
            append("\n").append(outputFormat)
        }
    }

    private fun getLanguageDisplayName(code: String): String {
        return TranslationConstants.targetLanguages.find { it.first == code }?.second ?: code
    }

    private fun isMostlyEnglish(text: String): Boolean {
        if (text.isEmpty()) return false
        val englishChars =
            text.count { it in 'A'..'Z' || it in 'a'..'z' || it in ".,!?;:'\"-()[]{}-" }
        return englishChars.toDouble() / text.length > 0.8
    }

    private fun isMostlyChinese(text: String): Boolean {
        if (text.isEmpty()) return false
        val chinesePunctuation = "。，！？；：“”‘’（）【】《》"
        val chineseChars = text.count {
            it in '一'..'鿿' || it in chinesePunctuation
        }
        return chineseChars.toDouble() / text.length > 0.8
    }

    private fun filterHighEnglishParagraphs(text: String): String {
        return text.split("\n")
            .filter { paragraph -> !isMostlyEnglish(paragraph) }
            .joinToString("\n")
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

@Keep
private data class GoogleTranslateResponse(
    val sentences: List<GoogleSentence>?
)

@Keep
private data class GoogleSentence(
    val trans: String?
)
