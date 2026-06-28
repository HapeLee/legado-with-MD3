package io.legado.app.ui.main.home

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import io.legado.app.data.entities.Book

@Stable
data class HomeUiState(
    val totalReadBooks: Int = 0,
    val totalReadTimeMillis: Long = 0L,
    val todayReadTimeMillis: Long = 0L,
    val dailyGoalMinutes: Int = DEFAULT_DAILY_READING_GOAL_MINUTES,
    val recentBook: HomeRecentBookUi? = null,
    val latestBackup: HomeBackupUi? = null,
    val isBackupLoading: Boolean = true,
    val isBackupActionRunning: Boolean = false,
    val activeDialog: HomeDialog? = null,
    val activeSheet: HomeSheet? = null,
)

@Stable
data class HomeRecentBookUi(
    val bookUrl: String?,
    val name: String,
    val author: String,
    val origin: String?,
    val coverPath: String?,
    val chapterTitle: String?,
    val chapterProgress: Float?,
)

@Stable
data class HomeBackupUi(
    val name: String,
    val lastModify: Long,
)

sealed interface HomeIntent {
    data object RecentBookClick : HomeIntent
    data object ReadingGoalClick : HomeIntent
    data class UpdateReadingGoal(val minutes: Int) : HomeIntent
    data object BackupClick : HomeIntent
    data class BackupDestinationSelected(val destination: HomeBackupDestination) : HomeIntent
    data class BackupDirectorySelected(
        val destination: HomeBackupDestination,
        val path: String,
    ) : HomeIntent

    data object RestoreClick : HomeIntent
    data object RestoreFromLocal : HomeIntent
    data object RestoreFromNetwork : HomeIntent
    data class RestoreLocalFileSelected(val uri: String) : HomeIntent
    data object ConfirmRestore : HomeIntent
    data object BackupSettingsClick : HomeIntent
    data object DismissDialog : HomeIntent
    data object DismissSheet : HomeIntent
}

sealed interface HomeEffect {
    data class OpenBook(val book: Book) : HomeEffect
    data object OpenBackupSettings : HomeEffect
    data class SelectBackupDirectory(
        val destination: HomeBackupDestination,
    ) : HomeEffect

    data class RequestBackupStoragePermission(
        val destination: HomeBackupDestination,
        val path: String,
    ) : HomeEffect

    data object SelectRestoreFile : HomeEffect
    data class ShowMessage(
        @param:StringRes val messageRes: Int,
        val detail: String? = null,
    ) : HomeEffect
}

@Stable
sealed interface HomeDialog {
    data class SetReadingGoal(val currentMinutes: Int) : HomeDialog
    data class ConfirmRestore(val backupName: String) : HomeDialog
}

@Stable
sealed interface HomeSheet {
    data object BackupOptions : HomeSheet
    data object RestoreOptions : HomeSheet
}

enum class HomeBackupDestination(val mode: String) {
    Local("local"),
    WebDav("webdav"),
    LocalAndWebDav("both"),
}

const val DEFAULT_DAILY_READING_GOAL_MINUTES = 30
const val MAX_DAILY_READING_GOAL_MINUTES = 24 * 60
