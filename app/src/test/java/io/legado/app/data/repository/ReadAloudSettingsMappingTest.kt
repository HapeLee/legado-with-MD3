package io.legado.app.data.repository

import io.legado.app.constant.PreferKey
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.domain.model.settings.ReadAloudContentSplitMode
import io.legado.app.domain.model.settings.ReadAloudSettings
import io.legado.app.domain.model.settings.ReadAloudTimerMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadAloudSettingsMappingTest {

    @Test
    fun `朗读设置 29 键写映射逐字段对应`() {
        readAloudMappingSamples().forEach { settings ->
            assertEquals(settings.expectedPrefMap(), settings.toPrefMap())
        }
    }

    @Test
    fun `朗读设置 29 键读映射逐字段对应`() {
        readAloudMappingSamples().forEach { expected ->
            assertEquals(expected, expected.expectedPrefMap().toTestPreferences().toReadAloudSettings())
        }
    }

    @Test
    fun `胶囊坐标与 nullable 引擎通过真实原子路径单批写入`() {
        val values = captureAtomicUpdateValues(
            current = ReadAloudSettings(ttsEngine = "engine-old"),
            read = { it.toReadAloudSettings() },
            toPrefMap = ReadAloudSettings::toPrefMap,
            transform = {
                it.copy(
                    ttsEngine = null,
                    capsuleOffsetX = 12.5f,
                    capsuleOffsetY = -8.25f,
                )
            },
        )

        assertEquals(
            mapOf(
                PreferKey.ttsEngine to null,
                CAPSULE_OFFSET_X to 12.5f,
                CAPSULE_OFFSET_Y to -8.25f,
            ),
            values,
        )
    }

    @Test
    fun `安卓媒体控制默认关闭`() {
        val settings = emptyMap<String, Any?>().toTestPreferences().toReadAloudSettings()

        assertEquals(false, settings.androidMediaControlEnabled)
    }

    @Test
    fun `定时默认按时间且未开启`() {
        val settings = emptyMap<String, Any?>().toTestPreferences().toReadAloudSettings()

        assertEquals(ReadAloudTimerMode.Minute.storageValue, settings.timerMode)
        assertEquals(0, settings.timerChapters)
    }

    @Test
    fun `到点后读完本章默认关闭`() {
        val settings = emptyMap<String, Any?>().toTestPreferences().toReadAloudSettings()

        assertEquals(false, settings.finishCurrentChapterAfterTimer)
    }

    @Test
    fun `朗读分析推理级别默认关闭`() {
        val settings = emptyMap<String, Any?>().toTestPreferences().toReadAloudSettings()

        assertEquals(AiReasoningLevel.OFF.storageValue, settings.speechAnalysisReasoningLevel)
    }
}

private const val CAPSULE_OFFSET_X = "read_aloud_capsule_offset_x"
private const val CAPSULE_OFFSET_Y = "read_aloud_capsule_offset_y"
private const val MEDIA_BUTTON_PER_NEXT = "mediaButtonPerNext"

private fun readAloudMappingSamples(): List<ReadAloudSettings> {
    val base = ReadAloudSettings(
        ttsEngine = "engine-unique",
        ttsParagraphInterval = 11,
        audioCacheCleanTime = 22,
        capsuleOffsetX = 3.25f,
        capsuleOffsetY = -4.5f,
        ttsTimer = 33,
        ttsSpeechRate = 44,
        speechAnalysisMode = "ai",
        speechAnalysisReasoningLevel = "medium",
        defaultInterface = "player",
        contentSelectSpeakMode = 55,
        audioPreDownloadNum = 66,
        capsuleAutoCollapse = false,
        ttsPreSynthesisConcurrency = 7,
    )
    return listOf(
        base,
        base.copy(ignoreAudioFocus = true),
        base.copy(mediaButtonOnExit = false),
        base.copy(readAloudByMediaButton = true),
        base.copy(pauseReadAloudWhilePhoneCalls = true),
        base.copy(readAloudWakeLock = true),
        base.copy(keepReadAloudOnExit = true),
        base.copy(showReadAloudCapsule = false),
        base.copy(mediaButtonPerNext = true),
        base.copy(readAloudByPage = true),
        base.copy(
            contentSplitMode = ReadAloudContentSplitMode.Symbols.storageValue,
            contentSplitSymbols = setOf("，", "。"),
        ),
        base.copy(androidMediaControlEnabled = true),
        base.copy(systemMediaControlCompatibilityChange = false),
        base.copy(streamReadAloudAudio = true),
        base.copy(
            timerMode = ReadAloudTimerMode.Chapter.storageValue,
            timerChapters = 3,
        ),
        base.copy(finishCurrentChapterAfterTimer = true),
        base.copy(ttsFollowSys = false),
        base.copy(useMultiSpeaker = false),
    )
}

private fun ReadAloudSettings.expectedPrefMap(): Map<String, Any?> = mapOf(
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
    CAPSULE_OFFSET_X to capsuleOffsetX,
    CAPSULE_OFFSET_Y to capsuleOffsetY,
    MEDIA_BUTTON_PER_NEXT to mediaButtonPerNext,
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
