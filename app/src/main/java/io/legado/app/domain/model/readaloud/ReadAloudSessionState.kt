package io.legado.app.domain.model.readaloud

enum class ReadAloudSessionStatus {
    Idle,
    Playing,
    Paused,
}

data class ReadAloudSessionState(
    val status: ReadAloudSessionStatus = ReadAloudSessionStatus.Idle,
    val playback: ReadAloudPlaybackInfo = ReadAloudPlaybackInfo(),
    val timerMinutes: Int = 0,
)
