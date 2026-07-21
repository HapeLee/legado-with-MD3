package io.legado.app.ui.book.read.sheet

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.constant.ReadMenuBlurMode
import io.legado.app.constant.ReadMenuBlurStyle
import io.legado.app.data.repository.ReadPreferences
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.book.read.ConfigUpdate
import io.legado.app.ui.book.read.ReadBookButtonConfigItem
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.book.read.ReadBookSheet
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.SectionTitle
import io.legado.app.ui.widget.components.dialog.ColorPickerSheet
import io.legado.app.ui.widget.components.pager.pagerHeight
import io.legado.app.ui.widget.components.pager.rememberPagerAnimatedHeight
import io.legado.app.ui.widget.components.pager.rememberPagerFlingPassThroughConnection
import io.legado.app.ui.widget.components.settingItem.TinyClearColorModeSettingItem
import io.legado.app.ui.widget.components.settingItem.TinyClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.TinyColorModeSettingItem
import io.legado.app.ui.widget.components.settingItem.TinyDropdownSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySliderSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySwitchSettingItem
import io.legado.app.ui.widget.components.tabRow.CardTabRow
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import kotlinx.coroutines.launch

private const val COLOR_BG = 5
private const val COLOR_MENU_ACCENT = 6
private const val COLOR_MENU_CONTAINER = 7
private const val COLOR_BG_NIGHT = 8
private const val COLOR_MENU_ACCENT_NIGHT = 9
private const val COLOR_MENU_CONTAINER_NIGHT = 10
private const val COLOR_BORDER = 11
private const val COLOR_BORDER_NIGHT = 12
private const val COLOR_BLUR_TINT = 13
private const val COLOR_BLUR_TINT_NIGHT = 14
private const val COLOR_MENU_TEXT = 15
private const val COLOR_MENU_TEXT_NIGHT = 16

