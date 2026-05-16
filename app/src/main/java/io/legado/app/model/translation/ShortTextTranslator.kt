package io.legado.app.model.translation

import io.legado.app.data.dao.TranslationCacheDao
import io.legado.app.data.entities.TranslationCache
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
import org.koin.core.context.GlobalContext
import java.util.regex.Pattern

/**
 * Fire-and-forget short text translation with debounced batching.
 *
 * translate() returns immediately:
 * - Cached result if available (from memory or DB)
 * - Original text if not cached (translation queued for async execution)
 *
 * Translation queue is debounced (200ms) or batched (30 items), then
 * executed asynchronously and cached for future reads.
 */
object ShortTextTranslator {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    // In-memory cache for super-fast intra-session lookups
    private val memoryCache = mutableMapOf<String, String>()

    // Pending translations (normalized -> original)
    private val pendingTexts = mutableMapOf<String, String>()
    private var batchJob: Job? = null
    private var isRunning = false

    private const val DEBOUNCE_MS = 200L
    private const val MAX_BATCH_SIZE = 30

    // Matches: "Vichin ch.01", "Vichin Pt.01", "Vichin-Chapter 01"
    // Captures: body, sep, marker, number
    private val chapterMarkerPattern = Pattern.compile(
        "(.+?)([\\s\\-_])(ch|pt)[.]*(\\d+[a-zA-Z]?)",
        Pattern.CASE_INSENSITIVE
    )

    private data class ChapterParts(val body: String, val sep: String, val marker: String, val number: String) {
        fun originalMarker() = "$sep$marker.$number"
    }

    private fun parseChapterParts(text: String): ChapterParts? {
        val matcher = chapterMarkerPattern.matcher(text)
        if (matcher.matches()) {
            return ChapterParts(
                body = matcher.group(1)!!,
                sep = matcher.group(2)!!,
                marker = matcher.group(3)!!,
                number = matcher.group(4)!!
            )
        }
        return null
    }

    private fun getGateway(): LlmGateway {
        return GlobalContext.get().get<LlmGateway>()
    }

    private fun getCacheDao(): TranslationCacheDao {
        return GlobalContext.get().get<TranslationCacheDao>()
    }

    /**
     * Build cache key for short text translation.
     * Format: targetLanguage@normalizedSource
     */
    private fun buildCacheKey(targetLanguage: String, normalizedSource: String): String {
        return "$targetLanguage@$normalizedSource"
    }

    /**
     * Translate a single short text (fire-and-forget).
     * Returns immediately with cached result or original text.
     * Translation happens asynchronously in the background.
     */
    suspend fun translate(text: String): String {
        if (!TranslationConfig.translateBookInfoEnabled) return text
        if (!TextLanguageHeuristics.needsTranslation(text)) return text

        val normalized = TextLanguageHeuristics.normalize(text)
        if (normalized.isBlank()) return text

        val targetLanguage = TranslationConfig.llmTargetLanguage

        // 检查是否需要章节分离处理
        val chapterParts = parseChapterParts(text)
        if (chapterParts != null) {
            // 有章节标记，先用主体部分查找缓存
            val bodyCacheKey = buildCacheKey(targetLanguage, chapterParts.body.lowercase())
            val cachedBodyTranslation = memoryCache[bodyCacheKey]
                ?: getCachedFromDb(bodyCacheKey)

            if (cachedBodyTranslation != null) {
                // 缓存命中，直接组合返回
                return "$cachedBodyTranslation${chapterParts.originalMarker()}"
            }

            // 缓存未命中，翻译主体，原文返回
            queueForTranslation(chapterParts.body, text)
            return text
        }

        // 普通文本，直接走原有逻辑
        val cacheKey = buildCacheKey(targetLanguage, normalized)

        // Check in-memory cache first (fastest)
        memoryCache[cacheKey]?.let { return it }

        // Check DB cache
//        getCachedFromDb(cacheKey)?.let { cached ->
//            memoryCache[cacheKey] = cached
//            return cached
//        }

        // Not cached - queue for async translation and return original immediately
        queueForTranslation(normalized, text)
        return text
    }

