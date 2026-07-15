package io.legado.app.ui.widget.components.reader

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import io.legado.app.ui.animation.InteractiveHighlight
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

fun readerMenuLiquidGlassAvailable(backdrop: Backdrop?): Boolean {
    return backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}

@Composable
fun Modifier.readerMenuLiquidGlass(
    backdrop: Backdrop?,
    shape: Shape,
    surfaceBrush: Brush,
    blurRadius: Dp,
    lensRadius: Dp,
    useLens: Boolean,
    interactive: Boolean = false,
): Modifier {
    if (!readerMenuLiquidGlassAvailable(backdrop)) return this

    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = if (interactive) {
        remember(animationScope) { InteractiveHighlight(animationScope) }
    } else {
        null
    }

    return drawBackdrop(
        backdrop = backdrop!!,
        shape = { shape },
        effects = {
            vibrancy()
            blur(blurRadius.coerceAtLeast(0.dp).toPx())
            if (useLens) {
                lens(lensRadius.toPx(), lensRadius.toPx())
            }
        },
        highlight = { Highlight.Default },
        shadow = null,
        layerBlock = if (interactiveHighlight != null) {
            {
                val width = size.width
                val height = size.height
                if (width > 0f && height > 0f) {
                    val progress = interactiveHighlight.pressProgress
                    val scale = 1f + 4.dp.toPx() / height * progress
                    val maxOffset = size.minDimension
                    val initialDerivative = 0.05f
                    val dragOffset = interactiveHighlight.dragOffset
                    translationX = maxOffset *
                            tanh(initialDerivative * dragOffset.x / maxOffset) * progress
                    translationY = maxOffset *
                            tanh(initialDerivative * dragOffset.y / maxOffset) * progress

                    val maxDragScale = 4.dp.toPx() / height
                    val offsetAngle = atan2(dragOffset.y, dragOffset.x)
                    scaleX = scale + maxDragScale *
                            abs(cos(offsetAngle) * dragOffset.x / size.maxDimension) *
                            (width / height).coerceAtMost(1f) * progress
                    scaleY = scale + maxDragScale *
                            abs(sin(offsetAngle) * dragOffset.y / size.maxDimension) *
                            (height / width).coerceAtMost(1f) * progress
                }
            }
        } else {
            null
        },
        onDrawSurface = { drawRect(surfaceBrush) },
    )
        .then(interactiveHighlight?.modifier ?: Modifier)
        .then(interactiveHighlight?.gestureModifier ?: Modifier)
}
