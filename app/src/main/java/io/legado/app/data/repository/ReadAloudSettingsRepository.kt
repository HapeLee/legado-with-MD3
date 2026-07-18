package io.legado.app.data.repository

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import io.legado.app.constant.PreferKey
import io.legado.app.domain.model.PlaybackTimer
import io.legado.app.domain.gateway.ReadAloudSettingsGateway
import io.legado.app.domain.gateway.ReadAloudSettingsUpdate
import io.legado.app.domain.model.settings.ReadAloudSettings
import io.legado.app.help.config.AppConfigStore
import io.legado.app.help.config.compatDsValue
import io.legado.app.ui.config.readConfig.ReadConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

typealias ReadAloudPreferences = ReadAloudSettings

class ReadAloudSettingsRepository(
    private val settingsRepository: SettingsRepository
) : ReadAloudSettingsGateway {

    override val currentSettings: ReadAloudSettings
        get() = AppConfigStore.preferences.toReadAloudPreferences()

    override val settings: Flow<ReadAloudSettings> = AppConfigStore.preferencesFlow
        .map { preferences ->
            preferences.toReadAloudPreferences()
        }
    val preferences: Flow<ReadAloudPreferences> = settings

    override suspend fun update(update: ReadAloudSettingsUpdate) {
        when (update) {
            is ReadAloudSettingsUpdate.IgnoreAudioFocus -> setIgnoreAudioFocus(update.value)
            is ReadAloudSettingsUpdate.MediaButtonOnExit -> setMediaButtonOnExit(update.value)
            is ReadAloudSettingsUpdate.ReadAloudByMediaButton -> setReadAloudByMediaButton(update.value)
            is ReadAloudSettingsUpdate.PauseWhilePhoneCalls -> setPauseReadAloudWhilePhoneCalls(update.value)
            is ReadAloudSettingsUpdate.ReadAloudWakeLock -> setReadAloudWakeLock(update.value)
            is ReadAloudSettingsUpdate.MediaButtonPerNext -> setMediaButtonPerNext(update.value)
            is ReadAloudSettingsUpdate.ReadAloudByPage -> setReadAloudByPage(update.value)
            is ReadAloudSettingsUpdate.SystemMediaControlCompatibility ->
                setSystemMediaControlCompatibilityChange(update.value)
            is ReadAloudSettingsUpdate.StreamAudio -> setStreamReadAloudAudio(update.value)
            is ReadAloudSettingsUpdate.Timer -> setTtsTimer(update.value)
            is ReadAloudSettingsUpdate.FollowSystem -> setTtsFollowSys(update.value)
            is ReadAloudSettingsUpdate.SpeechRate -> setTtsSpeechRate(update.value)
        }
    }

    suspend fun setIgnoreAudioFocus(value: Boolean) {
        ReadConfig.ignoreAudioFocus = value
    }

    suspend fun setMediaButtonOnExit(value: Boolean) {
        settingsRepository.putBoolean(PreferKey.mediaButtonOnExit, value)
    }

    suspend fun setReadAloudByMediaButton(value: Boolean) {
        settingsRepository.putBoolean(PreferKey.readAloudByMediaButton, value)
    }

    suspend fun setPauseReadAloudWhilePhoneCalls(value: Boolean) {
        ReadConfig.pauseReadAloudWhilePhoneCalls = value
    }

    suspend fun setReadAloudWakeLock(value: Boolean) {
        ReadConfig.readAloudWakeLock = value
    }

    suspend fun setMediaButtonPerNext(value: Boolean) {
        ReadConfig.mediaButtonPerNext = value
    }

    suspend fun setReadAloudByPage(value: Boolean) {
        ReadConfig.readAloudByPage = value
    }

    suspend fun setSystemMediaControlCompatibilityChange(value: Boolean) {
        ReadConfig.systemMediaControlCompatibilityChange = value
    }

    suspend fun setStreamReadAloudAudio(value: Boolean) {
        ReadConfig.streamReadAloudAudio = value
    }

    suspend fun setTtsTimer(value: Int) {
        val timer = PlaybackTimer.normalize(value)
        ReadConfig.ttsTimer = timer
    }

    suspend fun setTtsFollowSys(value: Boolean) {
        ReadConfig.ttsFollowSys = value
    }

    suspend fun setTtsSpeechRate(value: Int) {
        ReadConfig.ttsSpeechRate = value.coerceIn(0, 80)
    }

    private fun Preferences.toReadAloudPreferences(): ReadAloudPreferences {
        return ReadAloudPreferences(
            ignoreAudioFocus = compatDsValue(Keys.IgnoreAudioFocus, false),
            mediaButtonOnExit = compatDsValue(Keys.MediaButtonOnExit, true),
            readAloudByMediaButton = compatDsValue(Keys.ReadAloudByMediaButton, false),
            pauseReadAloudWhilePhoneCalls = compatDsValue(Keys.PauseReadAloudWhilePhoneCalls, false),
            readAloudWakeLock = compatDsValue(Keys.ReadAloudWakeLock, false),
            mediaButtonPerNext = compatDsValue(Keys.MediaButtonPerNext, false),
            readAloudByPage = compatDsValue(Keys.ReadAloudByPage, false),
            systemMediaControlCompatibilityChange =
                compatDsValue(Keys.SystemMediaControlCompatibilityChange, true),
            streamReadAloudAudio = compatDsValue(Keys.StreamReadAloudAudio, false),
            ttsTimer = PlaybackTimer.normalize(compatDsValue(Keys.TtsTimer, 0)),
            ttsFollowSys = compatDsValue(Keys.TtsFollowSys, true),
            ttsSpeechRate = compatDsValue(Keys.TtsSpeechRate, 5),
        )
    }

    private object Keys {
        val IgnoreAudioFocus = booleanPreferencesKey(PreferKey.ignoreAudioFocus)
        val MediaButtonOnExit = booleanPreferencesKey(PreferKey.mediaButtonOnExit)
        val ReadAloudByMediaButton = booleanPreferencesKey(PreferKey.readAloudByMediaButton)
        val PauseReadAloudWhilePhoneCalls =
            booleanPreferencesKey(PreferKey.pauseReadAloudWhilePhoneCalls)
        val ReadAloudWakeLock = booleanPreferencesKey(PreferKey.readAloudWakeLock)
        val MediaButtonPerNext = booleanPreferencesKey(KEY_MEDIA_BUTTON_PER_NEXT)
        val ReadAloudByPage = booleanPreferencesKey(PreferKey.readAloudByPage)
        val SystemMediaControlCompatibilityChange =
            booleanPreferencesKey(PreferKey.systemMediaControlCompatibilityChange)
        val StreamReadAloudAudio = booleanPreferencesKey(PreferKey.streamReadAloudAudio)
        val TtsTimer = androidx.datastore.preferences.core.intPreferencesKey(PreferKey.ttsTimer)
        val TtsFollowSys = booleanPreferencesKey(PreferKey.ttsFollowSys)
        val TtsSpeechRate = androidx.datastore.preferences.core.intPreferencesKey(
            PreferKey.ttsSpeechRate
        )
    }

    companion object {
        const val KEY_MEDIA_BUTTON_PER_NEXT = "mediaButtonPerNext"
    }
}
