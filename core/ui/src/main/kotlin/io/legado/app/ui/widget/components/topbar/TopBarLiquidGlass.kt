package io.legado.app.ui.widget.components.topbar

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import io.legado.app.ui.animation.InteractiveHighlight
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LocalTopBarBackdrop
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

// 顶栏组件本体（Glass*TopAppBar / TopBarButton / DynamicTopAppBar）已于 M1-3r 搬进
// `:core:designsystem/commonMain`，共享层通过 `LiquidGlassEffects` 契约回调这里。
// 实现之所以留在 `:core:ui` 而不是像其它契约那样放进 `app/.../platform`：本文件的
// `drawBackdrop` 链依赖 `InteractiveHighlight`（`android.graphics.RuntimeShader` / AGSL）与
// `android.os.Build`，而 `topBarLiquidGlass` 是 `internal`——`app` 看不见它。与其为搬家把
// 内部 API 提为 public，不如让实现留在自己的可见性范围里，只把 public 工厂
// `androidLiquidGlassEffects()` 交给 `PlatformServices.install()` 注入。

@Composable
internal fun Modifier.androidTopBarLiquidGlass(shape: Shape): Modifier {
    val backdrop = LocalTopBarBackdrop.current ?: return this
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return this
    val containerColor = LegadoTheme.colorScheme.surface.copy(
        alpha = 0.5f
    )
    val shadowColor = Color.Black.copy(alpha = 0.04f)
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) { InteractiveHighlight(animationScope) }
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(12.dp.toPx())
            lens(24.dp.toPx(), 24.dp.toPx())
        },
        highlight = { Highlight.Default },
        shadow = {
            Shadow(
                radius = 12.dp,
                color = shadowColor
            )
        },
        layerBlock = {
            val width = size.width
            val height = size.height
            if (width > 0f && height > 0f) {
                val progress = interactiveHighlight.pressProgress
                val scale = 1f + 4.dp.toPx() / height * progress
                val maxOffset = size.minDimension
                val dragOffset = interactiveHighlight.dragOffset
                translationX = maxOffset * tanh(0.05f * dragOffset.x / maxOffset) * progress
                translationY = maxOffset * tanh(0.05f * dragOffset.y / maxOffset) * progress
                val maxDragScale = 4.dp.toPx() / height
                val offsetAngle = atan2(dragOffset.y, dragOffset.x)
                scaleX = scale + maxDragScale *
                        abs(cos(offsetAngle) * dragOffset.x / size.maxDimension) *
                        (width / height).coerceAtMost(1f) * progress
                scaleY = scale + maxDragScale *
                        abs(sin(offsetAngle) * dragOffset.y / size.maxDimension) *
                        (height / width).coerceAtMost(1f) * progress
            }
        },
        onDrawSurface = {
            drawRect(containerColor)
        },
    )
        .then(interactiveHighlight.modifier)
        .then(interactiveHighlight.gestureModifier)
}

@Composable
internal fun androidTopBarLiquidGlassEnabled(): Boolean =
    LocalTopBarBackdrop.current != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/**
 * Android 侧的 [LiquidGlassEffects] 实现。由 `app` 的 `PlatformServices.install()` 注入
 * `LiquidGlassEffectsProvider`，共享层顶栏因此不直接依赖 `RuntimeShader` / `Build.VERSION`。
 */
fun androidLiquidGlassEffects(): LiquidGlassEffects = AndroidLiquidGlassEffects

private object AndroidLiquidGlassEffects : LiquidGlassEffects {
    @Composable
    override fun enabled(): Boolean = androidTopBarLiquidGlassEnabled()

    @Composable
    override fun Modifier.liquidGlass(shape: Shape): Modifier = androidTopBarLiquidGlass(shape)
}