@Composable
internal fun SystemMenuPage(
    preferences: ReadPreferences,
    customIcons: Map<String, String>,
    bottomBarButtons: List<ReadBookButtonConfigItem>,
    modifier: Modifier = Modifier,
    onIntent: (ReadBookIntent) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 3 })
    var selectedTab by remember { mutableIntStateOf(0) }
    var clickScrollCount by remember { mutableIntStateOf(0) }
    val childPagerNestedScrollConnection = rememberPagerFlingPassThroughConnection(
        state = pagerState,
        orientation = Orientation.Horizontal,
    )

    val pageHeights = remember { mutableStateMapOf<Int, Int>() }
    val animatedHeight by rememberPagerAnimatedHeight(pagerState, pageHeights)

    // Shared state for sheets
    var showColorPicker by remember { mutableStateOf(false) }
    var colorPickerId by remember { mutableIntStateOf(0) }
    var colorPickerInitial by remember { mutableIntStateOf(0) }
    var showIconSheet by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (clickScrollCount == 0) {
                selectedTab = page
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        val tabTitles = listOf(
            stringResource(R.string.read_config_menu_system),
            stringResource(R.string.read_menu_bottom_bar_layout),
            stringResource(R.string.title_bar_layout),
        )
        CardTabRow(
            tabTitles = tabTitles,
            selectedTabIndex = selectedTab,
            onTabSelected = { index ->
                selectedTab = index
                clickScrollCount++
                scope.launch {
                    try {
                        pagerState.animateScrollToPage(
                            page = index,
                            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                        )
                    } finally {
                        clickScrollCount = (clickScrollCount - 1).coerceAtLeast(0)
                    }
                }
            },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )

        HorizontalPager(
            state = pagerState,
            verticalAlignment = Alignment.Top,
            pageNestedScrollConnection = childPagerNestedScrollConnection,
            modifier = Modifier
                .weight(1f, fill = false)
                .clipToBounds()
                .pagerHeight(animatedHeight),
        ) { page ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { size ->
                        pageHeights[page] = size.height
                    }
            ) {
                when (page) {
                    0 -> GlobalMenuTab(
                        preferences = preferences,
                        onIntent = onIntent,
                        onShowColorPicker = { id, initial ->
                            colorPickerId = id
                            colorPickerInitial = initial
                            showColorPicker = true
                        },
                    )

                    1 -> BottomBarTab(
                        preferences = preferences,
                        customIcons = customIcons,
                        onIntent = onIntent,
                        onShowIconSheet = { showIconSheet = true },
                        onShowColorPicker = { id, initial ->
                            colorPickerId = id
                            colorPickerInitial = initial
                            showColorPicker = true
                        },
                    )

                    2 -> TopBarTab(preferences = preferences, onIntent = onIntent)
                }
            }
        }
    }

    // Floating sheets
    ColorPickerSheet(
        show = showColorPicker,
        initialColor = colorPickerInitial,
        onDismissRequest = { showColorPicker = false },
        onColorSelected = { color ->
            when (colorPickerId) {
                COLOR_BG -> onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.MenuBgColor(color)))
                COLOR_MENU_ACCENT -> onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.MenuAccentColor(color)))
                COLOR_MENU_CONTAINER -> onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.MenuContainerColor(color)))
                COLOR_BG_NIGHT -> onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.MenuBgColorNight(color)))
                COLOR_MENU_ACCENT_NIGHT -> onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.MenuAccentColorNight(color)))
                COLOR_MENU_CONTAINER_NIGHT -> onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.MenuContainerColorNight(color)))
                COLOR_BORDER -> onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.BorderColor(color)))
                COLOR_BORDER_NIGHT -> onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.BorderColorNight(color)))
                COLOR_BLUR_TINT -> onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.MenuBlurColor(color)))
                COLOR_BLUR_TINT_NIGHT -> onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.MenuBlurColorNight(color)))
                COLOR_MENU_TEXT -> onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.MenuTextColor(color)))
                COLOR_MENU_TEXT_NIGHT -> onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.MenuTextColorNight(color)))
            }
            showColorPicker = false
        },
    )

    BottomBarIconSheet(
        show = showIconSheet,
        items = bottomBarButtons,
        customIcons = customIcons,
        onDismissRequest = { showIconSheet = false },
        onIntent = onIntent,
    )
}

// ========== Tab 0: Global ==========

