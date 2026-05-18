package io.legado.app.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Applies a horizontal fading edge effect to a scrollable container.
 *
 * @param leftAlpha  0f = fully faded, 1f = fully visible
 * @param rightAlpha 0f = fully faded, 1f = fully visible
 * @param gradientWidth width of the fade gradient on each side
 */
fun Modifier.fadingEdge(
    leftAlpha: Float,
    rightAlpha: Float,
    gradientWidth: Dp = 24.dp
): Modifier = graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val width = size.width
        val gradientWidthPx = gradientWidth.toPx()
        val leftStop = gradientWidthPx / width
        val rightStop = 1f - (gradientWidthPx / width)
        drawRect(
            brush = Brush.horizontalGradient(
                colorStops = arrayOf(
                    0f to Color.Black.copy(alpha = 1f - leftAlpha),
                    leftStop to Color.Black,
                    rightStop to Color.Black,
                    1f to Color.Black.copy(alpha = 1f - rightAlpha)
                ),
                startX = 0f,
                endX = width
            ),
            blendMode = BlendMode.DstIn
        )
    }

/**
 * Convenience overload that derives fade alphas from a [LazyListState].
 * Left fade appears when scrolled past the start; right fade appears when more content is available.
 */
@Composable
fun Modifier.fadingEdge(
    listState: LazyListState,
    gradientWidth: Dp = 24.dp
): Modifier {
    val showLeft by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }
    val showRight by remember {
        derivedStateOf { listState.canScrollForward }
    }
    val leftAlpha by animateFloatAsState(
        targetValue = if (showLeft) 1f else 0f,
        animationSpec = tween(200),
        label = "LeftFadeAlpha"
    )
    val rightAlpha by animateFloatAsState(
        targetValue = if (showRight) 1f else 0f,
        animationSpec = tween(200),
        label = "RightFadeAlpha"
    )
    return fadingEdge(leftAlpha, rightAlpha, gradientWidth)
}
