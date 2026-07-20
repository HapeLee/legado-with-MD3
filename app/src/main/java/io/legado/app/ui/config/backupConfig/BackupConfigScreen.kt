package io.legado.app.ui.config.backupConfig

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.help.storage.ImportOldData
import io.legado.app.lib.permission.Permissions
import io.legado.app.lib.permission.PermissionsCompat
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.card.SelectionItemCard
import io.legado.app.ui.widget.components.checkBox.CheckboxItem
import io.legado.app.ui.widget.components.filePicker.FilePickerSheet
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.DropdownListSettingItem
import io.legado.app.ui.widget.components.settingItem.InputSettingItem
import io.legado.app.ui.widget.components.settingItem.SettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.takePersistablePermissionSafely
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

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
                    val message = effect.argument?.let {
                        context.getString(effect.messageRes, it)
                    } ?: context.getString(effect.messageRes)
                    snackbarHostState.showSnackbar(message)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupConfigScreen(
    state: BackupConfigUiState,
    onIntent: (BackupConfigIntent) -> Unit,
    onBackClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val settings = state.settings
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.backup_restore),
                scrollBehavior = scrollBehavior,
                navigationIcon = { TopBarNavigationButton(onClick = onBackClick) },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding(),
                bottom = 120.dp,
            ),
        ) {
            item {
                SplicedColumnGroup(title = stringResource(R.string.web_dav_set)) {
                    InputSettingItem(
                        title = stringResource(R.string.web_dav_url),
                        description = stringResource(R.string.web_dav_url_s),
                        value = settings.webDavUrl,
                        defaultValue = "",
                        onConfirm = { onIntent(BackupConfigIntent.SetWebDavUrl(it)) },
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.web_dav_account),
                        description = stringResource(R.string.web_dav_account_d),
                        onClick = { onIntent(BackupConfigIntent.OpenWebDavAuth) },
                    )
                    InputSettingItem(
                        title = stringResource(R.string.sub_dir),
                        value = settings.webDavDir,
                        defaultValue = "legado",
                        onConfirm = { onIntent(BackupConfigIntent.SetWebDavDir(it)) },
                    )
                    InputSettingItem(
                        title = stringResource(R.string.webdav_device_name),
                        value = settings.webDavDeviceName,
                        defaultValue = "",
                        onConfirm = { onIntent(BackupConfigIntent.SetWebDavDeviceName(it)) },
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.test_sync_t),
                        description = stringResource(R.string.test_sync_d),
                        onClick = { onIntent(BackupConfigIntent.TestWebDav) },
                    )
                    SwitchSettingItem(
                        title = stringResource(R.string.sync_book_progress_t),
                        description = stringResource(R.string.sync_book_progress_s),
                        checked = settings.syncBookProgress,
                        onCheckedChange = { onIntent(BackupConfigIntent.SetSyncBookProgress(it)) },
                    )
                    if (settings.syncBookProgress) {
                        SwitchSettingItem(
                            title = stringResource(R.string.sync_book_progress_plus_t),
                            description = stringResource(R.string.sync_book_progress_plus_s),
                            checked = settings.syncBookProgressPlus,
                            onCheckedChange = {
                                onIntent(BackupConfigIntent.SetSyncBookProgressPlus(it))
                            },
                        )
                    }
                    SwitchSettingItem(
                        title = stringResource(R.string.auto_check_new_backup_t),
                        description = stringResource(R.string.auto_check_new_backup_s),
                        checked = settings.autoCheckNewBackup,
                        onCheckedChange = {
                            onIntent(BackupConfigIntent.SetAutoCheckNewBackup(it))
                        },
                    )
                    DropdownListSettingItem(
                        title = stringResource(R.string.backup_sync_mode),
                        description = stringResource(R.string.backup_sync_mode_summary),
                        selectedValue = settings.backupSyncMode,
                        displayEntries = stringArrayResource(R.array.backup_sync_mode),
                        entryValues = stringArrayResource(R.array.backup_sync_mode_value),
                        onValueChange = { onIntent(BackupConfigIntent.SetBackupSyncMode(it)) },
                    )
                }
                SplicedColumnGroup(title = stringResource(R.string.backup_restore)) {
                    ClickableSettingItem(
                        title = stringResource(R.string.backup_path),
                        description = settings.backupPath ?: stringResource(R.string.select_backup_path),
                        onClick = {
                            onIntent(BackupConfigIntent.OpenSheet(BackupConfigSheet.ChooseBackupPath))
                        },
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.backup),
                        description = stringResource(R.string.backup_summary),
                        onClick = {
                            onIntent(BackupConfigIntent.OpenSheet(BackupConfigSheet.BackupOptions))
                        },
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.restore),
                        description = stringResource(R.string.restore_summary),
                        onClick = {
                            onIntent(BackupConfigIntent.OpenSheet(BackupConfigSheet.RestoreOptions))
                        },
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.restore_ignore),
                        description = stringResource(R.string.restore_ignore_summary),
                        onClick = { onIntent(BackupConfigIntent.OpenIgnoreDialog) },
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.backup_ignore),
                        description = stringResource(R.string.backup_ignore_summary),
                        onClick = { onIntent(BackupConfigIntent.OpenBackupIgnoreDialog) },
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.menu_import_old_version),
                        description = stringResource(R.string.import_old_summary),
                        onClick = { onIntent(BackupConfigIntent.RequestImportOldData) },
                    )
                    SwitchSettingItem(
                        title = stringResource(R.string.only_latest_backup_t),
                        description = stringResource(R.string.only_latest_backup_s),
                        checked = settings.onlyLatestBackup,
                        onCheckedChange = { onIntent(BackupConfigIntent.SetOnlyLatestBackup(it)) },
                    )
                }
            }
        }
    }

    BackupConfigSheets(state, onIntent)
    BackupConfigDialogs(state, onIntent)
}