    private fun queueForTranslation(body: String, originalText: String) {
        scope.launch {
            mutex.withLock {
                pendingTexts[body.lowercase()] = originalText
                // 只要 pending 还有数据，就确保 batchJob 在跑
                // 已经 active 的 job 不会被打断
                if (batchJob?.isActive != true) {
                    scheduleBatch()
                }
            }
        }
    }

    private suspend fun getCachedFromDb(cacheKey: String): String? {
        return try {
            val dao = getCacheDao()
            val cache = dao.getByCacheKey(cacheKey)
            if (cache != null && cache.isSuccess && cache.translatedChunkContent != null) {
                cache.translatedChunkContent
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun saveToDb(cacheKey: String, normalizedSource: String, original: String, translated: String) {
        try {
            val dao = getCacheDao()
            val cache = TranslationCache(
                cacheKey = cacheKey,
                bookUrl = "",
                chapterIndex = 0,
                chapterTitleMD5 = "",
                originalContentHash = normalizedSource,
                targetLanguage = TranslationConfig.llmTargetLanguage,
                provider = TranslationConfig.llmProvider,
                chunkIndex = 0,
                originalChunkContent = original,
                translatedChunkContent = translated,
                status = TranslationCache.STATUS_SUCCESS
            )
            dao.insert(cache)
        } catch (e: Exception) {
            // Ignore cache save errors
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
        // 如果已经有 job 在跑，就不要 cancel 它了，让它继续处理
        if (batchJob?.isActive == true) return

        batchJob = scope.launch {
            isRunning = true
            try {
                while (true) {
                    // 等待攒批：200ms 或达到 30 条
                    var waited = 0L
                    while (pendingTexts.size < MAX_BATCH_SIZE && waited < DEBOUNCE_MS) {
                        delay(50)
                        waited += 50
                    }

                    val batch = mutex.withLock {
                        if (pendingTexts.isEmpty()) return@withLock null
                        val result = pendingTexts.toMap()
                        pendingTexts.clear()
                        result
                    }

                    if (batch == null) break

                    // 一旦进入 flushBatch，不再被 cancel
                    // 新来的请求会等待当前 batch 完成
                    flushBatch(batch)

                    // flush 完成后检查是否还有新的待处理
                    if (pendingTexts.isEmpty()) break
                }
            } finally {
                isRunning = false
            }
        }
    }

    private suspend fun flushBatch(batch: Map<String, String>) {
        if (batch.isEmpty()) return

        val targetLanguage = TranslationConfig.llmTargetLanguage
        val normalizedList = batch.keys.toList()
        val batches = normalizedList.chunked(MAX_BATCH_SIZE)

        try {
            for (chunk in batches) {
                val provider = TranslationConfig.llmProvider
                val baseUrl = TranslationConfig.llmBaseUrl
                val apiKey = TranslationConfig.llmApiKey
                val model = TranslationConfig.llmModel

                val result = invokeTranslateShortBatch(chunk, targetLanguage, provider, baseUrl, apiKey, model)

                val translatedMap = result.getOrNull() ?: emptyMap()

                for (normalized in chunk) {
                    val index = chunk.indexOf(normalized)
                    val translated = translatedMap[index]
                    val original = batch[normalized]

                    if (translated != null) {
                        val cacheKey = buildCacheKey(targetLanguage, normalized)
                        memoryCache[cacheKey] = translated
                        saveToDb(cacheKey, normalized, original ?: normalized, translated)
                    }
                }
            }
        } catch (e: Exception) {
            // On error, just skip - translation will be retried next time text is requested
        }
    }

    fun clearCache() {
        memoryCache.clear()
    }
}