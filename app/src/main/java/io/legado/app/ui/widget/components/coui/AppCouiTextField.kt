package io.legado.app.ui.widget.components.coui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Density
import com.itos.coui.textfield.CouiTextFieldColors
import com.itos.coui.textfield.CouiTextFieldDefaults
import com.itos.coui.theme.CouiTheme
import io.legado.app.ui.widget.components.mergeFieldSlots
import com.itos.coui.textfield.TextField as CouiTextField

/**
 * COUI 原生输入框的共享适配。
 *
 * 映射宿主组件库与 COUI 都具备的字段：受控值 / [TextFieldState]、标签与占位提示、禁用与只读、
 * 单行与行数、前后图标槽、键盘与输入转换、文本排版回调、多行滚动状态、卡片轮廓与内留白。
 *
 * `prefix` / `suffix` 没有独立槽位，合成进 `leading` / `trailing`（COUI 的装饰行是无固定宽度的
 * Row），视觉顺序与宿主语义一致：图标 → 前缀 → 输入 → 后缀 → 尾图标。
 *
 * COUI 输入框没有错误态与卡片下方的辅助文字，`isError` / `supportingText` 因此静默丢弃，
 * 与 Miuix 引擎的现状保持一致；需要错误提示的调用点应自行在卡片外补充。
 *
 * [placeholderText] / [label] 是纯文本：COUI 的标签与占位提示和输入共用同一排版，
 * 宿主侧 `placeholder` 是可组合内容，无法在 COUI 中复用。
 */
@Composable
internal fun AppCouiTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    backgroundColor: Color = Color.Unspecified,
    label: String? = null,
    placeholderText: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    prefix: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    inputTransformation: InputTransformation? = null,
    outputTransformation: OutputTransformation? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.Default,
    onTextLayout: (Density.(getResult: () -> TextLayoutResult?) -> Unit)? = null,
    scrollState: ScrollState = rememberScrollState(),
    shape: Shape = CouiTextFieldDefaults.shape,
    contentPadding: PaddingValues? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    val dark = CouiTheme.isDark
    val singleLine = lineLimits is TextFieldLineLimits.SingleLine
    val multiLine = lineLimits as? TextFieldLineLimits.MultiLine
    val minLines = multiLine?.minHeightInLines ?: 1
    val maxLines = when {
        multiLine == null -> 1
        // COUI 会在组合期校验 maxLines >= minLines；调用方的非法组合按下限收敛，不把异常抛给页面。
        else -> multiLine.maxHeightInLines.coerceAtLeast(minLines)
    }
    CouiTextField(
        modifier = modifier,
        state = state,
        label = label,
        placeholder = placeholderText,
        leading = mergeFieldSlots(leadingIcon, prefix),
        trailing = mergeFieldSlots(suffix, trailingIcon),
        enabled = enabled,
        readOnly = readOnly,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        dark = dark,
        colors = couiTextFieldColors(dark, backgroundColor),
        shape = shape,
        contentPadding = contentPadding ?: CouiTextFieldDefaults.contentPadding,
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
        inputTransformation = inputTransformation,
        outputTransformation = outputTransformation,
        onTextLayout = onTextLayout,
        scrollState = scrollState,
        interactionSource = interactionSource,
    )
}

@Composable
internal fun AppCouiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    backgroundColor: Color = Color.Unspecified,
    label: String? = null,
    placeholderText: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    prefix: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    shape: Shape = CouiTextFieldDefaults.shape,
    contentPadding: PaddingValues? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    val dark = CouiTheme.isDark
    CouiTextField(
        modifier = modifier,
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = placeholderText,
        leading = mergeFieldSlots(leadingIcon, prefix),
        trailing = mergeFieldSlots(suffix, trailingIcon),
        enabled = enabled,
        readOnly = readOnly,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = if (singleLine) 1 else maxLines.coerceAtLeast(minLines),
        dark = dark,
        colors = couiTextFieldColors(dark, backgroundColor),
        shape = shape,
        contentPadding = contentPadding ?: CouiTextFieldDefaults.contentPadding,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        interactionSource = interactionSource,
    )
}

/** 卡片底色与同页分组卡片同源；宿主显式指定底色时只覆盖 `container`，其余角色保持主题解析结果。 */
@Composable
private fun couiTextFieldColors(dark: Boolean, backgroundColor: Color): CouiTextFieldColors {
    val colors = CouiTextFieldDefaults.colors(dark)
    return if (backgroundColor == Color.Unspecified) colors else colors.copy(container = backgroundColor)
}
