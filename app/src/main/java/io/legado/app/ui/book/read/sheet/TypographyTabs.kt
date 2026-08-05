package io.legado.app.ui.book.read.sheet

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlignHorizontalCenter
import androidx.compose.material.icons.filled.AlignHorizontalLeft
import androidx.compose.material.icons.filled.AlignHorizontalRight
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.CleanHands
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatIndentIncrease
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatLineSpacing
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LineWeight
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Segment
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewHeadline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.constant.ReadTipType
import io.legado.app.data.repository.ReadSettingsRepository
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
import io.legado.app.ui.widget.components.settingItem.TinyClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.TinyColorModeSettingItem
import io.legado.app.ui.widget.components.settingItem.TinyColorSettingItem
import io.legado.app.ui.widget.components.settingItem.TinyDropdownSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySliderSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySwitchSettingItem
import io.legado.app.ui.widget.components.text.AppText
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
    // 0 常规 / 1 粗体 / 2 细体，其余 100..900 为自定义可变字重
    val isCustom = value !in 0..2
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
        TinyDropdownSettingItem(
            title = stringResource(R.string.font_weight_text),
            selectedValue = if (isCustom) "-1" else value.toString(),
            displayEntries = arrayOf(
                weightEntries[2],
                weightEntries[0],
                weightEntries[1],
                stringResource(R.string.custom),
            ),
            entryValues = arrayOf("2", "0", "1", "-1"),
            imageVector = Icons.Default.LineWeight,
            onValueChange = {
                val v = it.toInt()
                onValueChange(if (v == -1) sliderValue.toInt() else v)
            },
        )

        AnimatedVisibility(visible = isCustom) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AppText(
                    text = stringResource(R.string.font_weight_custom_description),
                    style = LegadoTheme.typography.labelSmallEmphasized,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                )
                NormalCard(
                    modifier = Modifier
                        .fillMaxWidth()
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
}

