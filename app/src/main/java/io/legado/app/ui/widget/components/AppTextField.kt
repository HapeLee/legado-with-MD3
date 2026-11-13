package io.legado.app.ui.widget.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.ComposeEngine
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.coui.AppCouiTextField
import io.legado.app.ui.widget.components.text.AppText
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.InputField as MiuixSearchBarInputField
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults as MiuixTextFieldDefaults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    backgroundColor: Color = Color.Unspecified,
    label: String? = null,
    labelPosition: TextFieldLabelPosition = TextFieldLabelPosition.Inside(),
    placeholder: @Composable (() -> Unit)? = null,
    placeholderText: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    inputTransformation: InputTransformation? = null,
    outputTransformation: OutputTransformation? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.Default,
    onTextLayout: (Density.(getResult: () -> TextLayoutResult?) -> Unit)? = null,
    scrollState: ScrollState = rememberScrollState(),
    shape: Shape = TextFieldDefaults.shape,
    contentPadding: PaddingValues? = null,
    interactionSource: MutableInteractionSource? = null,
) {

    // COUI / Miuix 都是「纯文本卡片」：没有 error 角色与框下辅助文字，isError / supportingText
    // 静默丢弃；需要错误提示的调用点自己画在卡片外。其余能力都已在适配层映射，因此不再降级到 Material。
    if (LegadoTheme.composeEngine == ComposeEngine.Coui) {
        AppCouiTextField(
            state = state,
            modifier = modifier,
            enabled = enabled,
            readOnly = readOnly,
            backgroundColor = backgroundColor,
            label = label,
            placeholderText = placeholderText,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            prefix = prefix,
            suffix = suffix,
            inputTransformation = inputTransformation,
            outputTransformation = outputTransformation,
            keyboardOptions = keyboardOptions,
            onKeyboardAction = onKeyboardAction,
            lineLimits = lineLimits,
            onTextLayout = onTextLayout,
            scrollState = scrollState,
            shape = shape,
            contentPadding = contentPadding,
            interactionSource = interactionSource,
        )
        return
    }

    when (LegadoTheme.composeEngine) {
        ComposeEngine.Miuix -> {
            MiuixTextField(
                state = state,
                modifier = modifier,
                enabled = enabled,
                readOnly = readOnly,
                colors = MiuixTextFieldDefaults.textFieldColors(
                    backgroundColor = if (backgroundColor != Color.Unspecified) {
                        backgroundColor
                    } else {
                        MiuixTheme.colorScheme.surfaceContainerHigh
                    },
                ),
                // Miuix 也没有占位提示槽：只有 label 时把 label 当占位（不浮动），
                // 标签与占位同时存在时 label 正常浮动。
                label = label ?: placeholderText.orEmpty(),
                useLabelAsPlaceholder = label == null && placeholderText != null,
                leadingIcon = mergeFieldSlots(leadingIcon, prefix),
                trailingIcon = mergeFieldSlots(suffix, trailingIcon),
                inputTransformation = inputTransformation,
                outputTransformation = outputTransformation,
                keyboardOptions = keyboardOptions,
                onKeyboardAction = onKeyboardAction,
                lineLimits = lineLimits,
                onTextLayout = onTextLayout,
                scrollState = scrollState,
                interactionSource = interactionSource
            )
        }
        ComposeEngine.Material, ComposeEngine.Mochi, ComposeEngine.Coui -> {
            val resolvedContentPadding =
                contentPadding ?: if (label == null || labelPosition is TextFieldLabelPosition.Above) {
                    TextFieldDefaults.contentPaddingWithoutLabel()
                } else {
                    TextFieldDefaults.contentPaddingWithLabel()
                }

            val resolvedColors = if (backgroundColor != Color.Unspecified) {
                TextFieldDefaults.colors(
                    focusedContainerColor = backgroundColor,
                    unfocusedContainerColor = backgroundColor,
                    disabledContainerColor = backgroundColor,
                    errorContainerColor = backgroundColor,
                    focusedIndicatorColor = if (backgroundColor == Color.Transparent) Color.Transparent else TextFieldDefaults.colors().focusedIndicatorColor,
                    unfocusedIndicatorColor = if (backgroundColor == Color.Transparent) Color.Transparent else TextFieldDefaults.colors().unfocusedIndicatorColor,
                    disabledIndicatorColor = if (backgroundColor == Color.Transparent) Color.Transparent else TextFieldDefaults.colors().disabledIndicatorColor,
                )
            } else {
                TextFieldDefaults.colors()
            }

            TextField(
                state = state,
                modifier = modifier,
                enabled = enabled,
                readOnly = readOnly,
                labelPosition = labelPosition,
                label = label?.let { { AppText(it) } },
                placeholder = placeholder,
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon,
                prefix = prefix,
                suffix = suffix,
                supportingText = supportingText,
                isError = isError,
                inputTransformation = inputTransformation,
                outputTransformation = outputTransformation,
                keyboardOptions = keyboardOptions,
                onKeyboardAction = onKeyboardAction,
                lineLimits = lineLimits,
                onTextLayout = onTextLayout,
                scrollState = scrollState,
                shape = shape,
                colors = resolvedColors,
                contentPadding = resolvedContentPadding,
                interactionSource = interactionSource
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    backgroundColor: Color = Color.Unspecified,
    label: String? = null,
    placeholder: @Composable (() -> Unit)? = null,
    placeholderText: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    shape: Shape = TextFieldDefaults.shape,
    interactionSource: MutableInteractionSource? = null,
) {

    if (LegadoTheme.composeEngine == ComposeEngine.Coui) {
        AppCouiTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            readOnly = readOnly,
            backgroundColor = backgroundColor,
            label = label,
            placeholderText = placeholderText,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            prefix = prefix,
            suffix = suffix,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            shape = shape,
            interactionSource = interactionSource,
        )
        return
    }

    when (LegadoTheme.composeEngine) {
        ComposeEngine.Miuix -> {
            MiuixTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = modifier,
                enabled = enabled,
                readOnly = readOnly,
                colors = MiuixTextFieldDefaults.textFieldColors(
                    backgroundColor = if (backgroundColor != Color.Unspecified) {
                        backgroundColor
                    } else {
                        MiuixTheme.colorScheme.surfaceContainerHigh
                    },
                ),
                label = label ?: placeholderText.orEmpty(),
                useLabelAsPlaceholder = label == null && placeholderText != null,
                leadingIcon = mergeFieldSlots(leadingIcon, prefix),
                trailingIcon = mergeFieldSlots(suffix, trailingIcon),
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                singleLine = singleLine,
                maxLines = maxLines,
                minLines = minLines,
                interactionSource = interactionSource
            )
        }
        ComposeEngine.Material, ComposeEngine.Mochi, ComposeEngine.Coui -> {
            val resolvedColors = if (backgroundColor != Color.Unspecified) {
                TextFieldDefaults.colors(
                    focusedContainerColor = backgroundColor,
                    unfocusedContainerColor = backgroundColor,
                    disabledContainerColor = backgroundColor,
                    errorContainerColor = backgroundColor,
                    focusedIndicatorColor = if (backgroundColor == Color.Transparent) Color.Transparent else TextFieldDefaults.colors().focusedIndicatorColor,
                    unfocusedIndicatorColor = if (backgroundColor == Color.Transparent) Color.Transparent else TextFieldDefaults.colors().unfocusedIndicatorColor,
                    disabledIndicatorColor = if (backgroundColor == Color.Transparent) Color.Transparent else TextFieldDefaults.colors().disabledIndicatorColor,
                )
            } else {
                TextFieldDefaults.colors()
            }

            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = modifier,
                enabled = enabled,
                readOnly = readOnly,
                label = label?.let { { AppText(it) } },
                placeholder = placeholder,
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon,
                prefix = prefix,
                suffix = suffix,
                supportingText = supportingText,
                isError = isError,
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                singleLine = singleLine,
                maxLines = maxLines,
                minLines = minLines,
                shape = shape,
                colors = resolvedColors,
                interactionSource = interactionSource
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTextFieldSurface(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    label: String? = null,
    labelPosition: TextFieldLabelPosition = TextFieldLabelPosition.Inside(),
    placeholder: @Composable (() -> Unit)? = null,
    placeholderText: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    inputTransformation: InputTransformation? = null,
    outputTransformation: OutputTransformation? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.Default,
    onTextLayout: (Density.(getResult: () -> TextLayoutResult?) -> Unit)? = null,
    scrollState: ScrollState = rememberScrollState(),
    shape: Shape = TextFieldDefaults.shape,
    contentPadding: PaddingValues? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    AppTextField(
        state = state,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        // COUI 的输入框本身就是卡片（`card` 底色）；再压一层页面 `surface` 会让卡片与正文同色。
        backgroundColor = if (LegadoTheme.composeEngine == ComposeEngine.Coui) {
            Color.Unspecified
        } else {
            LegadoTheme.colorScheme.surface
        },
        label = label,
        labelPosition = labelPosition,
        placeholder = placeholder,
        placeholderText = placeholderText,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        prefix = prefix,
        suffix = suffix,
        supportingText = supportingText,
        isError = isError,
        inputTransformation = inputTransformation,
        outputTransformation = outputTransformation,
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
        lineLimits = lineLimits,
        onTextLayout = onTextLayout,
        scrollState = scrollState,
        shape = shape,
        contentPadding = contentPadding,
        interactionSource = interactionSource
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDenseTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    backgroundColor: Color = Color.Unspecified,
    label: String? = null,
    labelPosition: TextFieldLabelPosition = TextFieldLabelPosition.Inside(),
    placeholder: @Composable (() -> Unit)? = null,
    placeholderText: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    inputTransformation: InputTransformation? = null,
    outputTransformation: OutputTransformation? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.Default,
    onTextLayout: (Density.(getResult: () -> TextLayoutResult?) -> Unit)? = null,
    scrollState: ScrollState = rememberScrollState(),
    shape: Shape = TextFieldDefaults.shape,
    interactionSource: MutableInteractionSource? = null,
    miuixUseSearchBarInputField: Boolean = false,
    miuixSearchBarLabel: String = label ?: "",
    miuixOnSearch: (String) -> Unit = {},
) {
    val isMiuix = LegadoTheme.composeEngine == ComposeEngine.Miuix
    if (isMiuix && miuixUseSearchBarInputField) {
        MiuixSearchBarInputField(
            query = state.text.toString(),
            onQueryChange = { newQuery ->
                val current = state.text.toString()
                if (newQuery != current) {
                    state.edit {
                        replace(0, length, newQuery)
                    }
                }
            },
            onSearch = miuixOnSearch,
            expanded = false,
            onExpandedChange = {},
            modifier = modifier.heightIn(min = 45.dp),
            label = miuixSearchBarLabel,
            enabled = enabled,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            interactionSource = interactionSource
        )
        return
    }

    val isCoui = LegadoTheme.composeEngine == ComposeEngine.Coui
    val denseMinHeight = if (isMiuix) 45.dp else 48.dp
    val denseBackgroundColor = if (isMiuix && backgroundColor == Color.Unspecified) {
        MiuixTheme.colorScheme.surfaceContainerHigh
    } else {
        backgroundColor
    }

    AppTextField(
        state = state,
        modifier = modifier.heightIn(min = denseMinHeight),
        enabled = enabled,
        readOnly = readOnly,
        backgroundColor = denseBackgroundColor,
        label = label,
        labelPosition = labelPosition,
        placeholder = placeholder,
        placeholderText = placeholderText,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        prefix = prefix,
        suffix = suffix,
        supportingText = supportingText,
        isError = isError,
        inputTransformation = inputTransformation,
        outputTransformation = outputTransformation,
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
        lineLimits = lineLimits,
        onTextLayout = onTextLayout,
        scrollState = scrollState,
        shape = shape,
        // COUI 的紧凑稿是「52dp 最小高度 + 页面行留白」：M3 的 4/12dp 内留白会把原生卡片压扁。
        contentPadding = if (isCoui) null else PaddingValues(top = 4.dp, bottom = 4.dp, start = 12.dp, end = 12.dp),
        interactionSource = interactionSource
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTextFieldSurface(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    label: String? = null,
    placeholder: @Composable (() -> Unit)? = null,
    placeholderText: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    shape: Shape = TextFieldDefaults.shape,
    interactionSource: MutableInteractionSource? = null,
) {
    AppTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        // COUI 的输入框本身就是卡片（`card` 底色）；再压一层页面 `surface` 会让卡片与正文同色。
        backgroundColor = if (LegadoTheme.composeEngine == ComposeEngine.Coui) {
            Color.Unspecified
        } else {
            LegadoTheme.colorScheme.surface
        },
        label = label,
        placeholder = placeholder,
        placeholderText = placeholderText,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        prefix = prefix,
        suffix = suffix,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        shape = shape,
        interactionSource = interactionSource
    )
}

/**
 * 把「图标槽 + 文字槽」合成一个槽。
 *
 * COUI 与 Miuix 的输入框都只有 leading / trailing 两个位置：`prefix` / `suffix` 只能塞进同一行，
 * 顺序由调用顺序决定（前缀在输入前、后缀在输入后），间距固定 4dp 以免文字贴住图标。
 * 只有一个非空时直接复用，不额外包一层 Row。
 */
@Composable
internal fun mergeFieldSlots(
    before: (@Composable () -> Unit)?,
    after: (@Composable () -> Unit)?,
): (@Composable () -> Unit)? = when {
    before == null -> after
    after == null -> before
    else -> {
        {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                before()
                after()
            }
        }
    }
}
