package io.legado.app.ui.theme

import android.graphics.Paint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Applies a horizontal fading edge effect to a scrollable container.
 *
 * The alphas are passed as [State] and read inside the draw cache block, so a running
 * fade animation invalidates the gradient without recomposing the caller.
 *
 * @param leftAlpha Fade strength: 0f = fully visible, 1f = fully faded
 * @param rightAlpha Fade strength: 0f = fully visible, 1f = fully faded
 * @param gradientWidth width of the fade gradient on each side
 * @param bleed 非零时改用按 [bleed] 外扩的离屏层，让子级画在自身边界外的投影不被裁掉。
 *   未渐隐的一侧不再收边（此时没有滚出视口的内容），已渐隐的一侧仍收在节点边界，
 *   避免滚出视口的兄弟露出到渐隐带之外。两侧都未渐隐时完全不建层。
 */
fun Modifier.fadingEdge(
    leftAlpha: State<Float>,
    rightAlpha: State<Float>,
    gradientWidth: Dp = 24.dp,
    bleed: Dp = 0.dp
): Modifier = if (bleed <= 0.dp) {
    graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithCache {
            val width = size.width
            if (width <= 0f) {
                return@drawWithCache onDrawWithContent { drawContent() }
            }
            val gradientWidthPx = gradientWidth.toPx()
            val leftStop = gradientWidthPx / width
            val rightStop = 1f - (gradientWidthPx / width)
            val brush = Brush.horizontalGradient(
                colorStops = arrayOf(
                    0f to Color.Black.copy(alpha = 1f - leftAlpha.value),
                    leftStop to Color.Black,
                    rightStop to Color.Black,
                    1f to Color.Black.copy(alpha = 1f - rightAlpha.value)
                ),
                startX = 0f,
                endX = width
            )
            onDrawWithContent {
                drawContent()
                drawRect(brush = brush, blendMode = BlendMode.DstIn)
            }
        }
} else {
    drawWithCache {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) {
            return@drawWithCache onDrawWithContent { drawContent() }
        }
        val bleedPx = bleed.toPx()
        val gradientWidthPx = gradientWidth.toPx()
        val fadeLeft = leftAlpha.value > 0.001f
        val fadeRight = rightAlpha.value > 0.001f
        // 层边界要覆盖投影的外扩范围；两侧都不渐隐时不做任何裁剪。
        if (!fadeLeft && !fadeRight) {
            return@drawWithCache onDrawWithContent { drawContent() }
        }
        val leftStop = (gradientWidthPx / width).coerceIn(0f, 1f)
        val rightStop = 1f - leftStop
        val brush = Brush.horizontalGradient(
            colorStops = arrayOf(
                0f to Color.Black.copy(alpha = 1f - leftAlpha.value),
                leftStop to Color.Black,
                rightStop to Color.Black,
                1f to Color.Black.copy(alpha = 1f - rightAlpha.value)
            ),
            startX = 0f,
            endX = width
        )
        val layerPaint = Paint()
        onDrawWithContent {
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                val layer = native.saveLayer(
                    if (fadeLeft) 0f else -bleedPx,
                    -bleedPx,
                    if (fadeRight) width else width + bleedPx,
                    height + bleedPx,
                    layerPaint
                )
                drawContent()
                drawRect(brush = brush, blendMode = BlendMode.DstIn)
                native.restoreToCount(layer)
            }
        }
    }
}

/**
 * Convenience overload that derives fade alphas from a [LazyListState].
 */
@Composable
fun Modifier.fadingEdge(
    listState: LazyListState,
    gradientWidth: Dp = 24.dp,
    bleed: Dp = 0.dp
): Modifier {
    val startAlpha = animateFloatAsState(
        targetValue = if (listState.canScrollBackward) 1f else 0f,
        animationSpec = tween(300),
        label = "StartFadeAlpha"
    )
    val endAlpha = animateFloatAsState(
        targetValue = if (listState.canScrollForward) 1f else 0f,
        animationSpec = tween(300),
        label = "EndFadeAlpha"
    )
    return if (LocalLayoutDirection.current == LayoutDirection.Rtl) {
        fadingEdge(endAlpha, startAlpha, gradientWidth, bleed)
    } else {
        fadingEdge(startAlpha, endAlpha, gradientWidth, bleed)
    }
}

/**
 * Convenience overload that derives fade alphas from a [PagerState].
 */
@Composable
fun Modifier.fadingEdge(
    pagerState: PagerState,
    gradientWidth: Dp = 24.dp,
    bleed: Dp = 0.dp
): Modifier {
    val leftAlpha = animateFloatAsState(
        targetValue = if (pagerState.canScrollBackward) 1f else 0f,
        animationSpec = tween(300),
        label = "LeftFadeAlpha"
    )
    val rightAlpha = animateFloatAsState(
        targetValue = if (pagerState.canScrollForward) 1f else 0f,
        animationSpec = tween(300),
        label = "RightFadeAlpha"
    )
    return fadingEdge(leftAlpha, rightAlpha, gradientWidth, bleed)
}

/**
 * Convenience overload that derives fade alphas from a [ScrollState].
 */
@Composable
fun Modifier.fadingEdge(
    scrollState: ScrollState,
    gradientWidth: Dp = 24.dp,
    bleed: Dp = 0.dp
): Modifier {
    val startAlpha = animateFloatAsState(
        targetValue = if (scrollState.canScrollBackward) 1f else 0f,
        animationSpec = tween(300),
        label = "StartFadeAlpha"
    )
    val endAlpha = animateFloatAsState(
        targetValue = if (scrollState.canScrollForward) 1f else 0f,
        animationSpec = tween(300),
        label = "EndFadeAlpha"
    )
    return if (LocalLayoutDirection.current == LayoutDirection.Rtl) {
        fadingEdge(endAlpha, startAlpha, gradientWidth, bleed)
    } else {
        fadingEdge(startAlpha, endAlpha, gradientWidth, bleed)
    }
}
