package io.legado.app.domain.model.settings

data class ReadAloudSettings(
    val ignoreAudioFocus: Boolean = false,
    val mediaButtonOnExit: Boolean = true,
    val readAloudByMediaButton: Boolean = false,
    val pauseReadAloudWhilePhoneCalls: Boolean = false,
    val readAloudWakeLock: Boolean = false,
    val mediaButtonPerNext: Boolean = false,
    val readAloudByPage: Boolean = false,
    val systemMediaControlCompatibilityChange: Boolean = true,
    val streamReadAloudAudio: Boolean = false,
    val ttsTimer: Int = 0,
    val ttsFollowSys: Boolean = true,
    val ttsSpeechRate: Int = 5,
)
