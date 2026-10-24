package io.legado.app.ui.book.video

import androidx.compose.runtime.Stable

@Stable
data class VideoPlayerUiState(
    val bookName: String = "",
    val bookAuthor: String = "",
    val chapterTitle: String = "",
    val chapterIndex: Int = 0,
    val chapterCount: Int = 0,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val position: Long = 0L,
    val duration: Long = 0L,
    val bufferedPosition: Long = 0L,
)

sealed interface VideoPlayerIntent {
    data object PlayPause : VideoPlayerIntent
    data object Pause : VideoPlayerIntent
    data class SeekTo(val positionMs: Long) : VideoPlayerIntent
    data object PreviousChapter : VideoPlayerIntent
    data object NextChapter : VideoPlayerIntent
    data object Retry : VideoPlayerIntent
}

sealed interface VideoPlayerEffect {
    data class ShowToast(val message: String) : VideoPlayerEffect
}
