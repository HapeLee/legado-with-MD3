package io.legado.app.ui.book.read.sheet

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleanHands
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.data.repository.ReadSettingsRepository
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.ConfigUpdate
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.book.read.ReadSheetConfigUiState
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppSlider
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.FontFolderState
import io.legado.app.ui.widget.components.FontSelectSheet
import io.legado.app.ui.widget.components.SectionTitle
import io.legado.app.ui.widget.components.ValueStepper
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.dialog.ColorPickerSheet
import io.legado.app.ui.widget.components.dialog.CustomTipDialog
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.settingItem.TinyClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.TinyColorModeSettingItem
import io.legado.app.ui.widget.components.settingItem.TinyColorSettingItem
import io.legado.app.ui.widget.components.settingItem.TinyDropdownSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySliderSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySwitchSettingItem
import io.legado.app.utils.getCompatColor
import org.koin.compose.koinInject
import kotlin.math.roundToInt

// region Modal target sealed interface

/** Identifies which color picker to show in the typography page. */
internal sealed interface TypographyColorTarget {
    data object Text : TypographyColorTarget
    data object TextAccent : TypographyColorTarget
    data object Title : TypographyColorTarget
    data object TitleNight : TypographyColorTarget
    data object Header : TypographyColorTarget
    data object HeaderNight : TypographyColorTarget
    data object Footer : TypographyColorTarget
    data object FooterNight : TypographyColorTarget
    data object Divider : TypographyColorTarget
}

// endregion

// region Dropdown & Slider helpers

@Composable
private fun FontWeightSetting(
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    var showVariableWeight by remember { mutableStateOf(false) }
    var sliderValue by remember(value) {
        mutableFloatStateOf(
            when (value) {
                2 -> 300f
                0 -> 400f
                1 -> 900f
                else -> value.coerceIn(100, 900).toFloat()
            }
        )
    }
    val weightEntries = stringArrayResource(R.array.text_font_weight)

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                TinyDropdownSettingItem(
                    title = stringResource(R.string.font_weight_text),
                    selectedValue = value.toString(),
                    displayEntries = arrayOf(weightEntries[2], weightEntries[0], weightEntries[1]),
                    entryValues = arrayOf("2", "0", "1"),
                    onValueChange = { onValueChange(it.toInt()) },
                )
            }
            NormalCard(
                onClick = { showVariableWeight = !showVariableWeight },
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .height(56.dp)
                    .aspectRatio(1f),
                containerColor = if (showVariableWeight) LegadoTheme.colorScheme.secondaryContainer else LegadoTheme.colorScheme.surfaceContainerLow,
                contentColor = if (showVariableWeight) LegadoTheme.colorScheme.onSecondaryContainer else LegadoTheme.colorScheme.onSurfaceVariant,
                cornerRadius = 12.dp,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AppIcon(Icons.Default.Tune, stringResource(R.string.font_weight_text))
                }
            }
        }

        AnimatedVisibility(visible = showVariableWeight) {
            NormalCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
                    .height(56.dp),
                containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
                cornerRadius = 12.dp,
            ) {
                ValueStepper(
                    value = sliderValue,
                    displayValue = sliderValue,
                    valueRange = 100f..900f,
                    onValueChange = {
                        sliderValue = it
                        onValueChange(it.toInt())
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    content = {
                        AppSlider(
                            value = sliderValue,
                            onValueChange = { sliderValue = it },
                            onValueChangeFinished = { onValueChange(sliderValue.toInt()) },
                            valueRange = 100f..900f,
                            modifier = Modifier.weight(1f),
                        )
                    },
                )
            }
        }
    }
}

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

@Composable
private fun PaddingSliders(
    top: Float,
    bottom: Float,
    left: Float,
    right: Float,
    onTopChange: (Float) -> Unit,
    onBottomChange: (Float) -> Unit,
    onLeftChange: (Float) -> Unit,
    onRightChange: (Float) -> Unit,
) {
    TinySliderSettingItem(
        title = stringResource(R.string.padding_top),
        value = top,
        valueRange = 0f..200f,
        onValueChange = onTopChange,
    )
    TinySliderSettingItem(
        title = stringResource(R.string.padding_bottom),
        value = bottom,
        valueRange = 0f..200f,
        onValueChange = onBottomChange,
    )
    TinySliderSettingItem(
        title = stringResource(R.string.padding_left),
        value = left,
        valueRange = 0f..200f,
        onValueChange = onLeftChange,
    )
    TinySliderSettingItem(
        title = stringResource(R.string.padding_right),
        value = right,
        valueRange = 0f..200f,
        onValueChange = onRightChange,
    )
}

