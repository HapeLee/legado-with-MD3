package io.legado.app.ui.book.manga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.book.manga.config.MangaScrollMode
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.dialog.ColorPickerSheet
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.pager.pagerHeight
import io.legado.app.ui.widget.components.pager.rememberPagerAnimatedHeight
import io.legado.app.ui.widget.components.settingItem.TinyColorSettingItem
import io.legado.app.ui.widget.components.settingItem.TinyDropdownSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySliderSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySwitchSettingItem
import io.legado.app.ui.widget.components.tabRow.CardTabRow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val MangaSettingsPanelContentMaxHeight = 380.dp

/** 底部栏设置面板顶部的分类 tab（自动阅读不在此列，长按菜单按钮进入）。 */
private val MangaSettingsTabs = listOf(
    MangaReaderSettingsCategory.READER,
    MangaReaderSettingsCategory.FOOTER,
    MangaReaderSettingsCategory.MENU,
    MangaReaderSettingsCategory.FILTER,
    MangaReaderSettingsCategory.CLICK_ACTIONS,
)

private val MangaReaderSettingsCategory.titleRes: Int
    get() = when (this) {
        MangaReaderSettingsCategory.READER -> R.string.general
        MangaReaderSettingsCategory.FOOTER -> R.string.footer
        MangaReaderSettingsCategory.MENU -> R.string.manga_reader_menu_layout
        MangaReaderSettingsCategory.FILTER -> R.string.manga_reader_filter_short
        MangaReaderSettingsCategory.CLICK_ACTIONS -> R.string.manga_reader_click_area_short
        MangaReaderSettingsCategory.AUTO_READ -> R.string.manga_reader_auto_read
    }

private enum class MangaColorPickerTarget { BACKGROUND, FILTER, MENU_SEED }

/**
 * 漫画阅读设置的底部栏面板：顶部分类 tab（[CardTabRow]），内容区用
 * [HorizontalPager] 左右滑动切换；自动阅读分类不在 tab 里，由长按菜单按钮进入。
 */
