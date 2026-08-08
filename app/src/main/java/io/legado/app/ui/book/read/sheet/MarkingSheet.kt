package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.HighlightRule
import io.legado.app.domain.model.MarkingEffect
import io.legado.app.domain.model.TextProcessAnchor
import io.legado.app.domain.model.TextProcessStyle
import io.legado.app.ui.book.read.MarkingUiState
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.dialog.ColorPickerSheet
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.progressIndicator.AppCircularProgressIndicator
import io.legado.app.ui.widget.components.settingItem.TinyDropdownSettingItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject

/**
 * 划线/高亮笔记配置 Sheet。
 *
 * 样式来源两种：复用用户已有的一条高亮规则（[MarkingUiState.highlightRules]），
 * 或直接自定义本次的样式——上面一行预设颜色（尾部为自定义颜色），下面 5x1 效果格
 * （单实线/波浪线/虚线/背景色/字体色）。背景色自动加 ~20% 透明度。另带备注输入。
 * 两种进入方式：
 * - 新增：选中文本后点「划线」（[MarkingUiState.selection]）；
 * - 编辑：再次选中同一段文本，或从目录 Sheet 点标记项
 *   （[MarkingUiState.editing] 非空），预填样式与备注，保存即更新；编辑模式可删除。
 */
