package io.legado.app.ui.book.cache.manage

import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterCacheStatusTest {

    @Test
    fun failedRetryShowsErrorWaitingNotPlainWaiting() {
        assertEquals(
            ChapterCacheStatusKey.ErrorWaitingRetry,
            resolveChapterCacheStatusKey(
                isDownloading = false,
                isWaiting = true,
                isPaused = false,
                isError = true,
                isCached = false,
                hasProgressLabel = false,
            ),
        )
    }

    @Test
    fun progressLabelWinsWhileDownloading() {
        assertEquals(
            ChapterCacheStatusKey.ProgressLabel,
            resolveChapterCacheStatusKey(
                isDownloading = true,
                isWaiting = false,
                isPaused = false,
                isError = true,
                isCached = false,
                hasProgressLabel = true,
            ),
        )
    }

    @Test
    fun plainWaitingWhenNotError() {
        assertEquals(
            ChapterCacheStatusKey.Waiting,
            resolveChapterCacheStatusKey(
                isDownloading = false,
                isWaiting = true,
                isPaused = false,
                isError = false,
                isCached = false,
                hasProgressLabel = false,
            ),
        )
    }
}
