package io.legado.app.data.repository

import io.legado.app.constant.PreferKey
import io.legado.app.domain.model.settings.ReadAloudSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadAloudSettingsMappingTest {

    @Test
    fun `朗读设置 27 键写映射逐字段对应`() {
        readAloudMappingSamples().forEach { settings ->
            assertEquals(settings.expectedPrefMap(), settings.toPrefMap())
        }
    }

    @Test
    fun `朗读设置 27 键读映射逐字段对应`() {
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
    fun `定时到点后读完本章默认关闭`() {
        val settings = emptyMap<String, Any?>().toTestPreferences().toReadAloudSettings()

        assertEquals(false, settings.finishCurrentChapterAfterTimer)
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
        base.copy(showReadAloudCapsule = false),
        base.copy(mediaButtonPerNext = true),
        base.copy(readAloudByPage = true),
        base.copy(androidMediaControlEnabled = true),
        base.copy(systemMediaControlCompatibilityChange = false),
        base.copy(streamReadAloudAudio = true),
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
    PreferKey.showReadAloudCapsule to showReadAloudCapsule,
    PreferKey.capsuleAutoCollapse to capsuleAutoCollapse,
    CAPSULE_OFFSET_X to capsuleOffsetX,
    CAPSULE_OFFSET_Y to capsuleOffsetY,
    MEDIA_BUTTON_PER_NEXT to mediaButtonPerNext,
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
