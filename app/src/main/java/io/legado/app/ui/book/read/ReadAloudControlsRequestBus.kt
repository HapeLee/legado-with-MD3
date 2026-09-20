package io.legado.app.ui.book.read

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * 「切到经典朗读控制」的一次性请求通道。
 *
 * 听书播放界面是独立的 Navigation 3 目的地，盖在阅读界面之上时阅读器整棵子树已被销毁，
 * 只剩 [ReadBookViewModel] 随 back stack 存活。播放界面既拿不到它，也不该直接依赖它，
 * 因此在它之上发一次性请求、由存活的 [ReadBookViewModel] 消费，回到阅读界面时即可落在
 * 经典朗读控制页。
 *
 * 不设 replay：请求只在「阅读界面已经在栈上」时才有意义；没有阅读界面时调用方改为直接打开
 * 阅读界面，不依赖本通道。
 */
object ReadAloudControlsRequestBus {

    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events: SharedFlow<Unit> = _events

    fun request() {
        _events.tryEmit(Unit)
    }
}
