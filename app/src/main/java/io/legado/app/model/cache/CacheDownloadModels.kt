package io.legado.app.model.cache

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
)
