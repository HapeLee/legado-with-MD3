package io.legado.app.ui.widget.components.card

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LocalAppUiConfiguration
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.AppContainerBackgroundType
import io.legado.app.ui.widget.components.appContainerBackground
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardDefaults as MiuixCardDefaults

@Composable
private fun BaseCardContent(
    modifier: Modifier = Modifier,
    shape: Shape,
    useItemBackground: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!useItemBackground) {
        Column(modifier = modifier, content = content)
        return
    }

    Box(modifier = modifier) {
        Spacer(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .appContainerBackground(type = AppContainerBackgroundType.Item)
        )
        Column(content = content)
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun BaseCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    cornerRadius: Dp = MiuixCardDefaults.CornerRadius,
    pressFeedbackType: PressFeedbackType = PressFeedbackType.None,
    containerColor: Color? = null,
    contentColor: Color? = null,
    elevation: Dp = 0.dp,
    border: BorderStroke? = null,
    alpha: Float = 1f,
    useItemBackground: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val resolvedContainerColor = (containerColor ?: LegadoTheme.colorScheme.surfaceContainer)
        .let { it.copy(alpha = it.alpha * alpha) }
    val themeSettings = LocalAppUiConfiguration.current.theme
    val isTransparent = containerColor == Color.Transparent
    val resolvedCornerRadius = if (themeSettings.overrideBaseCardCornerRadius) {
        themeSettings.baseCardCornerRadius.dp
    } else {
        cornerRadius
    }
    val resolvedBorder = if (themeSettings.overrideBaseCardBorder) {
        val configuredColor = if (LegadoTheme.isDark) {
            themeSettings.baseCardBorderColorNight
        } else {
            themeSettings.baseCardBorderColor
        }
        BorderStroke(
            themeSettings.baseCardBorderWidth.dp,
            configuredColor.takeIf { it != 0 }?.let(::Color)
                ?: LegadoTheme.colorScheme.outlineVariant
        )
    } else {
        border
    }
    val resolvedShape = RoundedCornerShape(resolvedCornerRadius)
    val decoratedModifier = modifier.then(
        if (themeSettings.overrideBaseCardBorder && resolvedBorder != null) {
            Modifier.border(resolvedBorder, resolvedShape)
        } else {
            Modifier
        }
    )

    if (isTransparent) {
        val clickableModifier = if (onClick != null || onLongClick != null) {
            decoratedModifier
                .clip(resolvedShape)
                .combinedClickable(
                    onClick = { onClick?.invoke() },
                    onLongClick = onLongClick
                )
        } else {
            decoratedModifier.clip(resolvedShape)
        }
        BaseCardContent(
            modifier = clickableModifier,
            shape = resolvedShape,
            useItemBackground = useItemBackground,
            content = content,
        )
    } else if (ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)) {
        val colors = MiuixCardDefaults.defaultColors(
            color = resolvedContainerColor,
            contentColor = contentColor ?: LegadoTheme.colorScheme.onSurface
        )
        if (onClick != null) {
            MiuixCard(
                modifier = decoratedModifier,
                cornerRadius = resolvedCornerRadius,
                pressFeedbackType = pressFeedbackType,
                showIndication = true,
                onClick = onClick,
                onLongPress = onLongClick,
                content = {
                    BaseCardContent(
                        shape = resolvedShape,
                        useItemBackground = useItemBackground,
                        content = content,
                    )
                },
                colors = colors
            )
        } else {
            MiuixCard(
                modifier = decoratedModifier,
                cornerRadius = resolvedCornerRadius,
                content = {
                    BaseCardContent(
                        shape = resolvedShape,
                        useItemBackground = useItemBackground,
                        content = content,
                    )
                },
                colors = colors
            )
        }
    } else {
        val colors = CardDefaults.cardColors(
            containerColor = resolvedContainerColor,
            contentColor = contentColor ?: LegadoTheme.colorScheme.onSecondaryContainer,
            disabledContainerColor = LegadoTheme.colorScheme.onSecondaryContainer.copy(alpha = alpha * 0.38f),
            disabledContentColor = LegadoTheme.colorScheme.onSecondaryContainer.copy(alpha = alpha * 0.38f)
        )
        val clickableModifier = if (onClick != null || onLongClick != null) {
            modifier
                .clip(resolvedShape)
                .combinedClickable(
                    onClick = { onClick?.invoke() },
                    onLongClick = onLongClick
                )
        } else {
            modifier
        }
        Surface(
            modifier = clickableModifier,
            shape = resolvedShape,
            color = colors.containerColor,
            contentColor = colors.contentColor,
            tonalElevation = 0.dp,
            shadowElevation = elevation,
            border = resolvedBorder
        ) {
            BaseCardContent(
                shape = resolvedShape,
                useItemBackground = useItemBackground,
                content = content,
            )
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    cornerRadius: Dp = MiuixCardDefaults.CornerRadius,
    pressFeedbackType: PressFeedbackType = PressFeedbackType.None,
    containerColor: Color? = null,
    contentColor: Color? = null,
    elevation: Dp = 0.dp,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    BaseCard(
        modifier = modifier,
        onClick = onClick,
        onLongClick = onLongClick,
        cornerRadius = cornerRadius,
        pressFeedbackType = pressFeedbackType,
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = elevation,
        border = border,
        alpha = LocalAppUiConfiguration.current.theme.containerOpacity / 100f,
        useItemBackground = true,
        content = content
    )
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun NormalCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    cornerRadius: Dp = MiuixCardDefaults.CornerRadius,
    pressFeedbackType: PressFeedbackType = PressFeedbackType.None,
    containerColor: Color? = null,
    contentColor: Color? = null,
    elevation: Dp = 0.dp,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    BaseCard(
        modifier = modifier,
        onClick = onClick,
        onLongClick = onLongClick,
        cornerRadius = cornerRadius,
        pressFeedbackType = pressFeedbackType,
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = elevation,
        border = border,
        alpha = 1f,
        useItemBackground = false,
        content = content
    )
}
