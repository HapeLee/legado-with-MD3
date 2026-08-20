package io.legado.app.ui.book.audio

import androidx.compose.runtime.Stable
import io.legado.app.constant.ReadAloudBgMode
import io.legado.app.constant.Status
import io.legado.app.model.AudioPlay
import io.legado.app.ui.widget.components.player.PlayerChapterUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class AudioPlayUiState(
    val bookUrl: String = "",
    val bookName: String = "",
    val author: String = "",
    val coverPath: String? = null,
    val sourceOrigin: String? = null,
    val chapterIndex: Int = 0,
    val chapterTitle: String = "",
    val chapters: ImmutableList<PlayerChapterUi> = persistentListOf(),
    val status: Int = Status.STOP,
    val isLoading: Boolean = false,
    val position: Int = 0,
    val duration: Int = 0,
    val speed: Float = 1f,
    val timerMinutes: Int = 0,
    val playMode: AudioPlay.PlayMode = AudioPlay.PlayMode.LIST_END_STOP,
    val bgMode: Int = ReadAloudBgMode.Blur,
    val canLogin: Boolean = false,
    val wakeLockEnabled: Boolean = false,
    val mediaControlEnabled: Boolean = false,
    val canPrevious: Boolean = false,
    val canNext: Boolean = false,
    val openCredits: Int = 0,
    val closeCredits: Int = 0,
    val audioGain: Int = 0,
    val activeSheet: AudioPlaySheet? = null,
) {
    val isPlaying: Boolean get() = status == Status.PLAY
}

/** 播放器内的设置弹窗（由 UiState 管理，避免分散在 Screen 本地状态）。 */
sealed interface AudioPlaySheet {
    data object SkipCredits : AudioPlaySheet
    data object Gain : AudioPlaySheet
    data object Log : AudioPlaySheet
}

sealed interface AudioPlayIntent {
    data class Init(val bookUrl: String, val inBookshelf: Boolean) : AudioPlayIntent
    data object Refresh : AudioPlayIntent
    data object TogglePlay : AudioPlayIntent
    data object Stop : AudioPlayIntent
    data object PreviousChapter : AudioPlayIntent
    data object NextChapter : AudioPlayIntent
    data class SelectChapter(val index: Int) : AudioPlayIntent
    data class SeekTo(val positionMs: Int) : AudioPlayIntent
    data object ChangePlayMode : AudioPlayIntent
    data class SetSpeed(val value: Float) : AudioPlayIntent
    data class SetTimer(val minutes: Int) : AudioPlayIntent
    data class SetOpenCredits(val seconds: Int) : AudioPlayIntent
    data class SetCloseCredits(val seconds: Int) : AudioPlayIntent
    data class SetAudioGain(val gainMb: Int) : AudioPlayIntent
    data object CycleBgMode : AudioPlayIntent
    data class OpenSheet(val sheet: AudioPlaySheet) : AudioPlayIntent
    data object DismissSheet : AudioPlayIntent
    data object ChangeSource : AudioPlayIntent
    data object Login : AudioPlayIntent
    data object CopyPlayUrl : AudioPlayIntent
    data object EditSource : AudioPlayIntent
    data object ToggleWakeLock : AudioPlayIntent
    data object ToggleMediaControl : AudioPlayIntent
    data object SourceEdited : AudioPlayIntent
    data object BackPressed : AudioPlayIntent
}

sealed interface AudioPlayEffect {
    data object Finish : AudioPlayEffect
    data class ShowToast(val message: String) : AudioPlayEffect
    data class OpenChangeSource(val bookName: String, val author: String) : AudioPlayEffect
    data class OpenLogin(val sourceUrl: String) : AudioPlayEffect
    data object CopyPlayUrl : AudioPlayEffect
    data class OpenEditSource(val sourceUrl: String) : AudioPlayEffect
    data class OpenBookReader(val bookUrl: String) : AudioPlayEffect
}