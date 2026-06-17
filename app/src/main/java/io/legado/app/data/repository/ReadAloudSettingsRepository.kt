package io.legado.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import io.legado.app.constant.PreferKey
import io.legado.app.ui.config.readConfig.ReadConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

data class ReadAloudPreferences(
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

class ReadAloudSettingsRepository(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {

    val preferences: Flow<ReadAloudPreferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences.toReadAloudPreferences()
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
        val timer = value.coerceIn(0, 180)
        ReadConfig.ttsTimer = timer
    }

    suspend fun saveTtsTimer(value: Int) {
        settingsRepository.putInt(PreferKey.ttsTimer, value.coerceIn(0, 180))
    }

    suspend fun setTtsFollowSys(value: Boolean) {
        ReadConfig.ttsFollowSys = value
    }

    suspend fun setTtsSpeechRate(value: Int) {
        ReadConfig.ttsSpeechRate = value.coerceIn(0, 80)
    }

    private fun Preferences.toReadAloudPreferences(): ReadAloudPreferences {
        return ReadAloudPreferences(
            ignoreAudioFocus = this[Keys.IgnoreAudioFocus] ?: false,
            mediaButtonOnExit = this[Keys.MediaButtonOnExit] ?: true,
            readAloudByMediaButton = this[Keys.ReadAloudByMediaButton] ?: false,
            pauseReadAloudWhilePhoneCalls = this[Keys.PauseReadAloudWhilePhoneCalls] ?: false,
            readAloudWakeLock = this[Keys.ReadAloudWakeLock] ?: false,
            mediaButtonPerNext = this[Keys.MediaButtonPerNext] ?: false,
            readAloudByPage = this[Keys.ReadAloudByPage] ?: false,
            systemMediaControlCompatibilityChange =
                this[Keys.SystemMediaControlCompatibilityChange] ?: true,
            streamReadAloudAudio = this[Keys.StreamReadAloudAudio] ?: false,
            ttsTimer = this[Keys.TtsTimer] ?: 0,
            ttsFollowSys = this[Keys.TtsFollowSys] ?: true,
            ttsSpeechRate = this[Keys.TtsSpeechRate] ?: 5,
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
