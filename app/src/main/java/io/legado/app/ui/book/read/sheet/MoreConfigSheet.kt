package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.data.repository.ReadPreferences
import io.legado.app.data.repository.ReadSettingsRepository
import io.legado.app.ui.book.read.ConfigUpdate
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.book.read.ReadBookSheet
import io.legado.app.ui.widget.components.SectionTitle
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.TinyClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.TinyDropdownSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySwitchSettingItem
import org.koin.compose.koinInject

@Composable
fun MoreConfigSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onIntent: (ReadBookIntent) -> Unit,
    onOpenClickRegionalConfig: () -> Unit,
    onOpenPageKeyConfig: () -> Unit,
    onOpenTextSelectMenuConfig: () -> Unit,
) {
    val readSettingsRepository: ReadSettingsRepository = koinInject()
    val preferences by readSettingsRepository.preferences.collectAsStateWithLifecycle(
        initialValue = ReadPreferences()
    )

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.more_setting),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // Screen settings
            SectionTitle(stringResource(R.string.screen_settings))
            ScreenSettings(
                preferences = preferences,
                onScreenOrientationChange = {
                    onIntent(ReadBookIntent.SetOrientation(it))
                },
                onKeepLightChange = {
                    onIntent(ReadBookIntent.KeepLightChanged(it))
                },
                onHideStatusBarChange = {
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.HideStatusBar(it)))
                },
                onHideNavigationBarChange = {
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.HideNavigationBar(it)))
                },
                onPaddingDisplayCutoutsChange = {
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.PaddingDisplayCutouts(it)))
                },
                onReadBodyToLhChange = {
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.ReadBodyToLh(it)))
                },
                onTextFullJustifyChange = {
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TextFullJustify(it)))
                },
                onTextBottomJustifyChange = {
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TextBottomJustify(it)))
                },
                onAdaptSpecialStyleChange = {
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.AdaptSpecialStyle(it)))
                },
                onUseZhLayoutChange = {
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.UseZhLayout(it)))
                },
                onUseUnderlineChange = {
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.UseUnderlineGlobal(it)))
                },
                onOpenEyeProtectionConfig = {
                    onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.EyeProtection))
                }
            )

            // Page control
            SectionTitle(stringResource(R.string.page_control))
            PageControlSettings(
                preferences = preferences,
                onDoubleHorizontalPageChange = {
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.DoubleHorizontalPage(it)))
                },
                onProgressBarBehaviorChange = {
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.ProgressBarBehavior(it)))
                },
                onMouseWheelPageChange = {
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.MouseWheelPage(it)))
                },
                onVolumeKeyPageChange = {
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.VolumeKeyPage(it)))
                },
                onVolumeKeyPageOnPlayChange = {
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.VolumeKeyPageOnPlay(it)))
                },
                onKeyPageOnLongPressChange = {
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.KeyPageOnLongPress(it)))
                },
            )

            // Other
            SectionTitle(stringResource(R.string.other))
            OtherSettings(
                preferences = preferences,
                onSliderVibratorChange = {
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.SliderVibrator(it)))
                },
                onSelectVibratorChange = {
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.SelectVibrator(it)))
                },
                onAutoChangeSourceChange = {
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.AutoChangeSource(it)))
                },
                onDefaultSourceChangeAllChange = {
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.DefaultSourceChangeAll(it)))
                },
                onAutoSuggestDayNightChange = {
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.AutoSuggestDayNight(it)))
                },
                onSelectTextChange = {
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.SelectText(it)))
                },
                onNoAnimScrollPageChange = {
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.NoAnimScrollPage(it)))
                },
                onOptimizeRenderChange = {
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.OptimizeRender(it)))
                },
                onClickImgWayChange = {
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.ClickImgWay(it)))
                },
                onOpenClickRegionalConfig = onOpenClickRegionalConfig,
                onOpenPageKeyConfig = onOpenPageKeyConfig,
                onOpenTextSelectMenuConfig = onOpenTextSelectMenuConfig,
                onDisableReturnKeyChange = {
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.DisableReturnKey(it)))
                },
                onShowReadTitleAdditionChange = {
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.ShowReadTitleAddition(it)))
                },
                onShowMenuIconChange = {
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.ShowMenuIcon(it)))
                },
            )
        }
    }
}

