package io.legado.app.ui.config.readConfig

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import io.legado.app.ui.widget.components.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.button.TopbarNavigationButton
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.DropdownListSettingItem
import io.legado.app.ui.widget.components.settingItem.SliderSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.utils.canvasrecorder.CanvasRecorderFactory
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadConfigScreen(
    onBackClick: () -> Unit,
    viewModel: ReadConfigViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    var showPageKeySheet by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = {
                    Text(stringResource(R.string.read_config))
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

            SplicedColumnGroup(title = stringResource(R.string.screen_settings)) {
                DropdownListSettingItem(
                    title = stringResource(R.string.screen_direction),
                    selectedValue = ReadConfig.screenOrientation,
                    displayEntries = stringArrayResource(R.array.screen_direction_title),
                    entryValues = stringArrayResource(R.array.screen_direction_value),
                    onValueChange = { ReadConfig.screenOrientation = it }
                )

                DropdownListSettingItem(
                    title = stringResource(R.string.keep_light),
                    selectedValue = ReadConfig.keepLight,
                    displayEntries = stringArrayResource(R.array.screen_time_out),
                    entryValues = stringArrayResource(R.array.screen_time_out_value),
                    onValueChange = { ReadConfig.keepLight = it }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.pt_hide_status_bar),
                    checked = ReadConfig.hideStatusBar,
                    onCheckedChange = { viewModel.updateHideStatusBar(it) }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.pt_hide_navigation_bar),
                    checked = ReadConfig.hideNavigationBar,
                    onCheckedChange = { viewModel.updateHideNavigationBar(it) }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.padding_display_cutouts),
                    checked = ReadConfig.paddingDisplayCutouts,
                    onCheckedChange = { ReadConfig.paddingDisplayCutouts = it }
                )

                DropdownListSettingItem(
                    title = stringResource(R.string.title_bar_mode),
                    selectedValue = ReadConfig.titleBarMode,
                    displayEntries = stringArrayResource(R.array.title_bar_mode),
                    entryValues = stringArrayResource(R.array.title_bar_mode_value),
                    onValueChange = { ReadConfig.titleBarMode = it }
                )

                SliderSettingItem(
                    title = stringResource(R.string.menu_alpha),
                    description = stringResource(R.string.menu_alpha_sum, ReadConfig.menuAlpha),
                    value = ReadConfig.menuAlpha.toFloat(),
                    defaultValue = 100f,
                    valueRange = 0f..100f,
                    onValueChange = { viewModel.updateMenuAlpha(it.toInt()) }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.read_body_to_lh),
                    checked = ReadConfig.readBodyToLh,
                    onCheckedChange = { ReadConfig.readBodyToLh = it }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.read_change_all),
                    description = stringResource(R.string.read_change_all_s),
                    checked = ReadConfig.defaultSourceChangeAll,
                    onCheckedChange = { ReadConfig.defaultSourceChangeAll = it }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.text_full_justify),
                    checked = ReadConfig.textFullJustify,
                    onCheckedChange = {
                        ReadConfig.textFullJustify = it
                        viewModel.upLayout()
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.text_bottom_justify),
                    checked = ReadConfig.textBottomJustify,
                    onCheckedChange = {
                        ReadConfig.textBottomJustify = it
                        viewModel.upLayout()
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.adapt_special_style),
                    checked = ReadConfig.adaptSpecialStyle,
                    onCheckedChange = { ReadConfig.adaptSpecialStyle = it }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.use_zh_layout),
                    checked = ReadConfig.useZhLayout,
                    onCheckedChange = {
                        ReadConfig.useZhLayout = it
                        viewModel.upLayout()
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.show_brightness_view),
                    checked = ReadConfig.showBrightnessView,
                    onCheckedChange = { ReadConfig.showBrightnessView = it }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.use_underline),
                    checked = ReadConfig.useUnderline,
                    onCheckedChange = { ReadConfig.useUnderline = it }
                )
            }

            SplicedColumnGroup(title = stringResource(R.string.page_control)) {
                DropdownListSettingItem(
                    title = stringResource(R.string.read_slider_mode),
                    selectedValue = ReadConfig.readSliderMode,
                    displayEntries = stringArrayResource(R.array.read_slider_mode),
                    entryValues = stringArrayResource(R.array.read_slider_mode_value),
                    onValueChange = { viewModel.updateReadSliderMode(it) }
                )

                DropdownListSettingItem(
                    title = stringResource(R.string.double_page_horizontal),
                    selectedValue = ReadConfig.doubleHorizontalPage,
                    displayEntries = stringArrayResource(R.array.double_page_title),
                    entryValues = stringArrayResource(R.array.double_page_value),
                    onValueChange = {
                        ReadConfig.doubleHorizontalPage = it
                        viewModel.upLayout()
                    }
                )

                DropdownListSettingItem(
                    title = stringResource(R.string.progress_bar_behavior),
                    selectedValue = ReadConfig.progressBarBehavior,
                    displayEntries = stringArrayResource(R.array.progress_bar_behavior_title),
                    entryValues = stringArrayResource(R.array.progress_bar_behavior_value),
                    onValueChange = { viewModel.updateProgressBarBehavior(it) }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.mouse_wheel_page),
                    checked = ReadConfig.mouseWheelPage,
                    onCheckedChange = { ReadConfig.mouseWheelPage = it }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.volume_key_page),
                    checked = ReadConfig.volumeKeyPage,
                    onCheckedChange = { ReadConfig.volumeKeyPage = it }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.volume_key_page_on_play),
                    checked = ReadConfig.volumeKeyPageOnPlay,
                    onCheckedChange = { ReadConfig.volumeKeyPageOnPlay = it }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.key_page_on_long_press),
                    checked = ReadConfig.keyPageOnLongPress,
                    onCheckedChange = { ReadConfig.keyPageOnLongPress = it }
                )

                SliderSettingItem(
                    title = stringResource(R.string.page_touch_slop_title),
                    description = stringResource(
                        R.string.page_touch_slop_summary,
                        ReadConfig.pageTouchSlop
                    ),
                    value = ReadConfig.pageTouchSlop.toFloat(),
                    defaultValue = 0f,
                    valueRange = 0f..1000f,
                    onValueChange = { viewModel.updatePageTouchSlop(it.toInt()) }
                )
            }

            SplicedColumnGroup(title = stringResource(R.string.other)) {
                SwitchSettingItem(
                    title = stringResource(R.string.enable_slider_vibrator),
                    checked = ReadConfig.sliderVibrator,
                    onCheckedChange = { ReadConfig.sliderVibrator = it }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.enable_select_vibrator),
                    checked = ReadConfig.selectVibrator,
                    onCheckedChange = { ReadConfig.selectVibrator = it }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.auto_change_source),
                    checked = ReadConfig.autoChangeSource,
                    onCheckedChange = { ReadConfig.autoChangeSource = it }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.selectText),
                    checked = ReadConfig.selectText,
                    onCheckedChange = { ReadConfig.selectText = it }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.no_anim_scroll_page),
                    checked = ReadConfig.noAnimScrollPage,
                    onCheckedChange = {
                        ReadConfig.noAnimScrollPage = it
                        viewModel.upPageAnim()
                    }
                )

                DropdownListSettingItem(
                    title = stringResource(R.string.click_image_way),
                    selectedValue = ReadConfig.clickImgWay,
                    displayEntries = stringArrayResource(R.array.click_image_way_title),
                    entryValues = stringArrayResource(R.array.click_image_way_value),
                    onValueChange = { ReadConfig.clickImgWay = it }
                )

                if (CanvasRecorderFactory.isSupport) {
                    SwitchSettingItem(
                        title = stringResource(R.string.enable_optimize_render),
                        checked = ReadConfig.optimizeRender,
                        onCheckedChange = {
                            ReadConfig.optimizeRender = it
                            viewModel.upStyle()
                        }
                    )
                }

                ClickableSettingItem(
                    title = stringResource(R.string.click_regional_config),
                    onClick = { /* 暂时留空 */ }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.disable_return_key),
                    checked = ReadConfig.disableReturnKey,
                    onCheckedChange = { ReadConfig.disableReturnKey = it }
                )

                ClickableSettingItem(
                    title = stringResource(R.string.custom_page_key),
                    onClick = { showPageKeySheet = true }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.expand_text_menu),
                    checked = ReadConfig.expandTextMenu,
                    onCheckedChange = { ReadConfig.expandTextMenu = it }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.show_read_title_addition),
                    checked = ReadConfig.showReadTitleAddition,
                    onCheckedChange = { ReadConfig.showReadTitleAddition = it }
                )
            }
        }
    }

    if (showPageKeySheet) {
        PageKeySheet(onDismissRequest = { showPageKeySheet = false })
    }
}