@Composable
internal fun MangaSettingsPanel(
    state: MangaReaderUiState,
    onIntent: (MangaReaderIntent) -> Unit,
) {
    val current = state.settingsCategory ?: MangaReaderSettingsCategory.READER
    if (current == MangaReaderSettingsCategory.AUTO_READ) {
        AutoReadSettingsPage(state, onIntent)
        return
    }
    val scope = rememberCoroutineScope()
    val tabIndex = MangaSettingsTabs.indexOf(current).coerceAtLeast(0)
    val pagerState = rememberPagerState(
        initialPage = tabIndex,
        pageCount = { MangaSettingsTabs.size },
    )
    var selectedTab by remember { mutableIntStateOf(tabIndex) }
    var clickScrollCount by remember { mutableIntStateOf(0) }
    var colorPickerTarget by remember { mutableStateOf<MangaColorPickerTarget?>(null) }
    val pageHeights = remember { mutableStateMapOf<Int, Int>() }
    val animatedHeight by rememberPagerAnimatedHeight(pagerState, pageHeights)

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                if (clickScrollCount == 0) selectedTab = page
            }
    }
    LaunchedEffect(tabIndex) {
        if (pagerState.currentPage != tabIndex) {
            pagerState.scrollToPage(tabIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MediumTonalButton(
                onClick = { onIntent(MangaReaderIntent.CloseSettings) },
                icon = AppIcons.Back,
                contentDescription = stringResource(R.string.back),
            )
            CardTabRow(
                tabTitles = MangaSettingsTabs.map { stringResource(it.titleRes) },
                selectedTabIndex = selectedTab,
                onTabSelected = { index ->
                    selectedTab = index
                    clickScrollCount++
                    scope.launch {
                        try {
                            pagerState.animateScrollToPage(
                                page = index,
                                animationSpec = tween(
                                    durationMillis = 300,
                                    easing = FastOutSlowInEasing,
                                ),
                            )
                        } finally {
                            clickScrollCount = (clickScrollCount - 1).coerceAtLeast(0)
                        }
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        HorizontalPager(
            state = pagerState,
            // 顶部对齐 + 裁剪，对齐阅读正文 SystemMenuPage：切换不同高度页面时内容顶部不动、只收放底部
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = MangaSettingsPanelContentMaxHeight)
                .clipToBounds()
                .pagerHeight(animatedHeight),
        ) { page ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { size -> pageHeights[page] = size.height },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    when (MangaSettingsTabs[page]) {
                        MangaReaderSettingsCategory.READER -> ReaderSettingsContent(
                            state.settings,
                            onIntent
                        )

                        MangaReaderSettingsCategory.FOOTER -> FooterSettingsContent(
                            state.settings,
                            onIntent
                        )

                        MangaReaderSettingsCategory.MENU -> MenuSettingsContent(
                            settings = state.settings,
                            onPickSeedColor = { colorPickerTarget = MangaColorPickerTarget.MENU_SEED },
                            onIntent = onIntent,
                        )

                        MangaReaderSettingsCategory.FILTER -> FilterSettingsContent(
                            settings = state.settings,
                            onPickBackground = {
                                colorPickerTarget = MangaColorPickerTarget.BACKGROUND
                            },
                            onPickFilter = { colorPickerTarget = MangaColorPickerTarget.FILTER },
                            onIntent = onIntent,
                        )

                        MangaReaderSettingsCategory.CLICK_ACTIONS -> ClickActionsSettingsContent(
                            state.settings,
                            onIntent
                        )

                        // 自动阅读不走 pager，已在 MangaSettingsPanel 入口单独处理
                        MangaReaderSettingsCategory.AUTO_READ -> Unit
                    }
                }
            }
        }
    }

    colorPickerTarget?.let { target ->
        ColorPickerSheet(
            show = true,
            initialColor = when (target) {
                MangaColorPickerTarget.BACKGROUND -> state.settings.backgroundColor.toArgb()
                MangaColorPickerTarget.FILTER -> Color(
                    red = 255 - state.settings.filterRed,
                    green = 255 - state.settings.filterGreen,
                    blue = 255 - state.settings.filterBlue,
                ).toArgb()
                MangaColorPickerTarget.MENU_SEED -> state.settings.menuSeedColor.toArgb()
            },
            onDismissRequest = { colorPickerTarget = null },
            onColorSelected = { argb ->
                val picked = Color(argb)
                val red = (picked.red * 255).roundToInt()
                val green = (picked.green * 255).roundToInt()
                val blue = (picked.blue * 255).roundToInt()
                when (target) {
                    MangaColorPickerTarget.BACKGROUND -> {
                        onIntent(
                            MangaReaderIntent.UpdateSetting(
                                MangaReaderSettingKey.BACKGROUND_RED,
                                red
                            )
                        )
                        onIntent(
                            MangaReaderIntent.UpdateSetting(
                                MangaReaderSettingKey.BACKGROUND_GREEN,
                                green
                            )
                        )
                        onIntent(
                            MangaReaderIntent.UpdateSetting(
                                MangaReaderSettingKey.BACKGROUND_BLUE,
                                blue
                            )
                        )
                    }

                    MangaColorPickerTarget.FILTER -> {
                        // 存储的是「移除量」：所选颜色即要保留的通道，取反映射
                        onIntent(
                            MangaReaderIntent.UpdateSetting(
                                MangaReaderSettingKey.FILTER_RED,
                                255 - red
                            )
                        )
                        onIntent(
                            MangaReaderIntent.UpdateSetting(
                                MangaReaderSettingKey.FILTER_GREEN,
                                255 - green
                            )
                        )
                        onIntent(
                            MangaReaderIntent.UpdateSetting(
                                MangaReaderSettingKey.FILTER_BLUE,
                                255 - blue
                            )
                        )
                    }

                    MangaColorPickerTarget.MENU_SEED -> onIntent(
                        MangaReaderIntent.UpdateSetting(MangaReaderSettingKey.MENU_SEED_COLOR, argb)
                    )
                }
                colorPickerTarget = null
            },
        )
    }
}

/** 自动阅读配置页：返回 + 标题头，不显示分类 tab（对齐阅读正文 [io.legado.app.ui.book.read.ReadBookMenuBar] 的配置页头）。 */
@Composable
private fun AutoReadSettingsPage(
    state: MangaReaderUiState,
    onIntent: (MangaReaderIntent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MediumTonalButton(
                onClick = { onIntent(MangaReaderIntent.CloseSettings) },
                icon = AppIcons.Back,
                contentDescription = stringResource(R.string.back),
            )
            Text(
                text = stringResource(MangaReaderSettingsCategory.AUTO_READ.titleRes),
                style = LegadoTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            AutoReadSettingsContent(state, onIntent)
        }
    }
}

@Composable
private fun ReaderSettingsContent(
    settings: MangaReaderSettings,
    onIntent: (MangaReaderIntent) -> Unit,
) {
    val isWebtoon = settings.scrollMode == MangaScrollMode.WEBTOON ||
        settings.scrollMode == MangaScrollMode.WEBTOON_WITH_GAP
    val isHorizontalPaged = settings.scrollMode == MangaScrollMode.PAGE_LEFT_TO_RIGHT ||
        settings.scrollMode == MangaScrollMode.PAGE_RIGHT_TO_LEFT
    TinyDropdownSettingItem(
        title = stringResource(R.string.read_type),
        selectedValue = settings.scrollMode.toString(),
        displayEntries = arrayOf(
            stringResource(R.string.webtoon),
            stringResource(R.string.manga_reader_webtoon_gap),
            stringResource(R.string.manga_reader_left_to_right),
            stringResource(R.string.manga_reader_right_to_left),
            stringResource(R.string.manga_reader_top_to_bottom),
        ),
        entryValues = arrayOf(
            MangaScrollMode.WEBTOON,
            MangaScrollMode.WEBTOON_WITH_GAP,
            MangaScrollMode.PAGE_LEFT_TO_RIGHT,
            MangaScrollMode.PAGE_RIGHT_TO_LEFT,
            MangaScrollMode.PAGE_TOP_TO_BOTTOM,
        ).map(Int::toString).toTypedArray(),
        onValueChange = {
            onIntent(MangaReaderIntent.UpdateSetting(MangaReaderSettingKey.SCROLL_MODE, it.toInt()))
        },
    )
    if (isWebtoon) {
        SettingSlider(
            stringResource(R.string.manga_reader_side_padding),
            settings.sidePaddingPercent,
            0..45
        ) {
            onIntent(MangaReaderIntent.UpdateSetting(MangaReaderSettingKey.SIDE_PADDING, it))
        }
    } else {
        SettingDropdown(
            stringResource(R.string.manga_reader_scale_type),
            settings.pageScaleType,
            arrayOf(
                R.string.manga_reader_scale_fit_screen,
                R.string.manga_reader_scale_stretch,
                R.string.manga_reader_scale_fit_width,
                R.string.manga_reader_scale_fit_height,
                R.string.manga_reader_scale_original,
                R.string.manga_reader_scale_smart,
            ),
            MangaReaderSettingKey.PAGE_SCALE_TYPE,
            onIntent,
        )
        if (isHorizontalPaged) {
            SettingDropdown(
                stringResource(R.string.manga_reader_zoom_start),
                settings.zoomStartPosition,
                arrayOf(
                    R.string.manga_reader_position_automatic,
                    R.string.manga_reader_position_left,
                    R.string.manga_reader_position_right,
                    R.string.manga_reader_position_center,
                ),
                MangaReaderSettingKey.ZOOM_START_POSITION,
                onIntent,
            )
        }
        SettingDropdown(
            stringResource(R.string.manga_reader_wide_page),
            settings.widePageMode,
            arrayOf(
                R.string.manga_reader_wide_normal,
                R.string.manga_reader_wide_fit_width,
                R.string.manga_reader_wide_rotate,
            ),
            MangaReaderSettingKey.WIDE_PAGE_MODE,
            onIntent,
        )
        if (isHorizontalPaged) {
            SettingDropdown(
                stringResource(R.string.manga_reader_double_page),
                settings.doublePageMode,
                arrayOf(
                    R.string.manga_reader_mode_off,
                    R.string.manga_reader_mode_landscape,
                    R.string.manga_reader_mode_always,
                ),
                MangaReaderSettingKey.DOUBLE_PAGE_MODE,
                onIntent,
            )
        }
    }
    SettingSlider(
        stringResource(R.string.manga_reader_preload_pages),
        settings.preDownloadCount,
        0..30
    ) {
        onIntent(MangaReaderIntent.UpdateSetting(MangaReaderSettingKey.PRE_DOWNLOAD, it))
    }
    SettingSwitch(stringResource(R.string.manga_reader_pinch_zoom), !settings.disableScale) {
        onIntent(
            MangaReaderIntent.UpdateSetting(
                MangaReaderSettingKey.DISABLE_SCALE,
                (!it).intValue
            )
        )
    }
    SettingSwitch(stringResource(R.string.manga_reader_tap_turn), !settings.disableClickScroll) {
        onIntent(
            MangaReaderIntent.UpdateSetting(
                MangaReaderSettingKey.DISABLE_CLICK_SCROLL,
                (!it).intValue
            )
        )
    }
    SettingSwitch(
        stringResource(R.string.manga_reader_scroll_animation),
        !settings.disableScrollAnimation
    ) {
        onIntent(
            MangaReaderIntent.UpdateSetting(
                MangaReaderSettingKey.DISABLE_SCROLL_ANIMATION,
                (!it).intValue
            )
        )
    }
    SettingSwitch(stringResource(R.string.manga_reader_image_fade), !settings.disableCrossFade) {
        onIntent(
            MangaReaderIntent.UpdateSetting(
                MangaReaderSettingKey.DISABLE_CROSS_FADE,
                (!it).intValue
            )
        )
    }
    SettingSwitch(
        stringResource(R.string.manga_reader_long_press_save),
        settings.longPressEnabled
    ) {
        onIntent(MangaReaderIntent.UpdateSetting(MangaReaderSettingKey.LONG_PRESS, it.intValue))
    }
    SettingSwitch(stringResource(R.string.manga_reader_volume_page), settings.volumeKeyPage) {
        onIntent(
            MangaReaderIntent.UpdateSetting(
                MangaReaderSettingKey.VOLUME_KEY_PAGE,
                it.intValue
            )
        )
    }
    SettingSwitch(
        stringResource(R.string.manga_reader_reverse_volume),
        settings.reverseVolumeKeyPage
    ) {
        onIntent(
            MangaReaderIntent.UpdateSetting(
                MangaReaderSettingKey.REVERSE_VOLUME_KEY_PAGE,
                it.intValue
            )
        )
    }
    SettingSwitch(stringResource(R.string.manga_reader_hide_edge_prompt), settings.hideMangaTitle) {
        onIntent(
            MangaReaderIntent.UpdateSetting(
                MangaReaderSettingKey.HIDE_MANGA_TITLE,
                it.intValue
            )
        )
    }
}

@Composable
private fun FooterSettingsContent(
    settings: MangaReaderSettings,
    onIntent: (MangaReaderIntent) -> Unit,
) {
    SettingSwitch(
        stringResource(R.string.manga_reader_hide_footer),
        settings.hideFooter
    ) { updateBoolean(onIntent, MangaReaderSettingKey.HIDE_FOOTER, it) }
    SettingSwitch(
        stringResource(R.string.manga_reader_hide_chapter_name),
        settings.hideChapterName
    ) { updateBoolean(onIntent, MangaReaderSettingKey.HIDE_CHAPTER_NAME, it) }
    SettingSwitch(
        stringResource(R.string.manga_reader_hide_page_number),
        settings.hidePageNumber
    ) { updateBoolean(onIntent, MangaReaderSettingKey.HIDE_PAGE_NUMBER, it) }
    SettingSwitch(
        stringResource(R.string.manga_reader_hide_page_label),
        settings.hidePageNumberLabel
    ) { updateBoolean(onIntent, MangaReaderSettingKey.HIDE_PAGE_NUMBER_LABEL, it) }
    SettingSwitch(
        stringResource(R.string.manga_reader_hide_chapter_progress),
        settings.hideChapter
    ) { updateBoolean(onIntent, MangaReaderSettingKey.HIDE_CHAPTER, it) }
    SettingSwitch(
        stringResource(R.string.manga_reader_hide_chapter_label),
        settings.hideChapterLabel
    ) { updateBoolean(onIntent, MangaReaderSettingKey.HIDE_CHAPTER_LABEL, it) }
    SettingSwitch(
        stringResource(R.string.manga_reader_hide_total_progress),
        settings.hideProgress
    ) { updateBoolean(onIntent, MangaReaderSettingKey.HIDE_PROGRESS, it) }
    SettingSwitch(
        stringResource(R.string.manga_reader_hide_progress_label),
        settings.hideProgressLabel
    ) { updateBoolean(onIntent, MangaReaderSettingKey.HIDE_PROGRESS_LABEL, it) }
    TinyDropdownSettingItem(
        title = stringResource(R.string.manga_reader_footer_alignment),
        selectedValue = settings.footerAlignment.toString(),
        displayEntries = arrayOf(
            stringResource(R.string.manga_reader_left_align),
            stringResource(R.string.manga_reader_center_align),
        ),
        entryValues = arrayOf("0", "1"),
        onValueChange = {
            updateInt(onIntent, MangaReaderSettingKey.FOOTER_ALIGNMENT, it.toInt())
        },
    )
}

@Composable
private fun MenuSettingsContent(
    settings: MangaReaderSettings,
    onPickSeedColor: () -> Unit,
    onIntent: (MangaReaderIntent) -> Unit,
) {
    SettingDropdown(
        label = stringResource(R.string.read_menu_color_source),
        value = settings.menuColorSource,
        entryLabels = arrayOf(
            R.string.manga_reader_menu_color_background,
            R.string.manga_reader_menu_color_page,
            R.string.manga_reader_menu_color_system,
            R.string.manga_reader_menu_color_custom,
        ),
        key = MangaReaderSettingKey.MENU_COLOR_SOURCE,
        onIntent = onIntent,
    )
    if (settings.menuColorSource == 3) {
        TinyColorSettingItem(
            title = stringResource(R.string.seed_color),
            colorValue = settings.menuSeedColor.toArgb(),
            onClick = onPickSeedColor,
        )
    }
    val paletteEntries = stringArrayResource(R.array.paletteStyle)
    val paletteValues = stringArrayResource(R.array.paletteStyle_value)
    TinyDropdownSettingItem(
        title = stringResource(R.string.palette_style),
        selectedValue = settings.menuPaletteStyle,
        displayEntries = paletteEntries,
        entryValues = paletteValues,
        onValueChange = { onIntent(MangaReaderIntent.UpdateMenuPaletteStyle(it)) },
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.manga_reader_menu_top_bar_liquid_glass),
        description = stringResource(R.string.manga_reader_menu_top_bar_liquid_glass_summary),
        checked = settings.menuTopBarLiquidGlass,
        onCheckedChange = {
            updateBoolean(onIntent, MangaReaderSettingKey.MENU_TOP_BAR_LIQUID_GLASS, it)
        },
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.manga_reader_menu_bottom_bar_liquid_glass),
        description = stringResource(R.string.manga_reader_menu_bottom_bar_liquid_glass_summary),
        checked = settings.menuBottomBarLiquidGlass,
        onCheckedChange = {
            updateBoolean(onIntent, MangaReaderSettingKey.MENU_BOTTOM_BAR_LIQUID_GLASS, it)
        },
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.manga_reader_menu_bottom_bar_floating),
        checked = settings.menuBottomBarFloating,
        onCheckedChange = {
            updateBoolean(onIntent, MangaReaderSettingKey.MENU_BOTTOM_BAR_FLOATING, it)
        },
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.manga_reader_menu_bottom_bar_blur),
        description = stringResource(R.string.manga_reader_menu_bottom_bar_blur_summary),
        checked = settings.menuBottomBarBlur,
        onCheckedChange = {
            updateBoolean(onIntent, MangaReaderSettingKey.MENU_BOTTOM_BAR_BLUR, it)
        },
    )
    TinySwitchSettingItem(
        title = stringResource(R.string.manga_reader_menu_top_bar_compact),
        description = stringResource(R.string.manga_reader_menu_top_bar_compact_summary),
        checked = settings.menuTopBarCompact,
        onCheckedChange = {
            updateBoolean(onIntent, MangaReaderSettingKey.MENU_TOP_BAR_COMPACT, it)
        },
    )
}

