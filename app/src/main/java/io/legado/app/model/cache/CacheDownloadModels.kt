package io.legado.app.model.cache

/** 单章缓存失败的最大重试次数：缓存队列与阅读页预下载共用同一口径。 */
const val CACHE_DOWNLOAD_RETRY_LIMIT = 3

/**
 * 第 [attempt] 次失败后的退避时长（attempt 从 1 开始）。
 *
 * 上限压在 4 秒：独占 FIFO 下重试中的书仍占着队首，退避过长会让后面的书干等。
 */
internal fun cacheRetryBackoffMs(attempt: Int): Long = when (attempt) {
    1 -> 1_000L
    2 -> 2_000L
    else -> 4_000L
}

data class CacheDownloadRequest(
    val bookUrl: String,
    val selection: ChapterSelection,
    val source: CacheDownloadSource = CacheDownloadSource.Manual,
)

sealed interface ChapterSelection {
    data class Range(val start: Int, val end: Int) : ChapterSelection
    data class Indices(val values: Set<Int>) : ChapterSelection
    data class Single(val index: Int) : ChapterSelection
}

enum class CacheDownloadSource {
    Manual,
    Batch,
    ReadPreload,
}

data class CacheDownloadCandidate(
    val bookUrl: String,
    val chapterIndex: Int,
)

data class CacheDownloadQueueSnapshot(
    val waitingCount: Int,
)

data class CacheDownloadState(
    val isRunning: Boolean = false,
    val totalWaiting: Int = 0,
    val totalRunning: Int = 0,
    val totalPaused: Int = 0,
    val totalSuccess: Int = 0,
    val totalFailure: Int = 0,
    val books: Map<String, CacheBookDownloadState> = emptyMap(),
)

data class CacheBookDownloadState(
    val bookUrl: String,
    val waitingCount: Int = 0,
    val runningIndices: Set<Int> = emptySet(),
    val pausedIndices: Set<Int> = emptySet(),
    val failedIndices: Set<Int> = emptySet(),
    val successCount: Int = 0,
    val failureMessage: String? = null,
    val chapterProgress: Map<Int, CacheChapterProgress> = emptyMap(),
)

enum class CacheChapterProgressPhase {
    CONTENT,
    IMAGES,
}

data class CacheChapterProgress(
    val phase: CacheChapterProgressPhase,
    val completed: Int,
    val total: Int,
) {
    val fraction: Float
        get() = if (total <= 0) 0f else completed.toFloat() / total
}
