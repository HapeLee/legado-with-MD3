package io.legado.app.utils.eventBus

import io.legado.app.utils.eventBus.FlowEventBus.with
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FlowEventBusExt {

    // 在协程中直接调用
    suspend fun <T> post(tag: String, value: T) {
        with<T>(tag).emit(value)
    }

    // 在非协程环境调用
    fun <T> postSync(tag: String, value: T) {
        CoroutineScope(Dispatchers.Main).launch {
            with<T>(tag).emit(value)
        }
    }

}