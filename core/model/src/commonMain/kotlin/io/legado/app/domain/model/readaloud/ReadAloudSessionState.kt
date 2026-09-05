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
    /** 朗读位置是否跟随当前显示页；用户手动翻页/跳章后脱离，回到朗读位置或新会话时恢复。 */
    val followReadAloudPosition: Boolean = true,
)