@Composable
private fun GlobalMenuTab(
    preferences: ReadPreferences,
    onIntent: (ReadBookIntent) -> Unit,
    onShowColorPicker: (Int, Int) -> Unit,
) {
    val customIconCount = remember(preferences.titleBarCustomIcons) {
        countCustomIcons(preferences.titleBarCustomIcons)
    }
    val bottomMode = preferences.readBarStyle
    val colorMode = preferences.readMenuColorMode.coerceIn(0, 1)
    val dayMenuBgColor = preferences.readMenuBgColor
        .takeIf { it != 0 }
        ?: ReadBookConfig.durConfig.menuBgColor(isNight = false)
    val nightMenuBgColor = preferences.readMenuBgColorNight
        .takeIf { it != 0 }
        ?: ReadBookConfig.durConfig.menuBgColor(isNight = true)
    val dayMenuAccentColor = preferences.readMenuAccentColor
        .takeIf { it != 0 }
        ?: ReadBookConfig.durConfig.menuAccentColor(isNight = false)
    val nightMenuAccentColor = preferences.readMenuAccentColorNight
        .takeIf { it != 0 }
        ?: ReadBookConfig.durConfig.menuAccentColor(isNight = true)
    val dayMenuContainerColor = preferences.readMenuContainerColor
        .takeIf { it != 0 }
        ?: dayMenuBgColor
    val nightMenuContainerColor = preferences.readMenuContainerColorNight
        .takeIf { it != 0 }
        ?: nightMenuBgColor

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        TinyDropdownSettingItem(
            title = stringResource(R.string.tool_bar_style),
            selectedValue = bottomMode.toString(),
            displayEntries = arrayOf(
                stringResource(R.string.flow_sys),
                stringResource(R.string.follow_read_background),
                stringResource(R.string.custom),
            ),
            entryValues = arrayOf("0", "1", "2"),
            onValueChange = {
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.ReadBarStyle(it.toInt())))
            },
        )

        val showPaletteStyle = bottomMode == 1 || (bottomMode == 2 && colorMode == 0)
        AnimatedVisibility(visible = showPaletteStyle) {
            val paletteEntries = stringArrayResource(R.array.paletteStyle)
            val paletteValues = stringArrayResource(R.array.paletteStyle_value)
            val allEntries = arrayOf(stringResource(R.string.flow_sys)) + paletteEntries
            val allValues = arrayOf("") + paletteValues
            TinyDropdownSettingItem(
                title = stringResource(R.string.palette_style),
                selectedValue = preferences.readMenuPaletteStyle,
                displayEntries = allEntries,
                entryValues = allValues,
                onValueChange = {
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.MenuPaletteStyle(it)))
                },
            )
        }

        AnimatedVisibility(visible = bottomMode == 2) {
            Column {
                TinyDropdownSettingItem(
                    title = stringResource(R.string.read_menu_color_source),
                    selectedValue = colorMode.toString(),
                    displayEntries = arrayOf(
                        stringResource(R.string.seed_color),
                        stringResource(R.string.custom_theme_colors),
                    ),
                    entryValues = arrayOf("0", "1"),
                    onValueChange = {
                        onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.MenuColorMode(it.toInt())))
                    },
                )

                AnimatedVisibility(visible = colorMode == 0) {
                    TinyColorModeSettingItem(
                        title = stringResource(R.string.seed_color),
                        description = stringResource(R.string.seed_color_summary),
                        dayColor = dayMenuAccentColor,
                        nightColor = nightMenuAccentColor,
                        enabled = true,
                        onClickColor = { isNight ->
                            if (isNight) {
                                onShowColorPicker(COLOR_MENU_ACCENT_NIGHT, nightMenuAccentColor)
                            } else {
                                onShowColorPicker(COLOR_MENU_ACCENT, dayMenuAccentColor)
                            }
                        },
                    )
                }

                AnimatedVisibility(visible = colorMode == 1) {
                    Column {
                        TinyColorModeSettingItem(
                            title = stringResource(R.string.background_color),
                            description = stringResource(R.string.read_menu_bg_color_summary),
                            dayColor = dayMenuBgColor,
                            nightColor = nightMenuBgColor,
                            enabled = true,
                            onClickColor = { isNight ->
                                if (isNight) {
                                    onShowColorPicker(COLOR_BG_NIGHT, nightMenuBgColor)
                                } else {
                                    onShowColorPicker(COLOR_BG, dayMenuBgColor)
                                }
                            },
                        )
                        TinyColorModeSettingItem(
                            title = stringResource(R.string.container_background_color),
                            description = stringResource(R.string.read_menu_container_color_summary),
                            dayColor = dayMenuContainerColor,
                            nightColor = nightMenuContainerColor,
                            enabled = true,
                            onClickColor = { isNight ->
                                if (isNight) {
                                    onShowColorPicker(COLOR_MENU_CONTAINER_NIGHT, nightMenuContainerColor)
                                } else {
                                    onShowColorPicker(COLOR_MENU_CONTAINER, dayMenuContainerColor)
                                }
                            },
                        )
                        TinyColorModeSettingItem(
                            title = stringResource(R.string.accent),
                            description = stringResource(R.string.read_menu_accent_color_summary),
                            dayColor = dayMenuAccentColor,
                            nightColor = nightMenuAccentColor,
                            enabled = true,
                            onClickColor = { isNight ->
                                if (isNight) {
                                    onShowColorPicker(COLOR_MENU_ACCENT_NIGHT, nightMenuAccentColor)
                                } else {
                                    onShowColorPicker(COLOR_MENU_ACCENT, dayMenuAccentColor)
                                }
                            },
                        )
                    }
                }
            }
        }


        SectionTitle(stringResource(R.string.show_brightness_view))

        TinyDropdownSettingItem(
            title = stringResource(R.string.show_brightness_view),
            selectedValue = preferences.showBrightnessView,
            displayEntries = stringArrayResource(R.array.brightness_bar_mode_title),
            entryValues = stringArrayResource(R.array.brightness_bar_mode_value),
            onValueChange = {
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.ShowBrightnessView(it)))
            },
        )
        if (preferences.showBrightnessView == "2") {
            TinyDropdownSettingItem(
                title = stringResource(R.string.brightness_bar_position),
                selectedValue = preferences.brightnessVwPos,
                displayEntries = stringArrayResource(R.array.brightness_bar_position_title),
                entryValues = stringArrayResource(R.array.brightness_bar_position_value),
                onValueChange = {
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.BrightnessVwPos(it)))
                },
            )
        }

        val borderEnabled = preferences.readMenuBorderWidth > 0
        TinySwitchSettingItem(
            title = stringResource(R.string.read_menu_border),
            checked = borderEnabled,
            onCheckedChange = {
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.BorderWidth(if (it) 1 else 0)))
            },
        )
        AnimatedVisibility(visible = borderEnabled) {
            Column {
                TinySliderSettingItem(
                    title = stringResource(R.string.read_menu_border_width),
                    value = preferences.readMenuBorderWidth.coerceIn(1, 4).toFloat(),
                    valueRange = 1f..4f,
                    onValueChange = {
                        onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.BorderWidth(it.toInt())))
                    },
                )
                TinyClearColorModeSettingItem(
                    title = stringResource(R.string.read_menu_border_color),
                    dayColor = preferences.readMenuBorderColor,
                    nightColor = preferences.readMenuBorderColorNight,
                    onClearColor = { isNight ->
                        if (isNight) {
                            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.BorderColorNight(0)))
                        } else {
                            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.BorderColor(0)))
                        }
                    },
                    onClickColor = { isNight ->
                        if (isNight) {
                            onShowColorPicker(COLOR_BORDER_NIGHT, preferences.readMenuBorderColorNight)
                        } else {
                            onShowColorPicker(COLOR_BORDER, preferences.readMenuBorderColor)
                        }
                    },
                )
            }
        }

        TinySliderSettingItem(
            title = stringResource(R.string.read_menu_blur_radius),
            value = preferences.readMenuBlurRadius.toFloat(),
            valueRange = 0f..32f,
            steps = 31,
            description = stringResource(R.string.read_menu_blur_radius_summary),
            onValueChange = {
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.MenuBlurRadius(it.toInt())))
            },
        )
        TinySliderSettingItem(
            title = stringResource(R.string.read_menu_blur_alpha),
            value = preferences.readMenuBlurAlpha.toFloat(),
            valueRange = 0f..100f,
            steps = 99,
            onValueChange = {
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.MenuBlurAlpha(it.toInt())))
            },
        )
        TinyClearColorModeSettingItem(
            title = stringResource(R.string.read_menu_text_color),
            description = stringResource(R.string.read_menu_text_color_summary),
            dayColor = preferences.readMenuTextColor,
            nightColor = preferences.readMenuTextColorNight,
            onClearColor = { isNight ->
                onIntent(
                    ReadBookIntent.UpdateConfig(
                        if (isNight) ConfigUpdate.MenuTextColorNight(0)
                        else ConfigUpdate.MenuTextColor(0)
                    )
                )
            },
            onClickColor = { isNight ->
                if (isNight) {
                    onShowColorPicker(COLOR_MENU_TEXT_NIGHT, preferences.readMenuTextColorNight)
                } else {
                    onShowColorPicker(COLOR_MENU_TEXT, preferences.readMenuTextColor)
                }
            },
        )
        TinyColorModeSettingItem(
            title = stringResource(R.string.read_menu_blur_color),
            dayColor = preferences.readMenuBlurColor,
            nightColor = preferences.readMenuBlurColorNight,
            onClickColor = { isNight ->
                if (isNight) {
                    onShowColorPicker(COLOR_BLUR_TINT_NIGHT, preferences.readMenuBlurColorNight)
                } else {
                    onShowColorPicker(COLOR_BLUR_TINT, preferences.readMenuBlurColor)
                }
            },
        )

        // --- Floating icon settings (at bottom) ---
        SectionTitle(stringResource(R.string.show_title_bar_icons))

        TinySwitchSettingItem(
            title = stringResource(R.string.show_title_bar_icons),
            checked = preferences.showTitleBarIcons,
            onCheckedChange = {
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.ShowTitleBarIcons(it)))
            },
        )
        AnimatedVisibility(visible = preferences.showTitleBarIcons) {
            Column {
                TinyClickableSettingItem(
                    title = stringResource(R.string.title_bar_icons),
                    description = if (customIconCount == 0) {
                        stringResource(R.string.read_menu_custom_icons_none)
                    } else {
                        stringResource(R.string.read_menu_custom_icons_count, customIconCount)
                    },
                    onClick = {
                        onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.FloatingBarIconConfig))
                    },
                )
                TinyDropdownSettingItem(
                    title = stringResource(R.string.title_bar_icon_position),
                    selectedValue = preferences.titleBarIconPosition.toString(),
                    displayEntries = arrayOf(
                        stringResource(R.string.position_top_start),
                        stringResource(R.string.position_top_end),
                        stringResource(R.string.position_bottom_start),
                        stringResource(R.string.position_bottom_end),
                    ),
                    entryValues = arrayOf("0", "1", "2", "3"),
                    onValueChange = {
                        onIntent(
                            ReadBookIntent.UpdateConfig(
                                ConfigUpdate.TitleBarIconPosition(it.toInt())
                            )
                        )
                    },
                )
                TinySwitchSettingItem(
                    title = stringResource(R.string.read_menu_bar_liquid_glass_buttons),
                    checked = preferences.readMenuFloatingIconLiquidGlass,
                    onCheckedChange = {
                        onIntent(
                            ReadBookIntent.UpdateConfig(
                                ConfigUpdate.MenuFloatingIconLiquidGlass(
                                    it
                                )
                            )
                        )
                    },
                )
            }
        }
    }
}

