package io.legado.app.feature.settings.readconfig

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import io.legado.app.data.repository.ReadPreferences
import io.legado.app.feature.settings.res.Res
import io.legado.app.feature.settings.res.adapt_special_style
import io.legado.app.feature.settings.res.auto_change_source
import io.legado.app.feature.settings.res.auto_switch_theme_reminder_desc
import io.legado.app.feature.settings.res.auto_switch_theme_reminder_title
import io.legado.app.feature.settings.res.brightness_bar_mode_title
import io.legado.app.feature.settings.res.brightness_bar_mode_value
import io.legado.app.feature.settings.res.brightness_bar_position
import io.legado.app.feature.settings.res.brightness_bar_position_title
import io.legado.app.feature.settings.res.brightness_bar_position_value
import io.legado.app.feature.settings.res.click_image_way
import io.legado.app.feature.settings.res.click_image_way_title
import io.legado.app.feature.settings.res.click_image_way_value
import io.legado.app.feature.settings.res.click_regional_config
import io.legado.app.feature.settings.res.custom_page_key
import io.legado.app.feature.settings.res.disable_return_key
import io.legado.app.feature.settings.res.disabled
import io.legado.app.feature.settings.res.double_page_title
import io.legado.app.feature.settings.res.double_page_value
import io.legado.app.feature.settings.res.double_page_horizontal
import io.legado.app.feature.settings.res.enable_optimize_render
import io.legado.app.feature.settings.res.enable_select_vibrator
import io.legado.app.feature.settings.res.enable_slider_vibrator
import io.legado.app.feature.settings.res.enabled
import io.legado.app.feature.settings.res.eye_protection
import io.legado.app.feature.settings.res.keep_light
import io.legado.app.feature.settings.res.key_page_on_long_press
import io.legado.app.feature.settings.res.menu_alpha
import io.legado.app.feature.settings.res.menu_alpha_sum
import io.legado.app.feature.settings.res.mouse_wheel_page
import io.legado.app.feature.settings.res.no_anim_scroll_page
import io.legado.app.feature.settings.res.no_toc_split_length_summary
import io.legado.app.feature.settings.res.no_toc_split_length_title
import io.legado.app.feature.settings.res.other
import io.legado.app.feature.settings.res.padding_display_cutouts
import io.legado.app.feature.settings.res.page_control
import io.legado.app.feature.settings.res.page_touch_slop_summary
import io.legado.app.feature.settings.res.page_touch_slop_title
import io.legado.app.feature.settings.res.progress_bar_behavior
import io.legado.app.feature.settings.res.progress_bar_behavior_title
import io.legado.app.feature.settings.res.progress_bar_behavior_value
import io.legado.app.feature.settings.res.pt_hide_navigation_bar
import io.legado.app.feature.settings.res.pt_hide_status_bar
import io.legado.app.feature.settings.res.read_aloud_detach_reminder
import io.legado.app.feature.settings.res.read_aloud_detach_reminder_summary
import io.legado.app.feature.settings.res.read_body_to_lh
import io.legado.app.feature.settings.res.read_change_all
import io.legado.app.feature.settings.res.read_change_all_s
import io.legado.app.feature.settings.res.read_config
import io.legado.app.feature.settings.res.read_slider_mode
import io.legado.app.feature.settings.res.read_slider_mode_value
import io.legado.app.feature.settings.res.reading_anchor
import io.legado.app.feature.settings.res.reading_anchor_summary
import io.legado.app.feature.settings.res.screen_direction
import io.legado.app.feature.settings.res.screen_direction_title
import io.legado.app.feature.settings.res.screen_direction_value
import io.legado.app.feature.settings.res.screen_settings
import io.legado.app.feature.settings.res.screen_time_out
import io.legado.app.feature.settings.res.screen_time_out_value
import io.legado.app.feature.settings.res.selectText
import io.legado.app.feature.settings.res.show_brightness_view
import io.legado.app.feature.settings.res.show_menu_icon
import io.legado.app.feature.settings.res.show_read_title_addition
import io.legado.app.feature.settings.res.text_bottom_justify
import io.legado.app.feature.settings.res.text_full_justify
import io.legado.app.feature.settings.res.title_bar_mode
import io.legado.app.feature.settings.res.title_bar_mode_value
import io.legado.app.feature.settings.res.use_new_toc_sheet
import io.legado.app.feature.settings.res.use_underline
import io.legado.app.feature.settings.res.use_zh_layout
import io.legado.app.feature.settings.res.volume_key_page
import io.legado.app.feature.settings.res.volume_key_page_on_play
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
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

