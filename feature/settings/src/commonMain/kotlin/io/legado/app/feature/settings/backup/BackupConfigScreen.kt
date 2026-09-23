package io.legado.app.feature.settings.backup

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.legado.app.feature.settings.res.Res
import io.legado.app.feature.settings.res.auto_check_new_backup_s
import io.legado.app.feature.settings.res.auto_check_new_backup_t
import io.legado.app.feature.settings.res.backup
import io.legado.app.feature.settings.res.backup_ignore
import io.legado.app.feature.settings.res.backup_ignore_summary
import io.legado.app.feature.settings.res.backup_path
import io.legado.app.feature.settings.res.backup_restore
import io.legado.app.feature.settings.res.backup_summary
import io.legado.app.feature.settings.res.backup_sync_mode
import io.legado.app.feature.settings.res.backup_sync_mode_summary
import io.legado.app.feature.settings.res.backup_sync_mode_value
import io.legado.app.feature.settings.res.cancel
import io.legado.app.feature.settings.res.config_ignore
import io.legado.app.feature.settings.res.database_ignore
import io.legado.app.feature.settings.res.hide_password
import io.legado.app.feature.settings.res.import_old_summary
import io.legado.app.feature.settings.res.menu_import_old_version
import io.legado.app.feature.settings.res.ok
import io.legado.app.feature.settings.res.only_latest_backup_s
import io.legado.app.feature.settings.res.only_latest_backup_t
import io.legado.app.feature.settings.res.restore
import io.legado.app.feature.settings.res.restore_ignore
import io.legado.app.feature.settings.res.restore_ignore_summary
import io.legado.app.feature.settings.res.restore_summary
import io.legado.app.feature.settings.res.save
import io.legado.app.feature.settings.res.select_backup_path
import io.legado.app.feature.settings.res.select_restore_file
import io.legado.app.feature.settings.res.show_password
import io.legado.app.feature.settings.res.sub_dir
import io.legado.app.feature.settings.res.sync_book_progress_plus_s
import io.legado.app.feature.settings.res.sync_book_progress_plus_t
import io.legado.app.feature.settings.res.sync_book_progress_s
import io.legado.app.feature.settings.res.sync_book_progress_t
import io.legado.app.feature.settings.res.test_sync_d
import io.legado.app.feature.settings.res.test_sync_t
import io.legado.app.feature.settings.res.web_dav_account
import io.legado.app.feature.settings.res.web_dav_account_d
import io.legado.app.feature.settings.res.web_dav_pw
import io.legado.app.feature.settings.res.web_dav_set
import io.legado.app.feature.settings.res.web_dav_url
import io.legado.app.feature.settings.res.web_dav_url_s
import io.legado.app.feature.settings.res.webdav_device_name
import io.legado.app.feature.settings.res.webdav_restore_fallback_message
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
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.tabRow.CardTabRow
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