// ========== Tab 1: Bottom Bar ==========

@Composable
private fun BottomBarTab(
    preferences: ReadPreferences,
    customIcons: Map<String, String>,
    onIntent: (ReadBookIntent) -> Unit,
    onShowIconSheet: () -> Unit,
    onShowColorPicker: (Int, Int) -> Unit,
) {
    val floatingBottomBar = preferences.readMenuFloatingBottomBar
    val bottomBarBlurMode = if (
        !floatingBottomBar &&
        preferences.readMenuBottomBarBlurMode == ReadMenuBlurMode.LiquidGlass
    ) {
        ReadMenuBlurMode.Haze
    } else {
        preferences.readMenuBottomBarBlurMode
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        val readSliderModeEntries = stringArrayResource(R.array.read_slider_mode)
        val readSliderModeValues = stringArrayResource(R.array.read_slider_mode_value)

        TinyDropdownSettingItem(
            title = stringResource(R.string.read_slider_mode),
            selectedValue = preferences.readSliderMode,
            displayEntries = readSliderModeEntries,
            entryValues = readSliderModeValues,
            onValueChange = {
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.ReadSliderMode(it)))
            },
        )

        SectionTitle(stringResource(R.string.read_menu_icon_style))

        TinySwitchSettingItem(
            title = stringResource(R.string.read_menu_show_icon_text),
            checked = preferences.readMenuIconShowText,
            onCheckedChange = {
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.MenuIconShowText(it)))
            },
        )
        TinyDropdownSettingItem(
            title = stringResource(R.string.read_menu_icon_container_style),
            selectedValue = preferences.readMenuIconStyle.toString(),
            displayEntries = arrayOf(
                stringResource(R.string.read_menu_icon_style_plain),
                stringResource(R.string.read_menu_icon_style_tonal),
                stringResource(R.string.read_menu_icon_style_outlined),
            ),
            entryValues = arrayOf("0", "1", "2"),
            onValueChange = {
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.MenuIconStyle(it.toInt())))
            },
        )
        TinySliderSettingItem(
            title = stringResource(R.string.read_menu_icons_per_row),
            value = preferences.readMenuIconItemsPerRow.toFloat(),
            valueRange = 2f..8f,
            steps = 5,
            onValueChange = {
                onIntent(
                    ReadBookIntent.UpdateConfig(
                        ConfigUpdate.MenuIconItemsPerRow(it.toInt())
                    )
                )
            },
        )
        TinySliderSettingItem(
            title = stringResource(R.string.read_menu_icon_row_count),
            value = preferences.readMenuIconRowCount.toFloat(),
            valueRange = 1f..2f,
            steps = 0,
            onValueChange = {
                onIntent(
                    ReadBookIntent.UpdateConfig(
                        ConfigUpdate.MenuIconRowCount(it.toInt())
                    )
                )
            },
        )
        TinyClickableSettingItem(
            title = stringResource(R.string.config_btn),
            description = if (customIcons.isEmpty()) {
                stringResource(R.string.read_menu_custom_icons_none)
            } else {
                stringResource(R.string.read_menu_custom_icons_count, customIcons.size)
            },
            onClick = onShowIconSheet,
        )

        SectionTitle(stringResource(R.string.read_menu_bottom_bar_layout))

        TinySliderSettingItem(
            title = stringResource(R.string.read_menu_bottom_corner_radius),
            value = preferences.readMenuBottomCornerRadius.toFloat(),
            valueRange = 0f..32f,
            steps = 31,
            onValueChange = {
                onIntent(
                    ReadBookIntent.UpdateConfig(
                        ConfigUpdate.MenuBottomCornerRadius(it.toInt())
                    )
                )
            },
        )
        TinySwitchSettingItem(
            title = stringResource(R.string.read_menu_floating_bottom_bar),
            checked = floatingBottomBar,
            onCheckedChange = {
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.FloatingBottomBar(it)))
            },
        )
        TinySwitchSettingItem(
            title = stringResource(R.string.read_menu_bar_blur),
            checked = bottomBarBlurMode != ReadMenuBlurMode.None,
            onCheckedChange = {
                onIntent(
                    ReadBookIntent.UpdateConfig(
                        ConfigUpdate.MenuBottomBarBlurMode(
                            if (it) ReadMenuBlurMode.Haze else ReadMenuBlurMode.None
                        )
                    )
                )
            },
        )
        AnimatedVisibility(visible = !floatingBottomBar) {
            TinyDropdownSettingItem(
                title = stringResource(R.string.read_menu_bar_blur_style),
                selectedValue = preferences.readMenuBottomBarBlurStyle.toString(),
                displayEntries = arrayOf(
                    stringResource(R.string.read_menu_blur_style_solid),
                    stringResource(R.string.read_menu_blur_style_progressive),
                ),
                entryValues = arrayOf(
                    ReadMenuBlurStyle.Solid.toString(),
                    ReadMenuBlurStyle.Progressive.toString(),
                ),
                onValueChange = {
                    onIntent(
                        ReadBookIntent.UpdateConfig(
                            ConfigUpdate.MenuBottomBarBlurStyle(it.toInt())
                        )
                    )
                },
            )
        }
        AnimatedVisibility(visible = floatingBottomBar) {
            TinySwitchSettingItem(
                title = stringResource(R.string.read_menu_bar_liquid_glass),
                description = stringResource(R.string.read_menu_bar_liquid_glass_summary),
                checked = preferences.readMenuBottomBarBlurMode == ReadMenuBlurMode.LiquidGlass,
                onCheckedChange = {
                    onIntent(
                        ReadBookIntent.UpdateConfig(
                            ConfigUpdate.MenuBottomBarBlurMode(
                                if (it) {
                                    ReadMenuBlurMode.LiquidGlass
                                } else if (bottomBarBlurMode != ReadMenuBlurMode.None) {
                                    ReadMenuBlurMode.Haze
                                } else {
                                    ReadMenuBlurMode.None
                                }
                            )
                        )
                    )
                },
            )
        }
        TinySwitchSettingItem(
            title = stringResource(R.string.read_menu_bar_liquid_glass_buttons),
            description = stringResource(R.string.read_menu_bottom_bar_liquid_glass_buttons_summary),
            checked = preferences.readMenuBottomBarLiquidGlassButtons,
            onCheckedChange = {
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.MenuBottomBarLiquidGlassButtons(it)))
            },
        )
    }
}

