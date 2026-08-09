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

@Composable
internal fun Modifier.topBarLiquidGlass(shape: Shape): Modifier {
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
internal fun topBarLiquidGlassEnabled(): Boolean =
    LocalTopBarBackdrop.current != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