// endregion

// region Tab composables (placeholders — content implemented in subsequent tasks)

@Composable
internal fun TypographyBodyTab(
    config: ReadSheetConfigUiState,
    onIntent: (ReadBookIntent) -> Unit,
    onOpenFontSelect: () -> Unit,
    onOpenShadowSet: () -> Unit,
    onOpenUnderlineConfig: () -> Unit,
    onOpenHighlightRule: () -> Unit,
    onOpenColorPicker: (TypographyColorTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    var textItalic by remember(config.textItalic) { mutableStateOf(config.textItalic) }
    var textBold by remember(config.textBold) { mutableIntStateOf(config.textBold) }
    var letterSpacing by remember(config.letterSpacing) { mutableFloatStateOf(config.letterSpacing) }
    var lineSpacing by remember(config.lineSpacing) { mutableFloatStateOf(config.lineSpacing.toFloat()) }
    var paragraphSpacing by remember(config.paragraphSpacing) { mutableFloatStateOf(config.paragraphSpacing.toFloat()) }
    var indentCount by remember(config.paragraphIndentCount) { mutableIntStateOf(config.paragraphIndentCount) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        // 字体组
        SectionTitle(title = stringResource(R.string.text_typeface))
        TinyClickableSettingItem(
            title = stringResource(R.string.select_font),
            imageVector = Icons.Default.TextFields,
            onClick = onOpenFontSelect,
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
        FontWeightSetting(
            value = textBold,
            onValueChange = { value ->
                textBold = value
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TextBold(value)))
            },
        )
        val chineseConvertEntries = stringArrayResource(R.array.chinese_mode)
        val chineseConvertValues = remember { arrayOf("0", "1", "2") }
        TinyDropdownSettingItem(
            title = stringResource(R.string.chinese_converter),
            selectedValue = config.chineseConverterType.toString(),
            displayEntries = chineseConvertEntries,
            entryValues = chineseConvertValues,
            onValueChange = {
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.ChineseConverterType(it.toInt())))
            },
        )

        // 颜色组
        SectionTitle(title = stringResource(R.string.read_color))
        TinyColorSettingItem(
            title = stringResource(R.string.text_color),
            colorValue = config.textColor,
            onClick = { onOpenColorPicker(TypographyColorTarget.Text) },
        )
        TinyColorSettingItem(
            title = stringResource(R.string.text_accent_color),
            colorValue = config.textAccentColor,
            onClick = { onOpenColorPicker(TypographyColorTarget.TextAccent) },
        )

        // 效果组
        SectionTitle(title = stringResource(R.string.read_config_effects))
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

        // 间距组
        SectionTitle(title = stringResource(R.string.read_config_body_spacing))
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
            steps = 99,
            valueFormat = { ((it - 50) / 100f).toString() },
            onValueChange = { value ->
                letterSpacing = (value - 50) / 100f
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.LetterSpacing(letterSpacing)))
            },
        )
        TinySliderSettingItem(
            title = stringResource(R.string.line_size),
            value = lineSpacing,
            valueRange = 0f..20f,
            steps = 19,
            valueFormat = { ((it - 10) / 10f).toString() },
            onValueChange = { value ->
                lineSpacing = value
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.LineSpacing(value.toInt())))
            },
        )
        TinySliderSettingItem(
            title = stringResource(R.string.paragraph_size),
            value = paragraphSpacing,
            valueRange = 0f..20f,
            steps = 19,
            valueFormat = { (it / 10f).toString() },
            onValueChange = { value ->
                paragraphSpacing = value
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.ParagraphSpacing(value.toInt())))
            },
        )

        // 对齐组
        SectionTitle(title = stringResource(R.string.text_alignment))
        TinySwitchSettingItem(
            title = stringResource(R.string.text_full_justify),
            checked = config.textFullJustify,
            onCheckedChange = {
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TextFullJustify(it)))
            },
        )
        TinySwitchSettingItem(
            title = stringResource(R.string.text_bottom_justify),
            checked = config.textBottomJustify,
            onCheckedChange = {
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TextBottomJustify(it)))
            },
        )

        // 图片组
        SectionTitle(title = stringResource(R.string.image_style))
        val imgStyleEntries = arrayOf(
            stringResource(R.string.btn_default_s),
            stringResource(R.string.image_style_full),
            stringResource(R.string.image_style_text),
            stringResource(R.string.image_style_single),
        )
        val imgStyleValues = arrayOf(
            Book.imgStyleDefault,
            Book.imgStyleFull,
            Book.imgStyleText,
            Book.imgStyleSingle,
        )
        TinyDropdownSettingItem(
            title = stringResource(R.string.image_style),
            selectedValue = ReadBook.book?.getImageStyle() ?: Book.imgStyleDefault,
            displayEntries = imgStyleEntries,
            entryValues = imgStyleValues,
            onValueChange = { style ->
                onIntent(ReadBookIntent.MenuImageStyle(style))
            },
        )

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
internal fun TypographyTitleTab(
    config: ReadSheetConfigUiState,
    onIntent: (ReadBookIntent) -> Unit,
    onOpenTitleFontSelect: () -> Unit,
    onOpenColorPicker: (TypographyColorTarget) -> Unit,
    sameTitleRemoved: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var titleMode by remember(config.titleMode) { mutableIntStateOf(config.titleMode) }
    var titleBold by remember(config.titleBold) { mutableIntStateOf(config.titleBold) }
    var titleSegType by remember(config.titleSegType) { mutableIntStateOf(config.titleSegType) }
    var titleSegDistance by remember(config.titleSegDistance) { mutableIntStateOf(config.titleSegDistance) }
    var titleSegFlag by remember(config.titleSegFlag) { mutableStateOf(config.titleSegFlag) }
    var titleSegScaling by remember(config.titleSegScaling) { mutableFloatStateOf(config.titleSegScaling) }
    var titleLineSpacingExtra by remember(config.titleLineSpacingExtra) { mutableIntStateOf(config.titleLineSpacingExtra) }
    var titleLineSpacingSub by remember(config.titleLineSpacingSub) { mutableIntStateOf(config.titleLineSpacingSub) }

    // Title font size: <8 means relative offset (textSize + titleSize), >=8 means absolute
    var titleSize by remember(config.titleSize) {
        val initial = if (config.titleSize < 8) {
            (ReadBookConfig.textSize + config.titleSize).coerceIn(8, 60)
        } else {
            config.titleSize
        }
        mutableIntStateOf(initial)
    }

    var showFlagDialog by remember { mutableStateOf(false) }
    var flagText by remember { mutableStateOf(titleSegFlag) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        // 内容组
        SectionTitle(title = stringResource(R.string.content))
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

        // 字体组
        SectionTitle(title = stringResource(R.string.text_font))
        TinyClickableSettingItem(
            title = stringResource(R.string.select_font),
            imageVector = Icons.Default.TextFields,
            onClick = onOpenTitleFontSelect,
        )
        FontWeightSetting(
            value = titleBold,
            onValueChange = { value ->
                titleBold = value
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TitleBold(value)))
            },
        )
        TinyColorModeSettingItem(
            title = stringResource(R.string.title_color),
            dayColor = if (config.titleColor != 0) config.titleColor else config.textColorDay,
            nightColor = if (config.titleColorNight != 0) config.titleColorNight else config.textColorNight,
            onClickColor = { isNight ->
                if (isNight) {
                    onOpenColorPicker(TypographyColorTarget.TitleNight)
                } else {
                    onOpenColorPicker(TypographyColorTarget.Title)
                }
            },
        )
        TinySliderSettingItem(
            title = stringResource(R.string.title_font_size),
            value = titleSize.toFloat(),
            valueRange = 8f..60f,
            steps = 51,
            valueFormat = { "${it.toInt()}sp" },
            onValueChange = { value ->
                titleSize = value.toInt()
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TitleSize(titleSize)))
            },
        )

        // 分段组
        SectionTitle(title = stringResource(R.string.title_segment))
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
            TinyClickableSettingItem(
                title = stringResource(R.string.rule_segment),
                description = titleSegFlag.ifBlank { stringResource(R.string.split_title_mode) },
                onClick = { showFlagDialog = true },
            )
        }
        TinySliderSettingItem(
            title = stringResource(R.string.subtitle_scale),
            value = titleSegScaling,
            valueRange = 0f..2f,
            steps = 19,
            stepSize = 0.1f,
            valueFormat = { "%.1f".format(it) },
            onValueChange = { value ->
                titleSegScaling = (value * 10).roundToInt() / 10f
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TitleSegScaling(titleSegScaling)))
            },
        )

        // 间距组
        SectionTitle(title = stringResource(R.string.title_spacing))
        TinySliderSettingItem(
            title = stringResource(R.string.heading_spacing),
            value = titleLineSpacingExtra.toFloat(),
            valueRange = 0f..20f,
            onValueChange = { value ->
                titleLineSpacingExtra = value.toInt()
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TitleLineSpacingExtra(titleLineSpacingExtra)))
            },
        )
        TinySliderSettingItem(
            title = stringResource(R.string.subtitle_margin),
            value = titleLineSpacingSub.toFloat(),
            valueRange = -30f..30f,
            onValueChange = { value ->
                titleLineSpacingSub = value.toInt()
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TitleLineSpacingSub(titleLineSpacingSub)))
            },
        )

        // 去重组
        SectionTitle(title = stringResource(R.string.title_dedup))
        TinySwitchSettingItem(
            title = stringResource(R.string.same_title_removed),
            checked = sameTitleRemoved,
            imageVector = Icons.Default.CleanHands,
            onCheckedChange = {
                onIntent(ReadBookIntent.MenuSameTitleRemoved)
            },
        )

        Spacer(Modifier.height(8.dp))
    }

    // Segmentation rule dialog
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

