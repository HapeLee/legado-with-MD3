package io.legado.app.data.repository

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.legado.app.constant.PreferKey
import io.legado.app.domain.gateway.ReadAloudSettingsGateway
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.domain.model.PlaybackTimer
import io.legado.app.domain.model.settings.ReadAloudContentSplitMode
import io.legado.app.domain.model.settings.ReadAloudSettings
import io.legado.app.domain.model.settings.ReadAloudTimerMode
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

    /** 悬浮胶囊位置；两个分量同批写入，避免只落一个导致胶囊跳位。 */
    suspend fun putCapsulePosition(x: Float, y: Float) {
        AppConfigStore.putAllAndAwait(
            mapOf(
                ReadAloudKeys.CapsuleOffsetX.name to x,
                ReadAloudKeys.CapsuleOffsetY.name to y,
            )
        )
    }

    /**
     * 应用内容划分方式与配套标点集合。
     *
     * 两者在同一次写入里提交：划分方式与标点始终一起选择，
     * 迁移标记也必须与划分方式同批落盘，否则旧版「按页朗读」开关会在下次读取时
     * 反过来覆盖用户刚选的划分方式。
     */
    suspend fun setContentSplit(mode: ReadAloudContentSplitMode, symbols: Set<String>) {
        AppConfigStore.putAllAndAwait(
            mapOf(
                PreferKey.readAloudContentSplitMode to mode.storageValue,
                PreferKey.readAloudContentSplitSymbols to symbols,
                CONTENT_SPLIT_MIGRATION_KEY to true,
            )
        )
    }

    companion object {
        const val DEFAULT_INTERFACE_CLASSIC = "classic"
        const val DEFAULT_INTERFACE_PLAYER = "player"
        val AVAILABLE_INTERFACES = setOf(DEFAULT_INTERFACE_CLASSIC, DEFAULT_INTERFACE_PLAYER)

        /** 一次性迁移标记：把旧的「按页朗读」布尔值搬进「内容划分方式」。 */
        const val CONTENT_SPLIT_MIGRATION_KEY = "readAloudContentSplitMigrated"
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
    keepReadAloudOnExit = compatDsValue(ReadAloudKeys.KeepReadAloudOnExit, false),
    showReadAloudCapsule = compatDsValue(ReadAloudKeys.ShowReadAloudCapsule, true),
    capsuleAutoCollapse = compatDsValue(ReadAloudKeys.CapsuleAutoCollapse, true),
    capsuleOffsetX = compatDsValue(ReadAloudKeys.CapsuleOffsetX, 0f),
    capsuleOffsetY = compatDsValue(ReadAloudKeys.CapsuleOffsetY, 0f),
    mediaButtonPerNext = compatDsValue(ReadAloudKeys.MediaButtonPerNext, false),
    readAloudByPage = compatDsValue(ReadAloudKeys.ReadAloudByPage, false),
    contentSplitMode = compatContentSplitMode(),
    contentSplitSymbols = compatContentSplitSymbols(),
    androidMediaControlEnabled = compatDsValue(ReadAloudKeys.AndroidMediaControlEnabled, false),
    systemMediaControlCompatibilityChange =
        compatDsValue(ReadAloudKeys.SystemMediaControlCompatibilityChange, true),
    streamReadAloudAudio = compatDsValue(ReadAloudKeys.StreamReadAloudAudio, false),
    ttsTimer = PlaybackTimer.normalize(compatDsValue(ReadAloudKeys.TtsTimer, 0)),
    finishCurrentChapterAfterTimer =
        compatDsValue(ReadAloudKeys.FinishCurrentChapterAfterTimer, false),
    timerMode = compatDsValue(
        ReadAloudKeys.TimerMode,
        ReadAloudTimerMode.Minute.storageValue,
    ),
    timerChapters = PlaybackTimer.normalizeChapters(
        compatDsValue(ReadAloudKeys.TimerChapters, 0)
    ),
    ttsFollowSys = compatDsValue(ReadAloudKeys.TtsFollowSys, true),
    ttsSpeechRate = compatDsValue(ReadAloudKeys.TtsSpeechRate, 5),
    speechAnalysisMode = compatDsValue(ReadAloudKeys.SpeechAnalysisMode, "rule"),
    speechAnalysisReasoningLevel = compatDsValue(
        ReadAloudKeys.SpeechAnalysisReasoningLevel,
        AiReasoningLevel.OFF.storageValue,
    ),
    useMultiSpeaker = compatDsValue(ReadAloudKeys.UseMultiSpeaker, true),
    defaultInterface = compatDsValue(
        ReadAloudKeys.DefaultInterface,
        ReadAloudSettingsRepository.DEFAULT_INTERFACE_CLASSIC,
    ),
    contentSelectSpeakMode = compatDsValue(ReadAloudKeys.ContentSelectSpeakMode, 0),
    audioPreDownloadNum = compatDsValue(ReadAloudKeys.AudioPreDownloadNum, 10),
    ttsPreSynthesisConcurrency = compatDsValue(ReadAloudKeys.PreSynthesisConcurrency, 3),
)

