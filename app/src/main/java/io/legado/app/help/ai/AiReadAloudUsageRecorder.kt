package io.legado.app.help.ai

import io.legado.app.data.appDb
import io.legado.app.data.entities.AiReadAloudUsageRecord
import io.legado.app.data.entities.Book
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.ui.main.ai.AiModelConfig

object AiReadAloudUsageRecorder {

    data class UsageSnapshot(
        val elapsedMillis: Long = 0L,
        val requestCount: Int = 1,
        val usage: AiUsageStats = AiUsageStats()
    )

    class Tracker {
        private val startAt = System.currentTimeMillis()
        private var requestCounter = 0
        private var usage = AiUsageStats()

        @Synchronized
        fun onRequest() {
            requestCounter += 1
        }

        @Synchronized
        fun onUsage(stats: AiUsageStats) {
            usage += stats
        }

        @Synchronized
        fun snapshot(): UsageSnapshot {
            return UsageSnapshot(
                elapsedMillis = System.currentTimeMillis() - startAt,
                requestCount = requestCounter.coerceAtLeast(1),
                usage = usage
            )
        }
    }

    fun record(
        type: String,
        status: String,
        book: Book?,
        chapter: TextChapter?,
        cacheKey: String,
        batchName: String,
        modelConfig: AiModelConfig?,
        snapshot: UsageSnapshot,
        summary: String = "",
        error: String = ""
    ) {
        val provider = modelConfig?.let { AppConfig.aiProviderForModel(it) }
        val usage = snapshot.usage
        if (usage.totalTokens <= 0 &&
            usage.inputTokens <= 0 &&
            usage.outputTokens <= 0 &&
            usage.cachedInputTokens <= 0 &&
            status == AiReadAloudUsageRecord.STATUS_CACHE
        ) {
            return
        }
        appDb.aiReadAloudUsageRecordDao.insert(
            AiReadAloudUsageRecord(
                type = type,
                status = status,
                bookUrl = book?.bookUrl.orEmpty(),
                bookName = book?.name.orEmpty(),
                chapterTitle = chapter?.chapter?.title.orEmpty(),
                chapterIndex = chapter?.chapter?.index ?: -1,
                cacheKey = cacheKey,
                batchName = batchName,
                providerName = provider?.name.orEmpty(),
                modelId = modelConfig?.modelId.orEmpty(),
                elapsedMillis = snapshot.elapsedMillis,
                requestCount = snapshot.requestCount,
                inputTokens = usage.inputTokens,
                cachedInputTokens = usage.cachedInputTokens,
                outputTokens = usage.outputTokens,
                totalTokens = usage.totalTokens,
                summary = summary.take(240),
                error = error.take(500),
                createdAt = System.currentTimeMillis()
            )
        )
    }
}
