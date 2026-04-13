package io.legado.app.ui.widget.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.text.AppText
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    backgroundColor: Color = Color.Unspecified,
    label: String? = null,
    labelPosition: TextFieldLabelPosition = TextFieldLabelPosition.Attached(),
    placeholder: @Composable (() -> Unit)? = null,
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
    val isMiuix = ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)

    if (isMiuix) {
        MiuixTextField(
            state = state,
            modifier = modifier,
            enabled = enabled,
            readOnly = readOnly,
            backgroundColor = if (backgroundColor != Color.Unspecified) backgroundColor else MiuixTheme.colorScheme.surfaceContainerHigh,
            label = label ?: "",
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            inputTransformation = inputTransformation,
            outputTransformation = outputTransformation,
            keyboardOptions = keyboardOptions,
            onKeyboardAction = onKeyboardAction,
            lineLimits = lineLimits,
            onTextLayout = onTextLayout,
            scrollState = scrollState,
            interactionSource = interactionSource
        )
    } else {
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
    val isMiuix = ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)

    if (isMiuix) {
        MiuixTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            readOnly = readOnly,
            backgroundColor = if (backgroundColor != Color.Unspecified) backgroundColor else MiuixTheme.colorScheme.surfaceContainerHigh,
            label = label ?: "",
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            interactionSource = interactionSource
        )
    } else {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTextFieldSurface(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    label: String? = null,
    labelPosition: TextFieldLabelPosition = TextFieldLabelPosition.Attached(),
    placeholder: @Composable (() -> Unit)? = null,
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
        backgroundColor = LegadoTheme.colorScheme.surface,
        label = label,
        labelPosition = labelPosition,
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
    labelPosition: TextFieldLabelPosition = TextFieldLabelPosition.Attached(),
    placeholder: @Composable (() -> Unit)? = null,
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
) {
    AppTextField(
        state = state,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        readOnly = readOnly,
        backgroundColor = backgroundColor,
        label = label,
        labelPosition = labelPosition,
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
        contentPadding = PaddingValues(top = 4.dp, bottom = 4.dp, start = 12.dp, end = 12.dp),
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
        backgroundColor = LegadoTheme.colorScheme.surface,
        label = label,
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
        interactionSource = interactionSource
    )
}
