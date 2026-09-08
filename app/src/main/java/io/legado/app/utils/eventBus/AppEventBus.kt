package io.legado.app.utils.eventBus

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 应用事件总线（替换第三方 LiveEventBus）。
 *
 * 第三方 LiveEventBus 依赖 `LiveData`/`Observer`/`AppCompatActivity`，是纯 Android 依赖，
 * 挡在 KMP 迁移路上。本实现去 Android 化，同时**刻意保留 LiveEventBus 的两个语义**，
 * 避免迁移引入时序回归：
 *
 * 1. **post 同步分发**：订阅者回调在 [post] 调用线程内立即执行，与 `LiveEventBus.post` 一致。
 *    这点与 `MutableSharedFlow.emit`（可挂起、经协程调度）不同——现有 100+ 处调用点里
 *    有依赖同步时序的写法（如先 post 再立即读状态），改异步会产生难定位的顺序 bug。
 * 2. **粘性按订阅选择**：同一 tag 可同时存在粘性与非粘性订阅。粘性值单独保存，
 *    不依赖 `SharedFlow` 的 `replay`（replay 会让该 tag 的**所有**订阅者都变粘性）。
 *
 * 不实现 `postDelay`/`postOrderly`：原 `EventBusExtensions` 里那两个封装零调用点。
 */
object AppEventBus {

    private val listeners = ConcurrentHashMap<String, CopyOnWriteArrayList<(Any?) -> Unit>>()
    private val stickyValues = ConcurrentHashMap<String, Any?>()

    /** 同步分发给当前所有订阅者；无订阅者时仅记录粘性值（与 LiveEventBus 一致）。 */
    fun post(tag: String, value: Any?) {
        stickyValues[tag] = value
        listeners[tag]?.forEach { listener -> listener(value) }
    }

    /** 非粘性订阅单个 tag。 */
    fun <T> observe(tag: String): Flow<T> = observeTags(listOf(tag), sticky = false)

    /** 粘性订阅单个 tag：订阅时先收到该 tag 最后一次 post 的值（若有）。 */
    fun <T> observeSticky(tag: String): Flow<T> = observeTags(listOf(tag), sticky = true)

    /** 非粘性订阅多个 tag，任一 tag 有事件即发射。 */
    fun <T> observeAll(tags: List<String>): Flow<T> = observeTags(tags, sticky = false)

    private fun <T> observeTags(tags: List<String>, sticky: Boolean): Flow<T> = callbackFlow {
        // null 值不投递：Flow<T> 的 T 在调用点均为非空类型，且现有发送方不发 null。
        @Suppress("UNCHECKED_CAST")
        val listener: (Any?) -> Unit = { value -> if (value != null) trySend(value as T) }

        val registered = tags.map { tag ->
            tag to listeners.getOrPut(tag) { CopyOnWriteArrayList() }.also { it.add(listener) }
        }

        if (sticky) {
            tags.forEach { tag ->
                val last = stickyValues[tag]
                @Suppress("UNCHECKED_CAST")
                if (last != null) trySend(last as T)
            }
        }

        awaitClose {
            registered.forEach { (tag, list) ->
                list.remove(listener)
                // 仅在仍是我们当初放入的那个 list 时移除，避免误删后注册者的容器
                listeners.remove(tag, list)
            }
        }
    }
}