@Composable
private fun ScreenSettings(
    preferences: ReadPreferences,
    onScreenOrientationChange: (String) -> Unit,
    onKeepLightChange: (String) -> Unit,
    onHideStatusBarChange: (Boolean) -> Unit,
    onHideNavigationBarChange: (Boolean) -> Unit,
    onPaddingDisplayCutoutsChange: (Boolean) -> Unit,
    onReadBodyToLhChange: (Boolean) -> Unit,
    onTextFullJustifyChange: (Boolean) -> Unit,
    onTextBottomJustifyChange: (Boolean) -> Unit,
    onAdaptSpecialStyleChange: (Boolean) -> Unit,
    onUseZhLayoutChange: (Boolean) -> Unit,
    onUseUnderlineChange: (Boolean) -> Unit,
    onOpenEyeProtectionConfig:() -> Unit,
) {
    val screenDirectionEntries = stringArrayResource(R.array.screen_direction_title)
    val screenDirectionValues = stringArrayResource(R.array.screen_direction_value)
    val keepLightEntries = stringArrayResource(R.array.screen_time_out)
    val keepLightValues = stringArrayResource(R.array.screen_time_out_value)

    TinyDropdownSettingItem(
        title = stringResource(R.string.screen_direction),
        selectedValue = preferences.screenOrientation,
        displayEntries = screenDirectionEntries,
        entryValues = screenDirectionValues,
        onValueChange = onScreenOrientationChange,
    )
    TinyDropdownSettingItem(
        title = stringResource(R.string.keep_light),
        selectedValue = preferences.keepLight,
        displayEntries = keepLightEntries,
        entryValues = keepLightValues,
        onValueChange = onKeepLightChange,
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.pt_hide_status_bar),
        checked = preferences.hideStatusBar,
        onCheckedChange = onHideStatusBarChange,
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.pt_hide_navigation_bar),
        checked = preferences.hideNavigationBar,
        onCheckedChange = onHideNavigationBarChange,
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.padding_display_cutouts),
        checked = preferences.paddingDisplayCutouts,
        onCheckedChange = onPaddingDisplayCutoutsChange,
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.read_body_to_lh),
        checked = preferences.readBodyToLh,
        onCheckedChange = onReadBodyToLhChange,
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.text_full_justify),
        checked = preferences.textFullJustify,
        onCheckedChange = onTextFullJustifyChange,
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.text_bottom_justify),
        checked = preferences.textBottomJustify,
        onCheckedChange = onTextBottomJustifyChange,
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.adapt_special_style),
        checked = preferences.adaptSpecialStyle,
        onCheckedChange = onAdaptSpecialStyleChange,
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.use_zh_layout),
        checked = preferences.useZhLayout,
        onCheckedChange = onUseZhLayoutChange,
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.use_underline),
        checked = preferences.useUnderline,
        onCheckedChange = onUseUnderlineChange,
    )
    TinyClickableSettingItem(
        title = stringResource(R.string.eye_protection),
        onClick = onOpenEyeProtectionConfig,
    )
}

@Composable
private fun PageControlSettings(
    preferences: ReadPreferences,
    onDoubleHorizontalPageChange: (String) -> Unit,
    onProgressBarBehaviorChange: (String) -> Unit,
    onMouseWheelPageChange: (Boolean) -> Unit,
    onVolumeKeyPageChange: (Boolean) -> Unit,
    onVolumeKeyPageOnPlayChange: (Boolean) -> Unit,
    onKeyPageOnLongPressChange: (Boolean) -> Unit,
) {
    val doublePageEntries = stringArrayResource(R.array.double_page_title)
    val doublePageValues = stringArrayResource(R.array.double_page_value)
    val progressBarEntries = stringArrayResource(R.array.progress_bar_behavior_title)
    val progressBarValues = stringArrayResource(R.array.progress_bar_behavior_value)

    TinyDropdownSettingItem(
        title = stringResource(R.string.double_page_horizontal),
        selectedValue = preferences.doubleHorizontalPage,
        displayEntries = doublePageEntries,
        entryValues = doublePageValues,
        onValueChange = onDoubleHorizontalPageChange,
    )
    TinyDropdownSettingItem(
        title = stringResource(R.string.progress_bar_behavior),
        selectedValue = preferences.progressBarBehavior,
        displayEntries = progressBarEntries,
        entryValues = progressBarValues,
        onValueChange = onProgressBarBehaviorChange,
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.mouse_wheel_page),
        checked = preferences.mouseWheelPage,
        onCheckedChange = onMouseWheelPageChange,
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.volume_key_page),
        checked = preferences.volumeKeyPage,
        onCheckedChange = onVolumeKeyPageChange,
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.volume_key_page_on_play),
        checked = preferences.volumeKeyPageOnPlay,
        onCheckedChange = onVolumeKeyPageOnPlayChange,
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.key_page_on_long_press),
        checked = preferences.keyPageOnLongPress,
        onCheckedChange = onKeyPageOnLongPressChange,
    )
}

