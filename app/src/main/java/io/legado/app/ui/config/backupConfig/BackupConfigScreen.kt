package io.legado.app.ui.config.backupConfig

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import io.legado.app.help.storage.ImportOldData
import io.legado.app.help.storage.Restore
import io.legado.app.lib.permission.Permissions
import io.legado.app.lib.permission.PermissionsCompat
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.ui.widget.components.card.SelectionItemCard
import io.legado.app.ui.widget.components.checkBox.CheckboxItem
import io.legado.app.ui.widget.components.filePicker.FilePickerSheet
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.InputSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.takePersistablePermissionSafely
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.theme.MiuixTheme

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

    val webDavUrl = BackupConfig.webDavUrl
    val webDavDir = BackupConfig.webDavDir
    val webDavAccount = BackupConfig.webDavAccount
    val webDavPassword = BackupConfig.webDavPassword
    var webDavSyncReady by remember { mutableStateOf(false) }

    LaunchedEffect(webDavUrl, webDavDir, webDavAccount, webDavPassword) {
        if (webDavSyncReady) {
            viewModel.refreshWebDavConfig()
        } else {
            webDavSyncReady = true
        }
    }

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
            ImportOldData.importUri(context, uri)
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
                title = stringResource(R.string.backup_restore),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBackClick)
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding(),
                bottom = 120.dp
            )
        ) {
            item {
                SplicedColumnGroup(title = stringResource(R.string.web_dav_set)) {
                    InputSettingItem(
                        title = stringResource(R.string.web_dav_url),
                        description = stringResource(R.string.web_dav_url_s),
                        value = BackupConfig.webDavUrl,
                        defaultValue = "",
                        onConfirm = { BackupConfig.webDavUrl = it }
                    )

                ClickableSettingItem(
                    title = stringResource(R.string.web_dav_account),
                    description = stringResource(R.string.web_dav_account_d),
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
                    title = stringResource(R.string.test_sync_t),
                    description = stringResource(R.string.test_sync_d),
                    
                    onClick = {
                        scope.launch {
                            showLoadingDialog = true
                            loadingText = context.getString(R.string.test_sync_loading_text)
                            val success = viewModel.testWebDav()
                            showLoadingDialog = false
                            if (success) {
                                snackbarHostState.showSnackbar(context.getString(R.string.test_sync_status_success))
                            } else {
                                snackbarHostState.showSnackbar(context.getString(R.string.test_sync_status_fail))
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
    }

    AppAlertDialog(
        show = showWebDavAuthDialog,
        onDismissRequest = { showWebDavAuthDialog = false },
        title = stringResource(R.string.web_dav_account),
        content = {
            Column {
                AppTextField(
                    value = tempAccount,
                    onValueChange = { tempAccount = it },
                    backgroundColor = LegadoTheme.colorScheme.surface,
                    label = "账号"
                )
                Spacer(modifier = Modifier.height(12.dp))
                AppTextField(
                    value = tempPassword,
                    onValueChange = { tempPassword = it },
                    backgroundColor = MiuixTheme.colorScheme.surface,
                    label = "密码",
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        val image =
                            if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                        val description = if (passwordVisible) "隐藏密码" else "显示密码"
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = description)
                        }
                    }
                )
            }
        },
        confirmText = stringResource(R.string.ok),
        onConfirm = {
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
        },
        dismissText = stringResource(R.string.cancel),
        onDismiss = {
            showWebDavAuthDialog = false
        }
    )

    ConfirmDialog(
        show = showConfirmDialog,
        title = confirmDialogTitle,
        text = confirmDialogText,
        onConfirm = {
            onConfirmAction?.invoke()
            showConfirmDialog = false
        },
        onDismiss = { showConfirmDialog = false }
    )

    FilePickerSheet(
        show = showBackupFilePicker,
        onDismissRequest = { showBackupFilePicker = false },
        onSelectSysDir = {
            showBackupFilePicker = false
            try {
                selectBackupPathLauncher.launch(null)
            } catch (e: Exception) {
            }
        }
    )


    FilePickerSheet(
        show = showRestoreFilePicker,
        onDismissRequest = { showRestoreFilePicker = false },
        onSelectSysDir = {
            showRestoreFilePicker = false
            try {
                backupAndSelectLauncher.launch(null)
            } catch (e: Exception) {
            }
        }
    )

    AppModalBottomSheet(
        show = showRestoreSheet && backupNames.isNotEmpty(),
        onDismissRequest = { showRestoreSheet = false },
        title = stringResource(R.string.select_restore_file)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(backupNames) {
                SelectionItemCard(
                    title = it,
                    containerColor = LegadoTheme.colorScheme.surface,
                    onToggleSelection = {
                        showRestoreSheet = false
                        showLoadingDialog = true
                        loadingText = "恢复中…"
                        viewModel.restoreWebDav(
                            it,
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
                    }
                )
            }
        }
    }

    val checkedItems = remember(showBackupIgnoreDialog) {
        val keys = io.legado.app.help.storage.BackupConfig.ignoreKeys
        val config = io.legado.app.help.storage.BackupConfig.ignoreConfig

        // 初始化可观察的 List 状态
        mutableStateListOf(*Array(keys.size) { index ->
            config[keys[index]] ?: false
        })
    }

    AppAlertDialog(
        show = showBackupIgnoreDialog,
        onDismissRequest = {
            io.legado.app.help.storage.BackupConfig.saveIgnoreConfig()
            showBackupIgnoreDialog = false
        },
        title = stringResource(R.string.restore_ignore),
        content = {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                io.legado.app.help.storage.BackupConfig.ignoreTitle.forEachIndexed { index, title ->
                    CheckboxItem(
                        title = title,
                        checked = checkedItems[index],
                        onCheckedChange = { isChecked ->
                            checkedItems[index] = isChecked
                            io.legado.app.help.storage.BackupConfig.ignoreConfig[
                                io.legado.app.help.storage.BackupConfig.ignoreKeys[index]
                            ] = isChecked
                        }
                    )
                }
            }
        },
        confirmText = stringResource(R.string.ok),
        onConfirm = {
            io.legado.app.help.storage.BackupConfig.saveIgnoreConfig()
            showBackupIgnoreDialog = false
        },
        dismissText = stringResource(R.string.cancel),
        onDismiss = {
            io.legado.app.help.storage.BackupConfig.saveIgnoreConfig()
            showBackupIgnoreDialog = false
        }
    )


    AppAlertDialog(
        show = showLoadingDialog,
        onDismiss = {},
        onConfirm = {},
        title = loadingText,
        onDismissRequest = { showLoadingDialog = false }
    )

}

@Composable
fun ConfirmDialog(
    show: Boolean,
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AppAlertDialog(
        show = show,
        onDismissRequest = onDismiss,
        title = title,
        text = text,
        confirmText = stringResource(R.string.ok),
        onConfirm = onConfirm,
        dismissText = stringResource(R.string.cancel),
        onDismiss = onDismiss
    )
}

private fun startBackup(
    path: String,
    context: Context,
    viewModel: BackupConfigViewModel,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    viewModel.backup(path, onSuccess, onError)
}
