package io.legado.app.ui.book.read.sheet

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import io.legado.app.R
import io.legado.app.constant.ReadMenuBlurMode
import io.legado.app.constant.ReadMenuBlurStyle
import io.legado.app.data.repository.ReadPreferences
import io.legado.app.data.repository.ReadSettingsRepository
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.book.read.ConfigUpdate
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.book.read.ReadBookSheet
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.series.SmallTonalButton
import io.legado.app.ui.widget.components.dialog.ColorPickerSheet
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.TinyClearColorModeSettingItem
import io.legado.app.ui.widget.components.settingItem.TinyClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.TinyColorModeSettingItem
import io.legado.app.ui.widget.components.settingItem.TinyDropdownSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySliderSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySwitchSettingItem
import io.legado.app.ui.widget.components.tabRow.CardTabRow
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.io.File

private const val COLOR_BG = 5
private const val COLOR_MENU_ACCENT = 6
private const val COLOR_MENU_CONTAINER = 7
private const val COLOR_BG_NIGHT = 8
private const val COLOR_MENU_ACCENT_NIGHT = 9
private const val COLOR_MENU_CONTAINER_NIGHT = 10
private const val COLOR_BORDER = 11
private const val COLOR_BORDER_NIGHT = 12

@Composable
internal fun SystemMenuPage(
    modifier: Modifier = Modifier,
    onIntent: (ReadBookIntent) -> Unit,
) {
    val context = LocalContext.current
    val readSettingsRepository: ReadSettingsRepository = koinInject()
    val preferences by readSettingsRepository.preferences.collectAsStateWithLifecycle(
        initialValue = ReadPreferences()
    )
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 3 })
    var selectedTab by remember { mutableIntStateOf(0) }

    // Shared state for sheets
    var showColorPicker by remember { mutableStateOf(false) }
    var colorPickerId by remember { mutableIntStateOf(0) }
    var colorPickerInitial by remember { mutableIntStateOf(0) }
    var showIconSheet by remember { mutableStateOf(false) }
    var customIcons by remember { mutableStateOf(loadMenuCustomIcons(context)) }
    var activeIconId by remember { mutableStateOf<String?>(null) }

    val iconPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        val id = activeIconId ?: return@rememberLauncherForActivityResult
        uri?.let {
            val iconFile = File(context.filesDir, "read_menu_icons/$id.png")
            iconFile.parentFile?.mkdirs()
            context.contentResolver.openInputStream(it)?.use { input ->
                iconFile.outputStream().use { output -> input.copyTo(output) }
            }
            customIcons = customIcons + (id to iconFile.absolutePath)
            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.MenuCustomIcon(id, iconFile.absolutePath)))
        }
        activeIconId = null
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { selectedTab = it }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        val tabTitles = listOf(
            stringResource(R.string.read_config_menu_system),
            stringResource(R.string.read_menu_bottom_bar_layout),
            stringResource(R.string.title_bar_icons),
        )
        CardTabRow(
            tabTitles = tabTitles,
            selectedTabIndex = selectedTab,
            onTabSelected = { index ->
                selectedTab = index
                scope.launch { pagerState.animateScrollToPage(index) }
            },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f, fill = false),
        ) { page ->
            when (page) {
                0 -> GlobalMenuTab(
                    preferences = preferences,
                    readBarStyle = preferences.readBarStyle,
                    readMenuColorMode = preferences.readMenuColorMode,
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

    // Floating sheets
    if (showColorPicker) {
        ColorPickerSheet(
            show = true,
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
                }
                showColorPicker = false
            },
        )
    }

    if (showIconSheet) {
        MenuCustomIconSheet(
            customIcons = customIcons,
            onSelectIcon = { id ->
                activeIconId = id
                iconPicker.launch("image/*")
            },
            onClearIcon = { id ->
                customIcons[id]?.let { File(it).delete() }
                customIcons = customIcons - id
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.MenuCustomIcon(id, "")))
            },
            onDismissRequest = { showIconSheet = false },
        )
    }
}

