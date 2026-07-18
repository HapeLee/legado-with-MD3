package io.legado.app.data.repository

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.legado.app.constant.PreferKey
import io.legado.app.domain.model.PlaybackTimer
import io.legado.app.domain.gateway.ReadAloudSettingsGateway
import io.legado.app.domain.gateway.ReadAloudSettingsUpdate
import io.legado.app.domain.model.settings.ReadAloudSettings
import io.legado.app.help.config.AppConfigStore
import io.legado.app.help.config.compatDsValue
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
        settingsRepository.putBoolean(PreferKey.ignoreAudioFocus, value)
    }

    suspend fun setMediaButtonOnExit(value: Boolean) {
        settingsRepository.putBoolean(PreferKey.mediaButtonOnExit, value)
    }

    suspend fun setReadAloudByMediaButton(value: Boolean) {
        settingsRepository.putBoolean(PreferKey.readAloudByMediaButton, value)
    }

    suspend fun setPauseReadAloudWhilePhoneCalls(value: Boolean) {
        settingsRepository.putBoolean(PreferKey.pauseReadAloudWhilePhoneCalls, value)
    }

    suspend fun setReadAloudWakeLock(value: Boolean) {
        settingsRepository.putBoolean(PreferKey.readAloudWakeLock, value)
    }

    suspend fun setShowReadAloudCapsule(value: Boolean) {
        ReadConfig.showReadAloudCapsule = value
    }

    suspend fun setCapsulePosition(x: Float, y: Float) {
        settingsRepository.putFloat(Keys.CapsuleOffsetX.name, x)
        settingsRepository.putFloat(Keys.CapsuleOffsetY.name, y)
    }

    suspend fun resetCapsulePosition() = setCapsulePosition(0f, 0f)

    suspend fun setMediaButtonPerNext(value: Boolean) {
        settingsRepository.putBoolean(KEY_MEDIA_BUTTON_PER_NEXT, value)
    }

    suspend fun setReadAloudByPage(value: Boolean) {
        settingsRepository.putBoolean(PreferKey.readAloudByPage, value)
    }

    suspend fun setSystemMediaControlCompatibilityChange(value: Boolean) {
        settingsRepository.putBoolean(PreferKey.systemMediaControlCompatibilityChange, value)
    }

    suspend fun setStreamReadAloudAudio(value: Boolean) {
        settingsRepository.putBoolean(PreferKey.streamReadAloudAudio, value)
    }

    suspend fun setTtsTimer(value: Int) {
        val timer = PlaybackTimer.normalize(value)
        settingsRepository.putInt(PreferKey.ttsTimer, timer)
    }

    suspend fun setTtsFollowSys(value: Boolean) {
        settingsRepository.putBoolean(PreferKey.ttsFollowSys, value)
    }

    suspend fun setTtsSpeechRate(value: Int) {
        settingsRepository.putInt(PreferKey.ttsSpeechRate, value.coerceIn(0, 80))
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
            ignoreAudioFocus = compatDsValue(Keys.IgnoreAudioFocus, false),
            mediaButtonOnExit = compatDsValue(Keys.MediaButtonOnExit, true),
            readAloudByMediaButton = compatDsValue(Keys.ReadAloudByMediaButton, false),
            pauseReadAloudWhilePhoneCalls = compatDsValue(Keys.PauseReadAloudWhilePhoneCalls, false),
            readAloudWakeLock = compatDsValue(Keys.ReadAloudWakeLock, false),
            showReadAloudCapsule = compatDsValue(Keys.ShowReadAloudCapsule, true),
            capsuleOffsetX = compatDsValue(Keys.CapsuleOffsetX, 0f),
            capsuleOffsetY = compatDsValue(Keys.CapsuleOffsetY, 0f),
            mediaButtonPerNext = compatDsValue(Keys.MediaButtonPerNext, false),
            readAloudByPage = compatDsValue(Keys.ReadAloudByPage, false),
            systemMediaControlCompatibilityChange =
                compatDsValue(Keys.SystemMediaControlCompatibilityChange, true),
            streamReadAloudAudio = compatDsValue(Keys.StreamReadAloudAudio, false),
            ttsTimer = PlaybackTimer.normalize(compatDsValue(Keys.TtsTimer, 0)),
            ttsFollowSys = compatDsValue(Keys.TtsFollowSys, true),
            ttsSpeechRate = compatDsValue(Keys.TtsSpeechRate, 5),
            speechAnalysisMode = compatDsValue(Keys.SpeechAnalysisMode, "rule"),
            useMultiSpeaker = compatDsValue(Keys.UseMultiSpeaker, true),
            defaultInterface = compatDsValue(Keys.DefaultInterface, DEFAULT_INTERFACE_CLASSIC),
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
        val CapsuleOffsetX = floatPreferencesKey("read_aloud_capsule_offset_x")
        val CapsuleOffsetY = floatPreferencesKey("read_aloud_capsule_offset_y")
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
