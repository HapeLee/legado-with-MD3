package io.legado.app.ui.book.read.sheet

import io.legado.app.ui.book.read.ReadBookUiState
import io.legado.app.ui.book.readaloud.player.ReadAloudSettingsUiState
import kotlinx.collections.immutable.toImmutableSet

/**
 * 把朗读设置快照投影成配置内容需要的 UiState 形态。
 *
 * 听书播放界面是独立目的地，拿不到阅读器 ViewModel；而配置内容只读这些字段。
 * 与其复制一份几乎相同的表单，不如把两者收敛到同一个入口 —— 配置内容只有一处实现，
 * 两个宿主各自提供自己的快照。未列出的字段保持 [ReadBookUiState] 默认值。
 */
fun ReadAloudSettingsUiState.asReadBookUiState(): ReadBookUiState = ReadBookUiState(
    defaultReadAloudInterface = defaultReadAloudInterface,
    showReadAloudCapsule = showReadAloudCapsule,
    capsuleAutoCollapse = capsuleAutoCollapse,
    readAloudIgnoreAudioFocus = readAloudIgnoreAudioFocus,
    readAloudPauseOnPhoneCall = readAloudPauseOnPhoneCall,
    readAloudWakeLock = readAloudWakeLock,
    readAloudKeepOnExit = readAloudKeepOnExit,
    readAloudMediaButtonPerNext = readAloudMediaButtonPerNext,
    readAloudAndroidMediaControl = readAloudAndroidMediaControl,
    readAloudSystemMediaCompat = readAloudSystemMediaCompat,
    readAloudStreamAudio = readAloudStreamAudio,
    speechAnalysisMode = speechAnalysisMode,
    speechAnalysisReasoningLevel = speechAnalysisReasoningLevel,
    useMultiSpeaker = useMultiSpeaker,
    readAloudContentSplitMode = readAloudContentSplitMode,
    readAloudContentSplitSymbols = readAloudContentSplitSymbols.toImmutableSet(),
    preDownloadNum = preDownloadNum,
    preSynthesisConcurrency = preSynthesisConcurrency,
    readAloudParagraphInterval = readAloudParagraphInterval,
    audioCacheCleanTime = audioCacheCleanTime,
)
