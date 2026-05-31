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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.ui.config.readConfig.ReadConfig
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.TinyClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.TinyDropdownSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySwitchSettingItem
import io.legado.app.utils.postEvent

@Composable
fun MoreConfigSheet(
    onDismissRequest: () -> Unit,
    onOpenClickRegionalConfig: () -> Unit,
    onOpenPageKeyConfig: () -> Unit,
) {
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
            ScreenSettings()

            // Page control
            SectionTitle(stringResource(R.string.page_control))
            PageControlSettings()

            // Other
            SectionTitle(stringResource(R.string.other))
            OtherSettings(
                onOpenClickRegionalConfig = onOpenClickRegionalConfig,
                onOpenPageKeyConfig = onOpenPageKeyConfig,
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
private fun ScreenSettings() {
    var hideStatusBar by remember { mutableStateOf(ReadBookConfig.hideStatusBar) }
    var hideNavigationBar by remember { mutableStateOf(ReadBookConfig.hideNavigationBar) }

    val screenDirectionEntries = stringArrayResource(R.array.screen_direction_title)
    val screenDirectionValues = stringArrayResource(R.array.screen_direction_value)
    val keepLightEntries = stringArrayResource(R.array.screen_time_out)
    val keepLightValues = stringArrayResource(R.array.screen_time_out_value)
    val titleBarModeEntries = stringArrayResource(R.array.title_bar_mode)
    val titleBarModeValues = stringArrayResource(R.array.title_bar_mode_value)

    TinyDropdownSettingItem(
        title = stringResource(R.string.screen_direction),
        selectedValue = ReadConfig.screenOrientation,
        displayEntries = screenDirectionEntries,
        entryValues = screenDirectionValues,
        onValueChange = { ReadConfig.screenOrientation = it },
    )
    TinyDropdownSettingItem(
        title = stringResource(R.string.keep_light),
        selectedValue = ReadConfig.keepLight,
        displayEntries = keepLightEntries,
        entryValues = keepLightValues,
        onValueChange = { ReadConfig.keepLight = it },
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.pt_hide_status_bar),
        checked = hideStatusBar,
        onCheckedChange = {
            hideStatusBar = it
            ReadBookConfig.hideStatusBar = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(0, 2))
        },
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.pt_hide_navigation_bar),
        checked = hideNavigationBar,
        onCheckedChange = {
            hideNavigationBar = it
            ReadBookConfig.hideNavigationBar = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(0, 2))
        },
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.padding_display_cutouts),
        checked = ReadConfig.paddingDisplayCutouts,
        onCheckedChange = {
            ReadConfig.paddingDisplayCutouts = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(2))
        },
    )
    TinyDropdownSettingItem(
        title = stringResource(R.string.title_bar_mode),
        selectedValue = ReadConfig.titleBarMode,
        displayEntries = titleBarModeEntries,
        entryValues = titleBarModeValues,
        onValueChange = {
            ReadConfig.titleBarMode = it
            postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
        },
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.read_body_to_lh),
        checked = ReadConfig.readBodyToLh,
        onCheckedChange = {
            ReadConfig.readBodyToLh = it
        },
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.text_full_justify),
        checked = ReadConfig.textFullJustify,
        onCheckedChange = {
            ReadConfig.textFullJustify = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(5))
        },
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.text_bottom_justify),
        checked = ReadConfig.textBottomJustify,
        onCheckedChange = {
            ReadConfig.textBottomJustify = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(5))
        },
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.adapt_special_style),
        checked = ReadConfig.adaptSpecialStyle,
        onCheckedChange = {
            ReadConfig.adaptSpecialStyle = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(5))
        },
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.use_zh_layout),
        checked = ReadConfig.useZhLayout,
        onCheckedChange = {
            ReadConfig.useZhLayout = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(5))
        },
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.show_brightness_view),
        checked = ReadConfig.showBrightnessView,
        onCheckedChange = {
            ReadConfig.showBrightnessView = it
            postEvent(PreferKey.showBrightnessView, "")
        },
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.use_underline),
        checked = ReadConfig.useUnderline,
        onCheckedChange = {
            ReadConfig.useUnderline = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(5))
        },
    )
}

