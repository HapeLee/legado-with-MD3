package io.legado.app.ui.book.read

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FormatColorFill
import androidx.compose.material.icons.outlined.FormatColorText
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.Highlight
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.StrikethroughS
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.legado.app.R
import io.legado.app.data.entities.HighlightRule
import io.legado.app.domain.model.MarkingEffect
import io.legado.app.domain.model.TextProcessStyle
import io.legado.app.ui.book.read.sheet.MarkingPresetColors
import io.legado.app.ui.book.read.sheet.labelRes
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ProvideAppDensity
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.button.series.SmallOutlinedButton
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.dialog.ColorPickerSheet
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import kotlinx.coroutines.delay

@Composable
fun MarkingSelectionMenu(
    menuState: TextMenuState,
    state: MarkingUiState,
    lastMarkingStyle: String,
    onDismiss: () -> Unit,
    onApply: (TextProcessStyle, String) -> Unit,
    onDelete: () -> Unit,
) {
    val density = LocalDensity.current
    val windowSize = LocalWindowInfo.current.containerSize
    val width = with(density) { windowSize.width.toDp() } - 32.dp
    val safeInsets = WindowInsets.safeDrawing
    val layoutDirection = LocalLayoutDirection.current
    // 使用宿主窗口的原始 Insets；Popup 自身的局部窗口不能作为 IME 高度的基准。
    // 在测量和定位阶段读取，让约束与位置跟随同一帧的键盘动画，且不重复扣减可见窗口高度。
    val availableBounds = {
        IntRect(
            left = safeInsets.getLeft(density, layoutDirection),
            top = safeInsets.getTop(density),
            right = windowSize.width - safeInsets.getRight(density, layoutDirection),
            bottom = windowSize.height - safeInsets.getBottom(density),
        )
    }
    // 同一次选区的展开方向固定，输入增长/键盘显隐只做边界夹取，不重新上下翻转。
    val opensBelow = remember(menuState, density.density) {
        windowSize.height - menuState.endBottomY >
            with(density) { MARKING_MENU_ESTIMATED_HEIGHT.toPx() }
    }
    val transformOrigin = TransformOrigin(0.5f, if (opensBelow) 0f else 1f)
    val shadowPadding = 12.dp
    val positionProvider =
        remember(menuState, density.density, windowSize, safeInsets, layoutDirection) {
        TextMenuPositionProvider(
            density = density.density,
            startX = menuState.startX,
            startTopY = menuState.startTopY,
            startBottomY = menuState.startBottomY,
            endX = menuState.endX,
            endBottomY = menuState.endBottomY,
            shadowPadding = with(density) { shadowPadding.roundToPx() },
            preferBelow = opensBelow,
            availableBounds = availableBounds,
        )
        }
    var targetY by remember(menuState) { mutableStateOf<Int?>(null) }
    // 首次测量直接落位，保留原有缩放入场；只有后续位置变化才做纵向动画。
    val animatedY = targetY?.let { target ->
        animateIntAsState(
            targetValue = target,
            animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
            label = "MarkingMenuPositionY",
        )
    }
    val currentAnimatedY by rememberUpdatedState(animatedY)
    val shadowPaddingPx = with(density) { shadowPadding.roundToPx() }
    val animatedPositionProvider = remember(positionProvider, shadowPaddingPx) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val target = positionProvider.calculatePosition(
                    anchorBounds, windowSize, layoutDirection, popupContentSize,
                )
                // AnimatedVisibility 初始可能只测得阴影留白，不能用它初始化位移动画。
                if (popupContentSize.height <= shadowPaddingPx * 2) return target
                targetY = target.y
                // 在定位阶段读动画值，逐帧移动整个 Popup，绘制与点击区域保持一致。
                val y = currentAnimatedY?.value ?: target.y
                // 只约束最终目标；IME 收紧可用空间时也完整播放位移动画。
                return target.copy(y = y)
            }
        }
    }

    val storedStyle = remember(state.editing?.styleJson, lastMarkingStyle) {
        GSON.fromJsonObject<TextProcessStyle>(state.editing?.styleJson ?: lastMarkingStyle)
            .getOrNull() ?: MarkingEffect.BG.toStyle(MarkingEffect.DEFAULT_COLOR)
    }
    var style by remember(state.selection, state.editing?.id, storedStyle) {
        mutableStateOf(
            storedStyle
        )
    }
    var selectedEffect by remember(state.selection, state.editing?.id, storedStyle) {
        mutableStateOf(MarkingEffect.fromStyle(storedStyle))
    }
    var selectedColor by remember(state.selection, state.editing?.id, storedStyle) {
        mutableStateOf(MarkingEffect.colorOf(storedStyle))
    }
    var useRules by remember(state.selection, state.editing?.id) { mutableStateOf(false) }
    var selectedRuleId by remember(state.selection, state.editing?.id) {
        mutableStateOf<String?>(
            null
        )
    }
    var note by remember(
        state.selection,
        state.editing?.id
    ) { mutableStateOf(state.editing?.note.orEmpty()) }
    var noteInitialized by remember(state.selection, state.editing?.id) { mutableStateOf(false) }
    var noteDirty by remember(state.selection, state.editing?.id) { mutableStateOf(false) }
    var showColorPicker by remember(state.selection, state.editing?.id) { mutableStateOf(false) }
    // 打开取色器的起始色：点尾部按钮用当前色，长按预设色则用被长按的色做基准微调
    var colorPickerSeed by remember(state.selection, state.editing?.id) {
        mutableStateOf(selectedColor)
    }
    var menuVisible by remember(menuState) { mutableStateOf(false) }
    var closing by remember(menuState) { mutableStateOf(false) }

    fun requestDismiss() {
        if (!closing) {
            closing = true
            menuVisible = false
        }
    }

    LaunchedEffect(menuState) { menuVisible = true }
    LaunchedEffect(closing) {
        if (closing) {
            delay(SelectionMenuMotion.EXIT_DURATION_MILLIS.toLong())
            onDismiss()
        }
    }

    LaunchedEffect(state.loading, state.editing?.id) {
        if (!state.loading) {
            note = state.editing?.note.orEmpty()
            noteInitialized = true
            if (state.editing == null && state.selection != null) onApply(style, note)
        }
    }
    LaunchedEffect(note, noteInitialized, noteDirty, style) {
        if (noteInitialized && noteDirty && !state.loading) {
            delay(450)
            onApply(style, note)
            noteDirty = false
        }
    }
    LaunchedEffect(storedStyle, state.highlightRules) {
        state.highlightRules.firstOrNull { it.toProcessStyle() == storedStyle }?.let {
            useRules = true
            selectedRuleId = it.id
        }
    }

    Popup(
        popupPositionProvider = animatedPositionProvider,
        onDismissRequest = {
            if (!showColorPicker) requestDismiss()
        },
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        ProvideAppDensity {
            Box(
                Modifier
                    .layout { measurable, constraints ->
                        val bounds = availableBounds()
                        val shadow = shadowPadding.roundToPx() * 2
                        val placeable = measurable.measure(
                            constraints.copy(
                                minWidth = 0,
                                minHeight = 0,
                                maxWidth = (bounds.width - 32.dp.roundToPx() + shadow)
                                    .coerceIn(0, constraints.maxWidth),
                                maxHeight = (bounds.height - 24.dp.roundToPx() + shadow)
                                    .coerceIn(0, constraints.maxHeight),
                            )
                        )
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    }
                    .padding(shadowPadding)
            ) {
                AnimatedVisibility(
                    visible = menuVisible,
                    enter = SelectionMenuMotion.enter(transformOrigin),
                    exit = SelectionMenuMotion.exit(transformOrigin),
                ) {
                    NormalCard(
                        modifier = Modifier.width(width),
                        containerColor = LegadoTheme.colorScheme.surfaceBright,
                        elevation = 10.dp,
                        cornerRadius = 16.dp,
                    ) {
                        Column(
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 10.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            MarkingColorRow(
                                selectedColor = selectedColor,
                                useRules = useRules,
                                enabled = !state.loading,
                                onToggleRules = { useRules = !useRules },
                                onColorSelected = { color ->
                                    selectedColor = color
                                    useRules = false
                                    style = selectedEffect.toStyle(color)
                                    onApply(style, note)
                                },
                                onColorLongPress = { color ->
                                    colorPickerSeed = color
                                    showColorPicker = true
                                },
                                onCustomColor = {
                                    colorPickerSeed = selectedColor
                                    showColorPicker = true
                                },
                            )

                            if (useRules) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(state.highlightRules, key = { it.id }) { rule ->
                                        MarkingRuleTile(
                                            rule = rule,
                                            selected = selectedRuleId == rule.id,
                                            onClick = {
                                                selectedRuleId = rule.id
                                                style = rule.toProcessStyle()
                                                onApply(style, note)
                                            },
                                        )
                                    }
                                }
                            } else {
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    items(MarkingEffect.entries, key = { it.name }) { effect ->
                                        MarkingEffectTile(
                                            effect = effect,
                                            selected = selectedEffect == effect,
                                            onClick = {
                                                selectedEffect = effect
                                                style = effect.toStyle(selectedColor)
                                                onApply(style, note)
                                            },
                                        )
                                    }
                                }
                            }

                            AppTextField(
                                value = note,
                                onValueChange = {
                                    note = it
                                    noteDirty = true
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 88.dp),
                                placeholder = { AppText(stringResource(R.string.bookmark_mark_note_hint)) },
                            )

                            if (state.editing != null) {
                                SmallOutlinedButton(
                                    modifier = Modifier
                                        .align(Alignment.End),
                                    icon = Icons.Outlined.Delete,
                                    text = stringResource(R.string.bookmark_mark_delete_note),
                                    contentDescription = null,
                                    onClick = onDelete
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    ColorPickerSheet(
        show = showColorPicker,
        initialColor = colorPickerSeed,
        onDismissRequest = { showColorPicker = false },
        onColorSelected = { color ->
            selectedColor = color
            useRules = false
            selectedRuleId = null
            style = selectedEffect.toStyle(color)
            onApply(style, note)
        },
    )
}

/**
 * 预设颜色行：点按选中，长按以该色为基准打开取色器微调（与尾部自定义色按钮一致）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MarkingColorRow(
    selectedColor: Int,
    useRules: Boolean,
    enabled: Boolean,
    onToggleRules: () -> Unit,
    onColorSelected: (Int) -> Unit,
    onColorLongPress: (Int) -> Unit,
    onCustomColor: () -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "source") {
            RoundIconButton(
                selected = useRules,
                enabled = true,
                icon = Icons.Outlined.Style,
                contentDescription = stringResource(R.string.bookmark_mark_reuse_rule),
                onClick = onToggleRules,
            )
        }
        items(MarkingPresetColors, key = { it }) { color ->
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .combinedClickable(
                        enabled = enabled,
                        onClick = { onColorSelected(color) },
                        onLongClick = { onColorLongPress(color) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (color == selectedColor && !useRules) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = if (Color(color).luminance() > 0.5f) Color.Black else Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        item(key = "custom") {
            RoundIconButton(
                selected = false,
                enabled = enabled,
                icon = Icons.Outlined.Colorize,
                contentDescription = stringResource(R.string.bookmark_mark_custom_color),
                onClick = onCustomColor,
            )
        }
    }
}

@Composable
private fun RoundIconButton(
    selected: Boolean,
    enabled: Boolean,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(
                if (selected) LegadoTheme.colorScheme.secondaryContainer else Color.Transparent
            )
            .border(1.dp, LegadoTheme.colorScheme.outlineVariant, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(15.dp),
        )
    }
}

@Composable
private fun MarkingEffectTile(
    effect: MarkingEffect,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) LegadoTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        MarkingEffectIcon(effect = effect)
        AppText(
            text = stringResource(effect.labelRes()),
            style = LegadoTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@Composable
private fun MarkingEffectIcon(effect: MarkingEffect) {
    val foreground = LegadoTheme.colorScheme.onSurface
    when (effect) {
        MarkingEffect.SOLID -> Icon(
            imageVector = Icons.Outlined.FormatUnderlined,
            contentDescription = null,
            modifier = Modifier.size(23.dp),
        )

        MarkingEffect.BG -> Icon(
            imageVector = Icons.Outlined.FormatColorFill,
            contentDescription = null,
            tint = foreground,
            modifier = Modifier.size(23.dp),
        )

        MarkingEffect.TEXT -> Icon(
            imageVector = Icons.Outlined.FormatColorText,
            contentDescription = null,
            tint = foreground,
            modifier = Modifier.size(23.dp),
        )

        MarkingEffect.WAVE -> Icon(
            imageVector = Icons.Outlined.Waves,
            contentDescription = null,
            tint = foreground,
            modifier = Modifier.size(23.dp),
        )

        MarkingEffect.DASHED -> Icon(
            imageVector = Icons.Outlined.MoreHoriz,
            contentDescription = null,
            tint = foreground,
            modifier = Modifier.size(23.dp),
        )

        MarkingEffect.STRIKE -> Icon(
            imageVector = Icons.Outlined.StrikethroughS,
            contentDescription = null,
            modifier = Modifier.size(23.dp),
        )

        MarkingEffect.HIGHLIGHT -> Icon(
            imageVector = Icons.Outlined.Highlight,
            contentDescription = null,
            modifier = Modifier.size(23.dp),
        )
    }
}

@Composable
private fun MarkingRuleTile(rule: HighlightRule, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) LegadoTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    Color(
                        rule.underlineColor ?: rule.textColor ?: rule.bgColor
                        ?: MarkingEffect.DEFAULT_COLOR
                    )
                )
        )
        Spacer(Modifier.height(4.dp))
        AppText(
            rule.name.ifBlank { rule.displayPattern() },
            style = LegadoTheme.typography.labelSmall
        )
    }
}

private fun HighlightRule.toProcessStyle() = TextProcessStyle(
    textColor = textColor,
    bgColor = bgColor,
    underlineMode = underlineMode,
    underlineColor = underlineColor,
    underlineWidth = underlineWidth,
    underlineOffset = underlineOffset,
    underlineSvgPath = underlineSvgPath,
)

/**
 * 标记卡（颜色行 + 效果格 + 备注框）的常规高度估算，含选择柄与间距余量。
 * 用于固定本次展开方向；卡片实际位置仍按当前可见区域夹取。
 */
private val MARKING_MENU_ESTIMATED_HEIGHT = 240.dp
