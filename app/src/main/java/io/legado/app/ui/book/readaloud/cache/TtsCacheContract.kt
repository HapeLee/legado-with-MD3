package io.legado.app.ui.book.readaloud.cache

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

enum class TtsCacheTab { Files, Logs }

@Stable
data class TtsCacheUiState(
    val loading: Boolean = true,
    val files: ImmutableList<TtsCacheFileUi> = persistentListOf(),
    val totalSizeBytes: Long = 0,
    val logs: ImmutableList<TtsLogEntryUi> = persistentListOf(),
    val selectedTab: TtsCacheTab = TtsCacheTab.Files,
    val activeDialog: TtsCacheDialog? = null,
    val detailTitle: String = "",
    val detailContent: String = "",
    val showDetail: Boolean = false,
)

@Stable
data class TtsCacheFileUi(
    val name: String,
    val text: String,
    val sizeBytes: Long,
    val lastModified: Long,
)

@Stable
data class TtsLogEntryUi(
    val timestamp: Long,
    val message: String,
    val hasError: Boolean,
    val fullContent: String,
)

sealed interface TtsCacheIntent {
    data object LoadCache : TtsCacheIntent
    data object LoadLogs : TtsCacheIntent
    data class SelectTab(val tab: TtsCacheTab) : TtsCacheIntent
    data class DeleteFile(val name: String) : TtsCacheIntent
    data object ClearAll : TtsCacheIntent
    data object ShowClearAllDialog : TtsCacheIntent
    data object DismissDialog : TtsCacheIntent
    data class ShowFileDetail(
        val name: String,
        val text: String,
        val sizeBytes: Long,
        val lastModified: Long
    ) : TtsCacheIntent

    data class ShowLogDetail(val timestamp: Long, val fullContent: String) : TtsCacheIntent
    data object DismissDetail : TtsCacheIntent
}

sealed interface TtsCacheEffect {
    data class ShowToast(val message: String) : TtsCacheEffect
}

sealed interface TtsCacheDialog {
    data object ClearAll : TtsCacheDialog
}