@Composable
internal fun TypographyHeaderTab(
    config: ReadSheetConfigUiState,
    onIntent: (ReadBookIntent) -> Unit,
    onOpenHeaderFontSelect: () -> Unit,
    onOpenColorPicker: (TypographyColorTarget) -> Unit,
    onOpenCustomTip: (CustomTipTarget) -> Unit,
    headerMode: Int,
    headerLeft: Int,
    headerMiddle: Int,
    headerRight: Int,
    onHeaderModeChange: (Int) -> Unit,
    onTipChange: (CustomTipTarget, Int) -> Unit,
    showHeaderLine: Boolean,
    onShowHeaderLineChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val tipNames = remember { ReadBookConfig.tipNames }
    val tipValues = remember { ReadBookConfig.tipValues }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        // 内容组
        SectionTitle(title = stringResource(R.string.content))
        val headerModes = ReadBookConfig.getHeaderModes(context)
        TinyDropdownSettingItem(
            title = stringResource(R.string.header),
            selectedValue = headerMode.toString(),
            displayEntries = headerModes.values.toTypedArray(),
            entryValues = headerModes.keys.map { it.toString() }.toTypedArray(),
            onValueChange = { onHeaderModeChange(it.toInt()) },
        )
        TipPositionDropdown(
            label = stringResource(R.string.left),
            value = headerLeft,
            tipNames = tipNames,
            tipValues = tipValues,
            onValueChange = { onTipChange(CustomTipTarget.HEADER_LEFT, it) },
        )
        TipPositionDropdown(
            label = stringResource(R.string.middle),
            value = headerMiddle,
            tipNames = tipNames,
            tipValues = tipValues,
            onValueChange = { onTipChange(CustomTipTarget.HEADER_MIDDLE, it) },
        )
        TipPositionDropdown(
            label = stringResource(R.string.right),
            value = headerRight,
            tipNames = tipNames,
            tipValues = tipValues,
            onValueChange = { onTipChange(CustomTipTarget.HEADER_RIGHT, it) },
        )

        // 分隔线组
        SectionTitle(title = stringResource(R.string.read_config_divider_line))
        TinySwitchSettingItem(
            title = stringResource(R.string.showLine),
            checked = showHeaderLine,
            onCheckedChange = { onShowHeaderLineChange(it) },
        )
        TinyColorSettingItem(
            title = stringResource(R.string.tip_divider_color),
            colorValue = when (config.tipDividerColor) {
                -1 -> context.getCompatColor(R.color.divider)
                0 -> ReadBookConfig.textColor
                else -> config.tipDividerColor
            },
            onClick = { onOpenColorPicker(TypographyColorTarget.Divider) },
        )

        // 字体组
        SectionTitle(title = stringResource(R.string.text_font))
        TinyClickableSettingItem(
            title = stringResource(R.string.select_font),
            imageVector = Icons.Default.TextFields,
            onClick = onOpenHeaderFontSelect,
        )
        TinySliderSettingItem(
            title = stringResource(R.string.title_font_size),
            value = config.headerFontSize.toFloat(),
            valueRange = 0f..100f,
            onValueChange = { value ->
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.HeaderFontSize(value.toInt())))
                onIntent(ReadBookIntent.SaveReadStyleConfig)
            },
        )
        TinyColorModeSettingItem(
            title = stringResource(R.string.title_color),
            dayColor = if (config.tipHeaderColor != 0) config.tipHeaderColor else config.textColorDay,
            nightColor = if (config.tipHeaderColorNight != 0) config.tipHeaderColorNight else config.textColorNight,
            onClickColor = { isNight ->
                if (isNight) {
                    onOpenColorPicker(TypographyColorTarget.HeaderNight)
                } else {
                    onOpenColorPicker(TypographyColorTarget.Header)
                }
            },
        )

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
internal fun TypographyFooterTab(
    config: ReadSheetConfigUiState,
    onIntent: (ReadBookIntent) -> Unit,
    onOpenFooterFontSelect: () -> Unit,
    onOpenColorPicker: (TypographyColorTarget) -> Unit,
    onOpenCustomTip: (CustomTipTarget) -> Unit,
    footerMode: Int,
    footerLeft: Int,
    footerMiddle: Int,
    footerRight: Int,
    onFooterModeChange: (Int) -> Unit,
    onTipChange: (CustomTipTarget, Int) -> Unit,
    showFooterLine: Boolean,
    onShowFooterLineChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val tipNames = remember { ReadBookConfig.tipNames }
    val tipValues = remember { ReadBookConfig.tipValues }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        // 内容组
        SectionTitle(title = stringResource(R.string.content))
        val footerModes = ReadBookConfig.getFooterModes(context)
        TinyDropdownSettingItem(
            title = stringResource(R.string.footer),
            selectedValue = footerMode.toString(),
            displayEntries = footerModes.values.toTypedArray(),
            entryValues = footerModes.keys.map { it.toString() }.toTypedArray(),
            onValueChange = { onFooterModeChange(it.toInt()) },
        )
        TipPositionDropdown(
            label = stringResource(R.string.left),
            value = footerLeft,
            tipNames = tipNames,
            tipValues = tipValues,
            onValueChange = { onTipChange(CustomTipTarget.FOOTER_LEFT, it) },
        )
        TipPositionDropdown(
            label = stringResource(R.string.middle),
            value = footerMiddle,
            tipNames = tipNames,
            tipValues = tipValues,
            onValueChange = { onTipChange(CustomTipTarget.FOOTER_MIDDLE, it) },
        )
        TipPositionDropdown(
            label = stringResource(R.string.right),
            value = footerRight,
            tipNames = tipNames,
            tipValues = tipValues,
            onValueChange = { onTipChange(CustomTipTarget.FOOTER_RIGHT, it) },
        )

        // 分隔线组
        SectionTitle(title = stringResource(R.string.read_config_divider_line))
        TinySwitchSettingItem(
            title = stringResource(R.string.showLine),
            checked = showFooterLine,
            onCheckedChange = { onShowFooterLineChange(it) },
        )

        // 字体组
        SectionTitle(title = stringResource(R.string.text_font))
        TinySwitchSettingItem(
            title = stringResource(R.string.apply_header_style),
            checked = config.applyHeaderStyle,
            onCheckedChange = {
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.ApplyHeaderStyle(it)))
            },
        )
        if (!config.applyHeaderStyle) {
            TinyClickableSettingItem(
                title = stringResource(R.string.select_font),
                imageVector = Icons.Default.TextFields,
                onClick = onOpenFooterFontSelect,
            )
            TinySliderSettingItem(
                title = stringResource(R.string.title_font_size),
                value = config.footerFontSize.toFloat(),
                valueRange = 0f..100f,
                onValueChange = { value ->
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.FooterFontSize(value.toInt())))
                    onIntent(ReadBookIntent.SaveReadStyleConfig)
                },
            )
            TinyColorModeSettingItem(
                title = stringResource(R.string.title_color),
                dayColor = if (config.tipFooterColor != 0) config.tipFooterColor else config.textColorDay,
                nightColor = if (config.tipFooterColorNight != 0) config.tipFooterColorNight else config.textColorNight,
                onClickColor = { isNight ->
                    if (isNight) {
                        onOpenColorPicker(TypographyColorTarget.FooterNight)
                    } else {
                        onOpenColorPicker(TypographyColorTarget.Footer)
                    }
                },
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
internal fun TypographyMarginTab(
    config: ReadSheetConfigUiState,
    onIntent: (ReadBookIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Body padding state
    var paddingTop by remember(config.paddingTop) { mutableFloatStateOf(config.paddingTop.toFloat()) }
    var paddingBottom by remember(config.paddingBottom) { mutableFloatStateOf(config.paddingBottom.toFloat()) }
    var paddingLeft by remember(config.paddingLeft) { mutableFloatStateOf(config.paddingLeft.toFloat()) }
    var paddingRight by remember(config.paddingRight) { mutableFloatStateOf(config.paddingRight.toFloat()) }

    // Title padding state
    var titleTopSpacing by remember(config.titleTopSpacing) { mutableIntStateOf(config.titleTopSpacing) }
    var titleBottomSpacing by remember(config.titleBottomSpacing) { mutableIntStateOf(config.titleBottomSpacing) }

    // Header padding state
    var headerPaddingTop by remember(config.headerPaddingTop) { mutableFloatStateOf(config.headerPaddingTop.toFloat()) }
    var headerPaddingBottom by remember(config.headerPaddingBottom) { mutableFloatStateOf(config.headerPaddingBottom.toFloat()) }
    var headerPaddingLeft by remember(config.headerPaddingLeft) { mutableFloatStateOf(config.headerPaddingLeft.toFloat()) }
    var headerPaddingRight by remember(config.headerPaddingRight) { mutableFloatStateOf(config.headerPaddingRight.toFloat()) }

    // Footer padding state
    var footerPaddingTop by remember(config.footerPaddingTop) { mutableFloatStateOf(config.footerPaddingTop.toFloat()) }
    var footerPaddingBottom by remember(config.footerPaddingBottom) { mutableFloatStateOf(config.footerPaddingBottom.toFloat()) }
    var footerPaddingLeft by remember(config.footerPaddingLeft) { mutableFloatStateOf(config.footerPaddingLeft.toFloat()) }
    var footerPaddingRight by remember(config.footerPaddingRight) { mutableFloatStateOf(config.footerPaddingRight.toFloat()) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        // 正文边距
        SectionTitle(title = stringResource(R.string.body_padding))
        PaddingSliders(
            top = paddingTop, bottom = paddingBottom,
            left = paddingLeft, right = paddingRight,
            onTopChange = {
                paddingTop = it
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.PaddingTop(it.toInt())))
            },
            onBottomChange = {
                paddingBottom = it
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.PaddingBottom(it.toInt())))
            },
            onLeftChange = {
                paddingLeft = it
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.PaddingLeft(it.toInt())))
            },
            onRightChange = {
                paddingRight = it
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.PaddingRight(it.toInt())))
            },
        )

        // 标题边距
        SectionTitle(title = stringResource(R.string.title_padding))
        TinySliderSettingItem(
            title = stringResource(R.string.title_margin_top),
            value = titleTopSpacing.toFloat(),
            valueRange = 0f..200f,
            onValueChange = { value ->
                titleTopSpacing = value.toInt()
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TitleTopSpacing(titleTopSpacing)))
            },
        )
        TinySliderSettingItem(
            title = stringResource(R.string.title_margin_bottom),
            value = titleBottomSpacing.toFloat(),
            valueRange = 0f..200f,
            onValueChange = { value ->
                titleBottomSpacing = value.toInt()
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TitleBottomSpacing(titleBottomSpacing)))
            },
        )

        // 页眉边距
        SectionTitle(title = stringResource(R.string.header_padding))
        PaddingSliders(
            top = headerPaddingTop, bottom = headerPaddingBottom,
            left = headerPaddingLeft, right = headerPaddingRight,
            onTopChange = {
                headerPaddingTop = it
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.HeaderPaddingTop(it.toInt())))
            },
            onBottomChange = {
                headerPaddingBottom = it
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.HeaderPaddingBottom(it.toInt())))
            },
            onLeftChange = {
                headerPaddingLeft = it
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.HeaderPaddingLeft(it.toInt())))
            },
            onRightChange = {
                headerPaddingRight = it
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.HeaderPaddingRight(it.toInt())))
            },
        )

        // 页脚边距
        SectionTitle(title = stringResource(R.string.footer_padding))
        PaddingSliders(
            top = footerPaddingTop, bottom = footerPaddingBottom,
            left = footerPaddingLeft, right = footerPaddingRight,
            onTopChange = {
                footerPaddingTop = it
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.FooterPaddingTop(it.toInt())))
            },
            onBottomChange = {
                footerPaddingBottom = it
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.FooterPaddingBottom(it.toInt())))
            },
            onLeftChange = {
                footerPaddingLeft = it
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.FooterPaddingLeft(it.toInt())))
            },
            onRightChange = {
                footerPaddingRight = it
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.FooterPaddingRight(it.toInt())))
            },
        )

        Spacer(Modifier.height(8.dp))
    }
}

