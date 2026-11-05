package io.legado.app.model.cache

import org.junit.Assert.assertEquals
import org.junit.Test

class CacheRetryBackoffTest {

    @Test
    fun backoffGrowsWithAttemptAndStaysBounded() {
        assertEquals(1_000L, cacheRetryBackoffMs(1))
        assertEquals(2_000L, cacheRetryBackoffMs(2))
        assertEquals(4_000L, cacheRetryBackoffMs(3))
    }

    /**
     * 越界输入不应抛异常：`ConcurrentException` 不计数，attempt 可能是 0；
     * 上限必须压在数秒内，否则独占 FIFO 下重试中的书会把队首挂住。
     */
    @Test
    fun outOfRangeAttemptFallsBackToUpperBound() {
        assertEquals(4_000L, cacheRetryBackoffMs(0))
        assertEquals(4_000L, cacheRetryBackoffMs(-1))
        assertEquals(4_000L, cacheRetryBackoffMs(99))
    }

    @Test
    fun retryLimitIsSharedByQueueAndReaderPreload() {
        assertEquals(3, CACHE_DOWNLOAD_RETRY_LIMIT)
    }
}
