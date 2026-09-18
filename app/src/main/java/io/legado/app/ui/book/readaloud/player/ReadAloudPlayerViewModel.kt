package io.legado.app.ui.book.readaloud.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.constant.PreferKey
import io.legado.app.constant.ReadAloudBgMode
import io.legado.app.domain.gateway.ReadAloudSettingsGateway
import io.legado.app.domain.gateway.ReadSettingsGateway
import io.legado.app.domain.model.readaloud.ReadAloudContentSplitSetting
import io.legado.app.domain.model.readaloud.ReadAloudSplitSymbol
import io.legado.app.domain.model.settings.ReadAloudSettings
import io.legado.app.domain.model.settings.ReadAloudTimerMode
import io.legado.app.domain.model.settings.ReadSettings
import io.legado.app.help.config.AppConfigStore
import io.legado.app.help.config.compatDsInt
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.widget.components.player.PlayerChapterUi
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReadAloudPlayerViewModel(
    private val coordinator: ReadAloudPlayerCoordinator,
    private val readAloudSettingsGateway: ReadAloudSettingsGateway,
    private val readSettingsGateway: ReadSettingsGateway,
) : ViewModel() {

    private val activeSheet = MutableStateFlow<ReadAloudPlayerSheet?>(null)

    /**
     * 朗读设置快照。
     *
     * 听书播放界面是独立目的地，不依赖阅读器 ViewModel，所以设置直接从全局设置源投影；
     * 阅读界面里的配置卡片仍用 `ReadBookUiState`（内容相同，只是宿主不同）。
     */
    val readAloudSettings = combine(
        readAloudSettingsGateway.settings,
        readSettingsGateway.settings,
    ) { aloud, read ->
        toReadAloudSettingsUiState(aloud, read)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = toReadAloudSettingsUiState(
            aloud = readAloudSettingsGateway.currentSettings,
            read = readSettingsGateway.currentSettings,
        ),
    )

    val uiState = combine(
        coordinator.state,
        AppConfigStore.observeInt(PreferKey.readAloudPlayerBgMode),
        activeSheet,
    ) { source, bgMode, sheet ->
        toUiState(source, bgMode ?: ReadAloudBgMode.Blur, sheet)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = toUiState(coordinator.snapshot(), readBgMode(), null),
    )

    private val _effects = MutableSharedFlow<ReadAloudPlayerEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    fun onIntent(intent: ReadAloudPlayerIntent) {
        when (intent) {
            ReadAloudPlayerIntent.Refresh -> coordinator.refresh()
            ReadAloudPlayerIntent.TogglePause -> coordinator.togglePause()
            ReadAloudPlayerIntent.StopReadAloud -> coordinator.stop()
            ReadAloudPlayerIntent.PreviousParagraph -> coordinator.previousParagraph()
            ReadAloudPlayerIntent.NextParagraph -> coordinator.nextParagraph()
            ReadAloudPlayerIntent.PreviousChapter -> coordinator.previousChapter()
            ReadAloudPlayerIntent.NextChapter -> coordinator.nextChapter()
            ReadAloudPlayerIntent.SwitchToClassic -> effect(ReadAloudPlayerEffect.ReturnToClassic)
            ReadAloudPlayerIntent.OpenSettings -> effect(ReadAloudPlayerEffect.ReturnToReaderSettings)
            ReadAloudPlayerIntent.CycleBgMode -> cycleBgMode()
            is ReadAloudPlayerIntent.SelectChapter -> coordinator.selectChapter(intent.index)
            is ReadAloudPlayerIntent.SetBgMode -> AppConfigStore.putInt(
                PreferKey.readAloudPlayerBgMode,
                intent.value,
            )
            is ReadAloudPlayerIntent.SetSpeed -> viewModelScope.launch {
                coordinator.setSpeed(intent.value)
            }
            is ReadAloudPlayerIntent.SetTimer -> viewModelScope.launch {
                coordinator.setTimer(intent.minutes)
            }
            is ReadAloudPlayerIntent.SetTimerMode -> viewModelScope.launch {
                coordinator.setTimerMode(ReadAloudTimerMode.fromStorage(intent.value))
            }

            is ReadAloudPlayerIntent.SetTimerChapters -> viewModelScope.launch {
                coordinator.setTimerChapters(intent.value)
            }
            is ReadAloudPlayerIntent.SetFinishCurrentChapterAfterTimer ->
                viewModelScope.launch {
                    coordinator.setFinishCurrentChapterAfterTimer(intent.value)
                }
            is ReadAloudPlayerIntent.OpenSheet -> activeSheet.value = intent.sheet
            ReadAloudPlayerIntent.DismissSheet -> activeSheet.value = null
            is ReadAloudPlayerIntent.SeekTo -> coordinator.seekTo(
                chapterPosition = intent.chapterPosition,
                chapterLength = uiState.value.chapterLength,
            )
        }
    }

    /** 听书页配置卡片的设置写入；与阅读界面共用同一份设置语义。 */
    fun onConfigIntent(
        option: ReadAloudConfigOption,
        value: String = "",
        selected: Boolean = false,
        intValue: Int = 0,
    ) {
        viewModelScope.launch {
            when (option) {
                ReadAloudConfigOption.DefaultInterface ->
                    readAloudSettingsGateway.update { it.copy(defaultInterface = value) }

                ReadAloudConfigOption.ShowCapsule ->
                    readAloudSettingsGateway.update { it.copy(showReadAloudCapsule = selected) }

                ReadAloudConfigOption.CapsuleAutoCollapse ->
                    readAloudSettingsGateway.update { it.copy(capsuleAutoCollapse = selected) }

                ReadAloudConfigOption.IgnoreAudioFocus ->
                    readAloudSettingsGateway.update { it.copy(ignoreAudioFocus = selected) }

                ReadAloudConfigOption.PauseOnPhoneCall -> readAloudSettingsGateway.update {
                    it.copy(pauseReadAloudWhilePhoneCalls = selected)
                }

                ReadAloudConfigOption.WakeLock ->
                    readAloudSettingsGateway.update { it.copy(readAloudWakeLock = selected) }

                ReadAloudConfigOption.KeepOnExit ->
                    readAloudSettingsGateway.update { it.copy(keepReadAloudOnExit = selected) }

                ReadAloudConfigOption.MediaButtonPerNext ->
                    readAloudSettingsGateway.update { it.copy(mediaButtonPerNext = selected) }

                ReadAloudConfigOption.AndroidMediaControl -> readAloudSettingsGateway.update {
                    it.copy(androidMediaControlEnabled = selected)
                }

                ReadAloudConfigOption.SystemMediaCompat -> readAloudSettingsGateway.update {
                    it.copy(systemMediaControlCompatibilityChange = selected)
                }

                ReadAloudConfigOption.StreamAudio ->
                    readAloudSettingsGateway.update { it.copy(streamReadAloudAudio = selected) }

                ReadAloudConfigOption.SpeechAnalysisMode ->
                    readAloudSettingsGateway.update { it.copy(speechAnalysisMode = value) }

                ReadAloudConfigOption.SpeechAnalysisReasoningLevel ->
                    readAloudSettingsGateway.update {
                        it.copy(speechAnalysisReasoningLevel = value)
                    }

                ReadAloudConfigOption.UseMultiSpeaker ->
                    readAloudSettingsGateway.update { it.copy(useMultiSpeaker = selected) }

                ReadAloudConfigOption.ContentSplit -> {
                    val (mode, symbols) = ReadAloudContentSplitSetting.decode(value)
                    readAloudSettingsGateway.update {
                        it.copy(
                            contentSplitMode = mode.storageValue,
                            contentSplitSymbols = ReadAloudSplitSymbol.storageValues(symbols),
                        )
                    }
                }

                ReadAloudConfigOption.PreDownloadNum ->
                    readSettingsGateway.update { it.copy(preDownloadNum = intValue) }

                ReadAloudConfigOption.PreSynthesisConcurrency -> readAloudSettingsGateway.update {
                    it.copy(ttsPreSynthesisConcurrency = intValue.coerceIn(1, 8))
                }

                ReadAloudConfigOption.ParagraphInterval -> readAloudSettingsGateway.update {
                    it.copy(ttsParagraphInterval = intValue)
                }

                ReadAloudConfigOption.AudioCacheCleanTime -> readAloudSettingsGateway.update {
                    it.copy(audioCacheCleanTime = intValue)
                }
            }
        }
    }

    private fun cycleBgMode() {
        val next = when (readBgMode()) {
            ReadAloudBgMode.Solid -> ReadAloudBgMode.Blur
            ReadAloudBgMode.Blur -> ReadAloudBgMode.FlowingLight
            ReadAloudBgMode.FlowingLight -> ReadAloudBgMode.Transparent
            else -> ReadAloudBgMode.Solid
        }
        AppConfigStore.putInt(PreferKey.readAloudPlayerBgMode, next)
    }

    private fun toUiState(
        source: ReadAloudPlayerSourceState,
        bgMode: Int,
        sheet: ReadAloudPlayerSheet?,
    ): ReadAloudPlayerUiState {
        val activeIndex = source.textLines.indexOfLast {
            it.chapterPosition <= source.chapterPosition
        }
        val chapters = source.chapters.map { chapter ->
            PlayerChapterUi(
                index = chapter.index,
                title = chapter.title,
                isVolume = chapter.isVolume,
                tocLevel = chapter.tocLevel,
            )
        }.toImmutableList()
        return ReadAloudPlayerUiState(
            bookUrl = source.bookUrl,
            bookName = source.bookName,
            author = source.author,
            coverPath = source.coverPath,
            sourceOrigin = source.sourceOrigin,
            chapterIndex = source.chapterIndex,
            chapterTitle = source.chapterTitle,
            chapters = chapters,
            chapterText = source.chapterText,
            textLines = source.textLines,
            activeTextLine = activeIndex,
            currentText = source.textLines.getOrNull(activeIndex)?.text ?: source.playbackText,
            nextText = source.textLines.getOrNull(activeIndex + 1)?.text.orEmpty(),
            chapterPosition = source.chapterPosition,
            chapterLength = source.chapterLength,
            engineName = source.engineName,
            speakerName = source.speakerName,
            isPaused = source.isPaused,
            readAloudRunning = BaseReadAloudService.isRun,
            speed = source.speed,
            timerMinutes = source.timerMinutes,
            timerMode = source.timerMode,
            timerChapters = source.timerChapters,
            finishCurrentChapterAfterTimer = source.finishCurrentChapterAfterTimer,
            bgMode = bgMode,
            activeSheet = sheet,
        )
    }

    private fun effect(value: ReadAloudPlayerEffect) {
        _effects.tryEmit(value)
    }

    private fun readBgMode(): Int {
        return AppConfigStore.preferences.compatDsInt(PreferKey.readAloudPlayerBgMode)
            ?: ReadAloudBgMode.Blur
    }

}

