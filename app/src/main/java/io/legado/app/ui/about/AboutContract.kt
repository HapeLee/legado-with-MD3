package io.legado.app.ui.about

import androidx.compose.runtime.Stable
import io.legado.app.help.update.AppUpdate
import io.legado.app.utils.FileDoc

@Stable
data class AboutUiState(
    val sheet: AboutSheet = AboutSheet.None,
    val dialog: AboutDialog? = null,
    val crashLogFiles: List<FileDoc> = emptyList(),
)

sealed interface AboutSheet {
    data object None : AboutSheet
    data class Markdown(val title: String, val content: String) : AboutSheet
    data object CrashLogs : AboutSheet
    data class Update(
        val updateInfo: AppUpdate.UpdateInfo,
        val mode: UpdateMode = UpdateMode.UPDATE,
    ) : AboutSheet
}

enum class UpdateMode { UPDATE, VIEW_LOG }

sealed interface AboutDialog {
    data object CheckingUpdate : AboutDialog
}

sealed interface AboutIntent {
    data object DismissSheet : AboutIntent
    data object DismissDialog : AboutIntent
    data object CheckUpdate : AboutIntent
    data class ShowMdFile(val title: String, val fileName: String) : AboutIntent
    data object ShowCrashLogs : AboutIntent
    data object SaveLog : AboutIntent
    data object CreateHeapDump : AboutIntent
    data class OpenUrl(val url: String) : AboutIntent
    data object ClearCrashLogs : AboutIntent
    data class ReadCrashFile(val fileDoc: FileDoc) : AboutIntent
    data object StartDownload : AboutIntent
}

sealed interface AboutEffect {
    data class OpenUrl(val url: String) : AboutEffect
    data class ShowToast(val message: String) : AboutEffect
    data class StartDownload(val url: String, val fileName: String) : AboutEffect
}