// ========== Tab 2: Top Bar ==========

@Composable
private fun TopBarTab(
    preferences: ReadPreferences,
    onIntent: (ReadBookIntent) -> Unit,
) {
    val topBarBlurEnabled = preferences.readMenuTopBarBlurMode == ReadMenuBlurMode.Haze

    val titleBarModeEntries = stringArrayResource(R.array.title_bar_mode)
    val titleBarModeValues = stringArrayResource(R.array.title_bar_mode_value)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        TinySwitchSettingItem(
            title = stringResource(R.string.read_menu_top_bar_title_capsule),
            checked = preferences.readMenuTopBarTitleCapsule,
            onCheckedChange = {
                onIntent(
                    ReadBookIntent.UpdateConfig(
                        ConfigUpdate.MenuTopBarTitleCapsule(
                            it
                        )
                    )
                )
            },
        )
        AnimatedVisibility(visible = !preferences.readMenuTopBarTitleCapsule) {
            TinyDropdownSettingItem(
                title = stringResource(R.string.title_bar_mode),
                selectedValue = preferences.titleBarMode,
                displayEntries = titleBarModeEntries,
                entryValues = titleBarModeValues,
                onValueChange = { value ->
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TitleBarMode(value)))
                },
            )
        }
        TinySwitchSettingItem(
            title = stringResource(R.string.title_bar_compact),
            checked = preferences.titleBarCompact,
            onCheckedChange = {
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TitleBarCompact(it)))
            },
        )
        TinyDropdownSettingItem(
            title = stringResource(R.string.read_menu_icon_container_style),
            selectedValue = preferences.titleBarIconStyle.toString(),
            displayEntries = arrayOf(
                stringResource(R.string.read_menu_icon_style_plain),
                stringResource(R.string.read_menu_icon_style_tonal),
                stringResource(R.string.read_menu_icon_style_outlined),
            ),
            entryValues = arrayOf("0", "1", "2"),
            onValueChange = {
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TitleBarIconStyle(it.toInt())))
            },
        )
        TinySwitchSettingItem(
            title = stringResource(R.string.read_menu_bar_blur),
            checked = topBarBlurEnabled,
            onCheckedChange = {
                onIntent(
                    ReadBookIntent.UpdateConfig(
                        ConfigUpdate.MenuTopBarBlurSelection(
                            mode = if (it) ReadMenuBlurMode.Haze else ReadMenuBlurMode.None,
                            style = preferences.readMenuTopBarBlurStyle,
                        )
                    )
                )
            },
        )
        TinyDropdownSettingItem(
            title = stringResource(R.string.read_menu_bar_blur_style),
            selectedValue = preferences.readMenuTopBarBlurStyle.toString(),
            displayEntries = arrayOf(
                stringResource(R.string.read_menu_blur_style_solid),
                stringResource(R.string.read_menu_blur_style_progressive),
            ),
            entryValues = arrayOf(
                ReadMenuBlurStyle.Solid.toString(),
                ReadMenuBlurStyle.Progressive.toString(),
            ),
            onValueChange = {
                onIntent(
                    ReadBookIntent.UpdateConfig(
                        ConfigUpdate.MenuTopBarBlurSelection(
                            mode = preferences.readMenuTopBarBlurMode,
                            style = it.toInt(),
                        )
                    )
                )
            },
        )
        TinySwitchSettingItem(
            title = stringResource(R.string.read_menu_bar_liquid_glass_buttons),
            description = stringResource(R.string.read_menu_top_bar_liquid_glass_buttons_summary),
            checked = preferences.readMenuTopBarLiquidGlassButtons,
            onCheckedChange = {
                onIntent(
                    ReadBookIntent.UpdateConfig(
                        ConfigUpdate.MenuTopBarLiquidGlassButtons(
                            it
                        )
                    )
                )
            },
        )
    }
}

