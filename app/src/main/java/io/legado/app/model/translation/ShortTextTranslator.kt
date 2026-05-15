package io.legado.app.model.translation

import io.legado.app.domain.gateway.LlmGateway
import io.legado.app.ui.config.translation.TranslationConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext

/**
 * Orchestrator for short text translation (book names, authors, etc.).
 *
 * Features:
 * - Deduplicates concurrent requests for the same text via shared Deferred
 * - Debounces ~100ms and batches up to 20 texts per request
 * - Falls back to original text on batch failure
 * - Uses LlmGateway.translateShortBatch()
 */
object ShortTextTranslator {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val pendingTexts = mutableMapOf<String, String>() // normalized -> original
    private val pendingDeferreds = mutableMapOf<String, CompletableDeferred<String>>()
    private var batchJob: Job? = null

    private const val DEBOUNCE_MS = 100L
    private const val MAX_BATCH_SIZE = 20

    private class CompletableDeferred<T> {
        private var _isCompleted = false
        private var _result: T? = null
        private val waiters = mutableListOf<(T) -> Unit>()

        fun complete(result: T) {
            _isCompleted = true
            _result = result
            waiters.forEach { it(result) }
            waiters.clear()
        }

        fun await(onResult: (T) -> Unit) {
            if (_isCompleted) {
                _result?.let(onResult)
            } else {
                waiters.add(onResult)
            }
        }

        val isCompleted get() = _isCompleted
        val result get() = _result
    }

    private fun getGateway(): LlmGateway {
        return GlobalContext.get().get<LlmGateway>()
    }

    /**
     * Translate a single short text.
     * Returns the translated text, or original text if translation fails/disabled.
     */
    suspend fun translate(text: String): String {
        if (!TranslationConfig.translateBookInfoEnabled) return text
        if (!TextLanguageHeuristics.needsTranslation(text)) return text

        val normalized = TextLanguageHeuristics.normalize(text)
        if (normalized.isBlank()) return text

        // Create new deferred first, then check for existing under lock
        val newDeferred = CompletableDeferred<String>()
        val sharedDeferred = mutex.withLock {
            pendingDeferreds[normalized]?.let { existing ->
                existing // Found existing deferred, will share it
            } ?: run {
                // No existing, register the new one
                pendingDeferreds[normalized] = newDeferred
                pendingTexts[normalized] = text
                scheduleBatch()
                null // signal we created new
            }
        } ?: newDeferred // If null, we created; if not null, use existing

        // Wait for the result
        var result: String? = null
        sharedDeferred.await { result = it }
        return result ?: text
    }

    /**
     * Translate multiple short texts in batch.
     * Returns map of normalized text -> translated text.
     */
    suspend fun translateBatch(texts: List<String>): Map<String, String> {
        if (!TranslationConfig.translateBookInfoEnabled) return texts.associateWith { it }
        if (texts.isEmpty()) return emptyMap()

        val needsTranslation = texts.filter {
            TextLanguageHeuristics.needsTranslation(it) && it.isNotBlank()
        }
        if (needsTranslation.isEmpty()) return texts.associateWith { it }

        return withContext(Dispatchers.IO) {
            val results = mutableMapOf<String, String>()
            val normalizedToOriginal = needsTranslation.associate {
                TextLanguageHeuristics.normalize(it) to it
            }

            val provider = TranslationConfig.llmProvider
            val baseUrl = TranslationConfig.llmBaseUrl
            val apiKey = TranslationConfig.llmApiKey
            val model = TranslationConfig.llmModel
            val targetLanguage = TranslationConfig.llmTargetLanguage

            // Check if OpenAI-like provider is configured
            if (provider == TranslationConfig.PROVIDER_OPENAI) {
                if (baseUrl.isBlank() || apiKey.isBlank() || model.isBlank()) {
                    return@withContext normalizedToOriginal // Return originals on incomplete config
                }
            }

            val batch = normalizedToOriginal.keys.toList()
            val result = invokeTranslateShortBatch(batch, targetLanguage, provider, baseUrl, apiKey, model)

            result.onSuccess { translatedMap ->
                for (index in translatedMap.keys) {
                    if (index < batch.size) {
                        val normalized = batch[index]
                        val original = normalizedToOriginal[normalized]
                        if (original != null) {
                            results[original] = translatedMap[index] ?: original
                        }
                    }
                }
                // Add any texts that weren't translated (failed or not included)
                for ((_, original) in normalizedToOriginal) {
                    if (!results.containsKey(original)) {
                        results[original] = original
                    }
                }
            }.onFailure {
                // On failure, return originals
                for ((_, original) in normalizedToOriginal) {
                    results[original] = original
                }
            }

            results
        }
    }

    private suspend fun invokeTranslateShortBatch(
        texts: List<String>,
        targetLanguage: String,
        provider: String,
        baseUrl: String,
        apiKey: String,
        model: String
    ): Result<Map<Int, String>> {
        return try {
            val gateway = getGateway()
            gateway.translateShortBatch(texts, targetLanguage, provider, baseUrl, apiKey, model)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun scheduleBatch() {
        batchJob?.cancel()
        batchJob = scope.launch {
            delay(DEBOUNCE_MS)
            flushBatch()
        }
    }

    private suspend fun flushBatch() {
        val textsToTranslate: Map<String, String>
        val deferreds: Map<String, CompletableDeferred<String>>

        val locked = mutex.withLock {
            if (pendingTexts.isEmpty()) return

            pendingTexts.toMap() to pendingDeferreds.toMap().also {
                pendingTexts.clear()
                pendingDeferreds.clear()
            }
        }
        textsToTranslate = locked.first
        deferreds = locked.second

        if (textsToTranslate.isEmpty()) return

        // Split into batches of MAX_BATCH_SIZE
        val normalizedList = textsToTranslate.keys.toList()
        val batches = normalizedList.chunked(MAX_BATCH_SIZE)

        for (batch in batches) {
            val provider = TranslationConfig.llmProvider
            val baseUrl = TranslationConfig.llmBaseUrl
            val apiKey = TranslationConfig.llmApiKey
            val model = TranslationConfig.llmModel
            val targetLanguage = TranslationConfig.llmTargetLanguage

            val result = invokeTranslateShortBatch(batch, targetLanguage, provider, baseUrl, apiKey, model)

            val translatedMap = result.getOrNull() ?: emptyMap()

            // Resolve all deferreds
            for (normalized in batch) {
                val deferred = deferreds[normalized]
                val index = batch.indexOf(normalized)
                val translated = translatedMap[index]
                if (deferred != null && translated != null) {
                    val original = textsToTranslate[normalized]
                    deferred.complete(translated)
                } else {
                    val original = textsToTranslate[normalized]
                    deferred?.complete(original ?: normalized)
                }
            }
        }
    }
}