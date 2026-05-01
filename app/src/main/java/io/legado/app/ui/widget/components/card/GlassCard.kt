package io.legado.app.ui.widget.components.card

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import io.legado.app.ui.config.themeConfig.ThemeConfig
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardDefaults as MiuixCardDefaults

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
    content: @Composable ColumnScope.() -> Unit
) {
    val resolvedContainerColor = (containerColor ?: LegadoTheme.colorScheme.secondaryContainer)
        .let { it.copy(alpha = it.alpha * alpha) }
    if (ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)) {
        val colors = MiuixCardDefaults.defaultColors(
            color = resolvedContainerColor,
            contentColor = contentColor ?: LegadoTheme.colorScheme.onSurface
        )
        if (onClick != null) {
            MiuixCard(
                modifier = modifier,
                cornerRadius = cornerRadius,
                pressFeedbackType = pressFeedbackType,
                showIndication = true,
                onClick = onClick,
                onLongPress = onLongClick,
                content = content,
                colors = colors
            )
        } else {
            MiuixCard(
                modifier = modifier,
                cornerRadius = cornerRadius,
                content = content,
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
                .clip(RoundedCornerShape(cornerRadius))
                .combinedClickable(
                    onClick = { onClick?.invoke() },
                    onLongClick = onLongClick
                )
        } else {
            modifier
        }
        Surface(
            modifier = clickableModifier,
            shape = RoundedCornerShape(cornerRadius),
            color = colors.containerColor,
            contentColor = colors.contentColor,
            tonalElevation = 0.dp,
            shadowElevation = elevation,
            border = border
        ) {
            Column(content = content)
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
        alpha = ThemeConfig.containerOpacity / 100f,
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
        content = content
    )
}
