package io.legado.app.ui.widget.components.text

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
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
import io.legado.app.domain.model.settings.TextAnimationLevel
import io.legado.app.ui.theme.LocalAppUiConfiguration
import ui.feice.mochi.effect.content.MochiBlurCrossfade
import ui.feice.mochi.effect.content.MochiBlurTransition
import ui.feice.mochi.effect.content.MochiBlurTransitions

@Composable
fun AnimatedText(
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
    style: TextStyle = LocalTextStyle.current
) {
    val level = LocalAppUiConfiguration.current.appShell.textAnimationLevel
    val transition = remember(level) { level.blurTransition(MochiBlurTransitions.Counter) }
    Row(
        modifier = modifier.clearAndSetSemantics { this.text = AnnotatedString(text) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in text.indices) {
            val character = text[i].toString()
            AnimatedTextReplacement(
                text = character,
                level = level,
                transition = transition,
                direction = 1,
            ) { target ->
                AppText(
                    style = style,
                    color = color,
                    softWrap = softWrap,
                    text = target,
                    fontSize = fontSize,
                    fontStyle = fontStyle,
                    fontWeight = fontWeight,
                    fontFamily = fontFamily,
                    letterSpacing = letterSpacing,
                    textDecoration = textDecoration,
                    textAlign = textAlign,
                    lineHeight = lineHeight,
                    overflow = overflow,
                    maxLines = maxLines,
                    minLines = minLines,
                    onTextLayout = onTextLayout.takeIf { target == character }
                )
            }
        }
    }
}

/**
 * 动画文本控件
 *
 * 区别于 AnimatedText, 该控件在文本变化时，提供整行的滑动动画效果
 */
@Composable
fun AnimatedTextLine(
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
    style: TextStyle = LocalTextStyle.current
) {
    val level = LocalAppUiConfiguration.current.appShell.textAnimationLevel
    val transition = remember(level) { level.blurTransition(MochiBlurTransitions.Replace) }
    val alignment = when (textAlign ?: style.textAlign) {
        TextAlign.Center -> Alignment.Center
        TextAlign.End -> Alignment.CenterEnd
        TextAlign.Left -> AbsoluteAlignment.CenterLeft
        TextAlign.Right -> AbsoluteAlignment.CenterRight
        else -> Alignment.CenterStart
    }
    AnimatedTextReplacement(
        text = text,
        modifier = modifier,
        level = level,
        transition = transition,
        alignment = alignment,
    ) { targetText ->
        val isCurrent = targetText == text
        Box(
            modifier = if (isCurrent) Modifier else Modifier.clearAndSetSemantics {},
            propagateMinConstraints = true,
        ) {
            AppText(
                text = targetText,
                style = style,
                color = color,
                softWrap = softWrap,
                fontSize = fontSize,
                fontStyle = fontStyle,
                fontWeight = fontWeight,
                fontFamily = fontFamily,
                letterSpacing = letterSpacing,
                textDecoration = textDecoration,
                textAlign = textAlign,
                lineHeight = lineHeight,
                overflow = overflow,
                maxLines = maxLines,
                minLines = minLines,
                onTextLayout = onTextLayout.takeIf { isCurrent }
            )
        }
    }
}

private fun TextAnimationLevel.blurTransition(base: MochiBlurTransition): MochiBlurTransition? =
    when (this) {
        TextAnimationLevel.Slide, TextAnimationLevel.Off -> null
        TextAnimationLevel.Subtle -> base.copy(
            blur = base.blur * 0.5f,
            scale = 1f - (1f - base.scale) * 0.5f,
            slide = base.slide * 0.5f,
        )
        TextAnimationLevel.Standard -> base
        TextAnimationLevel.Strong -> base.copy(
            blur = base.blur * 1.5f,
            scale = 1f - (1f - base.scale) * 1.5f,
            slide = base.slide * 1.25f,
        )
    }

@Composable
private fun AnimatedTextReplacement(
    text: String,
    level: TextAnimationLevel,
    transition: MochiBlurTransition?,
    modifier: Modifier = Modifier,
    direction: Int = 0,
    alignment: Alignment = Alignment.CenterStart,
    content: @Composable (String) -> Unit,
) {
    when {
        level == TextAnimationLevel.Off -> Box(modifier, contentAlignment = alignment) { content(text) }
        transition != null -> MochiBlurCrossfade(
            targetState = text,
            modifier = modifier,
            transition = transition,
            direction = direction,
            contentAlignment = alignment,
            content = content,
        )
        else -> AnimatedContent(
            targetState = text,
            modifier = modifier,
            contentAlignment = alignment,
            transitionSpec = {
                slideInVertically { it } togetherWith slideOutVertically { -it }
            },
            label = "TextSlide",
        ) { content(it) }
    }
}

@Composable
fun AdaptiveAnimatedText(
    text: String,
    useCharMode: Boolean,
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
    style: TextStyle = LocalTextStyle.current
) {
    val content: @Composable (Boolean) -> Unit = { currentMode ->
        if (currentMode) {
            AnimatedText(
                text = text,
                color = color,
                fontSize = fontSize,
                fontStyle = fontStyle,
                fontWeight = fontWeight,
                fontFamily = fontFamily,
                letterSpacing = letterSpacing,
                textDecoration = textDecoration,
                textAlign = textAlign,
                lineHeight = lineHeight,
                overflow = overflow,
                softWrap = softWrap,
                maxLines = maxLines,
                minLines = minLines,
                onTextLayout = onTextLayout,
                style = style
            )
        } else {
            AnimatedTextLine(
                text = text,
                color = color,
                fontSize = fontSize,
                fontStyle = fontStyle,
                fontWeight = fontWeight,
                fontFamily = fontFamily,
                letterSpacing = letterSpacing,
                textDecoration = textDecoration,
                textAlign = textAlign,
                lineHeight = lineHeight,
                overflow = overflow,
                softWrap = softWrap,
                maxLines = maxLines,
                minLines = minLines,
                onTextLayout = onTextLayout,
                style = style
            )
        }
    }
    if (LocalAppUiConfiguration.current.appShell.textAnimationLevel == TextAnimationLevel.Off) {
        Box(modifier) { content(useCharMode) }
    } else {
        AnimatedContent(
            targetState = useCharMode,
            transitionSpec = {
                (slideInVertically { it } + fadeIn()).togetherWith(
                    slideOutVertically { -it } + fadeOut()
                )
            },
            label = "ModeSwitchAnimation",
            modifier = modifier,
        ) { content(it) }
    }
}
