package io.legado.app.data.repository

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.legado.app.constant.PreferKey
import io.legado.app.domain.gateway.ReadAloudSettingsGateway
import io.legado.app.domain.model.PlaybackTimer
import io.legado.app.domain.model.settings.ReadAloudSettings
import io.legado.app.help.config.AppConfigStore
import io.legado.app.help.config.compatDsString
import io.legado.app.help.config.compatDsValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ReadAloudSettingsRepository : ReadAloudSettingsGateway {

    override val currentSettings: ReadAloudSettings
        get() = AppConfigStore.preferences.toReadAloudSettings()

    override val settings: Flow<ReadAloudSettings> = AppConfigStore.preferencesFlow
        .map { preferences ->
            preferences.toReadAloudSettings()
        }
    val preferences: Flow<ReadAloudSettings> = settings

    override suspend fun update(transform: (ReadAloudSettings) -> ReadAloudSettings) {
        AppConfigStore.atomicUpdate(
            read = Preferences::toReadAloudSettings,
            toPrefMap = ReadAloudSettings::toPrefMap,
            transform = transform,
        )
    }

    companion object {
        const val DEFAULT_INTERFACE_CLASSIC = "classic"
        const val DEFAULT_INTERFACE_PLAYER = "player"
        val AVAILABLE_INTERFACES = setOf(DEFAULT_INTERFACE_CLASSIC, DEFAULT_INTERFACE_PLAYER)
    }
}

internal fun Preferences.toReadAloudSettings(): ReadAloudSettings = ReadAloudSettings(
    ttsEngine = compatDsString(PreferKey.ttsEngine),
    ttsParagraphInterval = compatDsValue(ReadAloudKeys.TtsParagraphInterval, 0),
    audioCacheCleanTime = compatDsValue(ReadAloudKeys.AudioCacheCleanTime, 10),
    ignoreAudioFocus = compatDsValue(ReadAloudKeys.IgnoreAudioFocus, false),
    mediaButtonOnExit = compatDsValue(ReadAloudKeys.MediaButtonOnExit, true),
    readAloudByMediaButton = compatDsValue(ReadAloudKeys.ReadAloudByMediaButton, false),
    pauseReadAloudWhilePhoneCalls =
        compatDsValue(ReadAloudKeys.PauseReadAloudWhilePhoneCalls, false),
    readAloudWakeLock = compatDsValue(ReadAloudKeys.ReadAloudWakeLock, false),
    showReadAloudCapsule = compatDsValue(ReadAloudKeys.ShowReadAloudCapsule, true),
    capsuleAutoCollapse = compatDsValue(ReadAloudKeys.CapsuleAutoCollapse, true),
    capsuleOffsetX = compatDsValue(ReadAloudKeys.CapsuleOffsetX, 0f),
    capsuleOffsetY = compatDsValue(ReadAloudKeys.CapsuleOffsetY, 0f),
    mediaButtonPerNext = compatDsValue(ReadAloudKeys.MediaButtonPerNext, false),
    readAloudByPage = compatDsValue(ReadAloudKeys.ReadAloudByPage, false),
    systemMediaControlCompatibilityChange =
        compatDsValue(ReadAloudKeys.SystemMediaControlCompatibilityChange, true),
    streamReadAloudAudio = compatDsValue(ReadAloudKeys.StreamReadAloudAudio, false),
    ttsTimer = PlaybackTimer.normalize(compatDsValue(ReadAloudKeys.TtsTimer, 0)),
    ttsFollowSys = compatDsValue(ReadAloudKeys.TtsFollowSys, true),
    ttsSpeechRate = compatDsValue(ReadAloudKeys.TtsSpeechRate, 5),
    speechAnalysisMode = compatDsValue(ReadAloudKeys.SpeechAnalysisMode, "rule"),
    useMultiSpeaker = compatDsValue(ReadAloudKeys.UseMultiSpeaker, true),
    defaultInterface = compatDsValue(
        ReadAloudKeys.DefaultInterface,
        ReadAloudSettingsRepository.DEFAULT_INTERFACE_CLASSIC,
    ),
    contentSelectSpeakMode = compatDsValue(ReadAloudKeys.ContentSelectSpeakMode, 0),
    audioPreDownloadNum = compatDsValue(ReadAloudKeys.AudioPreDownloadNum, 10),
    ttsPreSynthesisConcurrency = compatDsValue(ReadAloudKeys.PreSynthesisConcurrency, 3),
)

