package io.legado.app.ui.book.read.sheet

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.book.read.ConfigUpdate
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.config.readConfig.ReadConfig
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.SectionTitle
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.dialog.ColorPickerSheet
import io.legado.app.ui.widget.components.pager.rememberPagerFlingPassThroughConnection
import io.legado.app.ui.widget.components.settingItem.TinyClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.TinyColorSettingItem
import io.legado.app.ui.widget.components.settingItem.TinyDropdownSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySliderSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySwitchSettingItem
import io.legado.app.ui.widget.components.tabRow.CardTabRow
import kotlinx.coroutines.launch

// Color picker IDs
private const val COLOR_TEXT = 1
private const val COLOR_ACCENT = 2
private const val COLOR_TITLE = 4

@Composable
fun ReadStyleTextTitleContent(
    onOpenShadowSet: () -> Unit,
    onOpenUnderlineConfig: () -> Unit,
    onOpenHighlightRule: () -> Unit,
    onOpenFontSelect: () -> Unit,
    onOpenTitleFontSelect: () -> Unit,
    modifier: Modifier = Modifier,
    onIntent: (ReadBookIntent) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val tabTitles = listOf(
        stringResource(R.string.read_config_text_effects),
        stringResource(R.string.read_config_layout_spacing),
        stringResource(R.string.read_config_title_settings),
    )
    val pagerState = rememberPagerState(pageCount = { 3 })
    var selectedTab by remember { mutableIntStateOf(0) }
    var clickScrollCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (clickScrollCount == 0) {
                selectedTab = page
            }
        }
    }

    ReadStyleTextTitleContent(
        tabTitles = tabTitles,
        selectedTab = selectedTab,
        pagerState = pagerState,
        onOpenShadowSet = onOpenShadowSet,
        onOpenUnderlineConfig = onOpenUnderlineConfig,
        onOpenHighlightRule = onOpenHighlightRule,
        onOpenFontSelect = onOpenFontSelect,
        onOpenTitleFontSelect = onOpenTitleFontSelect,
        animateToPage = { page ->
            selectedTab = page
            clickScrollCount++
            scope.launch {
                try {
                    pagerState.animateScrollToPage(
                        page = page,
                        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                    )
                } finally {
                    clickScrollCount = (clickScrollCount - 1).coerceAtLeast(0)
                }
            }
        },
        modifier = modifier,
        onIntent = onIntent,
    )
}

@Composable
internal fun ReadStyleTextTitleContent(
    tabTitles: List<String>,
    selectedTab: Int,
    pagerState: PagerState,
    onOpenShadowSet: () -> Unit,
    onOpenUnderlineConfig: () -> Unit,
    onOpenHighlightRule: () -> Unit,
    onOpenFontSelect: () -> Unit,
    onOpenTitleFontSelect: () -> Unit,
    animateToPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onIntent: (ReadBookIntent) -> Unit,
) {
    val pageHeights = remember { mutableStateMapOf<Int, Int>() }
    val animatedHeight by rememberPagerAnimatedHeight(pagerState, pageHeights)
    val childPagerNestedScrollConnection = rememberPagerFlingPassThroughConnection(
        state = pagerState,
        orientation = Orientation.Horizontal,
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
    ) {
        CardTabRow(
            tabTitles = tabTitles,
            selectedTabIndex = selectedTab,
            onTabSelected = { index ->
                animateToPage(index)
            },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )
        HorizontalPager(
            state = pagerState,
            verticalAlignment = Alignment.Top,
            pageNestedScrollConnection = childPagerNestedScrollConnection,
            modifier = Modifier
                .fillMaxWidth()
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
                    0 -> TextEffectsPage(
                        onOpenShadowSet = onOpenShadowSet,
                        onOpenUnderlineConfig = onOpenUnderlineConfig,
                        onOpenHighlightRule = onOpenHighlightRule,
                        onOpenFontSelect = onOpenFontSelect,
                        onIntent = onIntent,
                    )

                    1 -> LayoutSpacingPage(onIntent = onIntent)
                    2 -> TitleSettingsPage(
                        onOpenTitleFontSelect = onOpenTitleFontSelect,
                        onIntent = onIntent,
                    )
                }
            }
        }
    }
}

