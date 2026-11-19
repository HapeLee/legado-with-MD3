package io.legado.app.ui.widget.components.text


import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import io.legado.app.ui.theme.LegadoTheme

@Composable
fun AppText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle? = null,
) {
    val baseStyle = style ?: if (LegadoTheme.composeEngine == io.legado.app.ui.theme.ComposeEngine.Oxygen) {
        androidx.compose.material3.LocalTextStyle.current
    } else LegadoTheme.typography.bodyMedium

    val defaultTextColor = if (LegadoTheme.composeEngine == io.legado.app.ui.theme.ComposeEngine.Oxygen) {
        androidx.compose.material3.LocalContentColor.current
    } else LegadoTheme.colorScheme.onSurface

    val finalTextColor = color.takeOrElse {
        baseStyle.color.takeOrElse { defaultTextColor }
    }

    BasicText(
        text = text,
        modifier = modifier,
        style = baseStyle.merge(
            color = finalTextColor,
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = textAlign ?: TextAlign.Unspecified,
            lineHeight = lineHeight,
            fontFamily = fontFamily,
            textDecoration = textDecoration,
            fontStyle = fontStyle,
            letterSpacing = letterSpacing,
        ),
        onTextLayout = onTextLayout,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines
    )
}

@Composable
fun AppText(
    text: AnnotatedString, // 接收 AnnotatedString
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle? = null,
) {
    // Oxygen bridges its slot locals into M3; nearer retained M3 controls still win.
    val baseStyle = style ?: if (LegadoTheme.composeEngine == io.legado.app.ui.theme.ComposeEngine.Oxygen) {
        androidx.compose.material3.LocalTextStyle.current
    } else LegadoTheme.typography.bodyMedium

    val defaultTextColor = if (LegadoTheme.composeEngine == io.legado.app.ui.theme.ComposeEngine.Oxygen) {
        androidx.compose.material3.LocalContentColor.current
    } else LegadoTheme.colorScheme.onSurface

    // 3. 颜色降级逻辑：传入的 color -> style 中的 color -> 主题默认色
    val finalTextColor = color.takeOrElse {
        baseStyle.color.takeOrElse { defaultTextColor }
    }

    BasicText(
        text = text,
        modifier = modifier,
        style = baseStyle.merge(
            color = finalTextColor,
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = textAlign ?: TextAlign.Unspecified,
            lineHeight = lineHeight,
            fontFamily = fontFamily,
            textDecoration = textDecoration,
            fontStyle = fontStyle,
            letterSpacing = letterSpacing,
        ),
        onTextLayout = onTextLayout,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines
    )
}