private fun toReadAloudSettingsUiState(
    aloud: ReadAloudSettings,
    read: ReadSettings,
): ReadAloudSettingsUiState = ReadAloudSettingsUiState(
    defaultReadAloudInterface = aloud.defaultInterface,
    showReadAloudCapsule = aloud.showReadAloudCapsule,
    capsuleAutoCollapse = aloud.capsuleAutoCollapse,
    readAloudIgnoreAudioFocus = aloud.ignoreAudioFocus,
    readAloudPauseOnPhoneCall = aloud.pauseReadAloudWhilePhoneCalls,
    readAloudWakeLock = aloud.readAloudWakeLock,
    readAloudKeepOnExit = aloud.keepReadAloudOnExit,
    readAloudMediaButtonPerNext = aloud.mediaButtonPerNext,
    readAloudAndroidMediaControl = aloud.androidMediaControlEnabled,
    readAloudSystemMediaCompat = aloud.systemMediaControlCompatibilityChange,
    readAloudStreamAudio = aloud.streamReadAloudAudio,
    speechAnalysisMode = aloud.speechAnalysisMode,
    speechAnalysisReasoningLevel = aloud.speechAnalysisReasoningLevel,
    useMultiSpeaker = aloud.useMultiSpeaker,
    readAloudContentSplitMode = aloud.contentSplitMode,
    readAloudContentSplitSymbols = aloud.contentSplitSymbols,
    preDownloadNum = read.preDownloadNum,
    preSynthesisConcurrency = aloud.ttsPreSynthesisConcurrency,
    readAloudParagraphInterval = aloud.ttsParagraphInterval,
    audioCacheCleanTime = aloud.audioCacheCleanTime,
)