@Composable
private fun PageControlSettings() {
    val readSliderModeEntries = stringArrayResource(R.array.read_slider_mode)
    val readSliderModeValues = stringArrayResource(R.array.read_slider_mode_value)
    val doublePageEntries = stringArrayResource(R.array.double_page_title)
    val doublePageValues = stringArrayResource(R.array.double_page_value)
    val progressBarEntries = stringArrayResource(R.array.progress_bar_behavior_title)
    val progressBarValues = stringArrayResource(R.array.progress_bar_behavior_value)

    TinyDropdownSettingItem(
        title = stringResource(R.string.read_slider_mode),
        selectedValue = ReadConfig.readSliderMode,
        displayEntries = readSliderModeEntries,
        entryValues = readSliderModeValues,
        onValueChange = {
            ReadConfig.readSliderMode = it
            postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
        },
    )
    TinyDropdownSettingItem(
        title = stringResource(R.string.double_page_horizontal),
        selectedValue = ReadConfig.doubleHorizontalPage,
        displayEntries = doublePageEntries,
        entryValues = doublePageValues,
        onValueChange = {
            ReadConfig.doubleHorizontalPage = it
            ChapterProvider.upLayout()
            ReadBook.loadContent(false)
        },
    )
    TinyDropdownSettingItem(
        title = stringResource(R.string.progress_bar_behavior),
        selectedValue = ReadConfig.progressBarBehavior,
        displayEntries = progressBarEntries,
        entryValues = progressBarValues,
        onValueChange = {
            ReadConfig.progressBarBehavior = it
            postEvent(EventBus.UP_SEEK_BAR, true)
        },
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.mouse_wheel_page),
        checked = ReadConfig.mouseWheelPage,
        onCheckedChange = { ReadConfig.mouseWheelPage = it },
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.volume_key_page),
        checked = ReadConfig.volumeKeyPage,
        onCheckedChange = { ReadConfig.volumeKeyPage = it },
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.volume_key_page_on_play),
        checked = ReadConfig.volumeKeyPageOnPlay,
        onCheckedChange = { ReadConfig.volumeKeyPageOnPlay = it },
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.key_page_on_long_press),
        checked = ReadConfig.keyPageOnLongPress,
        onCheckedChange = { ReadConfig.keyPageOnLongPress = it },
    )
}

@Composable
private fun OtherSettings(
    onOpenClickRegionalConfig: () -> Unit,
    onOpenPageKeyConfig: () -> Unit,
) {
    val clickImageWayEntries = stringArrayResource(R.array.click_image_way_title)
    val clickImageWayValues = stringArrayResource(R.array.click_image_way_value)

    TinySwitchSettingItem(
        title = stringResource(R.string.enable_slider_vibrator),
        checked = ReadConfig.sliderVibrator,
        onCheckedChange = { ReadConfig.sliderVibrator = it },
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.enable_select_vibrator),
        checked = ReadConfig.selectVibrator,
        onCheckedChange = { ReadConfig.selectVibrator = it },
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.auto_change_source),
        checked = ReadConfig.autoChangeSource,
        onCheckedChange = { ReadConfig.autoChangeSource = it },
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.selectText),
        checked = ReadConfig.selectText,
        onCheckedChange = { ReadConfig.selectText = it },
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.no_anim_scroll_page),
        checked = ReadConfig.noAnimScrollPage,
        onCheckedChange = {
            ReadConfig.noAnimScrollPage = it
            ReadBook.callBack?.upPageAnim()
        },
    )
    TinyDropdownSettingItem(
        title = stringResource(R.string.click_image_way),
        selectedValue = ReadConfig.clickImgWay,
        displayEntries = clickImageWayEntries,
        entryValues = clickImageWayValues,
        onValueChange = { ReadConfig.clickImgWay = it },
    )
    TinyClickableSettingItem(
        title = stringResource(R.string.click_regional_config),
        onClick = onOpenClickRegionalConfig,
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.disable_return_key),
        checked = ReadConfig.disableReturnKey,
        onCheckedChange = { ReadConfig.disableReturnKey = it },
    )
    TinyClickableSettingItem(
        title = stringResource(R.string.custom_page_key),
        onClick = onOpenPageKeyConfig,
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.expand_text_menu),
        checked = ReadConfig.expandTextMenu,
        onCheckedChange = { ReadConfig.expandTextMenu = it },
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.show_read_title_addition),
        checked = ReadConfig.showReadTitleAddition,
        onCheckedChange = {
            ReadConfig.showReadTitleAddition = it
            postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
        },
    )
}
