package io.legado.app.ui.book.cache.manage

/**
 * 缓存管理书籍列表排序：以显式下载 FIFO 顺序为主，避免按「是否正在下载」排序
 * 在 FAB 全局启停时因分批状态刷新导致列表跳动。
 */
fun sortBookCacheItems(
    items: List<BookCacheBookItem>,
    downloadOrder: List<String>,
): List<BookCacheBookItem> {
    val orderIndex = downloadOrder.withIndex().associate { (index, bookUrl) -> bookUrl to index }
    return items.sortedWith(
        compareBy<BookCacheBookItem> { item ->
            orderIndex[item.bookUrl] ?: Int.MAX_VALUE
        }.thenByDescending { it.hasDownloadTask }
            .thenByDescending { it.cachedCount }
            .thenBy { it.name },
    )
}
