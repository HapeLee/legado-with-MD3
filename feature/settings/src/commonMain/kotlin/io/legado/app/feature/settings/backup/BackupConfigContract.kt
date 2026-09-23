package io.legado.app.feature.settings.backup

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.settings.BackupSettings
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

// M5-9a：从 `:app` 的 `io.legado.app.ui.config.backupConfig` 迁来。
//
// 本片只迁**逻辑层**（Contract + ViewModel），页面本体（`BackupConfigScreen` 的 UI 部分）与
// RouteScreen 宿主壳仍留在 `:app` —— 沿用 M5-8a/8b 在 otherConfig 上用过的按层分片。
// （`:app` 的 `BackupConfigScreen.kt` 一个文件里同时有 RouteScreen 宿主壳与页面本体，
// 迁页面时要先把它拆开，属下一片。）
//
// ⚠️ **两处结构变更**（不是改名）：
//  ① `BackupConfigDialog.Loading(@StringRes titleRes: Int)` → `Loading(title: BackupConfigText)`；
//  ② `BackupConfigEffect.ShowMessage(@StringRes messageRes: Int, argument: String?)`
//     → `ShowMessage(message: BackupConfigText, argument: String? = null)`。
//  理由同 about/otherConfig：`@StringRes` 与资源 id 都是 Android 概念，进不了 commonMain；
//  契约改成枚举 + UI 侧查表（表在 `BackupConfigText.kt`），模块的公开契约因此零资源依赖。

@Stable
data class BackupConfigUiState(
    val settings: BackupSettings = BackupSettings(),
    val activeSheet: BackupConfigSheet? = null,
    val activeDialog: BackupConfigDialog? = null,
    val backupNames: ImmutableList<String> = persistentListOf(),
    val ignoreItems: ImmutableList<BackupIgnoreItem> = persistentListOf(),
    val backupIgnoreItems: ImmutableList<BackupIgnoreItem> = persistentListOf(),
    val dbIgnoreItems: ImmutableList<BackupIgnoreItem> = persistentListOf(),
    val backupDbIgnoreItems: ImmutableList<BackupIgnoreItem> = persistentListOf(),
)

@Stable
data class BackupIgnoreItem(
    val key: String,
    val title: String,
    val checked: Boolean,
)

sealed interface BackupConfigSheet {
    data object ChooseBackupPath : BackupConfigSheet
    data object ChooseBackupAndRun : BackupConfigSheet
    data object BackupOptions : BackupConfigSheet
    data object RestoreOptions : BackupConfigSheet
    data object RestoreFiles : BackupConfigSheet
    data object IgnoreRestoreItems : BackupConfigSheet
    data object IgnoreBackupItems : BackupConfigSheet
}

sealed interface BackupConfigDialog {
    data class WebDavAuth(
        val account: String,
        val password: String,
        val passwordVisible: Boolean = false,
    ) : BackupConfigDialog

    data class ConfirmLocalRestoreFallback(val error: String?) : BackupConfigDialog

    /** 加载中对话框的标题（迁移前是 `@StringRes Int`）。 */
    data class Loading(val title: BackupConfigText) : BackupConfigDialog
}

sealed interface BackupConfigIntent {
    data class SetWebDavUrl(val value: String) : BackupConfigIntent
    data class SetWebDavDir(val value: String) : BackupConfigIntent
    data class SetWebDavDeviceName(val value: String) : BackupConfigIntent
    data class SetSyncBookProgress(val value: Boolean) : BackupConfigIntent
    data class SetSyncBookProgressPlus(val value: Boolean) : BackupConfigIntent
    data class SetAutoCheckNewBackup(val value: Boolean) : BackupConfigIntent
    data class SetOnlyLatestBackup(val value: Boolean) : BackupConfigIntent
    data class SetBackupSyncMode(val value: String) : BackupConfigIntent
    data class OpenSheet(val sheet: BackupConfigSheet) : BackupConfigIntent
    data object DismissSheet : BackupConfigIntent
    data object OpenWebDavAuth : BackupConfigIntent
    data class EditWebDavAccount(val value: String) : BackupConfigIntent
    data class EditWebDavPassword(val value: String) : BackupConfigIntent
    data object TogglePasswordVisibility : BackupConfigIntent
    data object SaveWebDavAuth : BackupConfigIntent
    data object TestWebDav : BackupConfigIntent
    data object OpenIgnoreDialog : BackupConfigIntent
    data class ToggleIgnoreItem(val key: String, val value: Boolean) : BackupConfigIntent
    data object SaveIgnoreItems : BackupConfigIntent
    data object OpenBackupIgnoreDialog : BackupConfigIntent
    data class ToggleBackupIgnoreItem(val key: String, val value: Boolean) : BackupConfigIntent
    data object SaveBackupIgnoreItems : BackupConfigIntent
    data class ToggleDbIgnoreItem(val key: String, val value: Boolean) : BackupConfigIntent
    data object SaveDbIgnoreItems : BackupConfigIntent
    data class ToggleBackupDbIgnoreItem(val key: String, val value: Boolean) : BackupConfigIntent
    data object SaveBackupDbIgnoreItems : BackupConfigIntent
    data object DismissDialog : BackupConfigIntent
    data object SelectBackupDirectory : BackupConfigIntent
    data object SelectBackupAndRunDirectory : BackupConfigIntent
    data class BackupDirectorySelected(val path: String, val runBackup: Boolean) : BackupConfigIntent
    data class RequestBackup(val mode: String) : BackupConfigIntent
    data class PerformBackup(val path: String, val mode: String) : BackupConfigIntent
    data object RequestLocalRestore : BackupConfigIntent
    data class RestoreLocal(val uri: String) : BackupConfigIntent
    data object RequestNetworkRestore : BackupConfigIntent
    data class RestoreNetwork(val name: String) : BackupConfigIntent
    data object ConfirmLocalRestoreFallback : BackupConfigIntent
    data object RequestImportOldData : BackupConfigIntent
}

sealed interface BackupConfigEffect {
    data object LaunchBackupDirectoryPicker : BackupConfigEffect
    data object LaunchBackupAndRunDirectoryPicker : BackupConfigEffect
    data object LaunchRestoreFilePicker : BackupConfigEffect
    data object LaunchImportOldDataPicker : BackupConfigEffect

    /** 需要存储权限才能往该路径写备份（迁移前由宿主用 `PermissionsCompat` 申请）。 */
    data class RequestStoragePermission(val path: String, val mode: String) : BackupConfigEffect

    /** 一次性提示；`argument` 是可选格式化参数（迁移前 `context.getString(res, argument)`）。 */
    data class ShowMessage(
        val message: BackupConfigText,
        val argument: String? = null,
    ) : BackupConfigEffect
}
