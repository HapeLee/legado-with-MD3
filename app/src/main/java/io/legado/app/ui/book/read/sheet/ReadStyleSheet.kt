package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ReadTipConfig
import io.legado.app.model.ReadBook
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.fadingEdge
import io.legado.app.ui.widget.components.button.series.SmallTonalButton
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.dialog.ColorPickerSheet
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.TinyClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.TinyColorSettingItem
import io.legado.app.ui.widget.components.settingItem.TinyDropdownSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySettingItem
import io.legado.app.ui.widget.components.settingItem.TinySliderSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySwitchSettingItem
import io.legado.app.ui.widget.components.tabRow.CardTabRow
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.postEvent
import kotlinx.coroutines.launch

@Composable
fun ReadStyleSheet(
    onDismissRequest: () -> Unit,
    onOpenPaddingConfig: () -> Unit,
    onOpenMoreConfig: () -> Unit,
    onOpenBgTextConfig: (Int) -> Unit,
    onOpenShadowSet: () -> Unit,
    onOpenUnderlineConfig: () -> Unit,
    onOpenRegexColor: () -> Unit,
    onOpenFontSelect: () -> Unit,
    onToggleDayNight: () -> Unit,
) {
    var showTextTitle by remember { mutableStateOf(false) }

    AppModalBottomSheet(
        show = true,
        onDismissRequest = {
            ReadBookConfig.save()
            onDismissRequest()
        },
        title = stringResource(R.string.read_config),
    ) {
        ReadStyleContent(
            onOpenPaddingConfig = onOpenPaddingConfig,
            onOpenMoreConfig = onOpenMoreConfig,
            onOpenBgTextConfig = onOpenBgTextConfig,
            onOpenTextTitle = { showTextTitle = true },
            onOpenFontSelect = onOpenFontSelect,
            onToggleDayNight = onToggleDayNight,
        )

        TextTitlePage(
            show = showTextTitle,
            onDismissRequest = { showTextTitle = false },
            onOpenShadowSet = onOpenShadowSet,
            onOpenUnderlineConfig = onOpenUnderlineConfig,
            onOpenRegexColor = onOpenRegexColor,
            onOpenFontSelect = onOpenFontSelect,
        )
    }
}