@Composable
private fun BackupConfigSheets(
    state: BackupConfigUiState,
    onIntent: (BackupConfigIntent) -> Unit,
) {
    FilePickerSheet(
        show = state.activeSheet == BackupConfigSheet.ChooseBackupPath,
        onDismissRequest = { onIntent(BackupConfigIntent.DismissSheet) },
        onSelectSysDir = { onIntent(BackupConfigIntent.SelectBackupDirectory) },
    )
    FilePickerSheet(
        show = state.activeSheet == BackupConfigSheet.ChooseBackupAndRun,
        onDismissRequest = { onIntent(BackupConfigIntent.DismissSheet) },
        onSelectSysDir = { onIntent(BackupConfigIntent.SelectBackupAndRunDirectory) },
    )
    BackupOptionSheet(
        show = state.activeSheet == BackupConfigSheet.BackupOptions,
        onDismissRequest = { onIntent(BackupConfigIntent.DismissSheet) },
        onBackupToLocal = { onIntent(BackupConfigIntent.RequestBackup("local")) },
        onBackupToNetwork = { onIntent(BackupConfigIntent.RequestBackup("webdav")) },
        onBackupToLocalAndNetwork = { onIntent(BackupConfigIntent.RequestBackup("both")) },
    )
    RestoreOptionSheet(
        show = state.activeSheet == BackupConfigSheet.RestoreOptions,
        onDismissRequest = { onIntent(BackupConfigIntent.DismissSheet) },
        onRestoreFromLocal = { onIntent(BackupConfigIntent.RequestLocalRestore) },
        onRestoreFromNetwork = { onIntent(BackupConfigIntent.RequestNetworkRestore) },
    )
    AppModalBottomSheet(
        show = state.activeSheet == BackupConfigSheet.RestoreFiles && state.backupNames.isNotEmpty(),
        onDismissRequest = { onIntent(BackupConfigIntent.DismissSheet) },
        title = stringResource(R.string.select_restore_file),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.backupNames, key = { it }) { name ->
                SelectionItemCard(
                    title = name,
                    containerColor = LegadoTheme.colorScheme.surface,
                    onToggleSelection = { onIntent(BackupConfigIntent.RestoreNetwork(name)) },
                )
            }
        }
    }
    IgnoreItemsSheet(
        show = state.activeSheet == BackupConfigSheet.IgnoreRestoreItems,
        title = stringResource(R.string.restore_ignore),
        ignoreItems = state.ignoreItems,
        dbIgnoreItems = state.dbIgnoreItems,
        onToggleIgnoreItem = { key, value ->
            onIntent(
                BackupConfigIntent.ToggleIgnoreItem(
                    key,
                    value
                )
            )
        },
        onToggleDbIgnoreItem = { key, value ->
            onIntent(
                BackupConfigIntent.ToggleDbIgnoreItem(
                    key,
                    value
                )
            )
        },
        onConfirm = { onIntent(BackupConfigIntent.SaveIgnoreItems) },
        onDismissRequest = { onIntent(BackupConfigIntent.SaveIgnoreItems) },
    )
    IgnoreItemsSheet(
        show = state.activeSheet == BackupConfigSheet.IgnoreBackupItems,
        title = stringResource(R.string.backup_ignore),
        ignoreItems = state.backupIgnoreItems,
        dbIgnoreItems = state.backupDbIgnoreItems,
        onToggleIgnoreItem = { key, value ->
            onIntent(
                BackupConfigIntent.ToggleBackupIgnoreItem(
                    key,
                    value
                )
            )
        },
        onToggleDbIgnoreItem = { key, value ->
            onIntent(
                BackupConfigIntent.ToggleBackupDbIgnoreItem(
                    key,
                    value
                )
            )
        },
        onConfirm = { onIntent(BackupConfigIntent.SaveBackupIgnoreItems) },
        onDismissRequest = { onIntent(BackupConfigIntent.SaveBackupIgnoreItems) },
    )
}

