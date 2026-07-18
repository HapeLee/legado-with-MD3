package io.legado.app.domain.gateway

import io.legado.app.domain.model.settings.ReadAloudSettings
import kotlinx.coroutines.flow.Flow

interface ReadAloudSettingsGateway {
    val currentSettings: ReadAloudSettings
    val settings: Flow<ReadAloudSettings>
    suspend fun update(update: ReadAloudSettingsUpdate)
}

sealed interface ReadAloudSettingsUpdate {
    data class TtsEngine(val value: String?) : ReadAloudSettingsUpdate
    data class ParagraphInterval(val value: Int) : ReadAloudSettingsUpdate
    data class AudioCacheCleanTime(val value: Int) : ReadAloudSettingsUpdate
    data class IgnoreAudioFocus(val value: Boolean) : ReadAloudSettingsUpdate
    data class MediaButtonOnExit(val value: Boolean) : ReadAloudSettingsUpdate
    data class ReadAloudByMediaButton(val value: Boolean) : ReadAloudSettingsUpdate
    data class PauseWhilePhoneCalls(val value: Boolean) : ReadAloudSettingsUpdate
    data class ReadAloudWakeLock(val value: Boolean) : ReadAloudSettingsUpdate
    data class MediaButtonPerNext(val value: Boolean) : ReadAloudSettingsUpdate
    data class ReadAloudByPage(val value: Boolean) : ReadAloudSettingsUpdate
    data class SystemMediaControlCompatibility(val value: Boolean) : ReadAloudSettingsUpdate
    data class StreamAudio(val value: Boolean) : ReadAloudSettingsUpdate
    data class Timer(val value: Int) : ReadAloudSettingsUpdate
    data class FollowSystem(val value: Boolean) : ReadAloudSettingsUpdate
    data class SpeechRate(val value: Int) : ReadAloudSettingsUpdate
}
