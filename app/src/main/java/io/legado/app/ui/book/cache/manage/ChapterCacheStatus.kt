package io.legado.app.ui.book.cache.manage

/**
 * 缓存管理章节行状态文案键（与 strings.xml 对应）。
 * 纯函数，便于 JVM 单测覆盖优先级。
 */
enum class ChapterCacheStatusKey {
    ProgressLabel,
    Downloading,
    ErrorWaitingRetry,
    Waiting,
    Paused,
    Error,
    Cached,
    NotCached,
}

fun resolveChapterCacheStatusKey(
    isDownloading: Boolean,
    isWaiting: Boolean,
    isPaused: Boolean,
    isError: Boolean,
    isCached: Boolean,
    hasProgressLabel: Boolean,
): ChapterCacheStatusKey {
    if (isDownloading && hasProgressLabel) return ChapterCacheStatusKey.ProgressLabel
    if (isDownloading) return ChapterCacheStatusKey.Downloading
    if (isWaiting && isError) return ChapterCacheStatusKey.ErrorWaitingRetry
    if (isWaiting) return ChapterCacheStatusKey.Waiting
    if (isPaused) return ChapterCacheStatusKey.Paused
    if (isError) return ChapterCacheStatusKey.Error
    if (isCached) return ChapterCacheStatusKey.Cached
    return ChapterCacheStatusKey.NotCached
}
