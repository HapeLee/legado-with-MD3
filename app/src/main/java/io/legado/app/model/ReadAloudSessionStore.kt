package io.legado.app.model

import io.legado.app.domain.model.readaloud.ReadAloudPlaybackInfo
import io.legado.app.domain.model.readaloud.ReadAloudSessionState
import io.legado.app.domain.model.readaloud.ReadAloudSessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Process-local source of truth for the active read-aloud session. */
class ReadAloudSessionStore {

    private val _state = MutableStateFlow(ReadAloudSessionState())
    val state = _state.asStateFlow()

    fun setStatus(status: ReadAloudSessionStatus) {
        _state.update { it.copy(status = status) }
    }

    fun updatePlayback(playback: ReadAloudPlaybackInfo) {
        _state.update { it.copy(playback = playback) }
    }

    fun updateTimer(minutes: Int) {
        _state.update { it.copy(timerMinutes = minutes) }
    }

    /** 用户手动导航（翻页/跳章/拖动进度）导致页面脱离朗读位置。 */
    fun detachReadAloudFollow() {
        _state.update { it.copy(followReadAloudPosition = false) }
    }

    /** 回到朗读位置或新朗读会话开始时恢复跟随。 */
    fun restoreReadAloudFollow() {
        _state.update { it.copy(followReadAloudPosition = true) }
    }

    fun stop() {
        _state.value = ReadAloudSessionState()
    }
}