@Composable
private fun OtherSettings(
    preferences: ReadPreferences,
    onSliderVibratorChange: (Boolean) -> Unit,
    onSelectVibratorChange: (Boolean) -> Unit,
    onAutoChangeSourceChange: (Boolean) -> Unit,
    onDefaultSourceChangeAllChange: (Boolean) -> Unit,
    onAutoSuggestDayNightChange: (Boolean) -> Unit,
    onSelectTextChange: (Boolean) -> Unit,
    onNoAnimScrollPageChange: (Boolean) -> Unit,
    onOptimizeRenderChange: (Boolean) -> Unit,
    onClickImgWayChange: (String) -> Unit,
    onOpenClickRegionalConfig: () -> Unit,
    onDisableReturnKeyChange: (Boolean) -> Unit,
    onOpenPageKeyConfig: () -> Unit,
    onOpenTextSelectMenuConfig: () -> Unit,
    onShowReadTitleAdditionChange: (Boolean) -> Unit,
    onShowMenuIconChange: (Boolean) -> Unit,
) {
    val clickImageWayEntries = stringArrayResource(R.array.click_image_way_title)
    val clickImageWayValues = stringArrayResource(R.array.click_image_way_value)

    TinySwitchSettingItem(
        title = stringResource(R.string.enable_slider_vibrator),
        checked = preferences.sliderVibrator,
        onCheckedChange = onSliderVibratorChange,
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.enable_select_vibrator),
        checked = preferences.selectVibrator,
        onCheckedChange = onSelectVibratorChange,
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.auto_change_source),
        checked = preferences.autoChangeSource,
        onCheckedChange = onAutoChangeSourceChange,
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.read_change_all),
        description = stringResource(R.string.read_change_all_s),
        checked = preferences.defaultSourceChangeAll,
        onCheckedChange = onDefaultSourceChangeAllChange,
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.auto_switch_theme_reminder_title),
        description = stringResource(R.string.auto_switch_theme_reminder_desc),
        checked = preferences.autoSuggestDayNight,
        onCheckedChange = onAutoSuggestDayNightChange,
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.selectText),
        checked = preferences.selectText,
        onCheckedChange = onSelectTextChange,
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.no_anim_scroll_page),
        checked = preferences.noAnimScrollPage,
        onCheckedChange = onNoAnimScrollPageChange,
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.enable_optimize_render),
        checked = preferences.optimizeRender,
        onCheckedChange = onOptimizeRenderChange,
    )
    TinyDropdownSettingItem(
        title = stringResource(R.string.click_image_way),
        selectedValue = preferences.clickImgWay,
        displayEntries = clickImageWayEntries,
        entryValues = clickImageWayValues,
        onValueChange = onClickImgWayChange,
    )
    TinyClickableSettingItem(
        title = stringResource(R.string.click_regional_config),
        onClick = onOpenClickRegionalConfig,
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.disable_return_key),
        checked = preferences.disableReturnKey,
        onCheckedChange = onDisableReturnKeyChange,
    )
    TinyClickableSettingItem(
        title = stringResource(R.string.custom_page_key),
        onClick = onOpenPageKeyConfig,
    )
    TinyClickableSettingItem(
        title = stringResource(R.string.edit_select_menu),
        onClick = onOpenTextSelectMenuConfig,
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.show_read_title_addition),
        checked = preferences.showReadTitleAddition,
        onCheckedChange = onShowReadTitleAdditionChange,
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.show_menu_icon),
        checked = preferences.showMenuIcon,
        onCheckedChange = onShowMenuIconChange,
    )
}