// ========== Tab 0: Global ==========

@Composable
private fun GlobalMenuTab(
    preferences: ReadPreferences,
    readBarStyle: Int,
    readMenuColorMode: Int,
    onIntent: (ReadBookIntent) -> Unit,
    onShowColorPicker: (Int, Int) -> Unit,
) {
    var bottomMode by remember(readBarStyle) { mutableIntStateOf(readBarStyle) }
    var colorMode by remember(readMenuColorMode) {
        mutableIntStateOf(readMenuColorMode.coerceIn(0, 1))
    }
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
                bottomMode = it.toInt()
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.ReadBarStyle(bottomMode)))
            },
        )

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
                        colorMode = it.toInt()
                        onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.MenuColorMode(colorMode)))
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

        var iconPosition by remember { mutableIntStateOf(preferences.titleBarIconPosition) }
        TinyDropdownSettingItem(
            title = stringResource(R.string.title_bar_icon_position),
            selectedValue = iconPosition.toString(),
            displayEntries = arrayOf(
                stringResource(R.string.position_top_start),
                stringResource(R.string.position_top_end),
                stringResource(R.string.position_bottom_start),
                stringResource(R.string.position_bottom_end),
            ),
            entryValues = arrayOf("0", "1", "2", "3"),
            onValueChange = {
                iconPosition = it.toInt()
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TitleBarIconPosition(iconPosition)))
            },
        )

        Spacer(Modifier.height(8.dp))

        var borderEnabled by remember {
            mutableStateOf(preferences.readMenuBorderWidth > 0)
        }
        TinySwitchSettingItem(
            title = stringResource(R.string.read_menu_border),
            checked = borderEnabled,
            onCheckedChange = {
                borderEnabled = it
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

        Spacer(Modifier.height(8.dp))

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
    var showIconText by remember { mutableStateOf(preferences.readMenuIconShowText) }
    var iconStyle by remember { mutableIntStateOf(preferences.readMenuIconStyle) }
    var iconsPerRow by remember { mutableIntStateOf(preferences.readMenuIconItemsPerRow) }
    var iconRowCount by remember { mutableIntStateOf(preferences.readMenuIconRowCount) }
    var bottomCornerRadius by remember { mutableIntStateOf(preferences.readMenuBottomCornerRadius) }
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
        SectionTitle(stringResource(R.string.read_menu_icon_style))

        TinySwitchSettingItem(
            title = stringResource(R.string.read_menu_show_icon_text),
            checked = showIconText,
            onCheckedChange = {
                showIconText = it
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.MenuIconShowText(it)))
            },
        )
        TinyDropdownSettingItem(
            title = stringResource(R.string.read_menu_icon_container_style),
            selectedValue = iconStyle.toString(),
            displayEntries = arrayOf(
                stringResource(R.string.read_menu_icon_style_plain),
                stringResource(R.string.read_menu_icon_style_tonal),
                stringResource(R.string.read_menu_icon_style_outlined),
            ),
            entryValues = arrayOf("0", "1", "2"),
            onValueChange = {
                iconStyle = it.toInt()
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.MenuIconStyle(iconStyle)))
            },
        )
        TinySliderSettingItem(
            title = stringResource(R.string.read_menu_icons_per_row),
            value = iconsPerRow.toFloat(),
            valueRange = 2f..8f,
            steps = 5,
            onValueChange = {
                iconsPerRow = it.toInt()
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.MenuIconItemsPerRow(iconsPerRow)))
            },
        )
        TinySliderSettingItem(
            title = stringResource(R.string.read_menu_icon_row_count),
            value = iconRowCount.toFloat(),
            valueRange = 1f..2f,
            steps = 0,
            onValueChange = {
                iconRowCount = it.toInt()
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.MenuIconRowCount(iconRowCount)))
            },
        )
        TinyClickableSettingItem(
            title = stringResource(R.string.read_menu_custom_icons),
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
            value = bottomCornerRadius.toFloat(),
            valueRange = 0f..32f,
            steps = 31,
            onValueChange = {
                bottomCornerRadius = it.toInt()
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.MenuBottomCornerRadius(bottomCornerRadius)))
            },
        )
        TinySwitchSettingItem(
            title = stringResource(R.string.read_menu_floating_bottom_bar),
            checked = floatingBottomBar,
            onCheckedChange = {
                if (!it && preferences.readMenuBottomBarBlurMode == ReadMenuBlurMode.LiquidGlass) {
                    onIntent(
                        ReadBookIntent.UpdateConfig(
                            ConfigUpdate.MenuBottomBarBlurMode(
                                ReadMenuBlurMode.Haze
                            )
                        )
                    )
                }
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
        AnimatedVisibility(visible = !floatingBottomBar && bottomBarBlurMode == ReadMenuBlurMode.Haze) {
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
    val customIconCount = remember(preferences.titleBarCustomIcons) {
        countCustomIcons(preferences.titleBarCustomIcons)
    }
    val topBarBlurEnabled = preferences.readMenuTopBarBlurMode == ReadMenuBlurMode.Haze

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        TinyClickableSettingItem(
            title = stringResource(R.string.title_bar_icons),
            description = if (customIconCount == 0) {
                stringResource(R.string.read_menu_custom_icons_none)
            } else {
                stringResource(R.string.read_menu_custom_icons_count, customIconCount)
            },
            onClick = {
                onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.TitleBarIconConfig))
            },
        )
        Spacer(Modifier.height(8.dp))
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
        AnimatedVisibility(visible = topBarBlurEnabled) {
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
                                mode = ReadMenuBlurMode.Haze,
                                style = it.toInt(),
                            )
                        )
                    )
                },
            )
        }
        AnimatedVisibility(visible = topBarBlurEnabled) {
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
}

