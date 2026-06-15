package io.legado.app.ui.config.readConfig

import io.legado.app.constant.PreferKey
import io.legado.app.ui.config.prefDelegate

/**
 * TTS/朗读/音频相关配置
 *
 * 被 BaseReadAloudService、TTSReadAloudService、HttpReadAloudService 读取。
 */
object ReadTtsConfig {

    private const val defaultSpeechRate = 5

    val speechRatePlay: Int
        get() = if (ttsFollowSys) defaultSpeechRate else ttsSpeechRate

    var ttsEngine by prefDelegate<String?>(
        PreferKey.ttsEngine,
        null
    )

    var ttsFollowSys by prefDelegate(
        PreferKey.ttsFollowSys,
        true
    )

    var ttsSpeechRate by prefDelegate(
        PreferKey.ttsSpeechRate,
        5
    )

    var ttsTimer by prefDelegate(
        PreferKey.ttsTimer,
        0
    )

    var ignoreAudioFocus by prefDelegate(
        PreferKey.ignoreAudioFocus,
        false
    )

    var pauseReadAloudWhilePhoneCalls by prefDelegate(
        PreferKey.pauseReadAloudWhilePhoneCalls,
        false
    )

    var readAloudWakeLock by prefDelegate(
        PreferKey.readAloudWakeLock,
        false
    )

    var mediaButtonPerNext by prefDelegate(
        "mediaButtonPerNext",
        false
    )

    var readAloudByPage by prefDelegate(
        PreferKey.readAloudByPage,
        false
    )

    var systemMediaControlCompatibilityChange by prefDelegate(
        PreferKey.systemMediaControlCompatibilityChange,
        true
    )

    var streamReadAloudAudio by prefDelegate(
        PreferKey.streamReadAloudAudio,
        false
    )

    var contentSelectSpeakMod by prefDelegate(
        PreferKey.contentSelectSpeakMod,
        0
    )

    var audioPreDownloadNum by prefDelegate(
        PreferKey.audioPreDownloadNum,
        10
    )

    var audioCacheCleanTimeOrgin by prefDelegate(
        PreferKey.audioCacheCleanTime,
        10
    )

    val audioCacheCleanTime: Long
        get() = audioCacheCleanTimeOrgin * 60 * 1000L
}
