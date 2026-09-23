package io.legado.app.feature.settings.otherconfig

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import io.legado.app.feature.settings.res.Res
import io.legado.app.feature.settings.res.add_to_text_context_menu_s
import io.legado.app.feature.settings.res.add_to_text_context_menu_t
import io.legado.app.feature.settings.res.anti_alias
import io.legado.app.feature.settings.res.auto_check_update_on_start_summary
import io.legado.app.feature.settings.res.auto_check_update_on_start_title
import io.legado.app.feature.settings.res.auto_clear_expired
import io.legado.app.feature.settings.res.auto_clear_expired_summary
import io.legado.app.feature.settings.res.background_permission
import io.legado.app.feature.settings.res.book_tree_uri_t
import io.legado.app.feature.settings.res.clear_webview_data
import io.legado.app.feature.settings.res.clear_webview_data_summary
import io.legado.app.feature.settings.res.default_app_variant
import io.legado.app.feature.settings.res.default_app_variant_value
import io.legado.app.feature.settings.res.direct_link_upload_rule
import io.legado.app.feature.settings.res.direct_link_upload_rule_summary
import io.legado.app.feature.settings.res.firebase_enable_summary
import io.legado.app.feature.settings.res.firebase_enable_title
import io.legado.app.feature.settings.res.ignore_audio_focus_summary
import io.legado.app.feature.settings.res.ignore_audio_focus_title
import io.legado.app.feature.settings.res.ignore_battery_permission_rationale
import io.legado.app.feature.settings.res.language
import io.legado.app.feature.settings.res.language_value
import io.legado.app.feature.settings.res.main_activity
import io.legado.app.feature.settings.res.media_button_on_exit_summary
import io.legado.app.feature.settings.res.media_button_on_exit_title
import io.legado.app.feature.settings.res.notification_permission
import io.legado.app.feature.settings.res.notification_permission_rationale
import io.legado.app.feature.settings.res.other_setting
import io.legado.app.feature.settings.res.pref_anti_alias_summary
import io.legado.app.feature.settings.res.privacy
import io.legado.app.feature.settings.res.ps_auto_refresh
import io.legado.app.feature.settings.res.ps_default_read
import io.legado.app.feature.settings.res.pt_auto_refresh
import io.legado.app.feature.settings.res.pt_default_read
import io.legado.app.feature.settings.res.read
import io.legado.app.feature.settings.res.read_aloud_by_media_button_summary
import io.legado.app.feature.settings.res.read_aloud_by_media_button_title
import io.legado.app.feature.settings.res.record_debug_log
import io.legado.app.feature.settings.res.record_heap_dump_s
import io.legado.app.feature.settings.res.record_heap_dump_t
import io.legado.app.feature.settings.res.record_log
import io.legado.app.feature.settings.res.replace_enable_default_s
import io.legado.app.feature.settings.res.replace_enable_default_t
import io.legado.app.feature.settings.res.set_local_password
import io.legado.app.feature.settings.res.set_local_password_summary
import io.legado.app.feature.settings.res.show_add_to_shelf_alert_summary
import io.legado.app.feature.settings.res.show_add_to_shelf_alert_title
import io.legado.app.feature.settings.res.show_manga_ui
import io.legado.app.feature.settings.res.source_edit_text_max_line
import io.legado.app.feature.settings.res.update_to_variant_summary
import io.legado.app.feature.settings.res.update_to_variant_title
import io.legado.app.feature.settings.res.web_port_title
import io.legado.app.feature.settings.res.web_service_auto_start
import io.legado.app.feature.settings.res.web_service_wake_lock
import io.legado.app.feature.settings.res.web_service_wake_lock_summary
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
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