// ========== Tab: Layout & Spacing ==========

@Composable
internal fun LayoutSpacingPage(
    modifier: Modifier = Modifier,
    onIntent: (ReadBookIntent) -> Unit,
) {
    var letterSpacing by remember { mutableFloatStateOf(ReadBookConfig.letterSpacing) }
    var lineSpacing by remember { mutableFloatStateOf(ReadBookConfig.lineSpacingExtra.toFloat()) }
    var paragraphSpacing by remember { mutableFloatStateOf(ReadBookConfig.paragraphSpacing.toFloat()) }
    var indentCount by remember { mutableIntStateOf(ReadBookConfig.paragraphIndent.length) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
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
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.ParagraphIndent("　".repeat(indentCount))))
            },
        )
        TinySliderSettingItem(
            title = stringResource(R.string.text_letter_spacing),
            value = (letterSpacing * 100) + 50,
            valueRange = 0f..100f,
            onValueChange = { value ->
                letterSpacing = (value - 50) / 100f
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.LetterSpacing(letterSpacing)))
            },
        )
        TinySliderSettingItem(
            title = stringResource(R.string.line_size),
            value = lineSpacing,
            valueRange = 0f..20f,
            onValueChange = { value ->
                lineSpacing = value
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.LineSpacing(value.toInt())))
            },
        )
        TinySliderSettingItem(
            title = stringResource(R.string.paragraph_size),
            value = paragraphSpacing,
            valueRange = 0f..20f,
            onValueChange = { value ->
                paragraphSpacing = value
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.ParagraphSpacing(value.toInt())))
            },
        )
    }
}

// ========== Text & Effects (sub-page) ==========

@Composable
internal fun TextEffectsPage(
    onOpenShadowSet: () -> Unit,
    onOpenUnderlineConfig: () -> Unit,
    onOpenHighlightRule: () -> Unit,
    onOpenFontSelect: () -> Unit,
    modifier: Modifier = Modifier,
    onIntent: (ReadBookIntent) -> Unit,
) {
    var textItalic by remember { mutableStateOf(ReadBookConfig.textItalic) }
    var textBold by remember { mutableIntStateOf(ReadBookConfig.textBold) }

    var showColorPicker by remember { mutableStateOf(false) }
    var colorPickerId by remember { mutableIntStateOf(0) }
    var colorPickerInitial by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        SectionTitle(stringResource(R.string.text_typeface))
        TinySwitchSettingItem(
            title = stringResource(R.string.read_config_italic),
            checked = textItalic,
            imageVector = Icons.Default.FormatItalic,
            onCheckedChange = {
                textItalic = it
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TextItalic(it)))
            },
        )
        TinySliderSettingItem(
            title = stringResource(R.string.font_weight_text),
            value = textBold.coerceAtLeast(100).toFloat(),
            valueRange = 100f..900f,
            imageVector = Icons.Default.FormatBold,
            onValueChange = { value ->
                textBold = value.toInt()
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TextBold(value.toInt())))
            },
        )

        TinyClickableSettingItem(
            title = stringResource(R.string.select_font),
            imageVector = Icons.Default.TextFields,
            onClick = onOpenFontSelect,
        )

        val chineseConvertEntries = stringArrayResource(R.array.chinese_mode)
        val chineseConvertValues = remember { arrayOf("0", "1", "2") }
        TinyDropdownSettingItem(
            title = stringResource(R.string.chinese_converter),
            selectedValue = ReadConfig.chineseConverterType.toString(),
            displayEntries = chineseConvertEntries,
            entryValues = chineseConvertValues,
            onValueChange = {
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.ChineseConverterType(it.toInt())))
            },
        )

        Spacer(Modifier.height(8.dp))

        // Colors
        SectionTitle(stringResource(R.string.read_color))
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
            title = stringResource(R.string.highlight_rule_config),
            description = stringResource(R.string.read_config_regex_desc),
            imageVector = Icons.Default.Tune,
            onClick = onOpenHighlightRule,
        )

        Spacer(Modifier.height(8.dp))
    }

    // Color picker
    ColorPickerSheet(
        show = showColorPicker,
        initialColor = colorPickerInitial,
        onDismissRequest = { showColorPicker = false },
        onColorSelected = { color ->
            when (colorPickerId) {
                COLOR_TEXT -> {
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TextColor(color)))
                }

                COLOR_ACCENT -> {
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TextAccentColor(color)))
                }
            }
            showColorPicker = false
        },
    )
}