// endregion

// region Hoisted modal sheet composables

@Composable
internal fun TypographyColorPickerSheet(
    target: TypographyColorTarget,
    config: ReadSheetConfigUiState,
    onDismiss: () -> Unit,
    onIntent: (ReadBookIntent) -> Unit,
) {
    val context = LocalContext.current
    val initialColor = remember(target, config) {
        when (target) {
            TypographyColorTarget.Text -> config.textColor
            TypographyColorTarget.TextAccent -> config.textAccentColor
            TypographyColorTarget.Title ->
                if (config.titleColor != 0) config.titleColor else config.textColorDay
            TypographyColorTarget.TitleNight ->
                if (config.titleColorNight != 0) config.titleColorNight else config.textColorNight
            TypographyColorTarget.Header ->
                if (config.tipHeaderColor != 0) config.tipHeaderColor
                else config.textColorDay
            TypographyColorTarget.HeaderNight ->
                if (config.tipHeaderColorNight != 0) config.tipHeaderColorNight
                else config.textColorNight
            TypographyColorTarget.Footer ->
                if (config.tipFooterColor != 0) config.tipFooterColor
                else config.textColorDay
            TypographyColorTarget.FooterNight ->
                if (config.tipFooterColorNight != 0) config.tipFooterColorNight
                else config.textColorNight
            TypographyColorTarget.Divider -> when (config.tipDividerColor) {
                -1 -> context.getCompatColor(R.color.divider)
                0 -> config.textColor
                else -> config.tipDividerColor
            }
        }
    }

    ColorPickerSheet(
        show = true,
        initialColor = initialColor,
        onDismissRequest = onDismiss,
        onColorSelected = { color ->
            when (target) {
                TypographyColorTarget.Text ->
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TextColor(color)))
                TypographyColorTarget.TextAccent ->
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TextAccentColor(color)))
                TypographyColorTarget.Title ->
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TitleColor(color)))
                TypographyColorTarget.TitleNight ->
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TitleColorNight(color)))
                TypographyColorTarget.Header ->
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TipHeaderColor(color)))
                TypographyColorTarget.HeaderNight ->
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TipHeaderColorNight(color)))
                TypographyColorTarget.Footer ->
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TipFooterColor(color)))
                TypographyColorTarget.FooterNight ->
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TipFooterColorNight(color)))
                TypographyColorTarget.Divider ->
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TipDividerColor(color)))
            }
            onDismiss()
        },
    )
}