/**
 * M5-11d：从 `:app` 的 `ui/config/readConfig` 迁来，**readConfig 域收官**（逻辑层见 M5-11a，
 * 两个前置 sheet 见 M5-10a / M5-11b / M5-11c）。
 *
 * 差异三类（结构**逐字保留**，含原文的缩进与空行）：
 *   ① `androidx.compose.ui.res.{stringResource,stringArrayResource}` → CMP 的同名函数；
 *   ② `R.string.*`（55 条）/ `R.array.*`（18 个）→ `Res.string.*` / `Res.array.*`；
 *   ③ `CanvasRecorderFactory.isSupport` → [canvasRecorderSupported] 参数（见下）。
 *
 * ⚠️ `stringArrayResource` 在 CMP 里返回 `List<String>`，故每处都多一次 `.toTypedArray()`
 * （`DropdownListSettingItem` 收的是 `Array<String>`）—— 本页有 **9 对**数组，是最集中的一处。
 *
 * ⚠️ [canvasRecorderSupported]：迁移前这里是 `if (CanvasRecorderFactory.isSupport)`，
 * 而 `CanvasRecorderFactory` 依赖 `android.os.Build` 与三个 Android 专用的
 * `CanvasRecorder*Impl` ⇒ 不能直接进共享层。本片**没有**为它抽窄契约 —— 它是一个**只读的能力开关**，
 * 宿主（`ReadConfigRouteScreen`）直接读一次传进来即可，抽接口反而多一层没有调用方的抽象
 * （与 M5-11a 那个有 7 个动作的 `ReadConfigApplyPlatform` 不同：那个是**行为**，这个只是**事实**）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadConfigScreen(
    state: ReadConfigUiState,
    onIntent: (ReadConfigIntent) -> Unit,
    onBackClick: () -> Unit,
    preferences: ReadPreferences,
    onSetClickAction: (String, Int) -> Unit,
    canvasRecorderSupported: Boolean,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val settings = state

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(Res.string.read_config),
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
                SplicedColumnGroup(title = stringResource(Res.string.screen_settings)) {
                DropdownListSettingItem(
                    title = stringResource(Res.string.screen_direction),
                    selectedValue = settings.screenOrientation,
                    displayEntries = stringArrayResource(Res.array.screen_direction_title).toTypedArray(),
                    entryValues = stringArrayResource(Res.array.screen_direction_value).toTypedArray(),
                    onValueChange = {
                        onIntent(ReadConfigIntent.ScreenOrientationChanged(it))
                    }
                )

                DropdownListSettingItem(
                    title = stringResource(Res.string.keep_light),
                    selectedValue = settings.keepLight,
                    displayEntries = stringArrayResource(Res.array.screen_time_out).toTypedArray(),
                    entryValues = stringArrayResource(Res.array.screen_time_out_value).toTypedArray(),
                    onValueChange = {
                        onIntent(ReadConfigIntent.KeepLightChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(Res.string.pt_hide_status_bar),
                    checked = settings.hideStatusBar,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.HideStatusBarChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(Res.string.pt_hide_navigation_bar),
                    checked = settings.hideNavigationBar,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.HideNavigationBarChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(Res.string.padding_display_cutouts),
                    checked = settings.paddingDisplayCutouts,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.PaddingDisplayCutoutsChanged(it))
                    }
                )

                DropdownListSettingItem(
                    title = stringResource(Res.string.title_bar_mode),
                    selectedValue = settings.titleBarMode,
                    displayEntries = stringArrayResource(Res.array.title_bar_mode).toTypedArray(),
                    entryValues = stringArrayResource(Res.array.title_bar_mode_value).toTypedArray(),
                    onValueChange = {
                        onIntent(ReadConfigIntent.TitleBarModeChanged(it))
                    }
                )

                SliderSettingItem(
                    title = stringResource(Res.string.menu_alpha),
                    description = stringResource(Res.string.menu_alpha_sum, settings.readMenuBlurAlpha),
                    value = settings.readMenuBlurAlpha.toFloat(),
                    defaultValue = 60f,
                    valueRange = 0f..100f,
                    onValueChange = {
                        onIntent(ReadConfigIntent.ReadMenuBlurAlphaChanged(it.toInt()))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(Res.string.read_body_to_lh),
                    checked = settings.readBodyToLh,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.ReadBodyToLhChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(Res.string.read_change_all),
                    description = stringResource(Res.string.read_change_all_s),
                    checked = settings.defaultSourceChangeAll,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.DefaultSourceChangeAllChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(Res.string.text_full_justify),
                    checked = settings.textFullJustify,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.TextFullJustifyChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(Res.string.text_bottom_justify),
                    checked = settings.textBottomJustify,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.TextBottomJustifyChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(Res.string.adapt_special_style),
                    checked = settings.adaptSpecialStyle,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.AdaptSpecialStyleChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(Res.string.use_zh_layout),
                    checked = settings.useZhLayout,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.UseZhLayoutChanged(it))
                    }
                )

                ClickableSettingItem(
                    title = stringResource(Res.string.eye_protection),
                    option = if (state.eyeProtection.configured) {
                        stringResource(Res.string.enabled)
                    } else {
                        stringResource(Res.string.disabled)
                    },
                    onClick = { onIntent(ReadConfigIntent.OpenEyeProtection) },
                )

                    DropdownListSettingItem(
                    title = stringResource(Res.string.show_brightness_view),
                        selectedValue = settings.showBrightnessView,
                        displayEntries = stringArrayResource(Res.array.brightness_bar_mode_title).toTypedArray(),
                        entryValues = stringArrayResource(Res.array.brightness_bar_mode_value).toTypedArray(),
                        onValueChange = {
                        onIntent(ReadConfigIntent.ShowBrightnessViewChanged(it))
                    }
                )

                    if (settings.showBrightnessView == "2") {
                        DropdownListSettingItem(
                            title = stringResource(Res.string.brightness_bar_position),
                            selectedValue = settings.brightnessVwPos,
                            displayEntries = stringArrayResource(Res.array.brightness_bar_position_title).toTypedArray(),
                            entryValues = stringArrayResource(Res.array.brightness_bar_position_value).toTypedArray(),
                            onValueChange = {
                                onIntent(ReadConfigIntent.BrightnessVwPosChanged(it))
                            }
                        )
                    }

                SwitchSettingItem(
                    title = stringResource(Res.string.use_underline),
                    checked = settings.useUnderline,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.UseUnderlineChanged(it))
                    }
                )
            }

            SplicedColumnGroup(title = stringResource(Res.string.page_control)) {
                DropdownListSettingItem(
                    title = stringResource(Res.string.read_slider_mode),
                    selectedValue = settings.readSliderMode,
                    displayEntries = stringArrayResource(Res.array.read_slider_mode).toTypedArray(),
                    entryValues = stringArrayResource(Res.array.read_slider_mode_value).toTypedArray(),
                    onValueChange = {
                        onIntent(ReadConfigIntent.ReadSliderModeChanged(it))
                    }
                )

                DropdownListSettingItem(
                    title = stringResource(Res.string.double_page_horizontal),
                    selectedValue = settings.doubleHorizontalPage,
                    displayEntries = stringArrayResource(Res.array.double_page_title).toTypedArray(),
                    entryValues = stringArrayResource(Res.array.double_page_value).toTypedArray(),
                    onValueChange = {
                        onIntent(ReadConfigIntent.DoubleHorizontalPageChanged(it))
                    }
                )

                DropdownListSettingItem(
                    title = stringResource(Res.string.progress_bar_behavior),
                    selectedValue = settings.progressBarBehavior,
                    displayEntries = stringArrayResource(Res.array.progress_bar_behavior_title).toTypedArray(),
                    entryValues = stringArrayResource(Res.array.progress_bar_behavior_value).toTypedArray(),
                    onValueChange = {
                        onIntent(ReadConfigIntent.ProgressBarBehaviorChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(Res.string.mouse_wheel_page),
                    checked = settings.mouseWheelPage,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.MouseWheelPageChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(Res.string.volume_key_page),
                    checked = settings.volumeKeyPage,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.VolumeKeyPageChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(Res.string.volume_key_page_on_play),
                    checked = settings.volumeKeyPageOnPlay,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.VolumeKeyPageOnPlayChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(Res.string.key_page_on_long_press),
                    checked = settings.keyPageOnLongPress,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.KeyPageOnLongPressChanged(it))
                    }
                )

                SliderSettingItem(
                    title = stringResource(Res.string.page_touch_slop_title),
                    description = stringResource(
                        Res.string.page_touch_slop_summary,
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

                SplicedColumnGroup(title = stringResource(Res.string.other)) {
                SwitchSettingItem(
                    title = stringResource(Res.string.enable_slider_vibrator),
                    checked = settings.sliderVibrator,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.SliderVibratorChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(Res.string.use_new_toc_sheet),
                    checked = settings.useNewTocSheet,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.UseNewTocSheetChanged(it))
                    }
                )

                SliderSettingItem(
                    title = stringResource(Res.string.no_toc_split_length_title),
                    description = stringResource(
                        Res.string.no_toc_split_length_summary,
                        settings.maxLengthWithNoToc
                    ),
                    value = settings.maxLengthWithNoToc.toFloat(),
                    defaultValue = 3000f,
                    valueRange = 3000f..100000f,
                    onValueChange = {
                        onIntent(ReadConfigIntent.MaxLengthWithNoTocChanged(it.toInt()))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(Res.string.enable_select_vibrator),
                    checked = settings.selectVibrator,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.SelectVibratorChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(Res.string.auto_change_source),
                    checked = settings.autoChangeSource,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.AutoChangeSourceChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(Res.string.auto_switch_theme_reminder_title),
                    description = stringResource(Res.string.auto_switch_theme_reminder_desc),
                    checked = settings.autoSuggestDayNight,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.AutoSuggestDayNightChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(Res.string.reading_anchor),
                    description = stringResource(Res.string.reading_anchor_summary),
                    checked = settings.readingAnchorEnabled,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.ReadingAnchorChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(Res.string.read_aloud_detach_reminder),
                    description = stringResource(Res.string.read_aloud_detach_reminder_summary),
                    checked = settings.readAloudDetachReminderEnabled,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.ReadAloudDetachReminderChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(Res.string.selectText),
                    checked = settings.selectText,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.SelectTextChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(Res.string.no_anim_scroll_page),
                    checked = settings.noAnimScrollPage,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.NoAnimScrollPageChanged(it))
                    }
                )

                DropdownListSettingItem(
                    title = stringResource(Res.string.click_image_way),
                    selectedValue = settings.clickImgWay,
                    displayEntries = stringArrayResource(Res.array.click_image_way_title).toTypedArray(),
                    entryValues = stringArrayResource(Res.array.click_image_way_value).toTypedArray(),
                    onValueChange = {
                        onIntent(ReadConfigIntent.ClickImgWayChanged(it))
                    }
                )

                if (canvasRecorderSupported) {
                    SwitchSettingItem(
                        title = stringResource(Res.string.enable_optimize_render),
                        checked = settings.optimizeRender,
                        onCheckedChange = {
                            onIntent(ReadConfigIntent.OptimizeRenderChanged(it))
                        }
                    )
                }

                ClickableSettingItem(
                    title = stringResource(Res.string.click_regional_config),
                    onClick = { onIntent(ReadConfigIntent.OpenClickActions) }
                )

                SwitchSettingItem(
                    title = stringResource(Res.string.disable_return_key),
                    checked = settings.disableReturnKey,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.DisableReturnKeyChanged(it))
                    }
                )

                ClickableSettingItem(
                    title = stringResource(Res.string.custom_page_key),
                    onClick = { onIntent(ReadConfigIntent.OpenPageKeys) }
                )

                SwitchSettingItem(
                    title = stringResource(Res.string.show_read_title_addition),
                    checked = settings.showReadTitleAddition,
                    onCheckedChange = {
                        onIntent(ReadConfigIntent.ShowReadTitleAdditionChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(Res.string.show_menu_icon),
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
            preferences = preferences,
            onDismissRequest = { onIntent(ReadConfigIntent.DismissSheet) },
            onSetClickAction = onSetClickAction,
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
