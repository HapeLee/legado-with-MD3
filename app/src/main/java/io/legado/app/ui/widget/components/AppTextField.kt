package io.legado.app.ui.widget.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
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
    // 1. 核心思想：保留 AppText 的统一样式与颜色降级参数
    textStyle: TextStyle? = null,
    textColor: Color = Color.Unspecified,
    // 2. 将 Label 统一定义为 String 以兼容两套引擎
    label: String? = null,
    // 3. 保留 M3 TextField 的所有其他参数
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
    colors: TextFieldColors = TextFieldDefaults.colors(),
    contentPadding: PaddingValues? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    val baseStyle = textStyle ?: LegadoTheme.typography.bodyMedium
    val defaultTextColor = LegadoTheme.colorScheme.onSurface
    val finalTextColor = textColor.takeOrElse {
        baseStyle.color.takeOrElse { defaultTextColor }
    }
    val mergedTextStyle = baseStyle.merge(color = finalTextColor)
    val isMiuix = ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)

    if (isMiuix) {
        MiuixTextField(
            state = state,
            modifier = modifier,
            enabled = enabled,
            readOnly = readOnly,
            textStyle = MiuixTheme.textStyles.main,
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
        TextField(
            state = state,
            modifier = modifier,
            enabled = enabled,
            readOnly = readOnly,
            textStyle = mergedTextStyle,
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
            colors = colors,
            contentPadding = resolvedContentPadding,
            interactionSource = interactionSource
        )
    }
}