@Composable
fun ReadStyleContent(
    onOpenPaddingConfig: () -> Unit,
    onOpenMoreConfig: () -> Unit,
    onOpenBgTextConfig: (Int) -> Unit,
    onOpenTextTitle: () -> Unit,
    onOpenFontSelect: () -> Unit,
    onToggleDayNight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 3 })
    var currentPage by remember { mutableIntStateOf(0) }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            currentPage = page
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f, fill = false),
        ) { page ->
            when (page) {
                0 -> GlobalThemePage(
                    onToggleDayNight = onToggleDayNight,
                    onOpenBgTextConfig = onOpenBgTextConfig,
                    onOpenTextTitle = onOpenTextTitle,
                    onOpenPaddingConfig = onOpenPaddingConfig,
                )

                1 -> SystemMenuPage()
                2 -> HeaderFooterPage(
                    onOpenFontSelect = onOpenFontSelect,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            data class NavPage(
                val labelRes: Int,
                val imageVector: ImageVector,
            )

            val pages = listOf(
                NavPage(R.string.read_config_global_theme, Icons.Default.Settings),
                NavPage(R.string.read_config_menu_system, Icons.Default.Palette),
                NavPage(R.string.header_footer, Icons.Default.Info),
                NavPage(R.string.more_setting, Icons.Default.Tune),
            )
            pages.forEachIndexed { index, (labelRes, imageVector) ->
                val selected = currentPage == index
                NormalCard(
                    onClick = {
                        if (index < 3) {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        } else {
                            onOpenMoreConfig()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f),
                    containerColor = if (selected) {
                        LegadoTheme.colorScheme.secondaryContainer
                    } else {
                        LegadoTheme.colorScheme.onSheetContent
                    },
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Icon(
                            imageVector = imageVector,
                            contentDescription = stringResource(labelRes),
                            tint = if (selected) {
                                LegadoTheme.colorScheme.onPrimaryContainer
                            } else {
                                LegadoTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(labelRes),
                            style = LegadoTheme.typography.labelSmallEmphasized,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) {
                                LegadoTheme.colorScheme.onPrimaryContainer
                            } else {
                                LegadoTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

// ========== Page 0: Global & Theme ==========

@Composable
private fun GlobalThemePage(
    onToggleDayNight: () -> Unit,
    onOpenBgTextConfig: (Int) -> Unit,
    onOpenTextTitle: () -> Unit,
    onOpenPaddingConfig: () -> Unit,
) {
    var textSize by remember { mutableIntStateOf(ReadBookConfig.textSize) }
    var pageAnim by remember { mutableIntStateOf(ReadBook.pageAnim()) }
    var styleSelect by remember { mutableIntStateOf(ReadBookConfig.styleSelect) }
    var shareLayout by remember { mutableStateOf(ReadBookConfig.shareLayout) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TinySliderSettingItem(
                title = stringResource(R.string.text_size),
                value = textSize.toFloat(),
                valueRange = 5f..50f,
                steps = 44,
                modifier = Modifier.weight(1f),
                onValueChange = { value ->
                    textSize = value.toInt()
                    ReadBookConfig.textSize = value.toInt()
                    postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
                },
            )
            NormalCard(
                onClick = onOpenTextTitle,
                modifier = Modifier
                    .height(56.dp)
                    .aspectRatio(1f),
                containerColor = LegadoTheme.colorScheme.onSheetContent,
                cornerRadius = 12.dp
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        imageVector = Icons.Default.TextFields,
                        contentDescription = stringResource(R.string.read_config_text_effects),
                        tint = LegadoTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // Style section label + Day/Night
        NormalCard(
            containerColor = LegadoTheme.colorScheme.onSheetContent
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    AppText(
                        text = stringResource(R.string.text_bg_style),
                        style = LegadoTheme.typography.titleSmallEmphasized
                    )
                    AppText(
                        text = stringResource(R.string.long_click_to_custom),
                        style = LegadoTheme.typography.labelSmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SmallTonalButton(
                    onClick = onToggleDayNight,
                    icon = Icons.Default.Brightness6
                )
            }

            Spacer(Modifier.height(8.dp))

            // Style cards: [shareLayout] [cards...]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            ) {
                NormalCard(
                    onClick = {
                        shareLayout = !shareLayout
                        ReadBookConfig.shareLayout = shareLayout
                        postEvent(EventBus.UP_CONFIG, arrayListOf(1, 2, 5))
                    },
                    modifier = Modifier
                        .width(40.dp)
                        .height(56.dp),
                    containerColor = if (shareLayout) LegadoTheme.colorScheme.primaryContainer else null,
                    contentColor = if (shareLayout) LegadoTheme.colorScheme.onPrimaryContainer else null
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.GridView,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                val styleListState = rememberLazyListState()
                LazyRow(
                    state = styleListState,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                        .fadingEdge(styleListState),
                ) {
                    itemsIndexed(ReadBookConfig.configList) { index, config ->
                        StyleCard(
                            config = config,
                            isSelected = styleSelect == index,
                            onClick = {
                                styleSelect = index
                                ReadBookConfig.styleSelect = index
                                postEvent(EventBus.UP_CONFIG, arrayListOf(1, 2, 5))
                            },
                            onLongClick = {
                                styleSelect = index
                                ReadBookConfig.styleSelect = index
                                onOpenBgTextConfig(index)
                            },
                        )
                    }
                    item {
                        NormalCard(
                            onClick = {
                                ReadBookConfig.configList.add(ReadBookConfig.Config())
                                val newIndex = ReadBookConfig.configList.lastIndex
                                styleSelect = newIndex
                                ReadBookConfig.styleSelect = newIndex
                            },
                            modifier = Modifier
                                .width(40.dp)
                                .height(56.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        val pageAnimOptions = listOf(
            R.string.page_anim_cover,
            R.string.page_anim_slide,
            R.string.page_anim_simulation,
            R.string.page_anim_scroll,
            R.string.page_anim_fade,
            R.string.page_anim_none,
        )
        var showPageAnimMenu by remember { mutableStateOf(false) }
        val pageAnimEntries = pageAnimOptions.map { stringResource(it) }.toTypedArray()
        val pageAnimEntryValues = pageAnimOptions.indices.map { it.toString() }.toTypedArray()
        val currentPageAnimDisplay =
            pageAnimEntries.getOrNull(pageAnimEntryValues.indexOf(pageAnim.toString())) ?: ""

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                TinySettingItem(
                    title = stringResource(R.string.page_anim),
                    modifier = Modifier.fillMaxWidth(),
                    trailingContent = {
                        TextCard(
                            cornerRadius = 8.dp,
                            horizontalPadding = 8.dp,
                            verticalPadding = 4.dp,
                            text = currentPageAnimDisplay,
                            backgroundColor = LegadoTheme.colorScheme.surfaceContainer,
                            contentColor = LegadoTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = { showPageAnimMenu = true },
                )
                RoundDropdownMenu(
                    expanded = showPageAnimMenu,
                    onDismissRequest = { showPageAnimMenu = false },
                ) { dismiss ->
                    pageAnimEntries.forEachIndexed { index, display ->
                        RoundDropdownMenuItem(
                            text = display,
                            onClick = {
                                pageAnim = index
                                ReadBook.book?.setPageAnim(-1)
                                ReadBookConfig.pageAnim = pageAnim
                                ReadBook.loadContent(false)
                                dismiss()
                            },
                        )
                    }
                }
            }
            NormalCard(
                onClick = onOpenPaddingConfig,
                modifier = Modifier
                    .height(56.dp)
                    .aspectRatio(1f),
                containerColor = LegadoTheme.colorScheme.onSheetContent,
                cornerRadius = 12.dp
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        imageVector = Icons.Default.SpaceBar,
                        contentDescription = stringResource(R.string.padding),
                        tint = LegadoTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ========== Page 2: System & Menu ==========

@Composable
private fun SystemMenuPage() {
    var bottomMode by remember { mutableIntStateOf(AppConfig.readBarStyle) }
    var showColorPicker by remember { mutableStateOf(false) }
    var colorPickerId by remember { mutableIntStateOf(0) }
    var colorPickerInitial by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        SectionTitle(stringResource(R.string.read_config_menu_system))

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
                AppConfig.readBarStyle = bottomMode
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            },
        )

        Spacer(Modifier.height(8.dp))

        TinyColorSettingItem(
            title = stringResource(R.string.background_color),
            colorValue = ReadBookConfig.durConfig.curMenuBg(),
            onClick = {
                colorPickerId = COLOR_BG
                colorPickerInitial = ReadBookConfig.durConfig.curMenuBg()
                showColorPicker = true
            },
        )
        TinyColorSettingItem(
            title = stringResource(R.string.accent),
            colorValue = ReadBookConfig.durConfig.curMenuAc(),
            onClick = {
                colorPickerId = COLOR_MENU_ACCENT
                colorPickerInitial = ReadBookConfig.durConfig.curMenuAc()
                showColorPicker = true
            },
        )

    }

    if (showColorPicker) {
        ColorPickerSheet(
            show = true,
            initialColor = colorPickerInitial,
            onDismissRequest = { showColorPicker = false },
            onColorSelected = { color ->
                when (colorPickerId) {
                    COLOR_BG -> {
                        ReadBookConfig.durConfig.setMenuCurBg(color)
                        postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
                    }

                    COLOR_MENU_ACCENT -> {
                        ReadBookConfig.durConfig.setMenuCurAc(color)
                        postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
                    }
                }
                showColorPicker = false
            },
        )
    }
}

// ========== Tab: Layout & Spacing (in TextTitlePage) ==========

@Composable
private fun LayoutSpacingPage() {
    var letterSpacing by remember { mutableFloatStateOf(ReadBookConfig.letterSpacing) }
    var lineSpacing by remember { mutableFloatStateOf(ReadBookConfig.lineSpacingExtra.toFloat()) }
    var paragraphSpacing by remember { mutableFloatStateOf(ReadBookConfig.paragraphSpacing.toFloat()) }
    var indentCount by remember { mutableIntStateOf(ReadBookConfig.paragraphIndent.length) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        SectionTitle(stringResource(R.string.read_config_body_spacing))

        TinySliderSettingItem(
            title = stringResource(R.string.text_indent),
            value = indentCount.toFloat(),
            valueRange = 0f..4f,
            steps = 3,
            onValueChange = { value ->
                indentCount = value.toInt()
                ReadBookConfig.paragraphIndent = "　".repeat(indentCount)
                postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
            },
        )
        TinySliderSettingItem(
            title = stringResource(R.string.text_letter_spacing),
            value = (letterSpacing * 100) + 50,
            valueRange = 0f..100f,
            onValueChange = { value ->
                letterSpacing = (value - 50) / 100f
                ReadBookConfig.letterSpacing = letterSpacing
                postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
            },
        )
        TinySliderSettingItem(
            title = stringResource(R.string.line_size),
            value = lineSpacing,
            valueRange = 0f..20f,
            onValueChange = { value ->
                lineSpacing = value
                ReadBookConfig.lineSpacingExtra = value.toInt()
                postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
            },
        )
        TinySliderSettingItem(
            title = stringResource(R.string.paragraph_size),
            value = paragraphSpacing,
            valueRange = 0f..20f,
            onValueChange = { value ->
                paragraphSpacing = value
                ReadBookConfig.paragraphSpacing = value.toInt()
                postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
            },
        )
    }
}

// ========== Text & Title Sheet ==========

@Composable
private fun TextTitlePage(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onOpenShadowSet: () -> Unit,
    onOpenUnderlineConfig: () -> Unit,
    onOpenRegexColor: () -> Unit,
    onOpenFontSelect: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val tabTitles = listOf(
        stringResource(R.string.read_config_text_effects),
        stringResource(R.string.read_config_layout_spacing),
        stringResource(R.string.read_config_title_settings),
    )
    val pagerState = rememberPagerState(pageCount = { 3 })
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { selectedTab = it }
    }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.read_config_text_effects),
    ) {
        ReadStyleTextTitleContent(
            tabTitles = tabTitles,
            selectedTab = selectedTab,
            onSelectedTabChange = { selectedTab = it },
            pagerState = pagerState,
            onOpenShadowSet = onOpenShadowSet,
            onOpenUnderlineConfig = onOpenUnderlineConfig,
            onOpenRegexColor = onOpenRegexColor,
            onOpenFontSelect = onOpenFontSelect,
            animateToPage = { page -> scope.launch { pagerState.animateScrollToPage(page) } },
        )
    }
}

@Composable
fun ReadStyleTextTitleContent(
    onOpenShadowSet: () -> Unit,
    onOpenUnderlineConfig: () -> Unit,
    onOpenRegexColor: () -> Unit,
    onOpenFontSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val tabTitles = listOf(
        stringResource(R.string.read_config_text_effects),
        stringResource(R.string.read_config_layout_spacing),
        stringResource(R.string.read_config_title_settings),
    )
    val pagerState = rememberPagerState(pageCount = { 3 })
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { selectedTab = it }
    }

    ReadStyleTextTitleContent(
        tabTitles = tabTitles,
        selectedTab = selectedTab,
        onSelectedTabChange = { selectedTab = it },
        pagerState = pagerState,
        onOpenShadowSet = onOpenShadowSet,
        onOpenUnderlineConfig = onOpenUnderlineConfig,
        onOpenRegexColor = onOpenRegexColor,
        onOpenFontSelect = onOpenFontSelect,
        animateToPage = { page -> scope.launch { pagerState.animateScrollToPage(page) } },
        modifier = modifier,
    )
}

@Composable
private fun ReadStyleTextTitleContent(
    tabTitles: List<String>,
    selectedTab: Int,
    onSelectedTabChange: (Int) -> Unit,
    pagerState: androidx.compose.foundation.pager.PagerState,
    onOpenShadowSet: () -> Unit,
    onOpenUnderlineConfig: () -> Unit,
    onOpenRegexColor: () -> Unit,
    onOpenFontSelect: () -> Unit,
    animateToPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
    ) {
        CardTabRow(
            tabTitles = tabTitles,
            selectedTabIndex = selectedTab,
            onTabSelected = { index ->
                onSelectedTabChange(index)
                animateToPage(index)
            },
            modifier = Modifier.padding(bottom = 8.dp),
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            when (page) {
                0 -> TextEffectsPage(
                    onOpenShadowSet = onOpenShadowSet,
                    onOpenUnderlineConfig = onOpenUnderlineConfig,
                    onOpenRegexColor = onOpenRegexColor,
                    onOpenFontSelect = onOpenFontSelect,
                )

                1 -> LayoutSpacingPage()
                2 -> TitleSettingsPage()
            }
        }
    }
}

// ========== Text & Effects (sub-page) ==========

@Composable
private fun TextEffectsPage(
    onOpenShadowSet: () -> Unit,
    onOpenUnderlineConfig: () -> Unit,
    onOpenRegexColor: () -> Unit,
    onOpenFontSelect: () -> Unit,
) {
    var textItalic by remember { mutableStateOf(ReadBookConfig.textItalic) }
    var textBold by remember { mutableIntStateOf(ReadBookConfig.textBold) }

    var showColorPicker by remember { mutableStateOf(false) }
    var colorPickerId by remember { mutableIntStateOf(0) }
    var colorPickerInitial by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        SectionTitle(stringResource(R.string.text_typeface))
        TinySwitchSettingItem(
            title = stringResource(R.string.read_config_italic),
            checked = textItalic,
            imageVector = Icons.Default.FormatItalic,
            onCheckedChange = {
                textItalic = it
                ReadBookConfig.textItalic = it
                postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
            },
        )
        TinySliderSettingItem(
            title = stringResource(R.string.font_weight_text),
            value = textBold.coerceAtLeast(100).toFloat(),
            valueRange = 100f..900f,
            imageVector = Icons.Default.FormatBold,
            onValueChange = { value ->
                textBold = value.toInt()
                ReadBookConfig.textBold = value.toInt()
                postEvent(EventBus.UP_CONFIG, arrayListOf(8, 9, 6))
            },
        )

        TinyClickableSettingItem(
            title = stringResource(R.string.select_font),
            imageVector = Icons.Default.TextFields,
            onClick = onOpenFontSelect,
        )
        Spacer(Modifier.height(8.dp))

        // Colors
        Text(
            text = stringResource(R.string.read_color),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(4.dp))
        TinyColorSettingItem(
            title = stringResource(R.string.text_color),
            colorValue = ReadBookConfig.durConfig.curTextColor(),
            onClick = {
                colorPickerId = COLOR_TEXT
                colorPickerInitial = ReadBookConfig.durConfig.curTextColor()
                showColorPicker = true
            },
        )
        TinyColorSettingItem(
            title = stringResource(R.string.text_accent_color),
            colorValue = ReadBookConfig.durConfig.curTextAccentColor(),
            onClick = {
                colorPickerId = COLOR_ACCENT
                colorPickerInitial = ReadBookConfig.durConfig.curTextAccentColor()
                showColorPicker = true
            },
        )

        Spacer(Modifier.height(8.dp))

        SectionTitle(stringResource(R.string.read_config_effects))
        TinyClickableSettingItem(
            title = stringResource(R.string.text_shadow_set),
            description = stringResource(R.string.read_config_shadow_desc),
            imageVector = Icons.Default.Layers,
            onClick = onOpenShadowSet,
        )
        TinyClickableSettingItem(
            title = stringResource(R.string.text_underline),
            description = stringResource(R.string.read_config_underline_desc),
            imageVector = Icons.Default.FormatUnderlined,
            onClick = onOpenUnderlineConfig,
        )
        TinyClickableSettingItem(
            title = stringResource(R.string.regex_color_config),
            description = stringResource(R.string.read_config_regex_desc),
            imageVector = Icons.Default.Tune,
            onClick = onOpenRegexColor,
        )

        Spacer(Modifier.height(8.dp))
    }

    // Color picker
    if (showColorPicker) {
        ColorPickerSheet(
            show = true,
            initialColor = colorPickerInitial,
            onDismissRequest = { showColorPicker = false },
            onColorSelected = { color ->
                when (colorPickerId) {
                    COLOR_TEXT -> {
                        ReadBookConfig.durConfig.setCurTextColor(color)
                        postEvent(EventBus.UP_CONFIG, arrayListOf(2, 5, 9))
                    }

                    COLOR_ACCENT -> {
                        ReadBookConfig.durConfig.setCurTextAccentColor(color)
                        postEvent(EventBus.UP_CONFIG, arrayListOf(2, 5, 9))
                    }
                }
                showColorPicker = false
            },
        )
    }
}

// ========== Title Settings (sub-page) ==========

@Composable
private fun TitleSettingsPage() {
    var titleMode by remember { mutableIntStateOf(ReadBookConfig.titleMode) }
    var titleBold by remember { mutableIntStateOf(ReadBookConfig.titleBold) }

    var showColorPicker by remember { mutableStateOf(false) }
    var colorPickerId by remember { mutableIntStateOf(0) }
    var colorPickerInitial by remember { mutableIntStateOf(0) }

    val weightIconMap = mapOf(
        0 to Icons.Default.TextFields,
        1 to Icons.Default.TextFormat,
        2 to Icons.Default.FormatBold,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        TinyDropdownSettingItem(
            title = stringResource(R.string.body_title),
            selectedValue = titleMode.toString(),
            displayEntries = arrayOf(
                stringResource(R.string.title_left),
                stringResource(R.string.title_center),
                stringResource(R.string.title_hide),
            ),
            entryValues = arrayOf("0", "1", "2"),
            onValueChange = {
                titleMode = it.toInt()
                ReadBookConfig.titleMode = titleMode
                postEvent(EventBus.UP_CONFIG, arrayListOf(5))
            },
        )

        Spacer(Modifier.height(8.dp))

        TinySliderSettingItem(
            title = stringResource(R.string.font_weight_text),
            value = titleBold.coerceAtLeast(100).toFloat(),
            valueRange = 100f..900f,
            imageVector = weightIconMap[titleBold] ?: Icons.Default.FormatBold,
            onValueChange = { value ->
                titleBold = value.toInt()
                ReadBookConfig.titleBold = value.toInt()
                postEvent(EventBus.UP_CONFIG, arrayListOf(8, 9, 6))
            },
        )

        TinyColorSettingItem(
            title = stringResource(R.string.title_color),
            colorValue = if (ReadBookConfig.titleColor != 0) {
                ReadBookConfig.titleColor or 0xFF000000.toInt()
            } else {
                ReadBookConfig.textColor or 0xFF000000.toInt()
            },
            onClick = {
                colorPickerId = COLOR_TITLE
                colorPickerInitial = if (ReadBookConfig.titleColor != 0) {
                    ReadBookConfig.titleColor or 0xFF000000.toInt()
                } else {
                    ReadBookConfig.textColor or 0xFF000000.toInt()
                }
                showColorPicker = true
            },
        )

        Spacer(Modifier.height(8.dp))

        // Title spacing sliders
        TinySliderSettingItem(
            title = stringResource(R.string.subtitle_scale),
            value = ReadBookConfig.titleSegScaling * 10,
            valueRange = 0f..100f,
            onValueChange = { value ->
                ReadBookConfig.titleSegScaling = value / 10f
                postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
            },
        )
        TinySliderSettingItem(
            title = stringResource(R.string.heading_spacing),
            value = ReadBookConfig.titleLineSpacingExtra.toFloat(),
            valueRange = 0f..100f,
            onValueChange = { value ->
                ReadBookConfig.titleLineSpacingExtra = value.toInt()
                postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
            },
        )
        TinySliderSettingItem(
            title = stringResource(R.string.subtitle_margin),
            value = ReadBookConfig.titleLineSpacingSub.toFloat(),
            valueRange = 0f..100f,
            onValueChange = { value ->
                ReadBookConfig.titleLineSpacingSub = value.toInt()
                postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
            },
        )
        TinySliderSettingItem(
            title = stringResource(R.string.title_font_size),
            value = ReadBookConfig.titleSize.toFloat(),
            valueRange = 0f..100f,
            onValueChange = { value ->
                ReadBookConfig.titleSize = value.toInt()
                postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
            },
        )
        TinySliderSettingItem(
            title = stringResource(R.string.title_margin_top),
            value = ReadBookConfig.titleTopSpacing.toFloat(),
            valueRange = 0f..100f,
            onValueChange = { value ->
                ReadBookConfig.titleTopSpacing = value.toInt()
                postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
            },
        )
        TinySliderSettingItem(
            title = stringResource(R.string.title_margin_bottom),
            value = ReadBookConfig.titleBottomSpacing.toFloat(),
            valueRange = 0f..100f,
            onValueChange = { value ->
                ReadBookConfig.titleBottomSpacing = value.toInt()
                postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
            },
        )

    }

    // Color picker
    if (showColorPicker) {
        ColorPickerSheet(
            show = true,
            initialColor = colorPickerInitial,
            onDismissRequest = { showColorPicker = false },
            onColorSelected = { color ->
                when (colorPickerId) {
                    COLOR_TITLE -> {
                        ReadBookConfig.titleColor = color
                        postEvent(EventBus.UP_CONFIG, arrayListOf(2, 5, 9))
                    }
                }
                showColorPicker = false
            },
        )
    }
}

// ========== Page 3: Header & Footer ==========

@Composable
private fun HeaderFooterPage(
    onOpenFontSelect: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val tabTitles = listOf(
        stringResource(R.string.header),
        stringResource(R.string.footer),
        stringResource(R.string.general),
    )
    val pagerState = rememberPagerState(pageCount = { 3 })
    var selectedTab by remember { mutableIntStateOf(0) }

    // Header state
    var headerMode by remember { mutableIntStateOf(ReadTipConfig.headerMode) }
    var headerLeft by remember { mutableIntStateOf(ReadTipConfig.tipHeaderLeft) }
    var headerMiddle by remember { mutableIntStateOf(ReadTipConfig.tipHeaderMiddle) }
    var headerRight by remember { mutableIntStateOf(ReadTipConfig.tipHeaderRight) }

    // Footer state
    var footerMode by remember { mutableIntStateOf(ReadTipConfig.footerMode) }
    var footerLeft by remember { mutableIntStateOf(ReadTipConfig.tipFooterLeft) }
    var footerMiddle by remember { mutableIntStateOf(ReadTipConfig.tipFooterMiddle) }
    var footerRight by remember { mutableIntStateOf(ReadTipConfig.tipFooterRight) }

    // Global state
    var headerFontSize by remember { mutableIntStateOf(ReadBookConfig.headerFontSize) }

    var showColorPicker by remember { mutableStateOf(false) }
    var colorPickerId by remember { mutableIntStateOf(0) }
    var colorPickerInitial by remember { mutableIntStateOf(0) }

    val tipNames = remember { ReadTipConfig.tipNames }
    val tipValues = remember { ReadTipConfig.tipValues }

    fun clearRepeat(repeat: Int) {
        if (repeat == ReadTipConfig.none) return
        if (headerLeft == repeat) {
            headerLeft = ReadTipConfig.none; ReadTipConfig.tipHeaderLeft = ReadTipConfig.none
        }
        if (headerMiddle == repeat) {
            headerMiddle = ReadTipConfig.none; ReadTipConfig.tipHeaderMiddle = ReadTipConfig.none
        }
        if (headerRight == repeat) {
            headerRight = ReadTipConfig.none; ReadTipConfig.tipHeaderRight = ReadTipConfig.none
        }
        if (footerLeft == repeat) {
            footerLeft = ReadTipConfig.none; ReadTipConfig.tipFooterLeft = ReadTipConfig.none
        }
        if (footerMiddle == repeat) {
            footerMiddle = ReadTipConfig.none; ReadTipConfig.tipFooterMiddle = ReadTipConfig.none
        }
        if (footerRight == repeat) {
            footerRight = ReadTipConfig.none; ReadTipConfig.tipFooterRight = ReadTipConfig.none
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { selectedTab = it }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        CardTabRow(
            tabTitles = tabTitles,
            selectedTabIndex = selectedTab,
            onTabSelected = { index ->
                selectedTab = index
                scope.launch { pagerState.animateScrollToPage(index) }
            },
            modifier = Modifier.padding(vertical = 8.dp),
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            when (page) {
                0 -> {
                    // Header tab
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        val headerModes = ReadTipConfig.getHeaderModes(context)
                        TinyDropdownSettingItem(
                            title = stringResource(R.string.header),
                            selectedValue = headerMode.toString(),
                            displayEntries = headerModes.values.toTypedArray(),
                            entryValues = headerModes.keys.map { it.toString() }.toTypedArray(),
                            onValueChange = {
                                headerMode = it.toInt()
                                ReadTipConfig.headerMode = headerMode
                                postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                            },
                        )
                        TipPositionDropdown(
                            label = stringResource(R.string.left),
                            value = headerLeft,
                            tipNames = tipNames,
                            tipValues = tipValues,
                            onValueChange = {
                                clearRepeat(it)
                                headerLeft = it
                                ReadTipConfig.tipHeaderLeft = it
                                postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6))
                            },
                        )
                        TipPositionDropdown(
                            label = stringResource(R.string.middle),
                            value = headerMiddle,
                            tipNames = tipNames,
                            tipValues = tipValues,
                            onValueChange = {
                                clearRepeat(it)
                                headerMiddle = it
                                ReadTipConfig.tipHeaderMiddle = it
                                postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6))
                            },
                        )
                        TipPositionDropdown(
                            label = stringResource(R.string.right),
                            value = headerRight,
                            tipNames = tipNames,
                            tipValues = tipValues,
                            onValueChange = {
                                clearRepeat(it)
                                headerRight = it
                                ReadTipConfig.tipHeaderRight = it
                                postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6))
                            },
                        )
                        TinyColorSettingItem(
                            title = stringResource(R.string.header_color),
                            colorValue = if (ReadTipConfig.tipHeaderColor != 0) {
                                ReadTipConfig.tipHeaderColor
                            } else {
                                ReadBookConfig.textColor
                            },
                            onClick = {
                                colorPickerId = COLOR_HEADER
                                colorPickerInitial = if (ReadTipConfig.tipHeaderColor != 0) {
                                    ReadTipConfig.tipHeaderColor
                                } else {
                                    ReadBookConfig.textColor
                                }
                                showColorPicker = true
                            },
                        )
                    }
                }

                1 -> {
                    // Footer tab
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        val footerModes = ReadTipConfig.getFooterModes(context)
                        TinyDropdownSettingItem(
                            title = stringResource(R.string.footer),
                            selectedValue = footerMode.toString(),
                            displayEntries = footerModes.values.toTypedArray(),
                            entryValues = footerModes.keys.map { it.toString() }.toTypedArray(),
                            onValueChange = {
                                footerMode = it.toInt()
                                ReadTipConfig.footerMode = footerMode
                                postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                            },
                        )
                        TipPositionDropdown(
                            label = stringResource(R.string.left),
                            value = footerLeft,
                            tipNames = tipNames,
                            tipValues = tipValues,
                            onValueChange = {
                                clearRepeat(it)
                                footerLeft = it
                                ReadTipConfig.tipFooterLeft = it
                                postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6))
                            },
                        )
                        TipPositionDropdown(
                            label = stringResource(R.string.middle),
                            value = footerMiddle,
                            tipNames = tipNames,
                            tipValues = tipValues,
                            onValueChange = {
                                clearRepeat(it)
                                footerMiddle = it
                                ReadTipConfig.tipFooterMiddle = it
                                postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6))
                            },
                        )
                        TipPositionDropdown(
                            label = stringResource(R.string.right),
                            value = footerRight,
                            tipNames = tipNames,
                            tipValues = tipValues,
                            onValueChange = {
                                clearRepeat(it)
                                footerRight = it
                                ReadTipConfig.tipFooterRight = it
                                postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6))
                            },
                        )
                        TinyColorSettingItem(
                            title = stringResource(R.string.footer_color),
                            colorValue = if (ReadTipConfig.tipFooterColor != 0) {
                                ReadTipConfig.tipFooterColor
                            } else {
                                ReadBookConfig.textColor
                            },
                            onClick = {
                                colorPickerId = COLOR_FOOTER
                                colorPickerInitial = if (ReadTipConfig.tipFooterColor != 0) {
                                    ReadTipConfig.tipFooterColor
                                } else {
                                    ReadBookConfig.textColor
                                }
                                showColorPicker = true
                            },
                        )
                    }
                }

                2 -> {
                    // Global tab
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        SectionTitle(stringResource(R.string.read_config_divider_line))
                        TinyColorSettingItem(
                            title = stringResource(R.string.tip_divider_color),
                            colorValue = when (ReadTipConfig.tipDividerColor) {
                                -1 -> context.getCompatColor(R.color.divider)
                                0 -> ReadBookConfig.textColor
                                else -> ReadTipConfig.tipDividerColor
                            },
                            onClick = {
                                colorPickerId = COLOR_DIVIDER
                                colorPickerInitial = when (ReadTipConfig.tipDividerColor) {
                                    -1 -> context.getCompatColor(R.color.divider)
                                    0 -> ReadBookConfig.textColor
                                    else -> ReadTipConfig.tipDividerColor
                                }
                                showColorPicker = true
                            },
                        )

                        Spacer(Modifier.height(8.dp))

                        SectionTitle(stringResource(R.string.text_typeface))
                        TinyClickableSettingItem(
                            title = stringResource(R.string.header_font),
                            description = stringResource(R.string.select_font),
                            imageVector = Icons.Default.TextFields,
                            onClick = onOpenFontSelect,
                        )
                        TinySliderSettingItem(
                            title = stringResource(R.string.header_font_size),
                            value = headerFontSize.toFloat(),
                            valueRange = 0f..100f,
                            onValueChange = { value ->
                                headerFontSize = value.toInt()
                                ReadBookConfig.headerFontSize = value.toInt()
                                ReadBookConfig.save()
                                postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                            },
                        )
                    }
                }
            }
        }
    }

    // Color picker
    if (showColorPicker) {
        ColorPickerSheet(
            show = true,
            initialColor = colorPickerInitial,
            onDismissRequest = { showColorPicker = false },
            onColorSelected = { color ->
                when (colorPickerId) {
                    COLOR_HEADER -> {
                        ReadTipConfig.tipHeaderColor = color
                        postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                    }

                    COLOR_FOOTER -> {
                        ReadTipConfig.tipFooterColor = color
                        postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                    }

                    COLOR_DIVIDER -> {
                        ReadTipConfig.tipDividerColor = color
                        postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                    }
                }
                showColorPicker = false
            },
        )
    }
}

