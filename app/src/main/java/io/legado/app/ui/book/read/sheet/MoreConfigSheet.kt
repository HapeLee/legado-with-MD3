package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.data.repository.ReadPreferences
import io.legado.app.data.repository.ReadSettingsRepository
import io.legado.app.ui.book.read.ConfigUpdate
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.TinyClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.TinyDropdownSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySwitchSettingItem
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun MoreConfigSheet(
    onDismissRequest: () -> Unit,
    onIntent: (ReadBookIntent) -> Unit,
    onOpenClickRegionalConfig: () -> Unit,
    onOpenPageKeyConfig: () -> Unit,
) {
    val readSettingsRepository: ReadSettingsRepository = koinInject()
    val preferences by readSettingsRepository.preferences.collectAsStateWithLifecycle(
        initialValue = ReadPreferences()
    )
    val scope = rememberCoroutineScope()

    AppModalBottomSheet(
        show = true,
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
                    scope.launch { readSettingsRepository.setScreenOrientation(it) }
                },
                onKeepLightChange = {
                    scope.launch { readSettingsRepository.setKeepLight(it) }
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
                    scope.launch { readSettingsRepository.setReadBodyToLh(it) }
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
                onShowBrightnessViewChange = {
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.ShowBrightnessView(it)))
                },
                onUseUnderlineChange = {
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.UseUnderlineGlobal(it)))
                },
            )

            // Page control
            SectionTitle(stringResource(R.string.page_control))
            PageControlSettings(
                preferences = preferences,
                onReadSliderModeChange = {
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.ReadSliderMode(it)))
                },
                onDoubleHorizontalPageChange = {
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.DoubleHorizontalPage(it)))
                },
                onProgressBarBehaviorChange = {
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.ProgressBarBehavior(it)))
                },
                onMouseWheelPageChange = {
                    scope.launch { readSettingsRepository.setMouseWheelPage(it) }
                },
                onVolumeKeyPageChange = {
                    scope.launch { readSettingsRepository.setVolumeKeyPage(it) }
                },
                onVolumeKeyPageOnPlayChange = {
                    scope.launch { readSettingsRepository.setVolumeKeyPageOnPlay(it) }
                },
                onKeyPageOnLongPressChange = {
                    scope.launch { readSettingsRepository.setKeyPageOnLongPress(it) }
                },
            )

            // Other
            SectionTitle(stringResource(R.string.other))
            OtherSettings(
                preferences = preferences,
                onSliderVibratorChange = {
                    scope.launch { readSettingsRepository.setSliderVibrator(it) }
                },
                onSelectVibratorChange = {
                    scope.launch { readSettingsRepository.setSelectVibrator(it) }
                },
                onAutoChangeSourceChange = {
                    scope.launch { readSettingsRepository.setAutoChangeSource(it) }
                },
                onSelectTextChange = {
                    scope.launch { readSettingsRepository.setSelectText(it) }
                },
                onNoAnimScrollPageChange = {
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.NoAnimScrollPage(it)))
                },
                onClickImgWayChange = {
                    scope.launch { readSettingsRepository.setClickImgWay(it) }
                },
                onOpenClickRegionalConfig = onOpenClickRegionalConfig,
                onDisableReturnKeyChange = {
                    scope.launch { readSettingsRepository.setDisableReturnKey(it) }
                },
                onOpenPageKeyConfig = onOpenPageKeyConfig,
                onExpandTextMenuChange = {
                    scope.launch { readSettingsRepository.setExpandTextMenu(it) }
                },
                onShowReadTitleAdditionChange = {
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.ShowReadTitleAddition(it)))
                },
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
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
    onShowBrightnessViewChange: (Boolean) -> Unit,
    onUseUnderlineChange: (Boolean) -> Unit,
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
        title = stringResource(R.string.show_brightness_view),
        checked = preferences.showBrightnessView,
        onCheckedChange = onShowBrightnessViewChange,
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.use_underline),
        checked = preferences.useUnderline,
        onCheckedChange = onUseUnderlineChange,
    )
}

@Composable
private fun PageControlSettings(
    preferences: ReadPreferences,
    onReadSliderModeChange: (String) -> Unit,
    onDoubleHorizontalPageChange: (String) -> Unit,
    onProgressBarBehaviorChange: (String) -> Unit,
    onMouseWheelPageChange: (Boolean) -> Unit,
    onVolumeKeyPageChange: (Boolean) -> Unit,
    onVolumeKeyPageOnPlayChange: (Boolean) -> Unit,
    onKeyPageOnLongPressChange: (Boolean) -> Unit,
) {
    val readSliderModeEntries = stringArrayResource(R.array.read_slider_mode)
    val readSliderModeValues = stringArrayResource(R.array.read_slider_mode_value)
    val doublePageEntries = stringArrayResource(R.array.double_page_title)
    val doublePageValues = stringArrayResource(R.array.double_page_value)
    val progressBarEntries = stringArrayResource(R.array.progress_bar_behavior_title)
    val progressBarValues = stringArrayResource(R.array.progress_bar_behavior_value)

    TinyDropdownSettingItem(
        title = stringResource(R.string.read_slider_mode),
        selectedValue = preferences.readSliderMode,
        displayEntries = readSliderModeEntries,
        entryValues = readSliderModeValues,
        onValueChange = onReadSliderModeChange,
    )
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
    onSelectTextChange: (Boolean) -> Unit,
    onNoAnimScrollPageChange: (Boolean) -> Unit,
    onClickImgWayChange: (String) -> Unit,
    onOpenClickRegionalConfig: () -> Unit,
    onDisableReturnKeyChange: (Boolean) -> Unit,
    onOpenPageKeyConfig: () -> Unit,
    onExpandTextMenuChange: (Boolean) -> Unit,
    onShowReadTitleAdditionChange: (Boolean) -> Unit,
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
        title = stringResource(R.string.selectText),
        checked = preferences.selectText,
        onCheckedChange = onSelectTextChange,
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.no_anim_scroll_page),
        checked = preferences.noAnimScrollPage,
        onCheckedChange = onNoAnimScrollPageChange,
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
    TinySwitchSettingItem(
        title = stringResource(R.string.expand_text_menu),
        checked = preferences.expandTextMenu,
        onCheckedChange = onExpandTextMenuChange,
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.show_read_title_addition),
        checked = preferences.showReadTitleAddition,
        onCheckedChange = onShowReadTitleAdditionChange,
    )
}