@Composable
private fun TipPositionDropdown(
    label: String,
    value: Int,
    tipNames: List<String>,
    tipValues: Array<Int>,
    imageVector: ImageVector? = null,
    onValueChange: (Int) -> Unit,
) {
    TinyDropdownSettingItem(
        title = label,
        selectedValue = value.toString(),
        displayEntries = tipNames.toTypedArray(),
        entryValues = tipValues.map { it.toString() }.toTypedArray(),
        imageVector = imageVector,
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
        imageVector = Icons.Default.VerticalAlignTop,
        onValueChange = onTopChange,
    )
    TinySliderSettingItem(
        title = stringResource(R.string.padding_bottom),
        value = bottom,
        valueRange = 0f..200f,
        imageVector = Icons.Default.VerticalAlignBottom,
        onValueChange = onBottomChange,
    )
    TinySliderSettingItem(
        title = stringResource(R.string.padding_left),
        value = left,
        valueRange = 0f..200f,
        imageVector = Icons.Default.AlignHorizontalLeft,
        onValueChange = onLeftChange,
    )
    TinySliderSettingItem(
        title = stringResource(R.string.padding_right),
        value = right,
        valueRange = 0f..200f,
        imageVector = Icons.Default.AlignHorizontalRight,
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
        SectionTitle(title = stringResource(R.string.text_font))
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
            imageVector = Icons.Default.Translate,
            onValueChange = {
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.ChineseConverterType(it.toInt())))
            },
        )

        // 颜色组
        SectionTitle(title = stringResource(R.string.read_color))
        TinyColorSettingItem(
            title = stringResource(R.string.text_color),
            colorValue = config.textColor,
            imageVector = Icons.Default.FormatColorText,
            onClick = { onOpenColorPicker(TypographyColorTarget.Text) },
        )
        TinyColorSettingItem(
            title = stringResource(R.string.text_accent_color),
            colorValue = config.textAccentColor,
            imageVector = Icons.Default.Palette,
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
            imageVector = Icons.Default.FormatIndentIncrease,
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
            imageVector = Icons.Default.SpaceBar,
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
            imageVector = Icons.Default.FormatLineSpacing,
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
            imageVector = Icons.Default.Segment,
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
            imageVector = Icons.Default.FormatAlignJustify,
            onCheckedChange = {
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TextFullJustify(it)))
            },
        )
        TinySwitchSettingItem(
            title = stringResource(R.string.text_bottom_justify),
            checked = config.textBottomJustify,
            imageVector = Icons.Default.VerticalAlignBottom,
            onCheckedChange = {
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TextBottomJustify(it)))
            },
        )

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
    var titleTopSpacing by remember(config.titleTopSpacing) { mutableIntStateOf(config.titleTopSpacing) }
    var titleBottomSpacing by remember(config.titleBottomSpacing) { mutableIntStateOf(config.titleBottomSpacing) }

    var titleSize by remember(config.titleSize) {
        mutableIntStateOf(config.titleSize)
    }

    var showFlagDialog by remember { mutableStateOf(false) }
    var flagText by remember { mutableStateOf(titleSegFlag) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
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
            title = stringResource(R.string.color),
            dayColor = if (config.titleColor != 0) config.titleColor else config.textColorDay,
            nightColor = if (config.titleColorNight != 0) config.titleColorNight else config.textColorNight,
            imageVector = Icons.Default.Palette,
            onClickColor = { isNight ->
                if (isNight) {
                    onOpenColorPicker(TypographyColorTarget.TitleNight)
                } else {
                    onOpenColorPicker(TypographyColorTarget.Title)
                }
            },
        )
        TinySliderSettingItem(
            title = stringResource(R.string.font_size),
            value = titleSize.toFloat(),
            valueRange = 8f..60f,
            steps = 51,
            imageVector = Icons.Default.FormatSize,
            valueFormat = { "${it.toInt()}sp" },
            onValueChange = { value ->
                titleSize = value.toInt()
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TitleSize(titleSize)))
            },
        )

        // 样式组（标题位置 + 分段 + 间距 + 去重）
        SectionTitle(title = stringResource(R.string.style))
        TinyDropdownSettingItem(
            title = stringResource(R.string.title_position),
            selectedValue = titleMode.toString(),
            displayEntries = arrayOf(
                stringResource(R.string.title_left),
                stringResource(R.string.title_center),
                stringResource(R.string.title_hide),
            ),
            entryValues = arrayOf("0", "1", "2"),
            imageVector = Icons.Default.Title,
            onValueChange = {
                titleMode = it.toInt()
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TitleMode(titleMode)))
            },
        )
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
            imageVector = Icons.Default.CallSplit,
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
                imageVector = Icons.Default.SpaceBar,
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
                imageVector = Icons.Default.Code,
                onClick = { showFlagDialog = true },
            )
        }
        TinySliderSettingItem(
            title = stringResource(R.string.subtitle_scale),
            value = titleSegScaling,
            valueRange = 0f..2f,
            steps = 19,
            stepSize = 0.1f,
            imageVector = Icons.Default.AspectRatio,
            valueFormat = { "%.1f".format(it) },
            onValueChange = { value ->
                titleSegScaling = (value * 10).roundToInt() / 10f
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TitleSegScaling(titleSegScaling)))
            },
        )

        TinySwitchSettingItem(
            title = stringResource(R.string.same_title_removed),
            checked = sameTitleRemoved,
            imageVector = Icons.Default.CleanHands,
            onCheckedChange = {
                onIntent(ReadBookIntent.MenuSameTitleRemoved)
            },
        )

        // 间距组
        SectionTitle(title = stringResource(R.string.title_spacing))
        TinySliderSettingItem(
            title = stringResource(R.string.heading_spacing),
            value = titleLineSpacingExtra.toFloat(),
            valueRange = 0f..20f,
            imageVector = Icons.Default.LineWeight,
            onValueChange = { value ->
                titleLineSpacingExtra = value.toInt()
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TitleLineSpacingExtra(titleLineSpacingExtra)))
            },
        )
        TinySliderSettingItem(
            title = stringResource(R.string.subtitle_margin),
            value = titleLineSpacingSub.toFloat(),
            valueRange = -30f..30f,
            imageVector = Icons.Default.Height,
            onValueChange = { value ->
                titleLineSpacingSub = value.toInt()
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TitleLineSpacingSub(titleLineSpacingSub)))
            },
        )

        SectionTitle(title = stringResource(R.string.title_padding))
        TinySliderSettingItem(
            title = stringResource(R.string.title_margin_top),
            value = titleTopSpacing.toFloat(),
            valueRange = 0f..200f,
            imageVector = Icons.Default.VerticalAlignTop,
            onValueChange = { value ->
                titleTopSpacing = value.toInt()
                onIntent(
                    ReadBookIntent.UpdateConfig(
                        ConfigUpdate.TitleTopSpacing(titleTopSpacing)
                    )
                )
            },
        )
        TinySliderSettingItem(
            title = stringResource(R.string.title_margin_bottom),
            value = titleBottomSpacing.toFloat(),
            valueRange = 0f..200f,
            imageVector = Icons.Default.VerticalAlignBottom,
            onValueChange = { value ->
                titleBottomSpacing = value.toInt()
                onIntent(
                    ReadBookIntent.UpdateConfig(
                        ConfigUpdate.TitleBottomSpacing(titleBottomSpacing)
                    )
                )
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
    val tipNames = stringArrayResource(R.array.read_tip).toList()
    val tipValues = tipTypeValues

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        // 内容组
        SectionTitle(title = stringResource(R.string.content))
        val headerModes = headerModes(context)
        TinyDropdownSettingItem(
            title = stringResource(R.string.header),
            selectedValue = headerMode.toString(),
            displayEntries = headerModes.values.toTypedArray(),
            entryValues = headerModes.keys.map { it.toString() }.toTypedArray(),
            imageVector = Icons.Default.ViewHeadline,
            onValueChange = { onHeaderModeChange(it.toInt()) },
        )
        TipPositionDropdown(
            label = stringResource(R.string.left),
            value = headerLeft,
            tipNames = tipNames,
            tipValues = tipValues,
            imageVector = Icons.Default.AlignHorizontalLeft,
            onValueChange = { onTipChange(CustomTipTarget.HEADER_LEFT, it) },
        )
        TipPositionDropdown(
            label = stringResource(R.string.middle),
            value = headerMiddle,
            tipNames = tipNames,
            tipValues = tipValues,
            imageVector = Icons.Default.AlignHorizontalCenter,
            onValueChange = { onTipChange(CustomTipTarget.HEADER_MIDDLE, it) },
        )
        TipPositionDropdown(
            label = stringResource(R.string.right),
            value = headerRight,
            tipNames = tipNames,
            tipValues = tipValues,
            imageVector = Icons.Default.AlignHorizontalRight,
            onValueChange = { onTipChange(CustomTipTarget.HEADER_RIGHT, it) },
        )

        // 分隔线组
        SectionTitle(title = stringResource(R.string.read_config_divider_line))
        TinySwitchSettingItem(
            title = stringResource(R.string.showLine),
            checked = showHeaderLine,
            imageVector = Icons.Default.Minimize,
            onCheckedChange = { onShowHeaderLineChange(it) },
        )
        TinyColorSettingItem(
            title = stringResource(R.string.tip_divider_color),
            description = stringResource(R.string.tip_divider_color_shared_desc),
            colorValue = when (config.tipDividerColor) {
                -1 -> context.getCompatColor(R.color.divider)
                0 -> config.textColorDay
                else -> config.tipDividerColor
            },
            imageVector = Icons.Default.Palette,
            onClick = { onOpenColorPicker(TypographyColorTarget.Divider) },
        )

        SectionTitle(title = stringResource(R.string.text_typeface))
        TinyClickableSettingItem(
            title = stringResource(R.string.select_font),
            imageVector = Icons.Default.TextFields,
            onClick = onOpenHeaderFontSelect,
        )
        TinySliderSettingItem(
            title = stringResource(R.string.font_size),
            value = config.headerFontSize.toFloat(),
            valueRange = 0f..100f,
            imageVector = Icons.Default.FormatSize,
            onValueChange = { value ->
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.HeaderFontSize(value.toInt())))
                onIntent(ReadBookIntent.SaveReadStyleConfig)
            },
        )
        TinyColorModeSettingItem(
            title = stringResource(R.string.color),
            dayColor = if (config.tipHeaderColor != 0) config.tipHeaderColor else config.textColorDay,
            nightColor = if (config.tipHeaderColorNight != 0) config.tipHeaderColorNight else config.textColorNight,
            imageVector = Icons.Default.FormatColorText,
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
    val tipNames = stringArrayResource(R.array.read_tip).toList()
    val tipValues = tipTypeValues

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        // 内容组
        SectionTitle(title = stringResource(R.string.content))
        val footerModes = footerModes(context)
        TinyDropdownSettingItem(
            title = stringResource(R.string.footer),
            selectedValue = footerMode.toString(),
            displayEntries = footerModes.values.toTypedArray(),
            entryValues = footerModes.keys.map { it.toString() }.toTypedArray(),
            imageVector = Icons.Default.ViewAgenda,
            onValueChange = { onFooterModeChange(it.toInt()) },
        )
        TipPositionDropdown(
            label = stringResource(R.string.left),
            value = footerLeft,
            tipNames = tipNames,
            tipValues = tipValues,
            imageVector = Icons.Default.AlignHorizontalLeft,
            onValueChange = { onTipChange(CustomTipTarget.FOOTER_LEFT, it) },
        )
        TipPositionDropdown(
            label = stringResource(R.string.middle),
            value = footerMiddle,
            tipNames = tipNames,
            tipValues = tipValues,
            imageVector = Icons.Default.AlignHorizontalCenter,
            onValueChange = { onTipChange(CustomTipTarget.FOOTER_MIDDLE, it) },
        )
        TipPositionDropdown(
            label = stringResource(R.string.right),
            value = footerRight,
            tipNames = tipNames,
            tipValues = tipValues,
            imageVector = Icons.Default.AlignHorizontalRight,
            onValueChange = { onTipChange(CustomTipTarget.FOOTER_RIGHT, it) },
        )

        // 分隔线组
        SectionTitle(title = stringResource(R.string.read_config_divider_line))
        TinySwitchSettingItem(
            title = stringResource(R.string.showLine),
            checked = showFooterLine,
            imageVector = Icons.Default.Minimize,
            onCheckedChange = { onShowFooterLineChange(it) },
        )
        TinyColorSettingItem(
            title = stringResource(R.string.tip_divider_color),
            description = stringResource(R.string.tip_divider_color_shared_desc),
            colorValue = when (config.tipDividerColor) {
                -1 -> context.getCompatColor(R.color.divider)
                0 -> config.textColorDay
                else -> config.tipDividerColor
            },
            imageVector = Icons.Default.Palette,
            onClick = { onOpenColorPicker(TypographyColorTarget.Divider) },
        )

        // 字体组
        SectionTitle(title = stringResource(R.string.text_font))
        TinySwitchSettingItem(
            title = stringResource(R.string.apply_header_style),
            checked = config.applyHeaderStyle,
            imageVector = Icons.Default.CopyAll,
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
                title = stringResource(R.string.font_size),
                value = config.footerFontSize.toFloat(),
                valueRange = 0f..100f,
                imageVector = Icons.Default.FormatSize,
                onValueChange = { value ->
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.FooterFontSize(value.toInt())))
                    onIntent(ReadBookIntent.SaveReadStyleConfig)
                },
            )
            TinyColorModeSettingItem(
                title = stringResource(R.string.color),
                dayColor = if (config.tipFooterColor != 0) config.tipFooterColor else config.textColorDay,
                nightColor = if (config.tipFooterColorNight != 0) config.tipFooterColorNight else config.textColorNight,
                imageVector = Icons.Default.FormatColorText,
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
    page: Int,
    modifier: Modifier = Modifier,
) {
    // Body padding state
    var paddingTop by remember(config.paddingTop) { mutableFloatStateOf(config.paddingTop.toFloat()) }
    var paddingBottom by remember(config.paddingBottom) { mutableFloatStateOf(config.paddingBottom.toFloat()) }
    var paddingLeft by remember(config.paddingLeft) { mutableFloatStateOf(config.paddingLeft.toFloat()) }
    var paddingRight by remember(config.paddingRight) { mutableFloatStateOf(config.paddingRight.toFloat()) }

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
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp),
    ) {
        when (page) {
            0 -> PaddingSliders(
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

            1 -> PaddingSliders(
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

            else -> PaddingSliders(
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
        }

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
        selectedFontPath = config.headerFont,
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
        selectedFontPath = config.footerFont,
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
    config: ReadSheetConfigUiState,
    onDismiss: () -> Unit,
    onIntent: (ReadBookIntent) -> Unit,
) {
    val initialTemplate = target.customTemplateOf(config)

    CustomTipDialog(
        show = true,
        initialTemplate = initialTemplate,
        onConfirm = { template ->
            applyTipValue(target, ReadTipType.tipCustom, onIntent)
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

/**
 * 页眉页脚每个位置的可选项取值。**顺序必须与 `R.array.read_tip` 一致**——
 * 下拉项的显示名从那个数组取、取值从这里取，两者按下标配对。
 *
 * 放在唯一消费方旁边而不是 `ReadBookConfig` 里：它是静态选项表，不是配置状态，
 * 留在那个全局 object 上会让「弹层不得直读 ReadBookConfig」的护栏被迫开白名单。
 */
private val tipTypeValues = with(ReadTipType) {
    arrayOf(
        tipNone, tipBookName, tipChapterTitle, tipChapterTitleArrow, tipChapterTitleArrowClassic,
        tipTime, tipBattery, tipBatteryClassic, tipBatteryInside, tipBatteryIcon,
        tipBatteryPercentage, tipPage, tipTotalProgress, tipTotalProgress1, tipPageAndTotal,
        tipTimeBattery, tipTimeBatteryClassic, tipTimeBatteryPercentage, tipWholeBookPage,
        tipWholeBookPageAndProgress, tipCustom
    )
}

private fun headerModes(context: Context): LinkedHashMap<Int, String> = linkedMapOf(
    Pair(0, context.getString(R.string.hide_when_status_bar_show)),
    Pair(1, context.getString(R.string.show)),
    Pair(2, context.getString(R.string.hide))
)

private fun footerModes(context: Context): LinkedHashMap<Int, String> = linkedMapOf(
    Pair(0, context.getString(R.string.show)),
    Pair(1, context.getString(R.string.hide))
)
