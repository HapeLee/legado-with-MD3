package io.legado.app.ui.config.backupConfig

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.storage.Restore
import io.legado.app.lib.permission.Permissions
import io.legado.app.lib.permission.PermissionsCompat
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.button.TopbarNavigationButton
import io.legado.app.ui.widget.components.filePicker.FilePickerSheet
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.InputSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.takePersistablePermissionSafely
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupConfigScreen(
    onBackClick: () -> Unit,
    viewModel: BackupConfigViewModel = koinViewModel()
) {
    val context by rememberUpdatedState(LocalContext.current)
    val scope = rememberCoroutineScope()
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }

    var showWebDavAuthDialog by remember { mutableStateOf(false) }
    var showBackupIgnoreDialog by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var confirmDialogTitle by remember { mutableStateOf("") }
    var confirmDialogText by remember { mutableStateOf("") }
    var onConfirmAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    var tempAccount by remember { mutableStateOf("") }
    var tempPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var showBackupFilePicker by remember { mutableStateOf(false) }
    var showRestoreFilePicker by remember { mutableStateOf(false) }
    var showRestoreSheet by remember { mutableStateOf(false) }
    var backupNames by remember { mutableStateOf<List<String>>(emptyList()) }

    var showLoadingDialog by remember { mutableStateOf(false) }
    var loadingText by remember { mutableStateOf("") }

    val selectBackupPathLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            it.takePersistablePermissionSafely(context)
            if (it.isContentScheme()) {
                BackupConfig.backupPath = it.toString()
            } else {
                BackupConfig.backupPath = it.path
            }
        }
    }

    val backupAndSelectLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            it.takePersistablePermissionSafely(context)
            val path = if (it.isContentScheme()) {
                it.toString()
            } else {
                it.path ?: ""
            }
            BackupConfig.backupPath = path
            if (path.isNotEmpty()) {
                startBackup(path, context, viewModel, {
                    showLoadingDialog = false
                    scope.launch {
                        snackbarHostState.showSnackbar(context.getString(R.string.backup_success))
                    }
                }, { error ->
                    showLoadingDialog = false
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            context.getString(
                                R.string.backup_fail,
                                error
                            )
                        )
                    }
                })
            }
        }
    }

    val restoreFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            showLoadingDialog = true
            loadingText = "恢复中…"
            scope.launch {
                try {
                    Restore.restore(context, uri)
                    showLoadingDialog = false
                    snackbarHostState.showSnackbar("恢复成功")
                } catch (e: Exception) {
                    showLoadingDialog = false
                    snackbarHostState.showSnackbar("恢复出错: ${e.localizedMessage}")
                }
            }
        }
    }

    val importOldLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            io.legado.app.help.storage.ImportOldData.importUri(context, uri)
        }
    }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState
            )
        },
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = {
                    Text(stringResource(R.string.backup_restore))
                },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    TopbarNavigationButton(onClick = onBackClick)
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            SplicedColumnGroup(title = stringResource(R.string.web_dav_set)) {
                InputSettingItem(
                    title = stringResource(R.string.web_dav_url),
                    value = BackupConfig.webDavUrl,
                    defaultValue = "",
                    onConfirm = { BackupConfig.webDavUrl = it }
                )

                ClickableSettingItem(
                    title = stringResource(R.string.web_dav_account),
                    description = "设置 WebDAV 账号和密码",
                    onClick = {
                        tempAccount = viewModel.getWebDavAccount()
                        tempPassword = viewModel.getWebDavPassword()
                        showWebDavAuthDialog = true
                    }
                )

                InputSettingItem(
                    title = stringResource(R.string.sub_dir),
                    value = BackupConfig.webDavDir,
                    defaultValue = "legado",
                    onConfirm = { BackupConfig.webDavDir = it }
                )

                InputSettingItem(
                    title = stringResource(R.string.webdav_device_name),
                    value = BackupConfig.webDavDeviceName,
                    defaultValue = "",
                    onConfirm = { BackupConfig.webDavDeviceName = it }
                )

                ClickableSettingItem(
                    title = "测试 WebDav 配置",
                    description = "测试 WebDav 服务工作状态",
                    onClick = {
                        scope.launch {
                            showLoadingDialog = true
                            loadingText = "测试中…"
                            val success = viewModel.testWebDav()
                            showLoadingDialog = false
                            if (success) {
                                snackbarHostState.showSnackbar("WebDav 配置正确")
                            } else {
                                snackbarHostState.showSnackbar("WebDav 配置错误")
                            }
                        }
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.sync_book_progress_t),
                    description = stringResource(R.string.sync_book_progress_s),
                    checked = BackupConfig.syncBookProgress,
                    onCheckedChange = {
                        BackupConfig.syncBookProgress = it
                        if (!it) {
                            BackupConfig.syncBookProgressPlus = false
                        }
                    }
                )

                if (BackupConfig.syncBookProgress) {
                    SwitchSettingItem(
                        title = stringResource(R.string.sync_book_progress_plus_t),
                        description = stringResource(R.string.sync_book_progress_plus_s),
                        checked = BackupConfig.syncBookProgressPlus,
                        onCheckedChange = { BackupConfig.syncBookProgressPlus = it }
                    )
                }

                SwitchSettingItem(
                    title = stringResource(R.string.auto_check_new_backup_t),
                    description = stringResource(R.string.auto_check_new_backup_s),
                    checked = BackupConfig.autoCheckNewBackup,
                    onCheckedChange = { BackupConfig.autoCheckNewBackup = it }
                )
            }

            SplicedColumnGroup(title = stringResource(R.string.backup_restore)) {
                ClickableSettingItem(
                    title = stringResource(R.string.backup_path),
                    description = BackupConfig.backupPath
                        ?: stringResource(R.string.select_backup_path),
                    onClick = { showBackupFilePicker = true }
                )

                val backupText = stringResource(R.string.backup)

                ClickableSettingItem(
                    title = stringResource(R.string.backup),
                    description = stringResource(R.string.backup_summary),
                    onClick = {
                        val backupPath = BackupConfig.backupPath
                        if (backupPath.isNullOrEmpty()) {
                            showRestoreFilePicker = true
                        } else {
                            confirmDialogTitle = backupText
                            confirmDialogText = "确定要备份吗？"
                            onConfirmAction = {
                                if (backupPath.isContentScheme()) {
                                    startBackup(backupPath, context, viewModel, {
                                        showLoadingDialog = false
                                        scope.launch {
                                            snackbarHostState.showSnackbar(context.getString(R.string.backup_success))
                                        }
                                    }, { error ->
                                        showLoadingDialog = false
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                context.getString(
                                                    R.string.backup_fail,
                                                    error
                                                )
                                            )
                                        }
                                    })
                                } else {
                                    PermissionsCompat.Builder()
                                        .addPermissions(*Permissions.Group.STORAGE)
                                        .rationale(R.string.tip_perm_request_storage)
                                        .onGranted {
                                            startBackup(backupPath, context, viewModel, {
                                                showLoadingDialog = false
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        context.getString(
                                                            R.string.backup_success
                                                        )
                                                    )
                                                }
                                            }, { error ->
                                                showLoadingDialog = false
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        context.getString(
                                                            R.string.backup_fail,
                                                            error
                                                        )
                                                    )
                                                }
                                            })
                                        }
                                        .request()
                                }
                            }
                            showConfirmDialog = true
                        }
                    }
                )

                ClickableSettingItem(
                    title = stringResource(R.string.restore),
                    description = stringResource(R.string.restore_summary),
                    onClick = {
                        scope.launch {
                            showLoadingDialog = true
                            loadingText = "加载中"
                            try {
                                val names = viewModel.getBackupNames()
                                backupNames = names
                                showRestoreSheet = true
                            } catch (e: Exception) {
                                confirmDialogTitle = "恢复"
                                confirmDialogText =
                                    "WebDavError\n${e.localizedMessage}\n将从本地备份恢复。"
                                onConfirmAction = {
                                    restoreFileLauncher.launch(arrayOf("application/zip"))
                                }
                                showConfirmDialog = true
                            } finally {
                                showLoadingDialog = false
                            }
                        }
                    }
                )

                ClickableSettingItem(
                    title = stringResource(R.string.restore_ignore),
                    description = stringResource(R.string.restore_ignore_summary),
                    onClick = { showBackupIgnoreDialog = true }
                )

                ClickableSettingItem(
                    title = stringResource(R.string.menu_import_old_version),
                    description = stringResource(R.string.import_old_summary),
                    onClick = {
                        importOldLauncher.launch(arrayOf("*/*"))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.only_latest_backup_t),
                    description = stringResource(R.string.only_latest_backup_s),
                    checked = BackupConfig.onlyLatestBackup,
                    onCheckedChange = { BackupConfig.onlyLatestBackup = it }
                )
            }
        }
    }

    if (showWebDavAuthDialog) {
        AlertDialog(
            onDismissRequest = { showWebDavAuthDialog = false },
            title = { Text(stringResource(R.string.web_dav_account)) },
            text = {
                Column {
                    TextField(
                        value = tempAccount,
                        onValueChange = { tempAccount = it },
                        label = { Text("账号") }
                    )
                    Spacer(modifier = Modifier.padding(8.dp))
                    TextField(
                        value = tempPassword,
                        onValueChange = { tempPassword = it },
                        label = { Text("密码") },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            val image = if (passwordVisible)
                                Icons.Filled.Visibility
                            else Icons.Filled.VisibilityOff

                            val description = if (passwordVisible) "隐藏密码" else "显示密码"

                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(imageVector = image, description)
                            }
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setWebDavAccount(tempAccount, tempPassword)
                    showWebDavAuthDialog = false
                    scope.launch {
                        showLoadingDialog = true
                        loadingText = "测试中…"
                        val success = viewModel.testWebDav()
                        showLoadingDialog = false
                        if (success) {
                            snackbarHostState.showSnackbar("WebDav 配置正确")
                        } else {
                            snackbarHostState.showSnackbar("WebDav 配置错误")
                        }
                    }
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showWebDavAuthDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showConfirmDialog) {
        ConfirmDialog(
            title = confirmDialogTitle,
            text = confirmDialogText,
            onConfirm = {
                onConfirmAction?.invoke()
                showConfirmDialog = false
            },
            onDismiss = { showConfirmDialog = false }
        )
    }

    if (showBackupFilePicker) {
        FilePickerSheet(
            onDismissRequest = { showBackupFilePicker = false },
            onSelectSysDir = {
                showBackupFilePicker = false
                try {
                    selectBackupPathLauncher.launch(null)
                } catch (e: Exception) {
                }
            }
        )
    }

    if (showRestoreFilePicker) {
        FilePickerSheet(
            onDismissRequest = { showRestoreFilePicker = false },
            onSelectSysDir = {
                showRestoreFilePicker = false
                try {
                    backupAndSelectLauncher.launch(null)
                } catch (e: Exception) {
                }
            }
        )
    }

    if (showRestoreSheet && backupNames.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showRestoreSheet = false },
            title = { Text(stringResource(R.string.select_restore_file)) },
            text = {
                Column {
                    backupNames.forEach { name ->
                        TextButton(onClick = {
                            showRestoreSheet = false
                            showLoadingDialog = true
                            loadingText = "恢复中…"
                            viewModel.restoreWebDav(
                                name,
                                {
                                    showLoadingDialog = false
                                    scope.launch {
                                        snackbarHostState.showSnackbar("恢复成功")
                                    }
                                },
                                { error ->
                                    showLoadingDialog = false
                                    scope.launch {
                                        snackbarHostState.showSnackbar("WebDav恢复出错\n$error")
                                    }
                                }
                            )
                        }) {
                            Text(name)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showRestoreSheet = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showBackupIgnoreDialog) {
        val checkedItems = remember {
            BooleanArray(io.legado.app.help.storage.BackupConfig.ignoreKeys.size) { index ->
                io.legado.app.help.storage.BackupConfig.ignoreConfig[
                    io.legado.app.help.storage.BackupConfig.ignoreKeys[index]
                ] ?: false
            }
        }
        AlertDialog(
            onDismissRequest = {
                io.legado.app.help.storage.BackupConfig.saveIgnoreConfig()
                showBackupIgnoreDialog = false
            },
            title = { Text(stringResource(R.string.restore_ignore)) },
            text = {
                Column {
                    io.legado.app.help.storage.BackupConfig.ignoreTitle.forEachIndexed { index, title ->
                        TextButton(onClick = {
                            checkedItems[index] = !checkedItems[index]
                            io.legado.app.help.storage.BackupConfig.ignoreConfig[
                                io.legado.app.help.storage.BackupConfig.ignoreKeys[index]
                            ] = checkedItems[index]
                        }) {
                            val isChecked = checkedItems[index]
                            Text(if (isChecked) "✓ $title" else title)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    io.legado.app.help.storage.BackupConfig.saveIgnoreConfig()
                    showBackupIgnoreDialog = false
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    io.legado.app.help.storage.BackupConfig.saveIgnoreConfig()
                    showBackupIgnoreDialog = false
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showLoadingDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(loadingText) },
            confirmButton = {},
            dismissButton = {}
        )
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            OutlinedButton(onClick = onConfirm) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private fun startBackup(
    path: String,
    context: android.content.Context,
    viewModel: BackupConfigViewModel,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    viewModel.backup(path, onSuccess, onError)
}