// M5-8b：从 `:app` 的 `io.legado.app.ui.config.otherConfig` 迁来。
//
// 差异三类（**结构逐字保留**，含原文那两处缩进瑕疵 —— 这样 `git mv` 出来的 diff 只反映
// 语义改动，reviewer 不必在格式噪音里找差异）：
//
//   ① `androidx.compose.ui.res.stringResource` / `stringArrayResource`
//      → `org.jetbrains.compose.resources.*`；
//   ② `R.string.*` → `Res.string.*`（51 条，另 3 条 VM 文案上一片已加）；
//   ③ `R.array.*` → `Res.array.*`（4 个数组）。
//
// ⚠️ **`stringArrayResource` 的返回值类型与 androidx 不同**：CMP 返回 `List<String>`，
// 故要多一次 `.toTypedArray()`（`DropdownListSettingItem` 收的是 `Array<String>`）。
// 同模块的 `CustomThemeScreen` 已经是这个写法。
//
// ⚠️ **4 个数组的搬迁不是纯搬运**（详见 `:feature:settings` 的 arrays.xml 注释）：
// `default_app_variant` 在 `:app` 里是 `@string/*` 间接引用，Android 逐项按当前语言解析，
// 而共享层的数组约定是**纯字面量** ⇒ 这里写的是解析后的结果。
// 其中 zh-rHK / zh-rTW 的第 3 项是 `All Version`：`:app` 的 `all_version` 只在
// `values/` 与 `values-zh-rCN/` 的 arrays.xml 里定义，那两个语言会回落到英文 ——
// **既有行为，逐字保留**。
//
// `OtherConfigRouteScreen`（权限/SAF/重启/WebService 的宿主壳）与
// `DirectLinkUploadBottomSheet`（剪贴板/GSON/文件选择）**留在 `:app`**。

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
                title = stringResource(Res.string.other_setting),
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
                    title = stringResource(Res.string.language),
                    selectedValue = state.language,
                    displayEntries = stringArrayResource(Res.array.language).toTypedArray(),
                    entryValues = stringArrayResource(Res.array.language_value).toTypedArray(),
                    onValueChange = { onIntent(OtherConfigIntent.LanguageChanged(it)) }
                )

                DropdownListSettingItem(
                    title = stringResource(Res.string.update_to_variant_title),
                    description = stringResource(Res.string.update_to_variant_summary),
                    selectedValue = state.updateToVariant,
                    displayEntries = stringArrayResource(Res.array.default_app_variant).toTypedArray(),
                    entryValues = stringArrayResource(Res.array.default_app_variant_value).toTypedArray(),
                    onValueChange = { onIntent(OtherConfigIntent.UpdateToVariantChanged(it)) }
                )

                SwitchSettingItem(
                    title = stringResource(Res.string.auto_check_update_on_start_title),
                    description = stringResource(Res.string.auto_check_update_on_start_summary),
                    checked = state.autoCheckUpdateOnStart,
                    onCheckedChange = { onIntent(OtherConfigIntent.AutoCheckUpdateOnStartChanged(it)) }
                )

                SwitchSettingItem(
                    title = stringResource(Res.string.web_service_auto_start),
                    checked = state.webServiceAutoStart,
                    onCheckedChange = { onIntent(OtherConfigIntent.WebServiceAutoStartChanged(it)) }
                )
            }

            SplicedColumnGroup(title = stringResource(Res.string.main_activity)) {

                SwitchSettingItem(
                    title = stringResource(Res.string.pt_auto_refresh),
                    description = stringResource(Res.string.ps_auto_refresh),
                    checked = state.autoRefresh,
                    onCheckedChange = { onIntent(OtherConfigIntent.AutoRefreshChanged(it)) }
                )

                SwitchSettingItem(
                    title = stringResource(Res.string.pt_default_read),
                    description = stringResource(Res.string.ps_default_read),
                    checked = state.defaultToRead,
                    onCheckedChange = { onIntent(OtherConfigIntent.DefaultToReadChanged(it)) }
                )
            }

            SplicedColumnGroup(title = stringResource(Res.string.privacy)) {

                ClickableSettingItem(
                    title = stringResource(Res.string.notification_permission),
                    description = stringResource(Res.string.notification_permission_rationale),
                    onClick = { onIntent(OtherConfigIntent.RequestNotificationPermission) }
                )

                ClickableSettingItem(
                    title = stringResource(Res.string.background_permission),
                    description = stringResource(Res.string.ignore_battery_permission_rationale),
                    onClick = { onIntent(OtherConfigIntent.RequestBatteryPermission) }
                )

                SwitchSettingItem(
                    title = stringResource(Res.string.firebase_enable_title),
                    description = stringResource(Res.string.firebase_enable_summary),
                    checked = state.firebaseEnable,
                    onCheckedChange = { onIntent(OtherConfigIntent.FirebaseEnableChanged(it)) }
                )

                ClickableSettingItem(
                    title = stringResource(Res.string.set_local_password),
                    description = stringResource(Res.string.set_local_password_summary),
                    onClick = { onIntent(OtherConfigIntent.ShowOverlay(OtherConfigOverlay.Password)) }
                )

            }

            SplicedColumnGroup(title = stringResource(Res.string.read)) {

                ClickableSettingItem(
                    title = stringResource(Res.string.book_tree_uri_t),
                    description = state.defaultBookTreeUri,
                    onClick = { onIntent(OtherConfigIntent.ShowOverlay(OtherConfigOverlay.FilePicker)) }
                )

                SwitchSettingItem(
                    title = stringResource(Res.string.anti_alias),
                    description = stringResource(Res.string.pref_anti_alias_summary),
                    checked = state.antiAlias,
                    onCheckedChange = { onIntent(OtherConfigIntent.AntiAliasChanged(it)) }
                )

                SwitchSettingItem(
                    title = stringResource(Res.string.replace_enable_default_t),
                    description = stringResource(Res.string.replace_enable_default_s),
                    checked = state.replaceEnableDefault,
                    onCheckedChange = { onIntent(OtherConfigIntent.ReplaceEnableDefaultChanged(it)) }
                )

                SwitchSettingItem(
                    title = stringResource(Res.string.media_button_on_exit_title),
                    description = stringResource(Res.string.media_button_on_exit_summary),
                    checked = state.mediaButtonOnExit,
                    onCheckedChange = { onIntent(OtherConfigIntent.MediaButtonOnExitChanged(it)) }
                )

                SwitchSettingItem(
                    title = stringResource(Res.string.read_aloud_by_media_button_title),
                    description = stringResource(Res.string.read_aloud_by_media_button_summary),
                    checked = state.readAloudByMediaButton,
                    onCheckedChange = { onIntent(OtherConfigIntent.ReadAloudByMediaButtonChanged(it)) }
                )

                SwitchSettingItem(
                    title = stringResource(Res.string.ignore_audio_focus_title),
                    description = stringResource(Res.string.ignore_audio_focus_summary),
                    checked = state.ignoreAudioFocus,
                    onCheckedChange = { onIntent(OtherConfigIntent.IgnoreAudioFocusChanged(it)) }
                )

                SwitchSettingItem(
                    title = stringResource(Res.string.auto_clear_expired),
                    description = stringResource(Res.string.auto_clear_expired_summary),
                    checked = state.autoClearExpired,
                    onCheckedChange = { onIntent(OtherConfigIntent.AutoClearExpiredChanged(it)) }
                )

                SwitchSettingItem(
                    title = stringResource(Res.string.show_add_to_shelf_alert_title),
                    description = stringResource(Res.string.show_add_to_shelf_alert_summary),
                    checked = state.showAddToShelfAlert,
                    onCheckedChange = { onIntent(OtherConfigIntent.ShowAddToShelfAlertChanged(it)) }
                )

                SwitchSettingItem(
                    title = stringResource(Res.string.show_manga_ui),
                    checked = state.showMangaUi,
                    onCheckedChange = { onIntent(OtherConfigIntent.ShowMangaUiChanged(it)) }
                )
            }

                SplicedColumnGroup(title = stringResource(Res.string.other_setting)) {

                SwitchSettingItem(
                    title = stringResource(Res.string.web_service_wake_lock),
                    description = stringResource(Res.string.web_service_wake_lock_summary),
                    checked = state.webServiceWakeLock,
                    onCheckedChange = { onIntent(OtherConfigIntent.WebServiceWakeLockChanged(it)) }
                )

                InputSettingItem(
                    title = stringResource(Res.string.source_edit_text_max_line),
                    value = state.sourceEditMaxLine.toString(),
                    defaultValue = 500.toString(),
                    onConfirm = { onIntent(OtherConfigIntent.SourceEditMaxLineChanged(it.toIntOrNull() ?: 500)) }
                )

                ClickableSettingItem(
                    title = stringResource(Res.string.direct_link_upload_rule),
                    description = stringResource(Res.string.direct_link_upload_rule_summary),
                    onClick = { onIntent(OtherConfigIntent.ShowOverlay(OtherConfigOverlay.DirectLinkUpload)) }
                )

                InputSettingItem(
                    title = stringResource(Res.string.web_port_title),
                    value = state.webPort.toString(),
                    onConfirm = { onIntent(OtherConfigIntent.WebPortChanged(it.toInt())) }
                )

                ClickableSettingItem(
                    title = stringResource(Res.string.clear_webview_data),
                    description = stringResource(Res.string.clear_webview_data_summary),
                    onClick = { onIntent(OtherConfigIntent.ShowOverlay(OtherConfigOverlay.ClearWebViewConfirmation)) }
                )

                SwitchSettingItem(
                    title = stringResource(Res.string.add_to_text_context_menu_t),
                    description = stringResource(Res.string.add_to_text_context_menu_s),
                    checked = state.processText,
                    onCheckedChange = { onIntent(OtherConfigIntent.ProcessTextChanged(it)) }
                )

                SwitchSettingItem(
                    title = stringResource(Res.string.record_log),
                    description = stringResource(Res.string.record_debug_log),
                    checked = state.recordLog,
                    onCheckedChange = { onIntent(OtherConfigIntent.RecordLogChanged(it)) }
                )

                SwitchSettingItem(
                    title = stringResource(Res.string.record_heap_dump_t),
                    description = stringResource(Res.string.record_heap_dump_s),
                    checked = state.recordHeapDump,
                    onCheckedChange = { onIntent(OtherConfigIntent.RecordHeapDumpChanged(it)) }
                )
                }
            }
        }
    }
}
