package io.legado.app.ui.config.otherConfig

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.DropdownListSettingItem
import io.legado.app.ui.widget.components.settingItem.InputSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtherConfigScreen(
    state: OtherConfigUiState,
    onIntent: (OtherConfigIntent) -> Unit,
    onBackClick: () -> Unit,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.other_setting),
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
                SplicedColumnGroup {
                DropdownListSettingItem(
                    title = stringResource(R.string.language),
                    selectedValue = state.language,
                    displayEntries = stringArrayResource(R.array.language),
                    entryValues = stringArrayResource(R.array.language_value),
                    onValueChange = { onIntent(OtherConfigIntent.LanguageChanged(it)) }
                )

                DropdownListSettingItem(
                    title = stringResource(R.string.update_to_variant_title),
                    description = stringResource(R.string.update_to_variant_summary),
                    selectedValue = state.updateToVariant,
                    displayEntries = stringArrayResource(R.array.default_app_variant),
                    entryValues = stringArrayResource(R.array.default_app_variant_value),
                    onValueChange = { onIntent(OtherConfigIntent.UpdateToVariantChanged(it)) }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.auto_check_update_on_start_title),
                    description = stringResource(R.string.auto_check_update_on_start_summary),
                    checked = state.autoCheckUpdateOnStart,
                    onCheckedChange = { onIntent(OtherConfigIntent.AutoCheckUpdateOnStartChanged(it)) }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.web_service_auto_start),
                    checked = state.webServiceAutoStart,
                    onCheckedChange = { onIntent(OtherConfigIntent.WebServiceAutoStartChanged(it)) }
                )
            }

            SplicedColumnGroup(title = stringResource(R.string.main_activity)) {

                SwitchSettingItem(
                    title = stringResource(R.string.pt_auto_refresh),
                    description = stringResource(R.string.ps_auto_refresh),
                    checked = state.autoRefresh,
                    onCheckedChange = { onIntent(OtherConfigIntent.AutoRefreshChanged(it)) }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.pt_default_read),
                    description = stringResource(R.string.ps_default_read),
                    checked = state.defaultToRead,
                    onCheckedChange = { onIntent(OtherConfigIntent.DefaultToReadChanged(it)) }
                )
            }

            SplicedColumnGroup(title = stringResource(R.string.privacy)) {

                ClickableSettingItem(
                    title = stringResource(R.string.notification_permission),
                    description = stringResource(R.string.notification_permission_rationale),
                    onClick = { onIntent(OtherConfigIntent.RequestNotificationPermission) }
                )

                ClickableSettingItem(
                    title = stringResource(R.string.background_permission),
                    description = stringResource(R.string.ignore_battery_permission_rationale),
                    onClick = { onIntent(OtherConfigIntent.RequestBatteryPermission) }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.firebase_enable_title),
                    description = stringResource(R.string.firebase_enable_summary),
                    checked = state.firebaseEnable,
                    onCheckedChange = { onIntent(OtherConfigIntent.FirebaseEnableChanged(it)) }
                )

                ClickableSettingItem(
                    title = stringResource(R.string.set_local_password),
                    description = stringResource(R.string.set_local_password_summary),
                    onClick = { onIntent(OtherConfigIntent.ShowOverlay(OtherConfigOverlay.Password)) }
                )

            }

            SplicedColumnGroup(title = stringResource(R.string.read)) {

                ClickableSettingItem(
                    title = stringResource(R.string.book_tree_uri_t),
                    description = state.defaultBookTreeUri,
                    onClick = { onIntent(OtherConfigIntent.ShowOverlay(OtherConfigOverlay.FilePicker)) }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.anti_alias),
                    description = stringResource(R.string.pref_anti_alias_summary),
                    checked = state.antiAlias,
                    onCheckedChange = { onIntent(OtherConfigIntent.AntiAliasChanged(it)) }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.replace_enable_default_t),
                    description = stringResource(R.string.replace_enable_default_s),
                    checked = state.replaceEnableDefault,
                    onCheckedChange = { onIntent(OtherConfigIntent.ReplaceEnableDefaultChanged(it)) }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.media_button_on_exit_title),
                    description = stringResource(R.string.media_button_on_exit_summary),
                    checked = state.mediaButtonOnExit,
                    onCheckedChange = { onIntent(OtherConfigIntent.MediaButtonOnExitChanged(it)) }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.read_aloud_by_media_button_title),
                    description = stringResource(R.string.read_aloud_by_media_button_summary),
                    checked = state.readAloudByMediaButton,
                    onCheckedChange = { onIntent(OtherConfigIntent.ReadAloudByMediaButtonChanged(it)) }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.ignore_audio_focus_title),
                    description = stringResource(R.string.ignore_audio_focus_summary),
                    checked = state.ignoreAudioFocus,
                    onCheckedChange = { onIntent(OtherConfigIntent.IgnoreAudioFocusChanged(it)) }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.auto_clear_expired),
                    description = stringResource(R.string.auto_clear_expired_summary),
                    checked = state.autoClearExpired,
                    onCheckedChange = { onIntent(OtherConfigIntent.AutoClearExpiredChanged(it)) }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.show_add_to_shelf_alert_title),
                    description = stringResource(R.string.show_add_to_shelf_alert_summary),
                    checked = state.showAddToShelfAlert,
                    onCheckedChange = { onIntent(OtherConfigIntent.ShowAddToShelfAlertChanged(it)) }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.show_manga_ui),
                    checked = state.showMangaUi,
                    onCheckedChange = { onIntent(OtherConfigIntent.ShowMangaUiChanged(it)) }
                )
            }

                SplicedColumnGroup(title = stringResource(R.string.other_setting)) {

                SwitchSettingItem(
                    title = stringResource(R.string.web_service_wake_lock),
                    description = stringResource(R.string.web_service_wake_lock_summary),
                    checked = state.webServiceWakeLock,
                    onCheckedChange = { onIntent(OtherConfigIntent.WebServiceWakeLockChanged(it)) }
                )

                InputSettingItem(
                    title = stringResource(R.string.source_edit_text_max_line),
                    value = state.sourceEditMaxLine.toString(),
                    defaultValue = 500.toString(),
                    onConfirm = { onIntent(OtherConfigIntent.SourceEditMaxLineChanged(it.toIntOrNull() ?: 500)) }
                )

                ClickableSettingItem(
                    title = stringResource(R.string.check_source_config),
                    onClick = { onIntent(OtherConfigIntent.ShowOverlay(OtherConfigOverlay.CheckSource)) }
                )

                ClickableSettingItem(
                    title = stringResource(R.string.direct_link_upload_rule),
                    description = stringResource(R.string.direct_link_upload_rule_summary),
                    onClick = { onIntent(OtherConfigIntent.ShowOverlay(OtherConfigOverlay.DirectLinkUpload)) }
                )

                InputSettingItem(
                    title = stringResource(R.string.web_port_title),
                    value = state.webPort.toString(),
                    onConfirm = { onIntent(OtherConfigIntent.WebPortChanged(it.toInt())) }
                )

                ClickableSettingItem(
                    title = stringResource(R.string.clear_webview_data),
                    description = stringResource(R.string.clear_webview_data_summary),
                    onClick = { onIntent(OtherConfigIntent.ShowOverlay(OtherConfigOverlay.ClearWebViewConfirmation)) }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.add_to_text_context_menu_t),
                    description = stringResource(R.string.add_to_text_context_menu_s),
                    checked = state.processText,
                    onCheckedChange = { onIntent(OtherConfigIntent.ProcessTextChanged(it)) }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.record_log),
                    description = stringResource(R.string.record_debug_log),
                    checked = state.recordLog,
                    onCheckedChange = { onIntent(OtherConfigIntent.RecordLogChanged(it)) }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.record_heap_dump_t),
                    description = stringResource(R.string.record_heap_dump_s),
                    checked = state.recordHeapDump,
                    onCheckedChange = { onIntent(OtherConfigIntent.RecordHeapDumpChanged(it)) }
                )
                }
            }
        }
    }
}