@Composable
private fun FilterSettingsContent(
    settings: MangaReaderSettings,
    onPickBackground: () -> Unit,
    onPickFilter: () -> Unit,
    onIntent: (MangaReaderIntent) -> Unit,
) {
    SettingSwitch(
        stringResource(R.string.manga_reader_auto_background),
        settings.autoBackground,
    ) { updateBoolean(onIntent, MangaReaderSettingKey.AUTO_BACKGROUND, it) }
    SettingSwitch(
        stringResource(R.string.manga_reader_grayscale),
        settings.enableGray
    ) { updateBoolean(onIntent, MangaReaderSettingKey.ENABLE_GRAY, it) }
    SettingSwitch(stringResource(R.string.manga_reader_eink), settings.enableEInk) {
        updateBoolean(
            onIntent,
            MangaReaderSettingKey.ENABLE_EINK,
            it
        )
    }
    SettingSlider(
        stringResource(R.string.manga_reader_eink_threshold),
        settings.eInkThreshold,
        0..255
    ) {
        onIntent(MangaReaderIntent.UpdateSetting(MangaReaderSettingKey.EINK_THRESHOLD, it))
    }
    if (!settings.autoBackground) {
        TinyColorSettingItem(
            title = stringResource(R.string.background_color),
            colorValue = settings.backgroundColor.toArgb(),
            onClick = onPickBackground,
        )
    }
    TinyColorSettingItem(
        title = stringResource(R.string.manga_reader_display_filter),
        colorValue = Color(
            red = 255 - settings.filterRed,
            green = 255 - settings.filterGreen,
            blue = 255 - settings.filterBlue,
        ).toArgb(),
        onClick = onPickFilter,
    )
    SettingSlider(
        stringResource(R.string.manga_reader_filter_alpha),
        settings.filterAlpha,
        0..255
    ) { updateInt(onIntent, MangaReaderSettingKey.FILTER_ALPHA, it) }
    SettingSwitch(
        stringResource(R.string.manga_reader_system_brightness),
        settings.autoBrightness
    ) { updateBoolean(onIntent, MangaReaderSettingKey.AUTO_BRIGHTNESS, it) }
    if (!settings.autoBrightness) {
        SettingSlider(
            stringResource(R.string.manga_reader_screen_brightness),
            settings.brightness,
            0..255
        ) {
            updateInt(onIntent, MangaReaderSettingKey.BRIGHTNESS, it)
        }
    }
}

