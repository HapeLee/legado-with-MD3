package io.legado.app.ui.book.cache.manage

import org.junit.Assert.assertEquals
import org.junit.Test

class BookCacheListSortTest {

    @Test
    fun keepsFifoOrderEvenWhenOnlyOneBookIsDownloading() {
        val items = listOf(
            item("a", downloadingCount = 0, waitingCount = 3),
            item("b", downloadingCount = 2, waitingCount = 0),
            item("c", downloadingCount = 0, waitingCount = 0, pausedCount = 5),
        )

        val sorted = sortBookCacheItems(items, downloadOrder = listOf("a", "b", "c"))

        assertEquals(listOf("a", "b", "c"), sorted.map { it.bookUrl })
    }

    @Test
    fun doesNotJumpWhenMiddleBookBecomesActiveFirst() {
        // 模拟 FAB 恢复时 b 先刷新成下载中、a 仍显示暂停的中间态
        val midFlush = listOf(
            item("a", pausedCount = 10),
            item("b", downloadingCount = 1),
            item("c", pausedCount = 8),
        )

        val sorted = sortBookCacheItems(midFlush, downloadOrder = listOf("a", "b", "c"))

        assertEquals(listOf("a", "b", "c"), sorted.map { it.bookUrl })
    }

    @Test
    fun booksOutsideFifoStayAfterQueueOrderedByCachedCountThenName() {
        val items = listOf(
            item("zoo", cachedCount = 9),
            item("b", waitingCount = 1),
            item("alpha", cachedCount = 1),
            item("a", downloadingCount = 1),
        )

        val sorted = sortBookCacheItems(items, downloadOrder = listOf("a", "b"))

        assertEquals(listOf("a", "b", "zoo", "alpha"), sorted.map { it.bookUrl })
    }

    private fun item(
        bookUrl: String,
        downloadingCount: Int = 0,
        waitingCount: Int = 0,
        pausedCount: Int = 0,
        cachedCount: Int = 0,
    ): BookCacheBookItem {
        return BookCacheBookItem(
            bookUrl = bookUrl,
            name = bookUrl,
            author = "",
            totalCount = 10,
            cachedCount = cachedCount,
            cachedFileCount = cachedCount,
            waitingCount = waitingCount,
            downloadingCount = downloadingCount,
            pausedCount = pausedCount,
            errorCount = 0,
            isNotShelf = false,
            group = 0L,
        )
    }
}
