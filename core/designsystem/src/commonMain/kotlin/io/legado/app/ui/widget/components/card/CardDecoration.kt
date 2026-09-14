package io.legado.app.ui.widget.components.card

import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.domain.model.settings.ThemeSettings

/** 卡片基元最终采用的圆角与描边。 */
@Immutable
data class CardDecoration(
    val cornerRadius: Dp,
    val border: BorderStroke?,
)

/**
 * 卡片圆角/描边的**纯**决议：把 `ThemeSettings` 的两个主题覆盖开关与调用方请求合并成最终形态。
 *
 * 语义与搬动前 `GlassCard.kt` 内联的 `BaseCard` 完全一致：
 * - `overrideBaseCardCornerRadius` 为真时忽略调用方 [requestedCornerRadius]，改用主题的
 *   `baseCardCornerRadius`；
 * - `overrideBaseCardBorder` 为真时忽略调用方 [requestedBorder]，改用主题的
 *   `baseCardBorderWidth` + 明暗色（`baseCardBorderColorNight` / `baseCardBorderColor`），
 *   且颜色为 `0` 时回落到 [fallbackBorderColor]。
 *
 * 之所以抽成不依赖 CompositionLocal 的纯函数：这是本仓卡片外观的**唯一定义**，两个调用方
 * （`NormalCard`、`GlassCard`，M1-3p 后都在本模块）都必须走它，且覆盖语义要能被单测直接钉住
 * （CompositionLocal 读取无法脱离 Compose runtime 测试）。
 */
fun resolveCardDecoration(
    theme: ThemeSettings,
    isDark: Boolean,
    requestedCornerRadius: Dp,
    requestedBorder: BorderStroke?,
    fallbackBorderColor: Color,
): CardDecoration {
    val cornerRadius = if (theme.overrideBaseCardCornerRadius) {
        theme.baseCardCornerRadius.dp
    } else {
        requestedCornerRadius
    }

    val border = if (theme.overrideBaseCardBorder) {
        val configuredColor = if (isDark) {
            theme.baseCardBorderColorNight
        } else {
            theme.baseCardBorderColor
        }
        BorderStroke(
            theme.baseCardBorderWidth.dp,
            configuredColor.takeIf { it != 0 }?.let(::Color) ?: fallbackBorderColor
        )
    } else {
        requestedBorder
    }

    return CardDecoration(cornerRadius = cornerRadius, border = border)
}
