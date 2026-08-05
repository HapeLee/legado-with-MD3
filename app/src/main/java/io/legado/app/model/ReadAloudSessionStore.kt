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

    fun stop() {
        _state.value = ReadAloudSessionState()
    }
}
