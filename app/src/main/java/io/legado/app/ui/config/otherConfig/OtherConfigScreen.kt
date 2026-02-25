package io.legado.app.ui.config.otherConfig

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.service.WebService
import io.legado.app.ui.widget.components.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.button.TopbarNavigationButton
import io.legado.app.ui.widget.components.exportComponents.FilePickerSheet
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.DropdownListSettingItem
import io.legado.app.ui.widget.components.settingItem.InputSettingItem
import io.legado.app.ui.widget.components.settingItem.SliderSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.utils.restart
import io.legado.app.utils.takePersistablePermissionSafely
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtherConfigScreen(
    onBackClick: () -> Unit,
    viewModel: OtherConfigViewModel = koinViewModel()
) {
    val context = LocalContext.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->

    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showShrinkDbDialog by remember { mutableStateOf(false) }
    var showClearWebViewDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showCheckSourceSheet by remember { mutableStateOf(false) }
    var showDirectLinkUploadSheet by remember { mutableStateOf(false) }

    var tempPassword by remember { mutableStateOf("") }

    var showFilePicker by remember { mutableStateOf(false) }
    val selectDocTree = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            it.takePersistablePermissionSafely(context)
            viewModel.updateLocalBookDir(it.toString())
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = {
                    Text(stringResource(R.string.other_setting))
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

            SplicedColumnGroup {
                DropdownListSettingItem(
                    title = stringResource(R.string.language),
                    selectedValue = OtherConfig.language,
                    displayEntries = stringArrayResource(R.array.language),
                    entryValues = stringArrayResource(R.array.language_value),
                    onValueChange = { newValue ->
                        OtherConfig.language = newValue
                        context.restart()
                    }
                )

                DropdownListSettingItem(
                    title = stringResource(R.string.update_to_variant_title),
                    description = stringResource(R.string.update_to_variant_summary),
                    selectedValue = OtherConfig.updateToVariant,
                    displayEntries = stringArrayResource(R.array.default_app_variant),
                    entryValues = stringArrayResource(R.array.default_app_variant_value),
                    onValueChange = { OtherConfig.updateToVariant = it }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.web_service_auto_start),
                    checked = OtherConfig.webServiceAutoStart,
                    onCheckedChange = { OtherConfig.webServiceAutoStart = it }
                )
            }

            SplicedColumnGroup(title = stringResource(R.string.main_activity)) {

                SwitchSettingItem(
                    title = stringResource(R.string.pt_auto_refresh),
                    description = stringResource(R.string.ps_auto_refresh),
                    checked = OtherConfig.autoRefresh,
                    onCheckedChange = { OtherConfig.autoRefresh = it }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.pt_default_read),
                    description = stringResource(R.string.ps_default_read),
                    checked = OtherConfig.defaultToRead,
                    onCheckedChange = { OtherConfig.defaultToRead = it }
                )
            }

            SplicedColumnGroup(title = stringResource(R.string.privacy)) {

                ClickableSettingItem(
                    title = stringResource(R.string.notification_permission),
                    description = stringResource(R.string.notification_permission_rationale),
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            Toast.makeText(context, "无需申请", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                ClickableSettingItem(
                    title = stringResource(R.string.background_permission),
                    description = stringResource(R.string.ignore_battery_permission_rationale),
                    onClick = {

                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.firebase_enable_title),
                    description = stringResource(R.string.firebase_enable_summary),
                    checked = OtherConfig.firebaseEnable,
                    onCheckedChange = { OtherConfig.firebaseEnable = it }
                )

                ClickableSettingItem(
                    title = stringResource(R.string.set_local_password),
                    description = stringResource(R.string.set_local_password_summary),
                    onClick = { showPasswordDialog = true }
                )

            }

            SplicedColumnGroup(title = stringResource(R.string.read)) {

                ClickableSettingItem(
                    title = stringResource(R.string.book_tree_uri_t),
                    description = OtherConfig.defaultBookTreeUri,
                    onClick = { showFilePicker = true }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.anti_alias),
                    description = stringResource(R.string.pref_anti_alias_summary),
                    checked = OtherConfig.antiAlias,
                    onCheckedChange = { OtherConfig.antiAlias = it }
                )

                SliderSettingItem(
                    title = stringResource(R.string.bitmap_cache_size),
                    description = stringResource(R.string.bitmap_cache_size_summary),
                    value = OtherConfig.bitmapCacheSize.toFloat(),
                    defaultValue = 32f,
                    valueRange = 1f..2047f,
                    onValueChange = {
                        viewModel.updateBitmapCacheSize(it.toInt())
                    }
                )

                SliderSettingItem(
                    title = stringResource(R.string.image_retain_number),
                    description = stringResource(R.string.image_retain_number_summary),
                    value = OtherConfig.imageRetainNum.toFloat(),
                    defaultValue = 10f,
                    valueRange = 0f..100f,
                    onValueChange = { OtherConfig.imageRetainNum = it.toInt() }
                )

                SliderSettingItem(
                    title = stringResource(R.string.pre_download),
                    description = stringResource(R.string.pre_download_s),
                    value = OtherConfig.preDownloadNum.toFloat(),
                    defaultValue = 10f,
                    valueRange = 0f..100f,
                    onValueChange = { OtherConfig.preDownloadNum = it.toInt() }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.replace_enable_default_t),
                    description = stringResource(R.string.replace_enable_default_s),
                    checked = OtherConfig.replaceEnableDefault,
                    onCheckedChange = { OtherConfig.replaceEnableDefault = it }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.media_button_on_exit_title),
                    description = stringResource(R.string.media_button_on_exit_summary),
                    checked = OtherConfig.mediaButtonOnExit,
                    onCheckedChange = { OtherConfig.mediaButtonOnExit = it }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.read_aloud_by_media_button_title),
                    description = stringResource(R.string.read_aloud_by_media_button_summary),
                    checked = OtherConfig.readAloudByMediaButton,
                    onCheckedChange = { OtherConfig.readAloudByMediaButton = it }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.ignore_audio_focus_title),
                    description = stringResource(R.string.ignore_audio_focus_summary),
                    checked = OtherConfig.ignoreAudioFocus,
                    onCheckedChange = { OtherConfig.ignoreAudioFocus = it }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.auto_clear_expired),
                    description = stringResource(R.string.auto_clear_expired_summary),
                    checked = OtherConfig.autoClearExpired,
                    onCheckedChange = { OtherConfig.autoClearExpired = it }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.show_add_to_shelf_alert_title),
                    description = stringResource(R.string.show_add_to_shelf_alert_summary),
                    checked = OtherConfig.showAddToShelfAlert,
                    onCheckedChange = { OtherConfig.showAddToShelfAlert = it }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.show_manga_ui),
                    checked = OtherConfig.showMangaUi,
                    onCheckedChange = { OtherConfig.showMangaUi = it }
                )
            }

            SplicedColumnGroup(title = stringResource(R.string.other_setting)) {

                SwitchSettingItem(
                    title = stringResource(R.string.use_animation),
                    description = stringResource(R.string.opt_animation),
                    checked = OtherConfig.sharedElementEnterTransitionEnable,
                    onCheckedChange = { OtherConfig.sharedElementEnterTransitionEnable = it }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.delay_book_load),
                    description = stringResource(R.string.reduce_stutter),
                    checked = OtherConfig.delayBookLoadEnable,
                    onCheckedChange = { OtherConfig.delayBookLoadEnable = it }
                )

                InputSettingItem(
                    title = stringResource(R.string.user_agent),
                    value = OtherConfig.userAgent,
                    onConfirm = { viewModel.saveUserAgent(it) }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.web_service_wake_lock),
                    description = stringResource(R.string.web_service_wake_lock_summary),
                    checked = OtherConfig.webServiceWakeLock,
                    onCheckedChange = { OtherConfig.webServiceWakeLock = it }
                )

                InputSettingItem(
                    title = stringResource(R.string.source_edit_text_max_line),
                    value = OtherConfig.sourceEditMaxLine.toString(),
                    defaultValue = 500.toString(),
                    onConfirm = { OtherConfig.sourceEditMaxLine = it.toIntOrNull() ?: 500 }
                )

                ClickableSettingItem(
                    title = stringResource(R.string.check_source_config),
                    onClick = { showCheckSourceSheet = true }
                )

                ClickableSettingItem(
                    title = stringResource(R.string.direct_link_upload_rule),
                    description = stringResource(R.string.direct_link_upload_rule_summary),
                    onClick = { showDirectLinkUploadSheet = true }
                )

                SwitchSettingItem(
                    title = "Cronet",
                    description = stringResource(R.string.pref_cronet_summary),
                    checked = OtherConfig.cronetEnable,
                    onCheckedChange = { OtherConfig.cronetEnable = it }
                )

                InputSettingItem(
                    title = stringResource(R.string.web_port_title),
                    value = OtherConfig.webPort.toString(),
                    onConfirm = { newValue ->
                        OtherConfig.webPort = newValue.toInt()
                        if (WebService.isRun) {
                            WebService.stop(context)
                            WebService.start(context)
                        }
                    }
                )

                ClickableSettingItem(
                    title = stringResource(R.string.clear_cache),
                    description = stringResource(R.string.clear_cache_summary),
                    onClick = { showClearCacheDialog = true }
                )

                ClickableSettingItem(
                    title = stringResource(R.string.clear_webview_data),
                    description = stringResource(R.string.clear_webview_data_summary),
                    onClick = { showClearWebViewDialog = true }
                )

                ClickableSettingItem(
                    title = stringResource(R.string.shrink_database),
                    description = stringResource(R.string.shrink_database_summary),
                    onClick = { showShrinkDbDialog = true }
                )

                SliderSettingItem(
                    title = stringResource(R.string.threads_num_title),
                    value = OtherConfig.threadCount.toFloat(),
                    defaultValue = 8f,
                    valueRange = 1f..256f,
                    onValueChange = { OtherConfig.threadCount = it.toInt() }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.add_to_text_context_menu_t),
                    description = stringResource(R.string.add_to_text_context_menu_s),
                    checked = viewModel.isProcessTextEnabled(),
                    onCheckedChange = { viewModel.setProcessTextEnable(it) }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.record_log),
                    description = stringResource(R.string.record_debug_log),
                    checked = OtherConfig.recordLog,
                    onCheckedChange = { OtherConfig.recordLog = it }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.record_heap_dump_t),
                    description = stringResource(R.string.record_heap_dump_s),
                    checked = OtherConfig.recordHeapDump,
                    onCheckedChange = { OtherConfig.recordHeapDump = it }
                )
            }
        }

        if (showFilePicker) {
            FilePickerSheet(
                sheetState = rememberModalBottomSheetState(),
                onDismissRequest = { showFilePicker = false },
                onSelectSysDir = {
                    showFilePicker = false
                    try {
                        selectDocTree.launch(null)
                    } catch (e: Exception) {

                    }
                }
            )
        }

        if (showCheckSourceSheet) {
            CheckSourceBottomSheet(
                viewModel = viewModel,
                onDismiss = { showCheckSourceSheet = false }
            )
        }

        if (showDirectLinkUploadSheet) {
            DirectLinkUploadBottomSheet(
                viewModel = viewModel,
                onDismiss = { showDirectLinkUploadSheet = false }
            )
        }

        if (showClearCacheDialog) {
            ConfirmDialog(
                title = stringResource(R.string.clear_cache),
                text = stringResource(R.string.sure_del),
                onConfirm = {
                    viewModel.clearCache(context)
                    showClearCacheDialog = false
                },
                onDismiss = { showClearCacheDialog = false }
            )
        }

        if (showShrinkDbDialog) {
            ConfirmDialog(
                title = stringResource(R.string.shrink_database),
                text = stringResource(R.string.sure),
                onConfirm = {
                    viewModel.shrinkDatabase()
                    showShrinkDbDialog = false
                },
                onDismiss = { showShrinkDbDialog = false }
            )
        }

        if (showClearWebViewDialog) {
            ConfirmDialog(
                title = stringResource(R.string.clear_webview_data),
                text = stringResource(R.string.sure_del),
                onConfirm = {
                    viewModel.clearWebViewData(context)
                    showClearWebViewDialog = false
                },
                onDismiss = { showClearWebViewDialog = false }
            )
        }

        if (showPasswordDialog) {
            AlertDialog(
                onDismissRequest = { showPasswordDialog = false },
                title = { Text(stringResource(R.string.set_local_password)) },
                text = {
                    TextField(
                        value = tempPassword,
                        onValueChange = { tempPassword = it },
                        label = { Text("Password") }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.setLocalPassword(tempPassword)
                        showPasswordDialog = false
                    }) { Text(stringResource(R.string.ok)) }
                },
                dismissButton = {
                    TextButton(onClick = { showPasswordDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
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
