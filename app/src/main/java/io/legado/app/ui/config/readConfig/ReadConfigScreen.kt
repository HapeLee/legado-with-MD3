package io.legado.app.ui.config.readConfig

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
import io.legado.app.ui.book.read.sheet.ClickActionConfigSheet
import io.legado.app.ui.book.read.sheet.EyeProtectionConfigSheet
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.DropdownListSettingItem
import io.legado.app.ui.widget.components.settingItem.SliderSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.utils.canvasrecorder.CanvasRecorderFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadConfigScreen(
    state: ReadConfigUiState,
    onIntent: (ReadConfigIntent) -> Unit,
    onBackClick: () -> Unit,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val settings = state

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.read_config),
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
                SplicedColumnGroup(title = stringResource(R.string.screen_settings)) {
                DropdownListSettingItem(
                    title = stringResource(R.string.screen_direction),
                    selectedValue = settings.screenOrientation,
                    displayEntries = stringArrayResource(R.array.screen_direction_title),
                    entryValues = stringArrayResource(R.array.screen_direction_value),
                    onValueChange = {
                        onIntent(ReadConfigIntent.ScreenOrientationChanged(it))
                    }
                )

                DropdownListSettingItem(
                    title = stringResource(R.string.keep_light),
                    selectedValue = settings.keepLight,
                    displayEntries = stringArrayResource(R.array.screen_time_out),
                    entryValues = stringArrayResource(R.array.screen_time_out_value),
                    onValueChange = {
                        onIntent(ReadConfigIntent.KeepLightChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.pt_hide_status_bar),
                    checked = settings.hideStatusBar,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.HideStatusBarChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.pt_hide_navigation_bar),
                    checked = settings.hideNavigationBar,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.HideNavigationBarChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.padding_display_cutouts),
                    checked = settings.paddingDisplayCutouts,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.PaddingDisplayCutoutsChanged(it))
                    }
                )

                DropdownListSettingItem(
                    title = stringResource(R.string.title_bar_mode),
                    selectedValue = settings.titleBarMode,
                    displayEntries = stringArrayResource(R.array.title_bar_mode),
                    entryValues = stringArrayResource(R.array.title_bar_mode_value),
                    onValueChange = {
                        onIntent(ReadConfigIntent.TitleBarModeChanged(it))
                    }
                )

                SliderSettingItem(
                    title = stringResource(R.string.menu_alpha),
                    description = stringResource(R.string.menu_alpha_sum, settings.readMenuBlurAlpha),
                    value = settings.readMenuBlurAlpha.toFloat(),
                    defaultValue = 60f,
                    valueRange = 0f..100f,
                    onValueChange = {
                        onIntent(ReadConfigIntent.ReadMenuBlurAlphaChanged(it.toInt()))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.read_body_to_lh),
                    checked = settings.readBodyToLh,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.ReadBodyToLhChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.read_change_all),
                    description = stringResource(R.string.read_change_all_s),
                    checked = settings.defaultSourceChangeAll,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.DefaultSourceChangeAllChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.text_full_justify),
                    checked = settings.textFullJustify,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.TextFullJustifyChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.text_bottom_justify),
                    checked = settings.textBottomJustify,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.TextBottomJustifyChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.adapt_special_style),
                    checked = settings.adaptSpecialStyle,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.AdaptSpecialStyleChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.use_zh_layout),
                    checked = settings.useZhLayout,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.UseZhLayoutChanged(it))
                    }
                )

                ClickableSettingItem(
                    title = stringResource(R.string.eye_protection),
                    option = if (settings.eyeProtectionEnabled) {
                        stringResource(R.string.enabled)
                    } else {
                        stringResource(R.string.disabled)
                    },
                    onClick = { onIntent(ReadConfigIntent.OpenEyeProtection) },
                )

                    DropdownListSettingItem(
                    title = stringResource(R.string.show_brightness_view),
                        selectedValue = settings.showBrightnessView,
                        displayEntries = stringArrayResource(R.array.brightness_bar_mode_title),
                        entryValues = stringArrayResource(R.array.brightness_bar_mode_value),
                        onValueChange = {
                        onIntent(ReadConfigIntent.ShowBrightnessViewChanged(it))
                    }
                )

                    if (settings.showBrightnessView == "2") {
                        DropdownListSettingItem(
                            title = stringResource(R.string.brightness_bar_position),
                            selectedValue = settings.brightnessVwPos,
                            displayEntries = stringArrayResource(R.array.brightness_bar_position_title),
                            entryValues = stringArrayResource(R.array.brightness_bar_position_value),
                            onValueChange = {
                                onIntent(ReadConfigIntent.BrightnessVwPosChanged(it))
                            }
                        )
                    }

                SwitchSettingItem(
                    title = stringResource(R.string.use_underline),
                    checked = settings.useUnderline,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.UseUnderlineChanged(it))
                    }
                )
            }

            SplicedColumnGroup(title = stringResource(R.string.page_control)) {
                DropdownListSettingItem(
                    title = stringResource(R.string.read_slider_mode),
                    selectedValue = settings.readSliderMode,
                    displayEntries = stringArrayResource(R.array.read_slider_mode),
                    entryValues = stringArrayResource(R.array.read_slider_mode_value),
                    onValueChange = {
                        onIntent(ReadConfigIntent.ReadSliderModeChanged(it))
                    }
                )

                DropdownListSettingItem(
                    title = stringResource(R.string.double_page_horizontal),
                    selectedValue = settings.doubleHorizontalPage,
                    displayEntries = stringArrayResource(R.array.double_page_title),
                    entryValues = stringArrayResource(R.array.double_page_value),
                    onValueChange = {
                        onIntent(ReadConfigIntent.DoubleHorizontalPageChanged(it))
                    }
                )

                DropdownListSettingItem(
                    title = stringResource(R.string.progress_bar_behavior),
                    selectedValue = settings.progressBarBehavior,
                    displayEntries = stringArrayResource(R.array.progress_bar_behavior_title),
                    entryValues = stringArrayResource(R.array.progress_bar_behavior_value),
                    onValueChange = {
                        onIntent(ReadConfigIntent.ProgressBarBehaviorChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.mouse_wheel_page),
                    checked = settings.mouseWheelPage,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.MouseWheelPageChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.volume_key_page),
                    checked = settings.volumeKeyPage,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.VolumeKeyPageChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.volume_key_page_on_play),
                    checked = settings.volumeKeyPageOnPlay,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.VolumeKeyPageOnPlayChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.key_page_on_long_press),
                    checked = settings.keyPageOnLongPress,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.KeyPageOnLongPressChanged(it))
                    }
                )

                SliderSettingItem(
                    title = stringResource(R.string.page_touch_slop_title),
                    description = stringResource(
                        R.string.page_touch_slop_summary,
                        settings.pageTouchSlop
                    ),
                    value = settings.pageTouchSlop.toFloat(),
                    defaultValue = 0f,
                    valueRange = 0f..1000f,
                    onValueChange = {
                        onIntent(ReadConfigIntent.PageTouchSlopChanged(it.toInt()))
                    }
                )
            }

                SplicedColumnGroup(title = stringResource(R.string.other)) {
                SwitchSettingItem(
                    title = stringResource(R.string.enable_slider_vibrator),
                    checked = settings.sliderVibrator,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.SliderVibratorChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.enable_select_vibrator),
                    checked = settings.selectVibrator,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.SelectVibratorChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.auto_change_source),
                    checked = settings.autoChangeSource,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.AutoChangeSourceChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.auto_switch_theme_reminder_title),
                    description = stringResource(R.string.auto_switch_theme_reminder_desc),
                    checked = settings.autoSuggestDayNight,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.AutoSuggestDayNightChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.selectText),
                    checked = settings.selectText,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.SelectTextChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.no_anim_scroll_page),
                    checked = settings.noAnimScrollPage,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.NoAnimScrollPageChanged(it))
                    }
                )

                DropdownListSettingItem(
                    title = stringResource(R.string.click_image_way),
                    selectedValue = settings.clickImgWay,
                    displayEntries = stringArrayResource(R.array.click_image_way_title),
                    entryValues = stringArrayResource(R.array.click_image_way_value),
                    onValueChange = {
                        onIntent(ReadConfigIntent.ClickImgWayChanged(it))
                    }
                )

                if (CanvasRecorderFactory.isSupport) {
                    SwitchSettingItem(
                        title = stringResource(R.string.enable_optimize_render),
                        checked = settings.optimizeRender,
                        onCheckedChange = {
                            onIntent(ReadConfigIntent.OptimizeRenderChanged(it))
                        }
                    )
                }

                ClickableSettingItem(
                    title = stringResource(R.string.click_regional_config),
                    onClick = { onIntent(ReadConfigIntent.OpenClickActions) }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.disable_return_key),
                    checked = settings.disableReturnKey,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.DisableReturnKeyChanged(it))
                    }
                )

                ClickableSettingItem(
                    title = stringResource(R.string.custom_page_key),
                    onClick = { onIntent(ReadConfigIntent.OpenPageKeys) }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.show_read_title_addition),
                    checked = settings.showReadTitleAddition,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.ShowReadTitleAdditionChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.show_menu_icon),
                    checked = settings.showMenuIcon,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.ShowMenuIconChanged(it))
                    }
                )
                }
            }
        }
    }

    PageKeySheet(
        show = state.activeSheet == ReadConfigSheet.PageKeys,
        prevKeys = settings.prevKeys,
        nextKeys = settings.nextKeys,
        onDismissRequest = { onIntent(ReadConfigIntent.DismissSheet) },
        onConfirm = { prevKeys, nextKeys ->
            onIntent(ReadConfigIntent.PageKeysChanged(prevKeys, nextKeys))
        }
    )

    if (state.activeSheet == ReadConfigSheet.ClickActions) {
        ClickActionConfigSheet(
            onDismissRequest = { onIntent(ReadConfigIntent.DismissSheet) },
        )
    }

    EyeProtectionConfigSheet(
        show = state.activeSheet == ReadConfigSheet.EyeProtection,
        enabled = settings.eyeProtectionEnabled,
        intensity = settings.eyeProtectionIntensity,
        autoNight = settings.eyeProtectionAutoNight,
        onDismissRequest = { onIntent(ReadConfigIntent.DismissSheet) },
        onEnabledChange = { onIntent(ReadConfigIntent.EyeProtectionEnabledChanged(it)) },
        onIntensityChange = { onIntent(ReadConfigIntent.EyeProtectionIntensityChanged(it)) },
        onAutoNightChange = { onIntent(ReadConfigIntent.EyeProtectionAutoNightChanged(it)) },
    )
}
