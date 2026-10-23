package io.legado.app.ui.widget.components.card

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
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
import io.legado.app.ui.widget.components.AppContainerBackgroundType
import io.legado.app.ui.widget.components.appContainerBackground
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
    containerColor: Color? = null,
    contentColor: Color? = null,
    elevation: Dp = 0.dp,
    border: BorderStroke? = null,
    /**
     * 只在全局"覆盖卡片边框"打开时改写边框**颜色**（粗细默认仍跟全局，可用 [forcedBorderWidthExtra] 加粗）；
     * null（默认）= 完全按原逻辑，全局覆盖分支用配置色。
     * 用于"颜色必须赢"的标识（如阅读主题里"正在使用"的主色框，不能被全局色吃掉）。
     */
    forcedBorderColor: Color? = null,
    /**
     * 只在全局"覆盖卡片边框"打开时，在全局粗细之上再加粗这么多（全局 1dp + 1dp ⇒ 2dp）。
     * 用于"连粗细也要和全局框区分开"的标识。默认 0.dp = 不加粗。
     */
    forcedBorderWidthExtra: Dp = 0.dp,
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
            themeSettings.baseCardBorderWidth.dp + forcedBorderWidthExtra,
            // 调用方点名要的颜色优先（"正在使用"的主色框不能被全局色吃掉）
            forcedBorderColor
                ?: configuredColor.takeIf { it != 0 }?.let(::Color)
                ?: LegadoTheme.colorScheme.outlineVariant
        )
    } else {
        border
    }
    val resolvedShape = RoundedCornerShape(resolvedCornerRadius)
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
        border = resolvedBorder
    ) {
        BaseCardContent(
            shape = resolvedShape,
            useItemBackground = useItemBackground,
            content = content,
        )
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    cornerRadius: Dp = MiuixCardDefaults.CornerRadius,
    containerColor: Color? = null,
    contentColor: Color? = null,
    elevation: Dp = 0.dp,
    border: BorderStroke? = null,
    /** 见 [BaseCard.forcedBorderColor]：只在全局覆盖边框时改写颜色，宽度不占空间、也不影响布局 */
    forcedBorderColor: Color? = null,
    /** 见 [BaseCard.forcedBorderWidthExtra]：全局覆盖边框时在全局粗细之上再加粗 */
    forcedBorderWidthExtra: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    BaseCard(
        modifier = modifier,
        onClick = onClick,
        onLongClick = onLongClick,
        cornerRadius = cornerRadius,
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = elevation,
        border = border,
        forcedBorderColor = forcedBorderColor,
        forcedBorderWidthExtra = forcedBorderWidthExtra,
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
    containerColor: Color? = null,
    contentColor: Color? = null,
    elevation: Dp = 0.dp,
    border: BorderStroke? = null,
    /** 见 [BaseCard.forcedBorderColor]：只在全局覆盖边框时改写颜色，宽度不占空间、也不影响布局 */
    forcedBorderColor: Color? = null,
    /** 见 [BaseCard.forcedBorderWidthExtra]：全局覆盖边框时在全局粗细之上再加粗 */
    forcedBorderWidthExtra: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    BaseCard(
        modifier = modifier,
        onClick = onClick,
        onLongClick = onLongClick,
        cornerRadius = cornerRadius,
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = elevation,
        border = border,
        forcedBorderColor = forcedBorderColor,
        forcedBorderWidthExtra = forcedBorderWidthExtra,
        alpha = 1f,
        useItemBackground = false,
        content = content
    )
}