internal fun ReadAloudSettings.toPrefMap(): Map<String, Any?> = mapOf(
    PreferKey.ttsEngine to ttsEngine,
    PreferKey.ttsParagraphInterval to ttsParagraphInterval,
    PreferKey.audioCacheCleanTime to audioCacheCleanTime,
    PreferKey.ignoreAudioFocus to ignoreAudioFocus,
    PreferKey.mediaButtonOnExit to mediaButtonOnExit,
    PreferKey.readAloudByMediaButton to readAloudByMediaButton,
    PreferKey.pauseReadAloudWhilePhoneCalls to pauseReadAloudWhilePhoneCalls,
    PreferKey.readAloudWakeLock to readAloudWakeLock,
    PreferKey.showReadAloudCapsule to showReadAloudCapsule,
    PreferKey.capsuleAutoCollapse to capsuleAutoCollapse,
    ReadAloudKeys.CapsuleOffsetX.name to capsuleOffsetX,
    ReadAloudKeys.CapsuleOffsetY.name to capsuleOffsetY,
    MEDIA_BUTTON_PER_NEXT to mediaButtonPerNext,
    PreferKey.readAloudByPage to readAloudByPage,
    PreferKey.systemMediaControlCompatibilityChange to systemMediaControlCompatibilityChange,
    PreferKey.streamReadAloudAudio to streamReadAloudAudio,
    PreferKey.ttsTimer to ttsTimer,
    PreferKey.ttsFollowSys to ttsFollowSys,
    PreferKey.ttsSpeechRate to ttsSpeechRate,
    PreferKey.speechAnalysisMode to speechAnalysisMode,
    PreferKey.useMultiSpeaker to useMultiSpeaker,
    PreferKey.defaultReadAloudInterface to defaultInterface,
    PreferKey.contentSelectSpeakMod to contentSelectSpeakMode,
    PreferKey.audioPreDownloadNum to audioPreDownloadNum,
    PreferKey.ttsPreSynthesisConcurrency to ttsPreSynthesisConcurrency,
)

private const val MEDIA_BUTTON_PER_NEXT = "mediaButtonPerNext"

private object ReadAloudKeys {
    val TtsParagraphInterval = intPreferencesKey(PreferKey.ttsParagraphInterval)
    val AudioCacheCleanTime = intPreferencesKey(PreferKey.audioCacheCleanTime)
    val IgnoreAudioFocus = booleanPreferencesKey(PreferKey.ignoreAudioFocus)
    val MediaButtonOnExit = booleanPreferencesKey(PreferKey.mediaButtonOnExit)
    val ReadAloudByMediaButton = booleanPreferencesKey(PreferKey.readAloudByMediaButton)
    val PauseReadAloudWhilePhoneCalls =
        booleanPreferencesKey(PreferKey.pauseReadAloudWhilePhoneCalls)
    val ReadAloudWakeLock = booleanPreferencesKey(PreferKey.readAloudWakeLock)
    val ShowReadAloudCapsule = booleanPreferencesKey(PreferKey.showReadAloudCapsule)
    val CapsuleAutoCollapse = booleanPreferencesKey(PreferKey.capsuleAutoCollapse)
    val CapsuleOffsetX = floatPreferencesKey("read_aloud_capsule_offset_x")
    val CapsuleOffsetY = floatPreferencesKey("read_aloud_capsule_offset_y")
    val MediaButtonPerNext = booleanPreferencesKey(MEDIA_BUTTON_PER_NEXT)
    val ReadAloudByPage = booleanPreferencesKey(PreferKey.readAloudByPage)
    val SystemMediaControlCompatibilityChange =
        booleanPreferencesKey(PreferKey.systemMediaControlCompatibilityChange)
    val StreamReadAloudAudio = booleanPreferencesKey(PreferKey.streamReadAloudAudio)
    val TtsTimer = intPreferencesKey(PreferKey.ttsTimer)
    val TtsFollowSys = booleanPreferencesKey(PreferKey.ttsFollowSys)
    val TtsSpeechRate = intPreferencesKey(PreferKey.ttsSpeechRate)
    val SpeechAnalysisMode = stringPreferencesKey(PreferKey.speechAnalysisMode)
    val UseMultiSpeaker = booleanPreferencesKey(PreferKey.useMultiSpeaker)
    val DefaultInterface = stringPreferencesKey(PreferKey.defaultReadAloudInterface)
    val ContentSelectSpeakMode = intPreferencesKey(PreferKey.contentSelectSpeakMod)
    val AudioPreDownloadNum = intPreferencesKey(PreferKey.audioPreDownloadNum)
    val PreSynthesisConcurrency = intPreferencesKey(PreferKey.ttsPreSynthesisConcurrency)
}