/**
 * 读取内容划分方式，并一次性把旧的「按页朗读」开关迁移进来。
 *
 * 迁移标记保证只搬一次：否则用户把划分方式显式改回「整段」后，下一次读取又会被旧布尔值覆盖。
 * 显式选择过划分方式的用户（新 key 已存在）不参与迁移。
 */
private fun Preferences.compatContentSplitMode(): String {
    val stored = compatDsString(PreferKey.readAloudContentSplitMode)
    if (stored != null) return stored
    if (compatDsValue(ReadAloudKeys.ReadAloudContentSplitMigrated, false)) {
        return ReadAloudContentSplitMode.Default.storageValue
    }
    return if (compatDsValue(ReadAloudKeys.ReadAloudByPage, false)) {
        ReadAloudContentSplitMode.Page.storageValue
    } else {
        ReadAloudContentSplitMode.Default.storageValue
    }
}

private fun Preferences.compatContentSplitSymbols(): Set<String> =
    compatDsValue(ReadAloudKeys.ReadAloudContentSplitSymbols, emptySet())

internal fun ReadAloudSettings.toPrefMap(): Map<String, Any?> = mapOf(
    PreferKey.ttsEngine to ttsEngine,
    PreferKey.ttsParagraphInterval to ttsParagraphInterval,
    PreferKey.audioCacheCleanTime to audioCacheCleanTime,
    PreferKey.ignoreAudioFocus to ignoreAudioFocus,
    PreferKey.mediaButtonOnExit to mediaButtonOnExit,
    PreferKey.readAloudByMediaButton to readAloudByMediaButton,
    PreferKey.pauseReadAloudWhilePhoneCalls to pauseReadAloudWhilePhoneCalls,
    PreferKey.readAloudWakeLock to readAloudWakeLock,
    PreferKey.keepReadAloudOnExit to keepReadAloudOnExit,
    PreferKey.showReadAloudCapsule to showReadAloudCapsule,
    PreferKey.capsuleAutoCollapse to capsuleAutoCollapse,
    ReadAloudKeys.CapsuleOffsetX.name to capsuleOffsetX,
    ReadAloudKeys.CapsuleOffsetY.name to capsuleOffsetY,
    PreferKey.mediaButtonPerNext to mediaButtonPerNext,
    PreferKey.readAloudByPage to readAloudByPage,
    PreferKey.readAloudContentSplitMode to contentSplitMode,
    PreferKey.readAloudContentSplitSymbols to contentSplitSymbols,
    PreferKey.readAloudAndroidMediaControl to androidMediaControlEnabled,
    PreferKey.systemMediaControlCompatibilityChange to systemMediaControlCompatibilityChange,
    PreferKey.streamReadAloudAudio to streamReadAloudAudio,
    PreferKey.ttsTimer to ttsTimer,
    PreferKey.finishCurrentChapterAfterTimer to finishCurrentChapterAfterTimer,
    PreferKey.readAloudTimerMode to timerMode,
    PreferKey.readAloudTimerChapters to timerChapters,
    PreferKey.ttsFollowSys to ttsFollowSys,
    PreferKey.ttsSpeechRate to ttsSpeechRate,
    PreferKey.speechAnalysisMode to speechAnalysisMode,
    PreferKey.speechAnalysisReasoningLevel to speechAnalysisReasoningLevel,
    PreferKey.useMultiSpeaker to useMultiSpeaker,
    PreferKey.defaultReadAloudInterface to defaultInterface,
    PreferKey.contentSelectSpeakMod to contentSelectSpeakMode,
    PreferKey.audioPreDownloadNum to audioPreDownloadNum,
    PreferKey.ttsPreSynthesisConcurrency to ttsPreSynthesisConcurrency,
)

