package io.legado.app.utils.eventBus

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

object FlowEventBus {
    // 核心存储结构：Tag -> SharedFlow
    private val events = ConcurrentHashMap<String, MutableSharedFlow<Any>>()

    // 获取或创建对应 Tag 的 Flow
    @Suppress("UNCHECKED_CAST")
    fun <T> with(tag: String): MutableSharedFlow<T> {
        return events.getOrPut(tag) {
            MutableSharedFlow<Any>(
                replay = 1, // 相当于 LiveData 的粘性，确保新订阅者能收到最后一次通知
                extraBufferCapacity = 64,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            )
        } as MutableSharedFlow<T>
    }

    // 快捷发送
    fun post(tag: String, value: Any) {
        MainScope().launch {
            with<Any>(tag).emit(value)
        }
    }
}