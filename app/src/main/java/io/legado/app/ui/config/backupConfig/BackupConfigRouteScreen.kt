package io.legado.app.ui.config.backupConfig

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.feature.settings.backup.BackupConfigEffect
import io.legado.app.feature.settings.backup.BackupConfigIntent
import io.legado.app.feature.settings.backup.BackupConfigScreen
import io.legado.app.feature.settings.backup.BackupConfigViewModel
import io.legado.app.feature.settings.backup.localizedText
import io.legado.app.help.storage.ImportOldData
import io.legado.app.lib.permission.Permissions
import io.legado.app.lib.permission.PermissionsCompat
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.takePersistablePermissionSafely
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

// M5-9b：本文件是 `BackupConfigScreen.kt` 拆出来的**宿主壳那一半** —— 原文件里同时放着
// 宿主壳与 5 个 UI composable，本片把后者搬进 `:feature:settings/backup/`，壳留在这里
// （顺手把文件名改成与函数同名，与 `OtherConfigRouteScreen` / `AiProviderEditRouteScreen`
// 等宿主壳的命名一致）。
//
// 壳里保留的全是**平台动作**，共享层做不了：
//   · 4 个 `rememberLauncherForActivityResult`（选备份目录 ×2 / 选恢复文件 / 选旧版数据）；
//   · `takePersistablePermissionSafely` + `isContentScheme`（SAF 目录的持久授权与路径归一）；
//   · `ImportOldData.importUri`（旧版本数据导入）；
//   · `PermissionsCompat.Builder()` 申请存储权限，授权回调里再发 `PerformBackup`。
// 与其余页同形：**契约发 Effect、宿主执行平台动作、再把结果发回 Intent。**

@Composable
fun BackupConfigRouteScreen(
    onBackClick: () -> Unit,
    viewModel: BackupConfigViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val snackbarHostState = remember { SnackbarHostState() }
    val selectBackupPathLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        uri.takePersistablePermissionSafely(context)
        val path = if (uri.isContentScheme()) uri.toString() else uri.path.orEmpty()
        viewModel.onIntent(BackupConfigIntent.BackupDirectorySelected(path, runBackup = false))
    }
    val backupAndSelectLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        uri.takePersistablePermissionSafely(context)
        val path = if (uri.isContentScheme()) uri.toString() else uri.path.orEmpty()
        viewModel.onIntent(BackupConfigIntent.BackupDirectorySelected(path, runBackup = true))
    }
    val restoreFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.onIntent(BackupConfigIntent.RestoreLocal(it.toString())) } }
    val importOldLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { ImportOldData.importUri(context, it) } }

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                BackupConfigEffect.LaunchBackupDirectoryPicker -> selectBackupPathLauncher.launch(null)
                BackupConfigEffect.LaunchBackupAndRunDirectoryPicker ->
                    backupAndSelectLauncher.launch(null)
                BackupConfigEffect.LaunchRestoreFilePicker ->
                    restoreFileLauncher.launch(arrayOf("application/zip"))
                BackupConfigEffect.LaunchImportOldDataPicker -> importOldLauncher.launch(arrayOf("*/*"))
                is BackupConfigEffect.RequestStoragePermission -> {
                    PermissionsCompat.Builder()
                        .addPermissions(*Permissions.Group.STORAGE)
                        .rationale(R.string.tip_perm_request_storage)
                        .onGranted {
                            viewModel.onIntent(
                                BackupConfigIntent.PerformBackup(effect.path, effect.mode)
                            )
                        }
                        .request()
                }
                is BackupConfigEffect.ShowMessage -> {
                    // M5-9a：契约不再携带 `@StringRes Int`（那是 Android 概念，进不了共享层）
                    // ⇒ 枚举 + 查表。`localizedText(argument)` 对应迁移前的
                    // `context.getString(res, argument)` / `context.getString(res)` 两个重载，
                    // 且它是 suspend —— 这里本来就在 LaunchedEffect 里。
                    snackbarHostState.showSnackbar(effect.message.localizedText(effect.argument))
                }
            }
        }
    }

    BackupConfigScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick,
        snackbarHostState = snackbarHostState,
    )
}
