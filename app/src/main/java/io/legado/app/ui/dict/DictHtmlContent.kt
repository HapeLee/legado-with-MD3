package io.legado.app.ui.dict

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import io.legado.app.ui.theme.LegadoTheme

@Composable
internal fun DictHtmlContent(
    htmlContent: String,
    modifier: Modifier = Modifier,
) {
    val document = remember(htmlContent) { DictHtmlParser.parse(htmlContent) }
    val baseStyle =
        LegadoTheme.typography.bodyMedium.merge(color = LegadoTheme.colorScheme.onSurface)
    val linkColor = LegadoTheme.colorScheme.primary

    BoxWithConstraints(modifier = modifier.padding(horizontal = 32.dp, vertical = 16.dp)) {
        val availableWidthPx = constraints.maxWidth
        Column {
            document.paragraphs.forEachIndexed { paragraphIndex, paragraph ->
                DictHtmlParagraphContent(
                    paragraph = paragraph,
                    paragraphIndex = paragraphIndex,
                    maxWidthPx = availableWidthPx,
                    baseStyle = baseStyle,
                    linkColor = linkColor,
                )
            }
        }
    }
}

@Composable
private fun DictHtmlParagraphContent(
    paragraph: DictHtmlParagraph,
    paragraphIndex: Int,
    maxWidthPx: Int,
    baseStyle: TextStyle,
    linkColor: Color,
) {
    val density = LocalDensity.current
    val inlineContent = mutableMapOf<String, InlineTextContent>()
    var imageIndex = 0

    paragraph.content.forEach { inline ->
        if (inline !is DictHtmlInline.Image) return@forEach
        val id = "dict-image-$paragraphIndex-${imageIndex++}"
        val painter = rememberAsyncImagePainter(model = inline.source)
        val image = (painter.state.value as? AsyncImagePainter.State.Success)?.result?.image
        val drawableWidth = image?.width?.coerceAtLeast(1) ?: 1
        val drawableHeight = image?.height?.coerceAtLeast(1) ?: 1
        val scale = if (maxWidthPx in 1..<drawableWidth) {
            maxWidthPx.toFloat() / drawableWidth
        } else {
            1f
        }
        val width = with(density) { (drawableWidth * scale).toDp().toSp() }
        val height = with(density) { (drawableHeight * scale).toDp().toSp() }
        inlineContent[id] = InlineTextContent(
            placeholder = Placeholder(
                width = width.value.coerceAtLeast(1f).sp,
                height = height.value.coerceAtLeast(1f).sp,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextBottom,
            )
        ) {
            Image(
                painter = painter,
                contentDescription = inline.description,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }

    val annotatedText = buildAnnotatedString {
        imageIndex = 0
        paragraph.content.forEach { inline ->
            when (inline) {
                is DictHtmlInline.Text -> appendStyledText(inline, linkColor)
                is DictHtmlInline.Image -> {
                    val id = "dict-image-$paragraphIndex-${imageIndex++}"
                    if (inline.link != null) {
                        withLink(LinkAnnotation.Url(inline.link)) {
                            appendInlineContent(id, inline.description ?: "image")
                        }
                    } else {
                        appendInlineContent(id, inline.description ?: "image")
                    }
                }

                DictHtmlInline.LineBreak -> append('\n')
            }
        }
    }

    BasicText(
        text = annotatedText,
        inlineContent = inlineContent,
        style = baseStyle,
    )
}

private fun AnnotatedString.Builder.appendStyledText(
    text: DictHtmlInline.Text,
    linkColor: Color,
) {
    val spanStyle = text.style.toSpanStyle(
        fallbackColor = if (text.link != null) linkColor else null,
        underlineLink = text.link != null,
    )
    if (text.link != null) {
        withLink(LinkAnnotation.Url(text.link)) {
            withStyle(spanStyle) { append(text.value) }
        }
    } else {
        withStyle(spanStyle) { append(text.value) }
    }
}

private fun DictHtmlTextStyle.toSpanStyle(
    fallbackColor: Color?,
    underlineLink: Boolean,
): SpanStyle {
    val decorations = buildList {
        if (underline || underlineLink) add(TextDecoration.Underline)
        if (strikeThrough) add(TextDecoration.LineThrough)
    }
    return SpanStyle(
        color = parseHtmlColor(color) ?: fallbackColor ?: Color.Unspecified,
        background = parseHtmlColor(backgroundColor) ?: Color.Unspecified,
        fontWeight = if (bold) FontWeight.Bold else null,
        fontStyle = if (italic) FontStyle.Italic else null,
        fontSize = if (relativeFontSize == 1f) androidx.compose.ui.unit.TextUnit.Unspecified else relativeFontSize.em,
        textDecoration = decorations.takeIf { it.isNotEmpty() }?.let(TextDecoration::combine),
        baselineShift = when (baseline) {
            DictHtmlBaseline.Normal -> null
            DictHtmlBaseline.Subscript -> BaselineShift.Subscript
            DictHtmlBaseline.Superscript -> BaselineShift.Superscript
        },
    )
}

private fun parseHtmlColor(value: String?): Color? {
    if (value.isNullOrBlank()) return null
    val normalized = value.trim().lowercase()
    return runCatching {
        when {
            normalized.startsWith("rgb(") -> {
                val channels = normalized.substringAfter('(').substringBefore(')').split(',')
                    .map { it.trim().toInt().coerceIn(0, 255) }
                if (channels.size != 3) return null
                Color(channels[0], channels[1], channels[2])
            }

            normalized.startsWith("rgba(") -> {
                val channels =
                    normalized.substringAfter('(').substringBefore(')').split(',').map(String::trim)
                if (channels.size != 4) return null
                Color(
                    red = channels[0].toInt().coerceIn(0, 255),
                    green = channels[1].toInt().coerceIn(0, 255),
                    blue = channels[2].toInt().coerceIn(0, 255),
                    alpha = (channels[3].toFloat().coerceIn(0f, 1f) * 255).toInt(),
                )
            }

            else -> Color(normalized.toColorInt())
        }
    }.getOrNull()
}
