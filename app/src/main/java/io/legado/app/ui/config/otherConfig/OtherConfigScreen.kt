package io.legado.app.ui.config.otherConfig

import io.legado.app.feature.settings.search.SearchableSetting
import io.legado.app.feature.settings.search.SearchableSettingsList
import androidx.compose.foundation.layout.fillMaxSize
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
        SearchableSettingsList(
            modifier = Modifier.fillMaxSize(),
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding(),
                bottom = 120.dp
            )
        ) {
            item {
                SplicedColumnGroup {
                SearchableSetting("other.language") {
                    DropdownListSettingItem(
                        title = stringResource(R.string.language),
                        selectedValue = state.language,
                        displayEntries = stringArrayResource(R.array.language),
                        entryValues = stringArrayResource(R.array.language_value),
                        onValueChange = { onIntent(OtherConfigIntent.LanguageChanged(it)) }
                    )
                }

                SearchableSetting("other.update_to_variant_title") {
                    DropdownListSettingItem(
                        title = stringResource(R.string.update_to_variant_title),
                        description = stringResource(R.string.update_to_variant_summary),
                        selectedValue = state.updateToVariant,
                        displayEntries = stringArrayResource(R.array.default_app_variant),
                        entryValues = stringArrayResource(R.array.default_app_variant_value),
                        onValueChange = { onIntent(OtherConfigIntent.UpdateToVariantChanged(it)) }
                    )
                }

                SearchableSetting("other.update_endpoint_title") {
                    DropdownListSettingItem(
                        title = stringResource(R.string.update_endpoint_title),
                        description = stringResource(R.string.update_endpoint_summary),
                        selectedValue = state.updateEndpoint,
                        displayEntries = stringArrayResource(R.array.update_endpoint),
                        entryValues = stringArrayResource(R.array.update_endpoint_value),
                        onValueChange = { onIntent(OtherConfigIntent.UpdateEndpointChanged(it)) }
                    )
                }

                SearchableSetting("other.auto_check_update_on_start_title") {
                    SwitchSettingItem(
                        title = stringResource(R.string.auto_check_update_on_start_title),
                        description = stringResource(R.string.auto_check_update_on_start_summary),
                        checked = state.autoCheckUpdateOnStart,
                        onCheckedChange = { onIntent(OtherConfigIntent.AutoCheckUpdateOnStartChanged(it)) }
                    )
                }

                SearchableSetting("other.web_service_auto_start") {
                    SwitchSettingItem(
                        title = stringResource(R.string.web_service_auto_start),
                        checked = state.webServiceAutoStart,
                        onCheckedChange = { onIntent(OtherConfigIntent.WebServiceAutoStartChanged(it)) }
                    )
                }
            }

            SplicedColumnGroup(title = stringResource(R.string.main_activity)) {

                SearchableSetting("other.pt_auto_refresh") {
                    SwitchSettingItem(
                        title = stringResource(R.string.pt_auto_refresh),
                        description = stringResource(R.string.ps_auto_refresh),
                        checked = state.autoRefresh,
                        onCheckedChange = { onIntent(OtherConfigIntent.AutoRefreshChanged(it)) }
                    )
                }

                SearchableSetting("other.pt_default_read") {
                    SwitchSettingItem(
                        title = stringResource(R.string.pt_default_read),
                        description = stringResource(R.string.ps_default_read),
                        checked = state.defaultToRead,
                        onCheckedChange = { onIntent(OtherConfigIntent.DefaultToReadChanged(it)) }
                    )
                }
            }

            SplicedColumnGroup(title = stringResource(R.string.read)) {

                SearchableSetting("other.book_tree_uri_t") {
                    ClickableSettingItem(
                        title = stringResource(R.string.book_tree_uri_t),
                        description = state.defaultBookTreeUri,
                        onClick = { onIntent(OtherConfigIntent.ShowOverlay(OtherConfigOverlay.FilePicker)) }
                    )
                }

                SearchableSetting("other.anti_alias") {
                    SwitchSettingItem(
                        title = stringResource(R.string.anti_alias),
                        description = stringResource(R.string.pref_anti_alias_summary),
                        checked = state.antiAlias,
                        onCheckedChange = { onIntent(OtherConfigIntent.AntiAliasChanged(it)) }
                    )
                }

                SearchableSetting("other.replace_enable_default_t") {
                    SwitchSettingItem(
                        title = stringResource(R.string.replace_enable_default_t),
                        description = stringResource(R.string.replace_enable_default_s),
                        checked = state.replaceEnableDefault,
                        onCheckedChange = { onIntent(OtherConfigIntent.ReplaceEnableDefaultChanged(it)) }
                    )
                }

                SearchableSetting("other.media_button_on_exit_title") {
                    SwitchSettingItem(
                        title = stringResource(R.string.media_button_on_exit_title),
                        description = stringResource(R.string.media_button_on_exit_summary),
                        checked = state.mediaButtonOnExit,
                        onCheckedChange = { onIntent(OtherConfigIntent.MediaButtonOnExitChanged(it)) }
                    )
                }

                SearchableSetting("other.read_aloud_by_media_button_title") {
                    SwitchSettingItem(
                        title = stringResource(R.string.read_aloud_by_media_button_title),
                        description = stringResource(R.string.read_aloud_by_media_button_summary),
                        checked = state.readAloudByMediaButton,
                        onCheckedChange = { onIntent(OtherConfigIntent.ReadAloudByMediaButtonChanged(it)) }
                    )
                }

                SearchableSetting("other.ignore_audio_focus_title") {
                    SwitchSettingItem(
                        title = stringResource(R.string.ignore_audio_focus_title),
                        description = stringResource(R.string.ignore_audio_focus_summary),
                        checked = state.ignoreAudioFocus,
                        onCheckedChange = { onIntent(OtherConfigIntent.IgnoreAudioFocusChanged(it)) }
                    )
                }

                SearchableSetting("other.auto_clear_expired") {
                    SwitchSettingItem(
                        title = stringResource(R.string.auto_clear_expired),
                        description = stringResource(R.string.auto_clear_expired_summary),
                        checked = state.autoClearExpired,
                        onCheckedChange = { onIntent(OtherConfigIntent.AutoClearExpiredChanged(it)) }
                    )
                }

                SearchableSetting("other.show_add_to_shelf_alert_title") {
                    SwitchSettingItem(
                        title = stringResource(R.string.show_add_to_shelf_alert_title),
                        description = stringResource(R.string.show_add_to_shelf_alert_summary),
                        checked = state.showAddToShelfAlert,
                        onCheckedChange = { onIntent(OtherConfigIntent.ShowAddToShelfAlertChanged(it)) }
                    )
                }

            }

                SplicedColumnGroup(title = stringResource(R.string.other_setting)) {

                    SearchableSetting("other.background_battery_optimization_title") {
                        ClickableSettingItem(
                            title = stringResource(R.string.background_battery_optimization_title),
                            description = stringResource(
                                if (state.isBatteryIgnored) R.string.battery_optimization_status_ignored
                                else R.string.battery_optimization_status_not_ignored
                            ),
                            onClick = { onIntent(OtherConfigIntent.ShowOverlay(OtherConfigOverlay.BatteryOptimizationGuide)) }
                        )
                    }

                SearchableSetting("other.web_service_wake_lock") {
                    SwitchSettingItem(
                        title = stringResource(R.string.web_service_wake_lock),
                        description = stringResource(R.string.web_service_wake_lock_summary),
                        checked = state.webServiceWakeLock,
                        onCheckedChange = { onIntent(OtherConfigIntent.WebServiceWakeLockChanged(it)) }
                    )
                }

                SearchableSetting("other.source_edit_text_max_line") {
                    InputSettingItem(
                        title = stringResource(R.string.source_edit_text_max_line),
                        value = state.sourceEditMaxLine.toString(),
                        defaultValue = 500.toString(),
                        onConfirm = { onIntent(OtherConfigIntent.SourceEditMaxLineChanged(it.toIntOrNull() ?: 500)) }
                    )
                }

                SearchableSetting("other.direct_link_upload_rule") {
                    ClickableSettingItem(
                        title = stringResource(R.string.direct_link_upload_rule),
                        description = stringResource(R.string.direct_link_upload_rule_summary),
                        onClick = { onIntent(OtherConfigIntent.ShowOverlay(OtherConfigOverlay.DirectLinkUpload)) }
                    )
                }

                SearchableSetting("other.web_port_title") {
                    InputSettingItem(
                        title = stringResource(R.string.web_port_title),
                        value = state.webPort.toString(),
                        onConfirm = { onIntent(OtherConfigIntent.WebPortChanged(it.toInt())) }
                    )
                }

                SearchableSetting("other.clear_webview_data") {
                    ClickableSettingItem(
                        title = stringResource(R.string.clear_webview_data),
                        description = stringResource(R.string.clear_webview_data_summary),
                        onClick = { onIntent(OtherConfigIntent.ShowOverlay(OtherConfigOverlay.ClearWebViewConfirmation)) }
                    )
                }

                    SearchableSetting("other.block_source_browser") {
                        SwitchSettingItem(
                            title = stringResource(R.string.block_source_browser),
                            description = stringResource(R.string.block_source_browser_summary),
                            checked = state.blockSourceBrowser,
                            onCheckedChange = { onIntent(OtherConfigIntent.BlockSourceBrowserChanged(it)) }
                        )
                    }

                SearchableSetting("other.add_to_text_context_menu_t") {
                    SwitchSettingItem(
                        title = stringResource(R.string.add_to_text_context_menu_t),
                        description = stringResource(R.string.add_to_text_context_menu_s),
                        checked = state.processText,
                        onCheckedChange = { onIntent(OtherConfigIntent.ProcessTextChanged(it)) }
                    )
                }

                SearchableSetting("other.record_log") {
                    SwitchSettingItem(
                        title = stringResource(R.string.record_log),
                        description = stringResource(R.string.record_debug_log),
                        checked = state.recordLog,
                        onCheckedChange = { onIntent(OtherConfigIntent.RecordLogChanged(it)) }
                    )
                }

                SearchableSetting("other.record_heap_dump_t") {
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
}