// ========== Title Settings (sub-page) ==========

@Composable
internal fun TitleSettingsPage(
    onOpenTitleFontSelect: () -> Unit,
    modifier: Modifier = Modifier,
    onIntent: (ReadBookIntent) -> Unit,
) {
    var titleMode by remember(ReadBookConfig.titleMode) { mutableIntStateOf(ReadBookConfig.titleMode) }
    var titleBold by remember(ReadBookConfig.titleBold) { mutableIntStateOf(ReadBookConfig.titleBold) }
    var titleSegType by remember(ReadBookConfig.titleSegType) { mutableIntStateOf(ReadBookConfig.titleSegType) }
    var titleSegDistance by remember(ReadBookConfig.titleSegDistance) { mutableIntStateOf(ReadBookConfig.titleSegDistance) }
    var titleSegFlag by remember(ReadBookConfig.titleSegFlag) { mutableStateOf(ReadBookConfig.titleSegFlag) }
    var titleSegScaling by remember(ReadBookConfig.titleSegScaling) { mutableFloatStateOf(ReadBookConfig.titleSegScaling) }
    var titleLineSpacingExtra by remember(ReadBookConfig.titleLineSpacingExtra) { mutableIntStateOf(ReadBookConfig.titleLineSpacingExtra) }
    var titleLineSpacingSub by remember(ReadBookConfig.titleLineSpacingSub) { mutableIntStateOf(ReadBookConfig.titleLineSpacingSub) }
    var titleSize by remember(ReadBookConfig.titleSize) { mutableIntStateOf(ReadBookConfig.titleSize) }
    var titleTopSpacing by remember(ReadBookConfig.titleTopSpacing) { mutableIntStateOf(ReadBookConfig.titleTopSpacing) }
    var titleBottomSpacing by remember(ReadBookConfig.titleBottomSpacing) { mutableIntStateOf(ReadBookConfig.titleBottomSpacing) }

    var showColorPicker by remember { mutableStateOf(false) }
    var colorPickerId by remember { mutableIntStateOf(0) }
    var colorPickerInitial by remember { mutableIntStateOf(0) }

    val weightIconMap = mapOf(
        0 to Icons.Default.TextFields,
        1 to Icons.Default.TextFormat,
        2 to Icons.Default.FormatBold,
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
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
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TitleMode(titleMode)))
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
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TitleBold(value.toInt())))
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

        // Title font
        TinyClickableSettingItem(
            title = stringResource(R.string.read_config_title_settings),
            imageVector = Icons.Default.TextFields,
            onClick = onOpenTitleFontSelect,
        )

        Spacer(Modifier.height(8.dp))

        // Title segmentation
        TinyDropdownSettingItem(
            title = stringResource(R.string.split_title_mode),
            selectedValue = titleSegType.toString(),
            displayEntries = arrayOf(
                stringResource(R.string.close),
                stringResource(R.string.split_title_by_position),
                stringResource(R.string.split_title_by_flag),
                stringResource(R.string.split_title_by_regex),
            ),
            entryValues = arrayOf("0", "1", "2", "3"),
            onValueChange = {
                titleSegType = it.toInt()
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TitleSegType(titleSegType)))
            },
        )
        if (titleSegType == 1) {
            TinySliderSettingItem(
                title = stringResource(R.string.split_title_position),
                value = titleSegDistance.toFloat(),
                valueRange = 1f..20f,
                steps = 18,
                onValueChange = { value ->
                    titleSegDistance = value.toInt()
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TitleSegDistance(titleSegDistance)))
                },
            )
        }
        if (titleSegType == 2 || titleSegType == 3) {
            var showFlagDialog by remember { mutableStateOf(false) }
            var flagText by remember { mutableStateOf(titleSegFlag) }

            TinyClickableSettingItem(
                title = stringResource(R.string.rule_segment),
                description = titleSegFlag.ifBlank { stringResource(R.string.split_title_mode) },
                onClick = { showFlagDialog = true },
            )

            AppAlertDialog(
                show = showFlagDialog,
                onDismissRequest = { showFlagDialog = false },
                title = stringResource(R.string.rule_segment),
                content = {
                    AppTextField(
                        value = flagText,
                        onValueChange = { flagText = it },
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmText = stringResource(android.R.string.ok),
                onConfirm = {
                    titleSegFlag = flagText
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TitleSegFlag(titleSegFlag)))
                    showFlagDialog = false
                },
                dismissText = stringResource(android.R.string.cancel),
                onDismiss = { showFlagDialog = false },
            )
        }

        Spacer(Modifier.height(8.dp))

        // Title spacing sliders
        TinySliderSettingItem(
            title = stringResource(R.string.subtitle_scale),
            value = titleSegScaling * 10,
            valueRange = 0f..100f,
            onValueChange = { value ->
                titleSegScaling = value / 10f
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TitleSegScaling(titleSegScaling)))
            },
        )
        TinySliderSettingItem(
            title = stringResource(R.string.heading_spacing),
            value = titleLineSpacingExtra.toFloat(),
            valueRange = 0f..100f,
            onValueChange = { value ->
                titleLineSpacingExtra = value.toInt()
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TitleLineSpacingExtra(titleLineSpacingExtra)))
            },
        )
        TinySliderSettingItem(
            title = stringResource(R.string.subtitle_margin),
            value = titleLineSpacingSub.toFloat(),
            valueRange = 0f..100f,
            onValueChange = { value ->
                titleLineSpacingSub = value.toInt()
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TitleLineSpacingSub(titleLineSpacingSub)))
            },
        )
        TinySliderSettingItem(
            title = stringResource(R.string.title_font_size),
            value = titleSize.toFloat(),
            valueRange = 0f..100f,
            onValueChange = { value ->
                titleSize = value.toInt()
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TitleSize(titleSize)))
            },
        )
        TinySliderSettingItem(
            title = stringResource(R.string.title_margin_top),
            value = titleTopSpacing.toFloat(),
            valueRange = 0f..100f,
            onValueChange = { value ->
                titleTopSpacing = value.toInt()
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TitleTopSpacing(titleTopSpacing)))
            },
        )
        TinySliderSettingItem(
            title = stringResource(R.string.title_margin_bottom),
            value = titleBottomSpacing.toFloat(),
            valueRange = 0f..100f,
            onValueChange = { value ->
                titleBottomSpacing = value.toInt()
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TitleBottomSpacing(titleBottomSpacing)))
            },
        )

    }

    // Color picker
    ColorPickerSheet(
        show = showColorPicker,
        initialColor = colorPickerInitial,
        onDismissRequest = { showColorPicker = false },
        onColorSelected = { color ->
            when (colorPickerId) {
                COLOR_TITLE -> {
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TitleColor(color)))
                }
            }
            showColorPicker = false
        },
    )
}
