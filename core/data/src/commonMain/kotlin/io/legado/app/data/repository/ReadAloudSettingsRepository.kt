package io.legado.app.data.repository

import io.legado.app.constant.PreferKey
import io.legado.app.domain.gateway.ReadAloudSettingsGateway
import io.legado.app.domain.model.PlaybackTimer
import io.legado.app.domain.model.settings.ReadAloudSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ReadAloudSettingsRepository(
    private val store: PreferenceStore,
) : ReadAloudSettingsGateway {

    override val currentSettings: ReadAloudSettings
        get() = store.currentSnapshot().toReadAloudSettings()

    override val settings: Flow<ReadAloudSettings> = store.observeSnapshot()
        .map { it.toReadAloudSettings() }

    val preferences: Flow<ReadAloudSettings> = settings

    override suspend fun update(transform: (ReadAloudSettings) -> ReadAloudSettings) {
        store.atomicUpdateSettings(
            read = { it.toReadAloudSettings() },
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

internal fun Map<String, PreferenceValue>.toReadAloudSettings(): ReadAloudSettings = ReadAloudSettings(
    ttsEngine = compatString(PreferKey.ttsEngine),
    ttsParagraphInterval = compatValue(PreferKey.ttsParagraphInterval, 0),
    audioCacheCleanTime = compatValue(PreferKey.audioCacheCleanTime, 10),
    ignoreAudioFocus = compatValue(PreferKey.ignoreAudioFocus, false),
    mediaButtonOnExit = compatValue(PreferKey.mediaButtonOnExit, true),
    readAloudByMediaButton = compatValue(PreferKey.readAloudByMediaButton, false),
    pauseReadAloudWhilePhoneCalls =
        compatValue(PreferKey.pauseReadAloudWhilePhoneCalls, false),
    readAloudWakeLock = compatValue(PreferKey.readAloudWakeLock, false),
    showReadAloudCapsule = compatValue(PreferKey.showReadAloudCapsule, true),
    capsuleAutoCollapse = compatValue(PreferKey.capsuleAutoCollapse, true),
    capsuleOffsetX = compatValue(CAPSULE_OFFSET_X, 0f),
    capsuleOffsetY = compatValue(CAPSULE_OFFSET_Y, 0f),
    mediaButtonPerNext = compatValue(PreferKey.mediaButtonPerNext, false),
    readAloudByPage = compatValue(PreferKey.readAloudByPage, false),
    androidMediaControlEnabled = compatValue(PreferKey.readAloudAndroidMediaControl, false),
    systemMediaControlCompatibilityChange =
        compatValue(PreferKey.systemMediaControlCompatibilityChange, true),
    streamReadAloudAudio = compatValue(PreferKey.streamReadAloudAudio, false),
    ttsTimer = PlaybackTimer.normalize(compatValue(PreferKey.ttsTimer, 0)),
    finishCurrentChapterAfterTimer =
        compatValue(PreferKey.finishCurrentChapterAfterTimer, false),
    ttsFollowSys = compatValue(PreferKey.ttsFollowSys, true),
    ttsSpeechRate = compatValue(PreferKey.ttsSpeechRate, 5),
    speechAnalysisMode = compatValue(PreferKey.speechAnalysisMode, "rule"),
    useMultiSpeaker = compatValue(PreferKey.useMultiSpeaker, true),
    defaultInterface = compatValue(
        PreferKey.defaultReadAloudInterface,
        ReadAloudSettingsRepository.DEFAULT_INTERFACE_CLASSIC,
    ),
    contentSelectSpeakMode = compatValue(PreferKey.contentSelectSpeakMod, 0),
    audioPreDownloadNum = compatValue(PreferKey.audioPreDownloadNum, 10),
    ttsPreSynthesisConcurrency = compatValue(PreferKey.ttsPreSynthesisConcurrency, 3),
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
    CAPSULE_OFFSET_X to capsuleOffsetX,
    CAPSULE_OFFSET_Y to capsuleOffsetY,
    PreferKey.mediaButtonPerNext to mediaButtonPerNext,
    PreferKey.readAloudByPage to readAloudByPage,
    PreferKey.readAloudAndroidMediaControl to androidMediaControlEnabled,
    PreferKey.systemMediaControlCompatibilityChange to systemMediaControlCompatibilityChange,
    PreferKey.streamReadAloudAudio to streamReadAloudAudio,
    PreferKey.ttsTimer to ttsTimer,
    PreferKey.finishCurrentChapterAfterTimer to finishCurrentChapterAfterTimer,
    PreferKey.ttsFollowSys to ttsFollowSys,
    PreferKey.ttsSpeechRate to ttsSpeechRate,
    PreferKey.speechAnalysisMode to speechAnalysisMode,
    PreferKey.useMultiSpeaker to useMultiSpeaker,
    PreferKey.defaultReadAloudInterface to defaultInterface,
    PreferKey.contentSelectSpeakMod to contentSelectSpeakMode,
    PreferKey.audioPreDownloadNum to audioPreDownloadNum,
    PreferKey.ttsPreSynthesisConcurrency to ttsPreSynthesisConcurrency,
)

// 原 ReadAloudKeys 里两个无 PreferKey 对应项的硬编码 key
private const val CAPSULE_OFFSET_X = "read_aloud_capsule_offset_x"
private const val CAPSULE_OFFSET_Y = "read_aloud_capsule_offset_y"
