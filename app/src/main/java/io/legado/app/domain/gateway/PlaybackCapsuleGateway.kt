package io.legado.app.domain.gateway

import io.legado.app.domain.model.PlaybackCapsuleSource
import io.legado.app.domain.model.PlaybackCapsuleState
import kotlinx.coroutines.flow.StateFlow

interface PlaybackCapsuleGateway {
    val state: StateFlow<PlaybackCapsuleState>
    fun setSessionAvailable(source: PlaybackCapsuleSource, available: Boolean)

    /** 已加载但尚未启动服务的有声书，作为可恢复的暂停会话。 */
    fun prepareAudioBook(bookUrl: String)
    fun clearPreparedAudioBook(bookUrl: String)
    fun togglePause(source: PlaybackCapsuleSource)
    fun stop(source: PlaybackCapsuleSource)
}
