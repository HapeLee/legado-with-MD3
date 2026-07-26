package io.legado.app.model.cache

/**
 * 显式离线缓存的书籍级 FIFO 顺序。
 * 不包含阅读器预下载；暂停让位后恢复时通过 [moveToTail] 追加到队尾。
 */
class ExplicitCacheBookFifo {

    private val order = ArrayDeque<String>()
    private val bookUrls = hashSetOf<String>()

    val size: Int
        get() = order.size

    fun isEmpty(): Boolean = order.isEmpty()

    fun contains(bookUrl: String): Boolean = bookUrl in bookUrls

    fun ensure(bookUrl: String) {
        if (bookUrls.add(bookUrl)) {
            order.addLast(bookUrl)
        }
    }

    fun remove(bookUrl: String): Boolean {
        if (!bookUrls.remove(bookUrl)) return false
        order.remove(bookUrl)
        return true
    }

    fun moveToTail(bookUrl: String) {
        if (bookUrl !in bookUrls) return
        order.remove(bookUrl)
        order.addLast(bookUrl)
    }

    /**
     * 仅在已持有本实例监视器、且 [predicate] 不再获取其它锁时使用。
     * 若需结合 CacheBookModel 状态筛选，应先 [snapshot] 再在锁外判断，避免 ABBA 死锁。
     */
    fun headWhere(predicate: (String) -> Boolean): String? {
        return order.firstOrNull(predicate)
    }

    /** 顺序副本，供调用方在锁外做 model 状态判断。 */
    fun snapshot(): List<String> = order.toList()

    fun clear() {
        order.clear()
        bookUrls.clear()
    }
}