@Composable
fun MarkingSheet(
    show: Boolean,
    state: MarkingUiState,
    onDismissRequest: () -> Unit,
    onSave: (style: TextProcessStyle, note: String) -> Unit,
    onDelete: () -> Unit,
) {
    val selection = state.selection
    val editing = state.editing

    // 编辑模式无选中文本时，预览取标记锚点里的原文。
    val editingAnchorText = remember(editing) {
        editing?.anchorJson
            ?.let { GSON.fromJsonObject<TextProcessAnchor>(it).getOrNull() }
            ?.selectedText
            ?: ""
    }

    // 状态提升到 Sheet 顶层：底部 ColorPickerSheet 与内容共用；以 show + editing 为键，
    // 每次打开/切到编辑模式时重置（编辑模式的样式/颜色/备注来自已有标记）。
    val editingStyle = remember(editing) {
        editing?.styleJson?.let { GSON.fromJsonObject<TextProcessStyle>(it).getOrNull() }
    }
    // 新建默认走「复用规则」，编辑已有标记时默认走自定义以展示实际存储的样式
    var useRule by remember(show, editing) { mutableStateOf(editing == null) }
    var selectedRuleId by remember(show, editing) { mutableStateOf<String?>(null) }
    // 5x1 效果格 + 选中颜色：编辑模式从已有标记反推
    var effect by remember(show, editing) {
        mutableStateOf(MarkingEffect.fromStyle(editingStyle))
    }
    var markColor by remember(show, editing) {
        mutableStateOf(MarkingEffect.colorOf(editingStyle))
    }
    // 隐藏透传：编辑下划线类标记时保留已有宽度/偏移/SVG（自定义模式没有这些控件）
    var underlineWidth by remember(show, editing) {
        mutableStateOf(editingStyle?.underlineWidth ?: 1f)
    }
    var underlineOffset by remember(show, editing) {
        mutableStateOf(editingStyle?.underlineOffset ?: 2f)
    }
    var underlineSvgPath by remember(show, editing) {
        mutableStateOf(editingStyle?.underlineSvgPath)
    }
    var showColorPicker by remember(show, editing) { mutableStateOf(false) }
    val noteState = key(show, editing) {
        rememberTextFieldState(initialText = editing?.note ?: "")
    }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = selection?.bookText?.takeIf { it.isNotBlank() } ?: editingAnchorText,
        startAction = {
            if (editing != null) {
                MediumTonalButton(
                    onClick = onDelete,
                    icon = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete)
                )
            }
        },
        endAction = {
            MediumTonalButton(
                onClick = {
                    val style = buildStyle(
                        useRule = useRule,
                        selectedRule = state.highlightRules.firstOrNull { it.id == selectedRuleId },
                        effect = effect,
                        markColor = markColor,
                        underlineWidth = underlineWidth,
                        underlineOffset = underlineOffset,
                        underlineSvgPath = underlineSvgPath,
                    )
                    onSave(style, noteState.text.toString())
                },
                icon = Icons.Default.Save,
                contentDescription = stringResource(android.R.string.ok)
            )
        }
    ) {
        if (selection == null && editing == null) {
            // 编辑模式异步加载标记期间的占位，避免先空再弹内容
            if (state.loading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppCircularProgressIndicator()
                }
            }
            return@AppModalBottomSheet
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TinyDropdownSettingItem(
                title = stringResource(R.string.bookmark_mark_style_source),
                selectedValue = if (useRule) STYLE_SOURCE_RULE else STYLE_SOURCE_CUSTOM,
                displayEntries = arrayOf(
                    stringResource(R.string.bookmark_mark_reuse_rule),
                    stringResource(R.string.bookmark_mark_custom),
                ),
                entryValues = arrayOf(STYLE_SOURCE_RULE, STYLE_SOURCE_CUSTOM),
                onValueChange = { useRule = it == STYLE_SOURCE_RULE },
            )

            if (useRule) {
                val rules = state.highlightRules
                if (rules.isEmpty()) {
                    EmptyMessage(
                        message = stringResource(R.string.bookmark_mark_no_rules),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                    )
                } else {
                    rules.forEach { rule ->
                        val selected = selectedRuleId == rule.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedRuleId = if (selected) null else rule.id
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = if (selected) {
                                    LegadoTheme.colorScheme.primary
                                } else {
                                    LegadoTheme.colorScheme.outlineVariant
                                },
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                AppText(
                                    text = rule.name.ifBlank { rule.displayPattern() },
                                    style = LegadoTheme.typography.bodyMedium,
                                )
                                AppText(
                                    text = rule.styleSummary(),
                                    style = LegadoTheme.typography.labelSmall,
                                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            } else {
                // 自定义：预设颜色行（尾部自定义色）+ 5x1 效果格
                MarkingColorRow(
                    selectedColor = markColor,
                    onColorSelected = { markColor = it },
                    onCustomColorClick = { showColorPicker = true },
                )
                MarkingEffectGrid(
                    selectedEffect = effect,
                    onEffectSelected = { effect = it },
                )
            }

            Spacer(Modifier.padding(top = 16.dp))
            // 备注（笔记）
            AppTextField(
                state = noteState,
                modifier = Modifier
                    .fillMaxWidth(),
                label = stringResource(R.string.bookmark_mark_note),
                placeholder = {
                    AppText(stringResource(R.string.bookmark_mark_note_hint))
                },
            )
        }
    }

    ColorPickerSheet(
        show = showColorPicker,
        initialColor = markColor,
        onDismissRequest = { showColorPicker = false },
        onColorSelected = { color ->
            markColor = color
            showColorPicker = false
        },
    )
}

/** 自定义样式区：预设颜色行（尾部为自定义颜色，打开取色器）。 */
@Composable
private fun MarkingColorRow(
    selectedColor: Int,
    onColorSelected: (Int) -> Unit,
    onCustomColorClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MarkingPresetColors.forEach { color ->
            MarkingColorSwatch(
                color = color,
                selected = color == selectedColor,
                onClick = { onColorSelected(color) },
            )
        }
        // 尾部自定义颜色：未选自定义色时显示取色图标，选中后显示该色并高亮
        val isCustom = selectedColor !in MarkingPresetColors
        MarkingColorSwatch(
            color = if (isCustom) selectedColor else null,
            selected = isCustom,
            onClick = onCustomColorClick,
            custom = true,
        )
    }
}

