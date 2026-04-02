package io.legado.app.ui.widget.components.card

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import io.legado.app.ui.config.themeConfig.ThemeConfig
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardDefaults as MiuixCardDefaults

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun BaseCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = CardDefaults.shape,
    cornerRadius: Dp = MiuixCardDefaults.CornerRadius,
    containerColor: Color? = null,
    contentColor: Color? = null,
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
    alpha: Float = 1f,
    content: @Composable ColumnScope.() -> Unit
) {
    if (ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)) {
        val colors = MiuixCardDefaults.defaultColors(
            color = (containerColor
                ?: MiuixTheme.colorScheme.secondaryContainer).copy(alpha = alpha),
            contentColor = contentColor ?: MiuixTheme.colorScheme.onSurface
        )
        MiuixCard(
            modifier = modifier,
            cornerRadius = cornerRadius,
            onClick = onClick,
            content = content,
            colors = colors
        )
    } else {
        val colors = CardDefaults.cardColors(
            containerColor = (containerColor ?: MaterialTheme.colorScheme.secondaryContainer).copy(
                alpha = alpha
            ),
            contentColor = contentColor ?: MaterialTheme.colorScheme.onSecondaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = alpha * 0.38f),
            disabledContentColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = alpha * 0.38f)
        )
        if (onClick != null) {
            Card(
                modifier = modifier,
                shape = shape,
                colors = colors,
                elevation = elevation,
                border = border,
                onClick = onClick,
                content = content
            )
        } else {
            Card(
                modifier = modifier,
                shape = shape,
                colors = colors,
                elevation = elevation,
                border = border,
                content = content
            )
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = CardDefaults.shape,
    cornerRadius: Dp = MiuixCardDefaults.CornerRadius,
    containerColor: Color? = null,
    contentColor: Color? = null,
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    BaseCard(
        modifier = modifier,
        onClick = onClick,
        shape = shape,
        cornerRadius = cornerRadius,
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
    shape: Shape = CardDefaults.shape,
    cornerRadius: Dp = MiuixCardDefaults.CornerRadius,
    containerColor: Color? = null,
    contentColor: Color? = null,
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    BaseCard(
        modifier = modifier,
        onClick = onClick,
        shape = shape,
        cornerRadius = cornerRadius,
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = elevation,
        border = border,
        alpha = 1f,
        content = content
    )
}
