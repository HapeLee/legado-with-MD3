package io.legado.app.ui.book.readaloud.player

import io.legado.app.ui.book.read.ReadBookIntent

internal enum class ReadAloudPlayerConfigHostAction {
    OpenTtsEnginesAndVoices,
    OpenTtsCache,
    OpenBookVoiceCasting,
    OpenSystemTtsSettings,
    OpenPreDownloadNumPicker,
    OpenPreSynthesisConcurrencyPicker,
    OpenParagraphIntervalPicker,
    OpenCacheCleanTimePicker,
}

/**
 * 为 `ReadAloudConfigContent` 发出的每一个导航选项，返回对应的播放器宿主操作。
 *
 * 共享内容使用的是 [ReadBookIntent]，而播放器是一个 Activity 级别的浮层，
 * 没有阅读器的 ViewModel。将这个桥接逻辑保持为纯函数，
 * 可以让这些点击事件被转发出去，而不是落到设置适配器里。
 */
internal fun ReadBookIntent.toReadAloudPlayerConfigHostAction():
        ReadAloudPlayerConfigHostAction? =
    when (this) {
        ReadBookIntent.OpenTtsEnginesAndVoices ->
            ReadAloudPlayerConfigHostAction.OpenTtsEnginesAndVoices

        ReadBookIntent.OpenTtsCache -> ReadAloudPlayerConfigHostAction.OpenTtsCache
        ReadBookIntent.OpenBookVoiceCasting ->
            ReadAloudPlayerConfigHostAction.OpenBookVoiceCasting

        ReadBookIntent.OpenSystemTtsSettings ->
            ReadAloudPlayerConfigHostAction.OpenSystemTtsSettings

        ReadBookIntent.OpenPreDownloadNumPicker ->
            ReadAloudPlayerConfigHostAction.OpenPreDownloadNumPicker

        ReadBookIntent.OpenPreSynthesisConcurrencyPicker ->
            ReadAloudPlayerConfigHostAction.OpenPreSynthesisConcurrencyPicker

        ReadBookIntent.OpenParagraphIntervalPicker ->
            ReadAloudPlayerConfigHostAction.OpenParagraphIntervalPicker

        ReadBookIntent.OpenCacheCleanTimePicker ->
            ReadAloudPlayerConfigHostAction.OpenCacheCleanTimePicker

        else -> null
    }

/**
 * 把配置卡片的 [ReadBookIntent] 分发为全局设置写入、播放器命令或宿主动作。
 *
 * 配置内容（`ReadAloudConfigContent`）的契约是 `ReadBookIntent`，因为它的主宿主是阅读器。
 * 听书播放弹层是全局浮层、没有阅读器 ViewModel，所以这里把同一批意图落到
 * [ReadAloudPlayerViewModel.onConfigIntent] 或播放器宿主，两个宿主的设置语义完全一致。
 */
internal fun ReadAloudPlayerViewModel.applyReadBookConfigIntent(
    intent: ReadBookIntent,
    onHostAction: (ReadAloudPlayerConfigHostAction) -> Unit,
) {
    intent.toReadAloudPlayerConfigHostAction()?.let {
        onHostAction(it)
        return
    }
    when (intent) {
        is ReadBookIntent.SetDefaultReadAloudInterface ->
            onConfigIntent(ReadAloudConfigOption.DefaultInterface, value = intent.value)

        is ReadBookIntent.SetShowReadAloudCapsule ->
            onConfigIntent(ReadAloudConfigOption.ShowCapsule, selected = intent.value)

        is ReadBookIntent.SetCapsuleAutoCollapse ->
            onConfigIntent(ReadAloudConfigOption.CapsuleAutoCollapse, selected = intent.value)

        is ReadBookIntent.SetReadAloudIgnoreAudioFocus ->
            onConfigIntent(ReadAloudConfigOption.IgnoreAudioFocus, selected = intent.value)

        is ReadBookIntent.SetReadAloudPauseOnPhoneCall ->
            onConfigIntent(ReadAloudConfigOption.PauseOnPhoneCall, selected = intent.value)

        is ReadBookIntent.SetReadAloudWakeLock ->
            onConfigIntent(ReadAloudConfigOption.WakeLock, selected = intent.value)

        is ReadBookIntent.SetReadAloudKeepOnExit ->
            onConfigIntent(ReadAloudConfigOption.KeepOnExit, selected = intent.value)

        is ReadBookIntent.SetReadAloudMediaButtonPerNext ->
            onConfigIntent(ReadAloudConfigOption.MediaButtonPerNext, selected = intent.value)

        is ReadBookIntent.SetReadAloudAndroidMediaControl ->
            onConfigIntent(ReadAloudConfigOption.AndroidMediaControl, selected = intent.value)

        is ReadBookIntent.SetReadAloudSystemMediaCompat ->
            onConfigIntent(ReadAloudConfigOption.SystemMediaCompat, selected = intent.value)

        is ReadBookIntent.SetReadAloudStreamAudio ->
            onConfigIntent(ReadAloudConfigOption.StreamAudio, selected = intent.value)

        is ReadBookIntent.SetSpeechAnalysisMode ->
            onConfigIntent(ReadAloudConfigOption.SpeechAnalysisMode, value = intent.value)

        is ReadBookIntent.SetSpeechAnalysisReasoningLevel ->
            onConfigIntent(ReadAloudConfigOption.SpeechAnalysisReasoningLevel, value = intent.value)

        is ReadBookIntent.SetUseMultiSpeaker ->
            onConfigIntent(ReadAloudConfigOption.UseMultiSpeaker, selected = intent.value)

        is ReadBookIntent.SetMultiRoleCast ->
            onConfigIntent(ReadAloudConfigOption.MultiRoleCast, selected = intent.value)

        is ReadBookIntent.SetBgmAssign ->
            onConfigIntent(ReadAloudConfigOption.BgmAssign, selected = intent.value)

        is ReadBookIntent.SetReadAloudContentSplitMode ->
            onConfigIntent(ReadAloudConfigOption.ContentSplit, value = intent.value)

        is ReadBookIntent.ApplyPreDownloadNum ->
            onConfigIntent(ReadAloudConfigOption.PreDownloadNum, intValue = intent.value)

        is ReadBookIntent.ApplyPreSynthesisConcurrency ->
            onConfigIntent(ReadAloudConfigOption.PreSynthesisConcurrency, intValue = intent.value)

        is ReadBookIntent.ApplyParagraphInterval ->
            onConfigIntent(ReadAloudConfigOption.ParagraphInterval, intValue = intent.value)

        is ReadBookIntent.ApplyAudioCacheCleanTime ->
            onConfigIntent(ReadAloudConfigOption.AudioCacheCleanTime, intValue = intent.value)

        ReadBookIntent.ResetReadAloudCapsulePosition -> resetCapsulePosition()
        ReadBookIntent.ClearTtsCache -> clearTtsCache()
        else -> Unit
    }
}