@Composable
internal fun TypographyHeaderFontSelectSheet(
    config: ReadSheetConfigUiState,
    onDismiss: () -> Unit,
    onIntent: (ReadBookIntent) -> Unit,
) {
    TypographyFontSelectSheet(
        selectedFontPath = ReadBookConfig.headerFont,
        onSelectFont = { fileDoc ->
            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.HeaderFont(fileDoc.uri.toString())))
            onIntent(ReadBookIntent.SaveReadStyleConfig)
            onDismiss()
        },
        onSelectSystemTypeface = {
            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.HeaderFont("")))
            onIntent(ReadBookIntent.SaveReadStyleConfig)
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}

@Composable
internal fun TypographyFooterFontSelectSheet(
    config: ReadSheetConfigUiState,
    onDismiss: () -> Unit,
    onIntent: (ReadBookIntent) -> Unit,
) {
    TypographyFontSelectSheet(
        selectedFontPath = ReadBookConfig.footerFont,
        onSelectFont = { fileDoc ->
            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.FooterFont(fileDoc.uri.toString())))
            onIntent(ReadBookIntent.SaveReadStyleConfig)
            onDismiss()
        },
        onSelectSystemTypeface = {
            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.FooterFont("")))
            onIntent(ReadBookIntent.SaveReadStyleConfig)
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}

