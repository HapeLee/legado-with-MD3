package io.legado.app.ui.book.readaloud.player

import io.legado.app.ui.book.read.ReadBookIntent

/**
 * 把配置卡片的 [ReadBookIntent] 翻译成全局设置写入。
 *
 * 配置内容（`ReadAloudConfigContent`）的契约是 `ReadBookIntent`，因为它的主宿主是阅读器。
 * 听书播放界面是独立目的地、没有阅读器 ViewModel，所以这里把同一批意图落到
 * [ReadAloudPlayerViewModel.onConfigIntent]，两个宿主的设置语义完全一致。
 */
internal fun ReadAloudPlayerViewModel.applyReadBookConfigIntent(intent: ReadBookIntent) {
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

        // 其余意图在播放界面没有等价动作（缓存清理、数值选择器弹层等），静默忽略
        else -> Unit
    }
}