private fun countCustomIcons(value: String): Int {
    if (value.isBlank()) return 0
    return GSON.fromJsonObject<Map<String, String>>(value)
        .getOrNull()
        ?.count { it.value.isNotBlank() }
        ?: 0
}

// ========== Helpers ==========

internal data class ReadMenuButtonInfo(
    val id: String,
    val icon: ImageVector,
    val label: String,
)

internal fun readMenuButtonInfos(context: Context): List<ReadMenuButtonInfo> = listOf(
    ReadMenuButtonInfo("search", Icons.Default.Search, context.getString(R.string.search_content)),
    ReadMenuButtonInfo("auto_page", Icons.Default.PlayArrow, context.getString(R.string.auto_next_page)),
    ReadMenuButtonInfo("catalog", Icons.AutoMirrored.Filled.List, context.getString(R.string.chapter_list)),
    ReadMenuButtonInfo("read_aloud", Icons.Default.RecordVoiceOver, context.getString(R.string.read_aloud)),
    ReadMenuButtonInfo("setting", Icons.Default.Settings, context.getString(R.string.setting)),
    ReadMenuButtonInfo("addBookmark", Icons.Default.Bookmark, context.getString(R.string.bookmark)),
    ReadMenuButtonInfo("theme", Icons.Default.Brightness6, context.getString(R.string.day_night_switch)),
    ReadMenuButtonInfo("eye_protection", Icons.Default.Visibility, context.getString(R.string.eye_protection)),
    ReadMenuButtonInfo("prev_chapter", Icons.Default.SkipPrevious, context.getString(R.string.previous_chapter)),
    ReadMenuButtonInfo("next_chapter", Icons.Default.SkipNext, context.getString(R.string.next_chapter)),
    ReadMenuButtonInfo("replace", Icons.Default.FindReplace, context.getString(R.string.replace_purify)),
    ReadMenuButtonInfo(
        "replace_badge",
        Icons.Default.FindReplace,
        context.getString(R.string.replace_purify_badge)
    ),
    ReadMenuButtonInfo("translate", Icons.Default.Translate, context.getString(R.string.translate)),
    ReadMenuButtonInfo(
        "ai_summary",
        Icons.Default.AutoAwesome,
        context.getString(R.string.ai_chapter_summary)
    ),
    ReadMenuButtonInfo(
        "ai_rewrite",
        Icons.Default.Edit,
        context.getString(R.string.ai_text_rewrite)
    ),
)

// ========== Color Swatch ==========

@Composable
private fun TinyColorSwatch(
    color: Int,
) {
    val swatchColor = if (color != 0) Color(color) else LegadoTheme.colorScheme.onSurfaceVariant
    val borderColor = LegadoTheme.colorScheme.outline
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(swatchColor)
            .then(
                if (color == 0) Modifier.border(1.5.dp, borderColor, CircleShape)
                else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (color == 0) {
            AppText(
                text = "A",
                style = LegadoTheme.typography.labelSmall,
                color = LegadoTheme.colorScheme.surfaceContainerLow,
                textAlign = TextAlign.Center,
            )
        }
    }
}
