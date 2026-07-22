package io.legado.app.ui.book.readaloud.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.constant.PreferKey
import io.legado.app.constant.ReadAloudBgMode
import io.legado.app.help.config.AppConfigStore
import io.legado.app.help.config.compatDsInt
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReadAloudPlayerViewModel(
    private val coordinator: ReadAloudPlayerCoordinator,
) : ViewModel() {

    val uiState = combine(
        coordinator.state,
        AppConfigStore.observeInt(PreferKey.readAloudPlayerBgMode),
    ) { source, bgMode ->
        toUiState(source, bgMode ?: ReadAloudBgMode.Blur)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = toUiState(coordinator.snapshot(), readBgMode()),
    )

    private val _effects = MutableSharedFlow<ReadAloudPlayerEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    fun onIntent(intent: ReadAloudPlayerIntent) {
        when (intent) {
            ReadAloudPlayerIntent.Refresh -> coordinator.refresh()
            ReadAloudPlayerIntent.TogglePause -> coordinator.togglePause()
            ReadAloudPlayerIntent.PreviousParagraph -> coordinator.previousParagraph()
            ReadAloudPlayerIntent.NextParagraph -> coordinator.nextParagraph()
            ReadAloudPlayerIntent.PreviousChapter -> coordinator.previousChapter()
            ReadAloudPlayerIntent.NextChapter -> coordinator.nextChapter()
            ReadAloudPlayerIntent.OpenSettings -> effect(ReadAloudPlayerEffect.ReturnToReaderSettings)
            ReadAloudPlayerIntent.SwitchToClassic -> effect(ReadAloudPlayerEffect.ReturnToClassic)
            ReadAloudPlayerIntent.OpenToc -> effect(ReadAloudPlayerEffect.OpenToc)
            is ReadAloudPlayerIntent.SetBgMode -> AppConfigStore.putInt(
                PreferKey.readAloudPlayerBgMode,
                intent.value,
            )
            is ReadAloudPlayerIntent.SetSpeed -> viewModelScope.launch {
                coordinator.setSpeed(intent.value)
            }
            is ReadAloudPlayerIntent.SetTimer -> coordinator.setTimer(intent.minutes)
            is ReadAloudPlayerIntent.SeekTo -> coordinator.seekTo(
                chapterPosition = intent.chapterPosition,
                chapterLength = uiState.value.chapterLength,
            )
        }
    }

    private fun toUiState(
        source: ReadAloudPlayerSourceState,
        bgMode: Int,
    ): ReadAloudPlayerUiState {
        val activeIndex = source.textLines.indexOfLast {
            it.chapterPosition <= source.chapterPosition
        }
        return ReadAloudPlayerUiState(
            bookUrl = source.bookUrl,
            bookName = source.bookName,
            author = source.author,
            coverPath = source.coverPath,
            sourceOrigin = source.sourceOrigin,
            chapterIndex = source.chapterIndex,
            chapterTitle = source.chapterTitle,
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
            speed = source.speed,
            timerMinutes = source.timerMinutes,
            bgMode = bgMode,
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
