package io.legado.app.ui.book.readaloud.player

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class ReadAloudTextLineUi(
    val text: String,
    val chapterPosition: Int,
)

@Stable
data class ReadAloudPlayerUiState(
    val bookUrl: String = "",
    val bookName: String = "",
    val author: String = "",
    val coverPath: String? = null,
    val sourceOrigin: String? = null,
    val chapterIndex: Int = -1,
    val chapterTitle: String = "",
    val chapterText: String = "",
    val textLines: ImmutableList<ReadAloudTextLineUi> = persistentListOf(),
    val activeTextLine: Int = -1,
    val currentText: String = "",
    val nextText: String = "",
    val chapterPosition: Int = 0,
    val chapterLength: Int = 1,
    val engineName: String = "",
    val speakerName: String = "",
    val isPaused: Boolean = false,
    val speed: Int = 10,
    val timerMinutes: Int = 0,
    val bgMode: Int = 0,
)

sealed interface ReadAloudPlayerIntent {
    data object Refresh : ReadAloudPlayerIntent
    data object TogglePause : ReadAloudPlayerIntent
    data object PreviousChapter : ReadAloudPlayerIntent
    data object NextChapter : ReadAloudPlayerIntent
    data object PreviousParagraph : ReadAloudPlayerIntent
    data object NextParagraph : ReadAloudPlayerIntent
    data object OpenSettings : ReadAloudPlayerIntent
    data object SwitchToClassic : ReadAloudPlayerIntent
    data object OpenToc : ReadAloudPlayerIntent
    data class SetBgMode(val value: Int) : ReadAloudPlayerIntent
    data class SetSpeed(val value: Int) : ReadAloudPlayerIntent
    data class SetTimer(val minutes: Int) : ReadAloudPlayerIntent
    data class SeekTo(val chapterPosition: Int) : ReadAloudPlayerIntent
}

sealed interface ReadAloudPlayerEffect {
    data object OpenToc : ReadAloudPlayerEffect
    data object ReturnToReaderSettings : ReadAloudPlayerEffect
    data object ReturnToClassic : ReadAloudPlayerEffect
}