private fun countCustomIcons(value: String): Int {
    if (value.isBlank()) return 0
    return GSON.fromJsonObject<Map<String, String>>(value)
        .getOrNull()
        ?.count { it.value.isNotBlank() }
        ?: 0
}

// ========== Icon Sheet (Bottom Bar) ==========

@Composable
private fun MenuCustomIconSheet(
    customIcons: Map<String, String>,
    onSelectIcon: (String) -> Unit,
    onClearIcon: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("tool_button_config", Context.MODE_PRIVATE)
    }
    var items by remember {
        mutableStateOf(loadMenuCustomIconItems(prefs, context))
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        items = items.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
    }

    AppModalBottomSheet(
        show = true,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.read_menu_custom_icons),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            LazyColumn(
                state = lazyListState,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                items(items, key = { it.id }) { item ->
                    ReorderableItem(reorderableState, key = item.id) { isDragging ->
                        val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp)
                        Surface(shadowElevation = elevation) {
                            MenuCustomIconItem(
                                item = item,
                                customIcon = customIcons[item.id],
                                onSelectIcon = { onSelectIcon(item.id) },
                                onClearIcon = { onClearIcon(item.id) },
                                dragHandleModifier = Modifier.draggableHandle(),
                            )
                        }
                    }
                }
            }

            TextButton(
                onClick = {
                    saveMenuCustomIconOrder(prefs, items)
                    onDismissRequest()
                },
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

@Composable
private fun MenuCustomIconItem(
    item: ReadMenuButtonInfo,
    customIcon: String?,
    onSelectIcon: () -> Unit,
    onClearIcon: () -> Unit,
    dragHandleModifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(40.dp),
        ) {
            if (!customIcon.isNullOrBlank()) {
                AsyncImage(
                    model = customIcon,
                    contentDescription = item.label,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    painter = painterResource(item.iconRes),
                    contentDescription = item.label,
                    tint = LegadoTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Text(
            text = item.label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        )
        if (!customIcon.isNullOrBlank()) {
            IconButton(
                onClick = onClearIcon,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.delete))
            }
        } else {
            SmallTonalButton(
                onClick = onSelectIcon,
                icon = Icons.Default.Add,
                modifier = Modifier.size(36.dp),
            )
        }
        IconButton(
            modifier = dragHandleModifier.size(36.dp),
            onClick = {},
        ) {
            Icon(
                Icons.Default.Menu,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ========== Helpers ==========

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

internal data class ReadMenuButtonInfo(
    val id: String,
    val iconRes: Int,
    val label: String,
)

internal fun readMenuButtonInfos(context: Context): List<ReadMenuButtonInfo> = listOf(
    ReadMenuButtonInfo("search", R.drawable.ic_search, context.getString(R.string.search_content)),
    ReadMenuButtonInfo("auto_page", R.drawable.ic_auto_page, context.getString(R.string.auto_next_page)),
    ReadMenuButtonInfo("catalog", R.drawable.ic_toc, context.getString(R.string.chapter_list)),
    ReadMenuButtonInfo("read_aloud", R.drawable.ic_read_aloud, context.getString(R.string.read_aloud)),
    ReadMenuButtonInfo("setting", R.drawable.ic_settings, context.getString(R.string.setting)),
    ReadMenuButtonInfo("addBookmark", R.drawable.ic_bookmark, context.getString(R.string.bookmark)),
    ReadMenuButtonInfo("theme", R.drawable.ic_brightness, context.getString(R.string.day_night_switch)),
    ReadMenuButtonInfo("prev_chapter", R.drawable.ic_previous, context.getString(R.string.previous_chapter)),
    ReadMenuButtonInfo("next_chapter", R.drawable.ic_next, context.getString(R.string.next_chapter)),
    ReadMenuButtonInfo("replace", R.drawable.ic_find_replace, context.getString(R.string.replace_purify)),
    ReadMenuButtonInfo("replace_badge", R.drawable.ic_find_replace, context.getString(R.string.replace_purify_badge)),
    ReadMenuButtonInfo("translate", R.drawable.ic_translate, context.getString(R.string.translate)),
)

internal fun loadMenuCustomIcons(context: Context): Map<String, String> {
    val supportedIds = readMenuButtonInfos(context).map { it.id }.toSet()
    return ReadBookConfig.readMenuCustomIcons.filterKeys { it in supportedIds }
}

private fun loadMenuCustomIconItems(
    prefs: android.content.SharedPreferences,
    context: Context,
): List<ReadMenuButtonInfo> {
    val str = prefs.getString("tool_buttons", null)
    val allInfos = readMenuButtonInfos(context).associateBy { it.id }

    if (str.isNullOrBlank()) {
        return allInfos.values.toList()
    }

    val savedOrder = str.split(";").mapNotNull {
        val parts = it.split(",")
        parts.getOrNull(0)
    }
    val ordered = savedOrder.mapNotNull { allInfos[it] }
    val remaining = allInfos.values.filter { it.id !in savedOrder.toSet() }
    return ordered + remaining
}

private fun saveMenuCustomIconOrder(
    prefs: android.content.SharedPreferences,
    list: List<ReadMenuButtonInfo>,
) {
    // Preserve enabled state from existing config
    val existing = prefs.getString("tool_buttons", null)
    val enabledMap = if (!existing.isNullOrBlank()) {
        existing.split(";").associate {
            val parts = it.split(",")
            parts[0] to (parts.getOrNull(1)?.toBoolean() ?: true)
        }
    } else {
        list.mapIndexed { index, item -> item.id to (index < 5) }.toMap()
    }

    val str = list.joinToString(";") { "${it.id},${enabledMap[it.id] ?: true}" }
    prefs.edit().putString("tool_buttons", str).apply()
}
