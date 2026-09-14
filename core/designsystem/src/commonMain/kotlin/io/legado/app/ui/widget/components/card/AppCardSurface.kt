package io.legado.app.ui.widget.components.card

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LocalAppUiConfiguration
import top.yukonga.miuix.kmp.basic.CardDefaults as MiuixCardDefaults

@Composable
private fun BaseCardContent(
    modifier: Modifier = Modifier,
    shape: Shape,
    itemBackground: Modifier?,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (itemBackground == null) {
        Column(modifier = modifier, content = content)
        return
    }

    Box(modifier = modifier) {
        Spacer(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .then(itemBackground)
        )
        Column(content = content)
    }
}

/**
 * 卡片基元：本仓卡片类组件（[NormalCard] / [GlassCard]）共用的表面实现。
 *
 * [itemBackground] 表达「条目背景层」这一层能力，而不是一个布尔开关：
 * - `null` —— 不要背景层，直接 `Column`（`NormalCard` 形态）；
 * - 非 `null` —— 在内容下叠一层 `matchParentSize` 的 Spacer，并应用该 modifier
 *   （[GlassCard] 传入 `Modifier.appContainerBackground(Item)`）。
 *
 * M1-3p 后 [GlassCard] 与 `appContainerBackground` 也都住在本模块：后者里唯一真正平台相关的
 * 一步（`.9.png` 的九宫格解码）已下沉为 `:core:platform` 的 `NinePatchLoader` 窄契约，
 * 剩下的是跨平台的 Coil 加载管线。所以共享层**依然**不必知道九宫格怎么解、也不需要平台类型。
 */
@Composable
fun AppCardSurface(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    cornerRadius: Dp = MiuixCardDefaults.CornerRadius,
    containerColor: Color? = null,
    contentColor: Color? = null,
    elevation: Dp = 0.dp,
    border: BorderStroke? = null,
    alpha: Float = 1f,
    itemBackground: Modifier? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val resolvedContainerColor = (containerColor ?: LegadoTheme.colorScheme.surfaceContainer)
        .let { it.copy(alpha = it.alpha * alpha) }
    val themeSettings = LocalAppUiConfiguration.current.theme
    val isTransparent = containerColor == Color.Transparent
    val decoration = resolveCardDecoration(
        theme = themeSettings,
        isDark = LegadoTheme.isDark,
        requestedCornerRadius = cornerRadius,
        requestedBorder = border,
        fallbackBorderColor = LegadoTheme.colorScheme.outlineVariant,
    )
    val resolvedShape = RoundedCornerShape(decoration.cornerRadius)
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
        color = if (isTransparent) Color.Transparent else resolvedContainerColor,
        contentColor = contentColor ?: LegadoTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = elevation,
        border = decoration.border
    ) {
        BaseCardContent(
            shape = resolvedShape,
            itemBackground = itemBackground,
            content = content,
        )
    }
}

@Composable
fun NormalCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    cornerRadius: Dp = MiuixCardDefaults.CornerRadius,
    containerColor: Color? = null,
    contentColor: Color? = null,
    elevation: Dp = 0.dp,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    AppCardSurface(
        modifier = modifier,
        onClick = onClick,
        onLongClick = onLongClick,
        cornerRadius = cornerRadius,
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = elevation,
        border = border,
        alpha = 1f,
        itemBackground = null,
        content = content
    )
}
