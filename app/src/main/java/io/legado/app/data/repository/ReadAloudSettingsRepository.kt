package io.legado.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.legado.app.constant.PreferKey
import io.legado.app.data.local.preferences.LocalPreferencesKeys
import io.legado.app.data.local.preferences.LocalPreferencesRepository
import io.legado.app.domain.model.PlaybackTimer
import io.legado.app.ui.config.readConfig.ReadConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.io.IOException

data class ReadAloudPreferences(
    val ignoreAudioFocus: Boolean = false,
    val mediaButtonOnExit: Boolean = true,
    val readAloudByMediaButton: Boolean = false,
    val pauseReadAloudWhilePhoneCalls: Boolean = false,
    val readAloudWakeLock: Boolean = false,
    val showReadAloudCapsule: Boolean = true,
    val capsuleOffsetX: Float = 0f,
    val capsuleOffsetY: Float = 0f,
    val mediaButtonPerNext: Boolean = false,
    val readAloudByPage: Boolean = false,
    val systemMediaControlCompatibilityChange: Boolean = true,
    val streamReadAloudAudio: Boolean = false,
    val ttsTimer: Int = 0,
    val ttsFollowSys: Boolean = true,
    val ttsSpeechRate: Int = 5,
    val speechAnalysisMode: String = "rule",
    val useMultiSpeaker: Boolean = true,
    val defaultInterface: String = ReadAloudSettingsRepository.DEFAULT_INTERFACE_CLASSIC,
)

class ReadAloudSettingsRepository(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val localPreferencesRepository: LocalPreferencesRepository,
) {

    private val globalPreferences = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences -> preferences.toReadAloudPreferences() }

    val preferences: Flow<ReadAloudPreferences> = combine(
        globalPreferences,
        localPreferencesRepository.getPreference(LocalPreferencesKeys.READ_ALOUD_CAPSULE_OFFSET_X, 0f),
        localPreferencesRepository.getPreference(LocalPreferencesKeys.READ_ALOUD_CAPSULE_OFFSET_Y, 0f),
    ) { preferences, capsuleOffsetX, capsuleOffsetY ->
        preferences.copy(capsuleOffsetX = capsuleOffsetX, capsuleOffsetY = capsuleOffsetY)
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

    suspend fun setShowReadAloudCapsule(value: Boolean) {
        ReadConfig.showReadAloudCapsule = value
    }

    suspend fun setCapsulePosition(x: Float, y: Float) {
        localPreferencesRepository.updatePreferences {
            this[LocalPreferencesKeys.READ_ALOUD_CAPSULE_OFFSET_X] = x
            this[LocalPreferencesKeys.READ_ALOUD_CAPSULE_OFFSET_Y] = y
        }
    }

    suspend fun resetCapsulePosition() = setCapsulePosition(0f, 0f)

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

    suspend fun setSpeechAnalysisMode(value: String) {
        ReadConfig.speechAnalysisMode = value
    }

    suspend fun setUseMultiSpeaker(value: Boolean) {
        ReadConfig.useMultiSpeaker = value
    }

    suspend fun setDefaultInterface(value: String) {
        settingsRepository.putString(
            PreferKey.defaultReadAloudInterface,
            value.takeIf { it in AVAILABLE_INTERFACES } ?: DEFAULT_INTERFACE_CLASSIC,
        )
    }

    private fun Preferences.toReadAloudPreferences(): ReadAloudPreferences {
        return ReadAloudPreferences(
            ignoreAudioFocus = this[Keys.IgnoreAudioFocus] ?: false,
            mediaButtonOnExit = this[Keys.MediaButtonOnExit] ?: true,
            readAloudByMediaButton = this[Keys.ReadAloudByMediaButton] ?: false,
            pauseReadAloudWhilePhoneCalls = this[Keys.PauseReadAloudWhilePhoneCalls] ?: false,
            readAloudWakeLock = this[Keys.ReadAloudWakeLock] ?: false,
            showReadAloudCapsule = this[Keys.ShowReadAloudCapsule] ?: true,
            mediaButtonPerNext = this[Keys.MediaButtonPerNext] ?: false,
            readAloudByPage = this[Keys.ReadAloudByPage] ?: false,
            systemMediaControlCompatibilityChange =
                this[Keys.SystemMediaControlCompatibilityChange] ?: true,
            streamReadAloudAudio = this[Keys.StreamReadAloudAudio] ?: false,
            ttsTimer = PlaybackTimer.normalize(this[Keys.TtsTimer] ?: 0),
            ttsFollowSys = this[Keys.TtsFollowSys] ?: true,
            ttsSpeechRate = this[Keys.TtsSpeechRate] ?: 5,
            speechAnalysisMode = this[Keys.SpeechAnalysisMode] ?: "rule",
            useMultiSpeaker = this[Keys.UseMultiSpeaker] ?: true,
            defaultInterface = this[Keys.DefaultInterface] ?: DEFAULT_INTERFACE_CLASSIC,
        )
    }

    private object Keys {
        val IgnoreAudioFocus = booleanPreferencesKey(PreferKey.ignoreAudioFocus)
        val MediaButtonOnExit = booleanPreferencesKey(PreferKey.mediaButtonOnExit)
        val ReadAloudByMediaButton = booleanPreferencesKey(PreferKey.readAloudByMediaButton)
        val PauseReadAloudWhilePhoneCalls =
            booleanPreferencesKey(PreferKey.pauseReadAloudWhilePhoneCalls)
        val ReadAloudWakeLock = booleanPreferencesKey(PreferKey.readAloudWakeLock)
        val ShowReadAloudCapsule = booleanPreferencesKey(PreferKey.showReadAloudCapsule)
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
        val SpeechAnalysisMode = stringPreferencesKey(PreferKey.speechAnalysisMode)
        val UseMultiSpeaker = booleanPreferencesKey(PreferKey.useMultiSpeaker)
        val DefaultInterface = stringPreferencesKey(PreferKey.defaultReadAloudInterface)
    }

    companion object {
        const val KEY_MEDIA_BUTTON_PER_NEXT = "mediaButtonPerNext"
        const val DEFAULT_INTERFACE_CLASSIC = "classic"
        const val DEFAULT_INTERFACE_PLAYER = "player"
        val AVAILABLE_INTERFACES = setOf(DEFAULT_INTERFACE_CLASSIC, DEFAULT_INTERFACE_PLAYER)
    }
}