@Composable
private fun AutoReadSettingsContent(
    state: MangaReaderUiState,
    onIntent: (MangaReaderIntent) -> Unit,
) {
    SettingSwitch(stringResource(R.string.manga_reader_enable_auto_read), state.autoReadEnabled) {
        onIntent(MangaReaderIntent.ToggleAutoRead)
    }
    SettingSlider(
        stringResource(R.string.manga_reader_auto_speed),
        state.settings.autoReadSpeed,
        1..15
    ) {
        onIntent(MangaReaderIntent.UpdateSetting(MangaReaderSettingKey.AUTO_READ_SPEED, it))
    }
}

@Composable
private fun ClickActionsSettingsContent(
    settings: MangaReaderSettings,
    onIntent: (MangaReaderIntent) -> Unit,
) {
    val labels = mapOf(
        -1 to stringResource(R.string.non_action),
        0 to stringResource(R.string.manga_reader_menu),
        1 to stringResource(R.string.manga_reader_next_page),
        2 to stringResource(R.string.manga_reader_previous_page),
        3 to stringResource(R.string.next_chapter),
        4 to stringResource(R.string.previous_chapter),
    )
    repeat(3) { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            repeat(3) { column ->
                val index = row * 3 + column
                val action = settings.clickActions.getOrElse(index) { 0 }
                TextButton(onClick = {
                    val next = nextMangaClickAction(action)
                    onIntent(MangaReaderIntent.UpdateClickAction(index, next))
                }) { Text(labels[action].orEmpty()) }
            }
        }
    }
    Text(
        stringResource(R.string.manga_reader_click_cycle_hint),
        style = MaterialTheme.typography.bodySmall
    )
}

