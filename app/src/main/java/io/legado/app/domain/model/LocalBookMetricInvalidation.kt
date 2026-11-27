package io.legado.app.domain.model

/**
 * 本地书排序指标（文件大小）的单书失效广播。
 *
 * 导入、重导、重绑和远端恢复之后由 model/help 层发信号，数据层的
 * [io.legado.app.data.repository.BookshelfRepository] 订阅并清掉对应书籍的缓存。
 * 放在 domain 层是为了不让 `help`/`model` 反向依赖 `data.repository`。
 *
 * 没有订阅者时（冷启动先去导入页、书架仓库还没被创建）信号会暂存，
 * 第一个订阅者接入时立即补发，避免磁盘缓存里同校验戳的旧大小被重新命中。
 */
object LocalBookMetricInvalidation {

    /** 暂存上限：只用于仓库创建前的空窗，正常不会接近。 */
    private const val MAX_PENDING = 512

    private val lock = Any()
    private val listeners = ArrayList<(String) -> Unit>()
    private val pending = LinkedHashSet<String>()

    /** 通知某本书的缓存已不可信；可在任意线程调用。 */
    fun notifyChanged(bookUrl: String) {
        if (bookUrl.isBlank()) return
        val snapshot: List<(String) -> Unit>
        synchronized(lock) {
            if (listeners.isEmpty()) {
                if (pending.size >= MAX_PENDING) {
                    pending.remove(pending.first())
                }
                pending.add(bookUrl)
                return
            }
            snapshot = listeners.toList()
        }
        snapshot.forEach { it(bookUrl) }
    }

    /** 订阅失效信号，并补发接入前积压的条目。 */
    fun register(listener: (String) -> Unit) {
        val backlog: List<String>
        synchronized(lock) {
            listeners.add(listener)
            backlog = pending.toList()
            pending.clear()
        }
        backlog.forEach(listener)
    }

    fun unregister(listener: (String) -> Unit) {
        synchronized(lock) {
            listeners.remove(listener)
        }
    }
}