// ========== Shared Components ==========

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun PaddingSliders(
    top: Float, bottom: Float, left: Float, right: Float,
    onTopChange: (Float) -> Unit, onBottomChange: (Float) -> Unit,
    onLeftChange: (Float) -> Unit, onRightChange: (Float) -> Unit,
) {
    TinySliderSettingItem(
        title = stringResource(R.string.padding_top),
        value = top,
        valueRange = 0f..300f,
        onValueChange = onTopChange,
    )
    TinySliderSettingItem(
        title = stringResource(R.string.padding_bottom),
        value = bottom,
        valueRange = 0f..300f,
        onValueChange = onBottomChange,
    )
    TinySliderSettingItem(
        title = stringResource(R.string.padding_left),
        value = left,
        valueRange = 0f..300f,
        onValueChange = onLeftChange,
    )
    TinySliderSettingItem(
        title = stringResource(R.string.padding_right),
        value = right,
        valueRange = 0f..300f,
        onValueChange = onRightChange,
    )
}

@Composable
private fun StyleCard(
    config: ReadBookConfig.Config,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val bgColor = if (config.curBgType() == 0) {
        try {
            Color(config.curBgStr().toColorInt())
        } catch (_: Exception) {
            MaterialTheme.colorScheme.surface
        }
    } else {
        MaterialTheme.colorScheme.surface
    }
    val textColor = Color(config.curTextColor())
    val name = config.name.ifBlank { stringResource(R.string.text_bg_style) }

    Surface(
        modifier = Modifier
            .width(40.dp)
            .height(56.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = if (isSelected) {
            BorderStroke(2.dp, LegadoTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, LegadoTheme.colorScheme.outlineVariant)
        },
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// Color picker IDs
private const val COLOR_TEXT = 1
private const val COLOR_ACCENT = 2
private const val COLOR_TITLE = 4
private const val COLOR_BG = 5
private const val COLOR_MENU_ACCENT = 6
private const val COLOR_HEADER = 7
private const val COLOR_FOOTER = 8
private const val COLOR_DIVIDER = 9

@Composable
private fun TipPositionDropdown(
    label: String,
    value: Int,
    tipNames: List<String>,
    tipValues: Array<Int>,
    onValueChange: (Int) -> Unit,
) {
    TinyDropdownSettingItem(
        title = label,
        selectedValue = value.toString(),
        displayEntries = tipNames.toTypedArray(),
        entryValues = tipValues.map { it.toString() }.toTypedArray(),
        onValueChange = { onValueChange(it.toInt()) },
    )
}