private val Boolean.intValue: Int get() = if (this) 1 else 0

private fun updateBoolean(
    onIntent: (MangaReaderIntent) -> Unit,
    key: MangaReaderSettingKey,
    value: Boolean,
) = onIntent(MangaReaderIntent.UpdateSetting(key, value.intValue))

private fun updateInt(
    onIntent: (MangaReaderIntent) -> Unit,
    key: MangaReaderSettingKey,
    value: Int,
) = onIntent(MangaReaderIntent.UpdateSetting(key, value))

@Composable
private fun SettingDropdown(
    label: String,
    value: Int,
    entryLabels: Array<Int>,
    key: MangaReaderSettingKey,
    onIntent: (MangaReaderIntent) -> Unit,
) {
    TinyDropdownSettingItem(
        title = label,
        selectedValue = value.toString(),
        displayEntries = entryLabels.map { stringResource(it) }.toTypedArray(),
        entryValues = entryLabels.indices.map(Int::toString).toTypedArray(),
        onValueChange = { updateInt(onIntent, key, it.toInt()) },
    )
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    TinySwitchSettingItem(
        title = label,
        checked = checked,
        onCheckedChange = onCheckedChange,
    )
}

@Composable
private fun SettingSlider(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit
) {
    TinySliderSettingItem(
        title = label,
        value = value.toFloat().coerceIn(range.first.toFloat(), range.last.toFloat()),
        valueRange = range.first.toFloat()..range.last.toFloat(),
        onValueChange = { onValueChange(it.toInt()) },
    )
}
