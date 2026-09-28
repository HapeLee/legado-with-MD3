package io.legado.app.ui.book.readaloud

import io.legado.app.domain.model.PlaybackCapsuleSource
import io.legado.app.domain.model.PlaybackCapsuleState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * 「打开听书播放弹层」的全局请求通道。
 *
 * 播放页是 Activity 级 morph 浮层（见 [ReadAloudPlayerMorphHost]），不在导航栈上，
 * 阅读器的经典控制面板只能通过这条通道请求宿主把它拉起来。
 * 不设 replay：宿主始终挂在 Activity 上，请求发出时一定有收集方。
 */
object ReadAloudPlayerOverlayBus {

    private val _events = MutableSharedFlow<PlaybackCapsuleState>(extraBufferCapacity = 1)
    val events: SharedFlow<PlaybackCapsuleState> = _events

    fun request(state: PlaybackCapsuleState = PlaybackCapsuleState(source = PlaybackCapsuleSource.ReadAloud)) {
        _events.tryEmit(state)
    }
}
