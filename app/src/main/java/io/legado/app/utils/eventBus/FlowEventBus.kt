package io.legado.app.utils.eventBus

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

object FlowEventBus {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val events = ConcurrentHashMap<String, MutableSharedFlow<Any>>()

    @Suppress("UNCHECKED_CAST")
    fun <T> with(tag: String): MutableSharedFlow<T> {
        return events.getOrPut(tag) {
            MutableSharedFlow(
                replay = 1,
                extraBufferCapacity = 64,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            )
        } as MutableSharedFlow<T>
    }

    fun post(tag: String, value: Any) {
        scope.launch {
            with<Any>(tag).emit(value)
        }
    }
}