@Composable
private fun MarkingColorSwatch(
    color: Int?,
    selected: Boolean,
    onClick: () -> Unit,
    custom: Boolean = false,
) {
    val borderColor = if (selected) {
        LegadoTheme.colorScheme.primary
    } else {
        LegadoTheme.colorScheme.outlineVariant
    }
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(
                if (color != null) Color(color) else LegadoTheme.colorScheme.surfaceContainerHigh
            )
            .border(2.dp, borderColor, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (custom && color == null) {
            Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = stringResource(R.string.bookmark_mark_custom_color),
                tint = LegadoTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** 5x1 效果格：单实线 / 波浪线 / 虚线 / 背景色 / 字体色。 */
@Composable
private fun MarkingEffectGrid(
    selectedEffect: MarkingEffect,
    onEffectSelected: (MarkingEffect) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MarkingEffect.entries.forEach { entry ->
            val selected = entry == selectedEffect
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selected) {
                            LegadoTheme.colorScheme.secondaryContainer
                        } else {
                            LegadoTheme.colorScheme.surfaceContainer
                        }
                    )
                    .clickable { onEffectSelected(entry) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                AppText(
                    text = stringResource(entry.labelRes()),
                    style = LegadoTheme.typography.labelMediumEmphasized,
                    color = if (selected) {
                        LegadoTheme.colorScheme.onSecondaryContainer
                    } else {
                        LegadoTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

/** 效果标签：供 MarkingSheet 5x1 效果格与目录 Sheet 笔记项共用。 */
internal fun MarkingEffect.labelRes(): Int = when (this) {
    MarkingEffect.SOLID -> R.string.bookmark_mark_effect_solid
    MarkingEffect.WAVE -> R.string.bookmark_mark_effect_wave
    MarkingEffect.DASHED -> R.string.bookmark_mark_effect_dash
    MarkingEffect.BG -> R.string.bookmark_mark_effect_bg
    MarkingEffect.TEXT -> R.string.bookmark_mark_effect_text
}

/** 从标记的 styleJson 反推效果（目录 Sheet 笔记项展示用）。 */
internal fun MarkingEffect.Companion.fromStyleJson(styleJson: String?): MarkingEffect {
    val style = styleJson?.let { GSON.fromJsonObject<TextProcessStyle>(it).getOrNull() }
    return fromStyle(style)
}

private fun buildStyle(
    useRule: Boolean,
    selectedRule: HighlightRule?,
    effect: MarkingEffect,
    markColor: Int,
    underlineWidth: Float,
    underlineOffset: Float,
    underlineSvgPath: String?,
): TextProcessStyle {
    if (useRule && selectedRule != null) {
        return selectedRule.toProcessStyle()
    }
    val base = effect.toStyle(markColor)
    return if (effect.isUnderline) {
        base.copy(
            underlineWidth = underlineWidth,
            underlineOffset = underlineOffset,
            underlineSvgPath = underlineSvgPath,
        )
    } else {
        base
    }
}

private fun HighlightRule.toProcessStyle(): TextProcessStyle = TextProcessStyle(
    textColor = textColor,
    bgColor = bgColor,
    underlineMode = underlineMode,
    underlineColor = underlineColor,
    underlineWidth = underlineWidth,
    underlineOffset = underlineOffset,
    underlineSvgPath = underlineSvgPath,
)

/** 自定义模式的预设颜色（尾部之外的自定义色用取色器）。 */
private val MarkingPresetColors = listOf(
    0xFFFF5252.toInt(),
    0xFFFF9800.toInt(),
    0xFFFFEB3B.toInt(),
    0xFF4CAF50.toInt(),
    0xFF26A6D6.toInt(),
    0xFF2196F3.toInt(),
    0xFF9C27B0.toInt(),
    0xFFEC407A.toInt(),
    0xFF795548.toInt(),
    0xFF607D8B.toInt(),
)

private const val STYLE_SOURCE_RULE = "rule"
private const val STYLE_SOURCE_CUSTOM = "custom"