// M5-9b：从 `:app` 的 `io.legado.app.ui.config.backupConfig.BackupConfigScreen.kt` 迁来
// **只有页面本体这一半**。那个文件里同时放着 `BackupConfigRouteScreen`（宿主壳：4 个
// `rememberLauncherForActivityResult` + `PermissionsCompat` 申请存储权限 + `ImportOldData`）
// 与 5 个 UI composable —— 本片把它按职责拆开，宿主壳留在 `:app`
// （现住 `BackupConfigRouteScreen.kt`），本体搬到这里。
//
// 差异两类（**结构逐字保留**，含原文的缩进与空行 —— 这样 diff 只反映语义改动）：
//   ① `androidx.compose.ui.res.{stringResource,stringArrayResource}`
//      → `org.jetbrains.compose.resources.*`；
//   ② `R.string.*` → `Res.string.*`（42 条）、`R.array.*` → `Res.array.*`（2 个）。
//
// ⚠️ **`stringArrayResource` 的返回值类型与 androidx 不同**：CMP 返回 `List<String>`，
// 故要多一次 `.toTypedArray()`（`DropdownListSettingItem` 收的是 `Array<String>`）。
// 同模块的 `OtherConfigScreen` / `CustomThemeScreen` 已是这个写法。
//
// ⚠️ **`backup_sync_mode` 数组不是纯搬运**：`:app` 里它的条目是 `@string/*` 间接引用
// （Android 逐项按语言解析），而共享层的数组约定是纯字面量 ⇒ 写的是解析后的结果
// （4 个语言都写了）；机器值数组 `backup_sync_mode_value` 只放默认 `values/`。
// 细节见 `:feature:settings` 的 arrays.xml 注释。

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
                title = stringResource(Res.string.backup_restore),
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
                SplicedColumnGroup(title = stringResource(Res.string.web_dav_set)) {
                    InputSettingItem(
                        title = stringResource(Res.string.web_dav_url),
                        description = stringResource(Res.string.web_dav_url_s),
                        value = settings.webDavUrl,
                        defaultValue = "",
                        onConfirm = { onIntent(BackupConfigIntent.SetWebDavUrl(it)) },
                    )
                    ClickableSettingItem(
                        title = stringResource(Res.string.web_dav_account),
                        description = stringResource(Res.string.web_dav_account_d),
                        onClick = { onIntent(BackupConfigIntent.OpenWebDavAuth) },
                    )
                    InputSettingItem(
                        title = stringResource(Res.string.sub_dir),
                        value = settings.webDavDir,
                        defaultValue = "legado",
                        onConfirm = { onIntent(BackupConfigIntent.SetWebDavDir(it)) },
                    )
                    InputSettingItem(
                        title = stringResource(Res.string.webdav_device_name),
                        value = settings.webDavDeviceName,
                        defaultValue = "",
                        onConfirm = { onIntent(BackupConfigIntent.SetWebDavDeviceName(it)) },
                    )
                    ClickableSettingItem(
                        title = stringResource(Res.string.test_sync_t),
                        description = stringResource(Res.string.test_sync_d),
                        onClick = { onIntent(BackupConfigIntent.TestWebDav) },
                    )
                    SwitchSettingItem(
                        title = stringResource(Res.string.sync_book_progress_t),
                        description = stringResource(Res.string.sync_book_progress_s),
                        checked = settings.syncBookProgress,
                        onCheckedChange = { onIntent(BackupConfigIntent.SetSyncBookProgress(it)) },
                    )
                    if (settings.syncBookProgress) {
                        SwitchSettingItem(
                            title = stringResource(Res.string.sync_book_progress_plus_t),
                            description = stringResource(Res.string.sync_book_progress_plus_s),
                            checked = settings.syncBookProgressPlus,
                            onCheckedChange = {
                                onIntent(BackupConfigIntent.SetSyncBookProgressPlus(it))
                            },
                        )
                    }
                    SwitchSettingItem(
                        title = stringResource(Res.string.auto_check_new_backup_t),
                        description = stringResource(Res.string.auto_check_new_backup_s),
                        checked = settings.autoCheckNewBackup,
                        onCheckedChange = {
                            onIntent(BackupConfigIntent.SetAutoCheckNewBackup(it))
                        },
                    )
                    DropdownListSettingItem(
                        title = stringResource(Res.string.backup_sync_mode),
                        description = stringResource(Res.string.backup_sync_mode_summary),
                        selectedValue = settings.backupSyncMode,
                        displayEntries = stringArrayResource(Res.array.backup_sync_mode).toTypedArray(),
                        entryValues = stringArrayResource(Res.array.backup_sync_mode_value).toTypedArray(),
                        onValueChange = { onIntent(BackupConfigIntent.SetBackupSyncMode(it)) },
                    )
                }
                SplicedColumnGroup(title = stringResource(Res.string.backup_restore)) {
                    ClickableSettingItem(
                        title = stringResource(Res.string.backup_path),
                        description = settings.backupPath ?: stringResource(Res.string.select_backup_path),
                        onClick = {
                            onIntent(BackupConfigIntent.OpenSheet(BackupConfigSheet.ChooseBackupPath))
                        },
                    )
                    ClickableSettingItem(
                        title = stringResource(Res.string.backup),
                        description = stringResource(Res.string.backup_summary),
                        onClick = {
                            onIntent(BackupConfigIntent.OpenSheet(BackupConfigSheet.BackupOptions))
                        },
                    )
                    ClickableSettingItem(
                        title = stringResource(Res.string.restore),
                        description = stringResource(Res.string.restore_summary),
                        onClick = {
                            onIntent(BackupConfigIntent.OpenSheet(BackupConfigSheet.RestoreOptions))
                        },
                    )
                    ClickableSettingItem(
                        title = stringResource(Res.string.restore_ignore),
                        description = stringResource(Res.string.restore_ignore_summary),
                        onClick = { onIntent(BackupConfigIntent.OpenIgnoreDialog) },
                    )
                    ClickableSettingItem(
                        title = stringResource(Res.string.backup_ignore),
                        description = stringResource(Res.string.backup_ignore_summary),
                        onClick = { onIntent(BackupConfigIntent.OpenBackupIgnoreDialog) },
                    )
                    ClickableSettingItem(
                        title = stringResource(Res.string.menu_import_old_version),
                        description = stringResource(Res.string.import_old_summary),
                        onClick = { onIntent(BackupConfigIntent.RequestImportOldData) },
                    )
                    SwitchSettingItem(
                        title = stringResource(Res.string.only_latest_backup_t),
                        description = stringResource(Res.string.only_latest_backup_s),
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
        title = stringResource(Res.string.select_restore_file),
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
        title = stringResource(Res.string.restore_ignore),
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
        title = stringResource(Res.string.backup_ignore),
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
    var selectedTab by remember { mutableIntStateOf(0) }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = title,
        endAction = {
            MediumTonalButton(
                onClick = onConfirm,
                icon = Icons.Default.Save,
                contentDescription = stringResource(Res.string.save),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CardTabRow(
                tabTitles = listOf(
                    stringResource(Res.string.config_ignore),
                    stringResource(Res.string.database_ignore),
                ),
                selectedTabIndex = selectedTab,
                onTabSelected = { selectedTab = it },
            )
            when (selectedTab) {
                0 -> {
                    ignoreItems.forEach { item: BackupIgnoreItem ->
                        CheckboxItem(
                            title = item.title,
                            checked = item.checked,
                            onCheckedChange = { onToggleIgnoreItem(item.key, it) },
                        )
                    }
                }

                1 -> {
                    dbIgnoreItems.forEach { item: BackupIgnoreItem ->
                        CheckboxItem(
                            title = item.title,
                            checked = item.checked,
                            onCheckedChange = { onToggleDbIgnoreItem(item.key, it) },
                        )
                    }
                }
            }
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
        title = stringResource(Res.string.web_dav_account),
        content = {
            auth?.let {
                Column {
                    AppTextField(
                        value = it.account,
                        onValueChange = { value ->
                            onIntent(BackupConfigIntent.EditWebDavAccount(value))
                        },
                        backgroundColor = LegadoTheme.colorScheme.surface,
                        label = stringResource(Res.string.web_dav_account),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    AppTextField(
                        value = it.password,
                        onValueChange = { value ->
                            onIntent(BackupConfigIntent.EditWebDavPassword(value))
                        },
                        backgroundColor = LegadoTheme.colorScheme.surface,
                        label = stringResource(Res.string.web_dav_pw),
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
                                        if (it.passwordVisible) Res.string.hide_password
                                        else Res.string.show_password
                                    ),
                                )
                            }
                        },
                    )
                }
            }
        },
        confirmText = stringResource(Res.string.ok),
        onConfirm = { onIntent(BackupConfigIntent.SaveWebDavAuth) },
        dismissText = stringResource(Res.string.cancel),
        onDismiss = { onIntent(BackupConfigIntent.DismissDialog) },
    )

    val fallback = dialog as? BackupConfigDialog.ConfirmLocalRestoreFallback
    ConfirmDialog(
        show = fallback != null,
        title = stringResource(Res.string.restore),
        text = stringResource(Res.string.webdav_restore_fallback_message, fallback?.error.orEmpty()),
        onConfirm = { onIntent(BackupConfigIntent.ConfirmLocalRestoreFallback) },
        onDismiss = { onIntent(BackupConfigIntent.DismissDialog) },
    )

    val loading = dialog as? BackupConfigDialog.Loading
    AppAlertDialog(
        show = loading != null,
        onDismissRequest = { onIntent(BackupConfigIntent.DismissDialog) },
        // M5-9a：`Loading.title` 从 `@StringRes Int` 换成枚举 ⇒ 组合内查表
        // （`localized()` 是 `@Composable` 的，与 Effect 那条路径的 `localizedText()`
        // 共用同一张映射表）。
        title = loading?.let { it.title.localized() }.orEmpty(),
    )
}

/**
 * ⚠️ 迁移前这个 composable 的可见性是 `public`，但它**只被同文件的 `BackupConfigDialogs` 用**。
 * 本片**没有**顺手改成 `private`：可见性属于 API 表面，改它不该夹在一次搬迁里
 * （即便当前看起来无害）。若日后确认无人使用，应由一个专门的清理切片收紧。
 */
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
        confirmText = stringResource(Res.string.ok),
        onConfirm = onConfirm,
        dismissText = stringResource(Res.string.cancel),
        onDismiss = onDismiss,
    )
}