@Composable
internal fun TypographyCustomTipDialog(
    target: CustomTipTarget,
    onDismiss: () -> Unit,
    onIntent: (ReadBookIntent) -> Unit,
) {
    val initialTemplate = remember(target) { target.customTemplate }

    CustomTipDialog(
        show = true,
        initialTemplate = initialTemplate,
        onConfirm = { template ->
            applyTipValue(target, ReadBookConfig.tipCustom, onIntent)
            target.applyTemplate(template, onIntent)
            onDismiss()
        },
        onDismissRequest = onDismiss,
    )
}

// endregion

// region Private helpers

@Composable
private fun TypographyFontSelectSheet(
    selectedFontPath: String,
    onSelectFont: (io.legado.app.utils.FileDoc) -> Unit,
    onSelectSystemTypeface: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val readSettingsRepository: ReadSettingsRepository = koinInject()
    val preferences by readSettingsRepository.preferences.collectAsStateWithLifecycle(
        initialValue = null
    )
    val fontFolderState = remember(preferences) {
        val pref = preferences
        if (pref == null) {
            FontFolderState.Loading
        } else {
            FontFolderState.Loaded(pref.fontFolder.takeIf { it.isNotEmpty() }?.toUri())
        }
    }
    val systemTypefaces = stringArrayResource(R.array.system_typefaces)

    FontSelectSheet(
        show = true,
        title = stringResource(R.string.select_font),
        folderState = fontFolderState,
        selectedFontPath = selectedFontPath,
        onDismissRequest = onDismiss,
        onSelectFont = onSelectFont,
        onSelectSystemTypeface = onSelectSystemTypeface,
        onOpenFolderPicker = { /* handled by FontSelectSheet internally */ },
        systemTypefaces = systemTypefaces,
    )
}

private fun applyTipValue(
    target: CustomTipTarget,
    value: Int,
    onIntent: (ReadBookIntent) -> Unit,
) {
    val configUpdate = when (target) {
        CustomTipTarget.HEADER_LEFT -> ConfigUpdate.TipHeaderLeft(value)
        CustomTipTarget.HEADER_MIDDLE -> ConfigUpdate.TipHeaderMiddle(value)
        CustomTipTarget.HEADER_RIGHT -> ConfigUpdate.TipHeaderRight(value)
        CustomTipTarget.FOOTER_LEFT -> ConfigUpdate.TipFooterLeft(value)
        CustomTipTarget.FOOTER_MIDDLE -> ConfigUpdate.TipFooterMiddle(value)
        CustomTipTarget.FOOTER_RIGHT -> ConfigUpdate.TipFooterRight(value)
    }
    onIntent(ReadBookIntent.UpdateConfig(configUpdate))
}

// endregion
