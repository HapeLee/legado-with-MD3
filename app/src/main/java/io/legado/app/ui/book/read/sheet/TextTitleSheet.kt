package io.legado.app.ui.book.read.sheet

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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.book.read.ConfigUpdate
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.widget.components.dialog.ColorPickerSheet
import io.legado.app.ui.widget.components.settingItem.TinyClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.TinyColorSettingItem
import io.legado.app.ui.widget.components.settingItem.TinyDropdownSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySliderSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySwitchSettingItem
import io.legado.app.ui.widget.components.tabRow.CardTabRow
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import kotlinx.coroutines.launch

// Color picker IDs
private const val COLOR_TEXT = 1
private const val COLOR_ACCENT = 2
private const val COLOR_TITLE = 4

// ========== Text & Title Sheet ==========

@Composable
internal fun TextTitlePage(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onOpenShadowSet: () -> Unit,
    onOpenUnderlineConfig: () -> Unit,
    onOpenRegexColor: () -> Unit,
    onOpenFontSelect: () -> Unit,
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
            onIntent = onIntent,
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
        onIntent = onIntent,
    )
}

@Composable
internal fun ReadStyleTextTitleContent(
    tabTitles: List<String>,
    selectedTab: Int,
    onSelectedTabChange: (Int) -> Unit,
    pagerState: PagerState,
    onOpenShadowSet: () -> Unit,
    onOpenUnderlineConfig: () -> Unit,
    onOpenRegexColor: () -> Unit,
    onOpenFontSelect: () -> Unit,
    animateToPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onIntent: (ReadBookIntent) -> Unit,
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
                    onIntent = onIntent,
                )

                1 -> LayoutSpacingPage(onIntent = onIntent)
                2 -> TitleSettingsPage(onIntent = onIntent)
            }
        }
    }
}

// ========== Tab: Layout & Spacing ==========

@Composable
internal fun LayoutSpacingPage(
    onIntent: (ReadBookIntent) -> Unit,
) {
    var letterSpacing by remember { mutableFloatStateOf(ReadBookConfig.letterSpacing) }
    var lineSpacing by remember { mutableFloatStateOf(ReadBookConfig.lineSpacingExtra.toFloat()) }
    var paragraphSpacing by remember { mutableFloatStateOf(ReadBookConfig.paragraphSpacing.toFloat()) }
    var indentCount by remember { mutableIntStateOf(ReadBookConfig.paragraphIndent.length) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = stringResource(R.string.read_config_body_spacing),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp),
        )

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
    onOpenRegexColor: () -> Unit,
    onOpenFontSelect: () -> Unit,
    onIntent: (ReadBookIntent) -> Unit,
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
        Text(
            text = stringResource(R.string.text_typeface),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp),
        )
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

        Text(
            text = stringResource(R.string.read_config_effects),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp),
        )
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
}

// ========== Title Settings (sub-page) ==========

@Composable
internal fun TitleSettingsPage(
    onIntent: (ReadBookIntent) -> Unit,
) {
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

        // Title spacing sliders
        TinySliderSettingItem(
            title = stringResource(R.string.subtitle_scale),
            value = ReadBookConfig.titleSegScaling * 10,
            valueRange = 0f..100f,
            onValueChange = { value ->
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TitleSegScaling(value / 10f)))
            },
        )
        TinySliderSettingItem(
            title = stringResource(R.string.heading_spacing),
            value = ReadBookConfig.titleLineSpacingExtra.toFloat(),
            valueRange = 0f..100f,
            onValueChange = { value ->
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TitleLineSpacingExtra(value.toInt())))
            },
        )
        TinySliderSettingItem(
            title = stringResource(R.string.subtitle_margin),
            value = ReadBookConfig.titleLineSpacingSub.toFloat(),
            valueRange = 0f..100f,
            onValueChange = { value ->
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TitleLineSpacingSub(value.toInt())))
            },
        )
        TinySliderSettingItem(
            title = stringResource(R.string.title_font_size),
            value = ReadBookConfig.titleSize.toFloat(),
            valueRange = 0f..100f,
            onValueChange = { value ->
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TitleSize(value.toInt())))
            },
        )
        TinySliderSettingItem(
            title = stringResource(R.string.title_margin_top),
            value = ReadBookConfig.titleTopSpacing.toFloat(),
            valueRange = 0f..100f,
            onValueChange = { value ->
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TitleTopSpacing(value.toInt())))
            },
        )
        TinySliderSettingItem(
            title = stringResource(R.string.title_margin_bottom),
            value = ReadBookConfig.titleBottomSpacing.toFloat(),
            valueRange = 0f..100f,
            onValueChange = { value ->
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TitleBottomSpacing(value.toInt())))
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
                        onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TitleColor(color)))
                    }
                }
                showColorPicker = false
            },
        )
    }
}