private object ReadAloudKeys {
    val TtsParagraphInterval = intPreferencesKey(PreferKey.ttsParagraphInterval)
    val AudioCacheCleanTime = intPreferencesKey(PreferKey.audioCacheCleanTime)
    val IgnoreAudioFocus = booleanPreferencesKey(PreferKey.ignoreAudioFocus)
    val MediaButtonOnExit = booleanPreferencesKey(PreferKey.mediaButtonOnExit)
    val ReadAloudByMediaButton = booleanPreferencesKey(PreferKey.readAloudByMediaButton)
    val PauseReadAloudWhilePhoneCalls =
        booleanPreferencesKey(PreferKey.pauseReadAloudWhilePhoneCalls)
    val ReadAloudWakeLock = booleanPreferencesKey(PreferKey.readAloudWakeLock)
    val KeepReadAloudOnExit = booleanPreferencesKey(PreferKey.keepReadAloudOnExit)
    val ShowReadAloudCapsule = booleanPreferencesKey(PreferKey.showReadAloudCapsule)
    val CapsuleAutoCollapse = booleanPreferencesKey(PreferKey.capsuleAutoCollapse)
    val CapsuleOffsetX = floatPreferencesKey("read_aloud_capsule_offset_x")
    val CapsuleOffsetY = floatPreferencesKey("read_aloud_capsule_offset_y")
    val MediaButtonPerNext = booleanPreferencesKey(PreferKey.mediaButtonPerNext)
    val ReadAloudByPage = booleanPreferencesKey(PreferKey.readAloudByPage)
    val ReadAloudContentSplitMode = stringPreferencesKey(PreferKey.readAloudContentSplitMode)
    val ReadAloudContentSplitSymbols =
        stringSetPreferencesKey(PreferKey.readAloudContentSplitSymbols)

    /** 一次性迁移标记：把旧的「按页朗读」布尔值搬进「内容划分方式」。 */
    val ReadAloudContentSplitMigrated =
        booleanPreferencesKey(ReadAloudSettingsRepository.CONTENT_SPLIT_MIGRATION_KEY)
    val AndroidMediaControlEnabled =
        booleanPreferencesKey(PreferKey.readAloudAndroidMediaControl)
    val SystemMediaControlCompatibilityChange =
        booleanPreferencesKey(PreferKey.systemMediaControlCompatibilityChange)
    val StreamReadAloudAudio = booleanPreferencesKey(PreferKey.streamReadAloudAudio)
    val TtsTimer = intPreferencesKey(PreferKey.ttsTimer)
    val FinishCurrentChapterAfterTimer =
        booleanPreferencesKey(PreferKey.finishCurrentChapterAfterTimer)
    val TimerMode = stringPreferencesKey(PreferKey.readAloudTimerMode)
    val TimerChapters = intPreferencesKey(PreferKey.readAloudTimerChapters)
    val TtsFollowSys = booleanPreferencesKey(PreferKey.ttsFollowSys)
    val TtsSpeechRate = intPreferencesKey(PreferKey.ttsSpeechRate)
    val SpeechAnalysisMode = stringPreferencesKey(PreferKey.speechAnalysisMode)
    val SpeechAnalysisReasoningLevel =
        stringPreferencesKey(PreferKey.speechAnalysisReasoningLevel)
    val UseMultiSpeaker = booleanPreferencesKey(PreferKey.useMultiSpeaker)
    val DefaultInterface = stringPreferencesKey(PreferKey.defaultReadAloudInterface)
    val ContentSelectSpeakMode = intPreferencesKey(PreferKey.contentSelectSpeakMod)
    val AudioPreDownloadNum = intPreferencesKey(PreferKey.audioPreDownloadNum)
    val PreSynthesisConcurrency = intPreferencesKey(PreferKey.ttsPreSynthesisConcurrency)
}