@Composable
private fun IgnoreItemsSheet(
    show: Boolean,
    title: String,
    ignoreItems: ImmutableList<BackupIgnoreItem>,
    dbIgnoreItems: ImmutableList<BackupIgnoreItem>,
    onToggleIgnoreItem: (String, Boolean) -> Unit,
    onToggleDbIgnoreItem: (String, Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    var configExpanded by remember { mutableStateOf(true) }
    var dbExpanded by remember { mutableStateOf(false) }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = title,
        endAction = {
            MediumTonalButton(
                onClick = onConfirm,
                icon = Icons.Default.Save,
                contentDescription = stringResource(R.string.save),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingItem(
                title = stringResource(R.string.config_ignore),
                description = stringResource(R.string.restore_ignore_summary),
                expanded = configExpanded,
                onExpandChange = { configExpanded = it },
                expandContent = {
                    ignoreItems.forEach { item: BackupIgnoreItem ->
                        CheckboxItem(
                            title = item.title,
                            checked = item.checked,
                            onCheckedChange = { onToggleIgnoreItem(item.key, it) },
                        )
                        Spacer(
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                },
            )
            SettingItem(
                title = stringResource(R.string.database_ignore),
                description = stringResource(R.string.database_ignore_summary),
                expanded = dbExpanded,
                onExpandChange = { dbExpanded = it },
                expandContent = {
                    dbIgnoreItems.forEach { item: BackupIgnoreItem ->
                        CheckboxItem(
                            title = item.title,
                            checked = item.checked,
                            onCheckedChange = { onToggleDbIgnoreItem(item.key, it) },
                        )
                        Spacer(
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun BackupConfigDialogs(
    state: BackupConfigUiState,
    onIntent: (BackupConfigIntent) -> Unit,
) {
    val dialog = state.activeDialog
    val auth = dialog as? BackupConfigDialog.WebDavAuth
    AppAlertDialog(
        show = auth != null,
        onDismissRequest = { onIntent(BackupConfigIntent.DismissDialog) },
        title = stringResource(R.string.web_dav_account),
        content = {
            auth?.let {
                Column {
                    AppTextField(
                        value = it.account,
                        onValueChange = { value ->
                            onIntent(BackupConfigIntent.EditWebDavAccount(value))
                        },
                        backgroundColor = LegadoTheme.colorScheme.surface,
                        label = stringResource(R.string.web_dav_account),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    AppTextField(
                        value = it.password,
                        onValueChange = { value ->
                            onIntent(BackupConfigIntent.EditWebDavPassword(value))
                        },
                        backgroundColor = LegadoTheme.colorScheme.surface,
                        label = stringResource(R.string.web_dav_pw),
                        visualTransformation = if (it.passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = {
                                onIntent(BackupConfigIntent.TogglePasswordVisibility)
                            }) {
                                Icon(
                                    imageVector = if (it.passwordVisible) {
                                        Icons.Filled.Visibility
                                    } else {
                                        Icons.Filled.VisibilityOff
                                    },
                                    contentDescription = stringResource(
                                        if (it.passwordVisible) R.string.hide_password
                                        else R.string.show_password
                                    ),
                                )
                            }
                        },
                    )
                }
            }
        },
        confirmText = stringResource(R.string.ok),
        onConfirm = { onIntent(BackupConfigIntent.SaveWebDavAuth) },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { onIntent(BackupConfigIntent.DismissDialog) },
    )

    val fallback = dialog as? BackupConfigDialog.ConfirmLocalRestoreFallback
    ConfirmDialog(
        show = fallback != null,
        title = stringResource(R.string.restore),
        text = stringResource(R.string.webdav_restore_fallback_message, fallback?.error.orEmpty()),
        onConfirm = { onIntent(BackupConfigIntent.ConfirmLocalRestoreFallback) },
        onDismiss = { onIntent(BackupConfigIntent.DismissDialog) },
    )

    val loading = dialog as? BackupConfigDialog.Loading
    AppAlertDialog(
        show = loading != null,
        onDismissRequest = { onIntent(BackupConfigIntent.DismissDialog) },
        title = loading?.let { stringResource(it.titleRes) }.orEmpty(),
    )
}

@Composable
fun ConfirmDialog(
    show: Boolean,
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppAlertDialog(
        show = show,
        onDismissRequest = onDismiss,
        title = title,
        text = text,
        confirmText = stringResource(R.string.ok),
        onConfirm = onConfirm,
        dismissText = stringResource(R.string.cancel),
        onDismiss = onDismiss,
    )
}
