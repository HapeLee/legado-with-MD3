package io.legado.app.ui.config.readConfig

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.feature.settings.reading.ReadSetting
import io.legado.app.feature.settings.reading.ReadSettingSection
import io.legado.app.feature.settings.search.SettingSearchTarget
import io.legado.app.ui.book.read.sheet.ClickActionConfigSheet
import io.legado.app.ui.book.read.sheet.EyeProtectionConfigSheet
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.DropdownListSettingItem
import io.legado.app.ui.widget.components.settingItem.SliderSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadConfigScreen(
    state: ReadConfigUiState,
    onIntent: (ReadConfigIntent) -> Unit,
    onBackClick: () -> Unit,
    focusSettingId: String? = null,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val settings = state
    val listState = rememberLazyListState()
    val requestedSetting = ReadSetting.fromId(focusSettingId)
    val target = requestedSetting?.navigationTarget(settings.showBrightnessView)
    var focusHandled by rememberSaveable(focusSettingId) { mutableStateOf(false) }
    var groupReady by remember(focusSettingId) { mutableStateOf(false) }
    LaunchedEffect(focusSettingId) {
        if (target != null && !focusHandled) {
            listState.scrollToItem(ReadSettingSection.entries.indexOf(target.section))
            groupReady = true
        }
    }

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
        @Composable
        fun Setting(setting: ReadSetting, content: @Composable () -> Unit) {
            SettingSearchTarget(
                settingId = setting.id,
                locate = setting == target && groupReady && !focusHandled,
                topInset = paddingValues.calculateTopPadding(),
                onLocated = { focusHandled = true },
            ) {
                content()
                if (setting == ReadSetting.BrightnessMode &&
                    requestedSetting == ReadSetting.BrightnessPosition && target != requestedSetting
                ) {
                    AppText(
                        stringResource(
                            R.string.settings_search_brightness_guidance,
                            stringResource(R.string.brightness_bar_vertical),
                        )
                    )
                }
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding(),
                bottom = 120.dp
            )
        ) {
            item(ReadSettingSection.Screen.name) {
                SplicedColumnGroup(title = stringResource(ReadSettingSection.Screen.titleRes)) {
                    Setting(ReadSetting.Orientation) {
                        DropdownListSettingItem(
                            title = stringResource(ReadSetting.Orientation.titleRes),
                            selectedValue = settings.screenOrientation,
                            displayEntries = stringArrayResource(R.array.screen_direction_title),
                            entryValues = stringArrayResource(R.array.screen_direction_value),
                            onValueChange = {
                                onIntent(ReadConfigIntent.ScreenOrientationChanged(it))
                            }
                        )
                    }

                    Setting(ReadSetting.KeepLight) {
                        DropdownListSettingItem(
                            title = stringResource(ReadSetting.KeepLight.titleRes),
                            selectedValue = settings.keepLight,
                            displayEntries = stringArrayResource(R.array.screen_time_out),
                            entryValues = stringArrayResource(R.array.screen_time_out_value),
                            onValueChange = {
                                onIntent(ReadConfigIntent.KeepLightChanged(it))
                            }
                        )
                    }

                    Setting(ReadSetting.HideStatusBar) {
                        SwitchSettingItem(
                            title = stringResource(ReadSetting.HideStatusBar.titleRes),
                            checked = settings.hideStatusBar,
                            onCheckedChange = {
                                onIntent(ReadConfigIntent.HideStatusBarChanged(it))
                            }
                        )
                    }

                    Setting(ReadSetting.HideNavigationBar) {
                        SwitchSettingItem(
                            title = stringResource(ReadSetting.HideNavigationBar.titleRes),
                            checked = settings.hideNavigationBar,
                            onCheckedChange = {
                                onIntent(ReadConfigIntent.HideNavigationBarChanged(it))
                            }
                        )
                    }

                    Setting(ReadSetting.DisplayCutouts) {
                        SwitchSettingItem(
                            title = stringResource(ReadSetting.DisplayCutouts.titleRes),
                            checked = settings.paddingDisplayCutouts,
                            onCheckedChange = {
                                onIntent(ReadConfigIntent.PaddingDisplayCutoutsChanged(it))
                            }
                        )
                    }

                    Setting(ReadSetting.TitleBar) {
                        DropdownListSettingItem(
                            title = stringResource(ReadSetting.TitleBar.titleRes),
                            selectedValue = settings.titleBarMode,
                            displayEntries = stringArrayResource(R.array.title_bar_mode),
                            entryValues = stringArrayResource(R.array.title_bar_mode_value),
                            onValueChange = {
                                onIntent(ReadConfigIntent.TitleBarModeChanged(it))
                            }
                        )
                    }

                    Setting(ReadSetting.MenuOpacity) {
                        SliderSettingItem(
                            title = stringResource(ReadSetting.MenuOpacity.titleRes),
                            description = stringResource(R.string.menu_alpha_sum, settings.readMenuBlurAlpha),
                            value = settings.readMenuBlurAlpha.toFloat(),
                            defaultValue = 60f,
                            valueRange = 0f..100f,
                            onValueChange = {
                                onIntent(ReadConfigIntent.ReadMenuBlurAlphaChanged(it.toInt()))
                            }
                        )
                    }

                    Setting(ReadSetting.BodyToLineHeight) {
                        SwitchSettingItem(
                            title = stringResource(ReadSetting.BodyToLineHeight.titleRes),
                            checked = settings.readBodyToLh,
                            onCheckedChange = {
                                onIntent(ReadConfigIntent.ReadBodyToLhChanged(it))
                            }
                        )
                    }

                    Setting(ReadSetting.ChangeAllSources) {
                        SwitchSettingItem(
                            title = stringResource(ReadSetting.ChangeAllSources.titleRes),
                            description = stringResource(R.string.read_change_all_s),
                            checked = settings.defaultSourceChangeAll,
                            onCheckedChange = {
                                onIntent(ReadConfigIntent.DefaultSourceChangeAllChanged(it))
                            }
                        )
                    }

                    Setting(ReadSetting.FullJustify) {
                        SwitchSettingItem(
                            title = stringResource(ReadSetting.FullJustify.titleRes),
                            checked = settings.textFullJustify,
                            onCheckedChange = {
                                onIntent(ReadConfigIntent.TextFullJustifyChanged(it))
                            }
                        )
                    }

                    Setting(ReadSetting.BottomJustify) {
                        SwitchSettingItem(
                            title = stringResource(ReadSetting.BottomJustify.titleRes),
                            checked = settings.textBottomJustify,
                            onCheckedChange = {
                                onIntent(ReadConfigIntent.TextBottomJustifyChanged(it))
                            }
                        )
                    }

                    Setting(ReadSetting.SpecialStyle) {
                        SwitchSettingItem(
                            title = stringResource(ReadSetting.SpecialStyle.titleRes),
                            checked = settings.adaptSpecialStyle,
                            onCheckedChange = {
                                onIntent(ReadConfigIntent.AdaptSpecialStyleChanged(it))
                            }
                        )
                    }

                    Setting(ReadSetting.ChineseLayout) {
                        SwitchSettingItem(
                            title = stringResource(ReadSetting.ChineseLayout.titleRes),
                            checked = settings.useZhLayout,
                            onCheckedChange = {
                                onIntent(ReadConfigIntent.UseZhLayoutChanged(it))
                            }
                        )
                    }

                    Setting(ReadSetting.EyeProtection) {
                        ClickableSettingItem(
                            title = stringResource(ReadSetting.EyeProtection.titleRes),
                            option = if (state.eyeProtection.configured) {
                                stringResource(R.string.enabled)
                            } else {
                                stringResource(R.string.disabled)
                            },
                            onClick = { onIntent(ReadConfigIntent.OpenEyeProtection) },
                        )
                    }

                    Setting(ReadSetting.BrightnessMode) {
                        DropdownListSettingItem(
                            title = stringResource(ReadSetting.BrightnessMode.titleRes),
                            selectedValue = settings.showBrightnessView,
                            displayEntries = stringArrayResource(R.array.brightness_bar_mode_title),
                            entryValues = stringArrayResource(R.array.brightness_bar_mode_value),
                            onValueChange = {
                                onIntent(ReadConfigIntent.ShowBrightnessViewChanged(it))
                            }
                        )
                    }

                    if (settings.showBrightnessView == "2") {
                        Setting(ReadSetting.BrightnessPosition) {
                            DropdownListSettingItem(
                                title = stringResource(ReadSetting.BrightnessPosition.titleRes),
                                selectedValue = settings.brightnessVwPos,
                                displayEntries = stringArrayResource(R.array.brightness_bar_position_title),
                                entryValues = stringArrayResource(R.array.brightness_bar_position_value),
                                onValueChange = {
                                    onIntent(ReadConfigIntent.BrightnessVwPosChanged(it))
                                }
                            )
                        }
                    }

                    Setting(ReadSetting.Underline) {
                        SwitchSettingItem(
                            title = stringResource(ReadSetting.Underline.titleRes),
                            checked = settings.useUnderline,
                            onCheckedChange = {
                                onIntent(ReadConfigIntent.UseUnderlineChanged(it))
                            }
                        )
                    }

                }
            }
            item(ReadSettingSection.PageControl.name) {
                SplicedColumnGroup(title = stringResource(ReadSettingSection.PageControl.titleRes)) {
                    Setting(ReadSetting.SliderMode) {
                        DropdownListSettingItem(
                            title = stringResource(ReadSetting.SliderMode.titleRes),
                            selectedValue = settings.readSliderMode,
                            displayEntries = stringArrayResource(R.array.read_slider_mode),
                            entryValues = stringArrayResource(R.array.read_slider_mode_value),
                            onValueChange = {
                                onIntent(ReadConfigIntent.ReadSliderModeChanged(it))
                            }
                        )
                    }

                    Setting(ReadSetting.DoublePage) {
                        DropdownListSettingItem(
                            title = stringResource(ReadSetting.DoublePage.titleRes),
                            selectedValue = settings.doubleHorizontalPage,
                            displayEntries = stringArrayResource(R.array.double_page_title),
                            entryValues = stringArrayResource(R.array.double_page_value),
                            onValueChange = {
                                onIntent(ReadConfigIntent.DoubleHorizontalPageChanged(it))
                            }
                        )
                    }

                    Setting(ReadSetting.ProgressBar) {
                        DropdownListSettingItem(
                            title = stringResource(ReadSetting.ProgressBar.titleRes),
                            selectedValue = settings.progressBarBehavior,
                            displayEntries = stringArrayResource(R.array.progress_bar_behavior_title),
                            entryValues = stringArrayResource(R.array.progress_bar_behavior_value),
                            onValueChange = {
                                onIntent(ReadConfigIntent.ProgressBarBehaviorChanged(it))
                            }
                        )
                    }

                    Setting(ReadSetting.MouseWheel) {
                        SwitchSettingItem(
                            title = stringResource(ReadSetting.MouseWheel.titleRes),
                            checked = settings.mouseWheelPage,
                            onCheckedChange = {
                                onIntent(ReadConfigIntent.MouseWheelPageChanged(it))
                            }
                        )
                    }

                    Setting(ReadSetting.VolumeKeys) {
                        SwitchSettingItem(
                            title = stringResource(ReadSetting.VolumeKeys.titleRes),
                            checked = settings.volumeKeyPage,
                            onCheckedChange = {
                                onIntent(ReadConfigIntent.VolumeKeyPageChanged(it))
                            }
                        )
                    }

                    Setting(ReadSetting.VolumeKeysWhilePlaying) {
                        SwitchSettingItem(
                            title = stringResource(ReadSetting.VolumeKeysWhilePlaying.titleRes),
                            checked = settings.volumeKeyPageOnPlay,
                            onCheckedChange = {
                                onIntent(ReadConfigIntent.VolumeKeyPageOnPlayChanged(it))
                            }
                        )
                    }

                    Setting(ReadSetting.LongPressKeys) {
                        SwitchSettingItem(
                            title = stringResource(ReadSetting.LongPressKeys.titleRes),
                            checked = settings.keyPageOnLongPress,
                            onCheckedChange = {
                                onIntent(ReadConfigIntent.KeyPageOnLongPressChanged(it))
                            }
                        )
                    }

                    Setting(ReadSetting.TouchSlop) {
                        SliderSettingItem(
                            title = stringResource(ReadSetting.TouchSlop.titleRes),
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

                }
            }
            item(ReadSettingSection.Other.name) {
                SplicedColumnGroup(title = stringResource(ReadSettingSection.Other.titleRes)) {
                    Setting(ReadSetting.SliderVibration) {
                        SwitchSettingItem(
                            title = stringResource(ReadSetting.SliderVibration.titleRes),
                            checked = settings.sliderVibrator,
                            onCheckedChange = {
                                onIntent(ReadConfigIntent.SliderVibratorChanged(it))
                            }
                        )
                    }

                    Setting(ReadSetting.TocSheet) {
                        SwitchSettingItem(
                            title = stringResource(ReadSetting.TocSheet.titleRes),
                            checked = settings.useNewTocSheet,
                            onCheckedChange = {
                                onIntent(ReadConfigIntent.UseNewTocSheetChanged(it))
                            }
                        )
                    }

                    Setting(ReadSetting.TocSplitLength) {
                        SliderSettingItem(
                            title = stringResource(ReadSetting.TocSplitLength.titleRes),
                            description = stringResource(
                                R.string.no_toc_split_length_summary,
                                settings.maxLengthWithNoToc
                            ),
                            value = settings.maxLengthWithNoToc.toFloat(),
                            defaultValue = 3000f,
                            valueRange = 3000f..100000f,
                            onValueChange = {
                                onIntent(ReadConfigIntent.MaxLengthWithNoTocChanged(it.toInt()))
                            }
                        )
                    }

                    Setting(ReadSetting.SelectionVibration) {
                        SwitchSettingItem(
                            title = stringResource(ReadSetting.SelectionVibration.titleRes),
                            checked = settings.selectVibrator,
                            onCheckedChange = {
                                onIntent(ReadConfigIntent.SelectVibratorChanged(it))
                            }
                        )
                    }

                    Setting(ReadSetting.AutoChangeSource) {
                        SwitchSettingItem(
                            title = stringResource(ReadSetting.AutoChangeSource.titleRes),
                            checked = settings.autoChangeSource,
                            onCheckedChange = {
                                onIntent(ReadConfigIntent.AutoChangeSourceChanged(it))
                            }
                        )
                    }

                    Setting(ReadSetting.ThemeReminder) {
                        SwitchSettingItem(
                            title = stringResource(ReadSetting.ThemeReminder.titleRes),
                            description = stringResource(R.string.auto_switch_theme_reminder_desc),
                            checked = settings.autoSuggestDayNight,
                            onCheckedChange = {
                                onIntent(ReadConfigIntent.AutoSuggestDayNightChanged(it))
                            }
                        )
                    }

                    Setting(ReadSetting.ReadingAnchor) {
                        SwitchSettingItem(
                            title = stringResource(ReadSetting.ReadingAnchor.titleRes),
                            description = stringResource(R.string.reading_anchor_summary),
                            checked = settings.readingAnchorEnabled,
                            onCheckedChange = {
                                onIntent(ReadConfigIntent.ReadingAnchorChanged(it))
                            }
                        )
                    }

                    Setting(ReadSetting.AloudDetachReminder) {
                        SwitchSettingItem(
                            title = stringResource(ReadSetting.AloudDetachReminder.titleRes),
                            description = stringResource(R.string.read_aloud_detach_reminder_summary),
                            checked = settings.readAloudDetachReminderEnabled,
                            onCheckedChange = {
                                onIntent(ReadConfigIntent.ReadAloudDetachReminderChanged(it))
                            }
                        )
                    }

                    Setting(ReadSetting.SelectText) {
                        SwitchSettingItem(
                            title = stringResource(ReadSetting.SelectText.titleRes),
                            checked = settings.selectText,
                            onCheckedChange = {
                                onIntent(ReadConfigIntent.SelectTextChanged(it))
                            }
                        )
                    }

                    Setting(ReadSetting.ScrollAnimation) {
                        SwitchSettingItem(
                            title = stringResource(ReadSetting.ScrollAnimation.titleRes),
                            checked = settings.noAnimScrollPage,
                            onCheckedChange = {
                                onIntent(ReadConfigIntent.NoAnimScrollPageChanged(it))
                            }
                        )
                    }

                    Setting(ReadSetting.ImageClick) {
                        DropdownListSettingItem(
                            title = stringResource(ReadSetting.ImageClick.titleRes),
                            selectedValue = settings.clickImgWay,
                            displayEntries = stringArrayResource(R.array.click_image_way_title),
                            entryValues = stringArrayResource(R.array.click_image_way_value),
                            onValueChange = {
                                onIntent(ReadConfigIntent.ClickImgWayChanged(it))
                            }
                        )
                    }

                    Setting(ReadSetting.OptimizeRender) {
                        SwitchSettingItem(
                            title = stringResource(ReadSetting.OptimizeRender.titleRes),
                            checked = settings.optimizeRender,
                            onCheckedChange = {
                                onIntent(ReadConfigIntent.OptimizeRenderChanged(it))
                            }
                        )
                    }

                    Setting(ReadSetting.ClickRegions) {
                        ClickableSettingItem(
                            title = stringResource(ReadSetting.ClickRegions.titleRes),
                            onClick = { onIntent(ReadConfigIntent.OpenClickActions) }
                        )
                    }

                    Setting(ReadSetting.DisableBack) {
                        SwitchSettingItem(
                            title = stringResource(ReadSetting.DisableBack.titleRes),
                            checked = settings.disableReturnKey,
                            onCheckedChange = {
                                onIntent(ReadConfigIntent.DisableReturnKeyChanged(it))
                            }
                        )
                    }

                    Setting(ReadSetting.PageKeys) {
                        ClickableSettingItem(
                            title = stringResource(ReadSetting.PageKeys.titleRes),
                            onClick = { onIntent(ReadConfigIntent.OpenPageKeys) }
                        )
                    }

                    Setting(ReadSetting.TitleAddition) {
                        SwitchSettingItem(
                            title = stringResource(ReadSetting.TitleAddition.titleRes),
                            checked = settings.showReadTitleAddition,
                            onCheckedChange = {
                                onIntent(ReadConfigIntent.ShowReadTitleAdditionChanged(it))
                            }
                        )
                    }

                    Setting(ReadSetting.MenuIcons) {
                        SwitchSettingItem(
                            title = stringResource(ReadSetting.MenuIcons.titleRes),
                            checked = settings.showMenuIcon,
                            onCheckedChange = {
                                onIntent(ReadConfigIntent.ShowMenuIconChanged(it))
                            }
                        )
                    }

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
        enabled = state.eyeProtection.enabled,
        intensity = state.eyeProtection.intensity,
        autoNight = state.eyeProtection.autoNight,
        schedule = state.eyeProtection.schedule,
        startTime = state.eyeProtection.startTime,
        endTime = state.eyeProtection.endTime,
        onDismissRequest = { onIntent(ReadConfigIntent.DismissSheet) },
        onEnabledChange = { onIntent(ReadConfigIntent.EyeProtectionEnabledChanged(it)) },
        onIntensityChange = { onIntent(ReadConfigIntent.EyeProtectionIntensityChanged(it)) },
        onAutoNightChange = { onIntent(ReadConfigIntent.EyeProtectionAutoNightChanged(it)) },
        onScheduleChange = { onIntent(ReadConfigIntent.EyeProtectionScheduleChanged(it)) },
        onStartTimeChange = { onIntent(ReadConfigIntent.EyeProtectionStartTimeChanged(it)) },
        onEndTimeChange = { onIntent(ReadConfigIntent.EyeProtectionEndTimeChanged(it)) },
    )
}
