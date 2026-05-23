package io.legado.app.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
 */
@Composable
fun Modifier.fadingEdge(
    listState: LazyListState,
    gradientWidth: Dp = 24.dp
): Modifier {
    val leftAlpha by animateFloatAsState(
        targetValue = if (listState.canScrollBackward) 1f else 0f,
        animationSpec = tween(300),
        label = "LeftFadeAlpha"
    )
    val rightAlpha by animateFloatAsState(
        targetValue = if (listState.canScrollForward) 1f else 0f,
        animationSpec = tween(300),
        label = "RightFadeAlpha"
    )
    return fadingEdge(leftAlpha, rightAlpha, gradientWidth)
}

/**
 * Convenience overload that derives fade alphas from a [PagerState].
 */
@Composable
fun Modifier.fadingEdge(
    pagerState: PagerState,
    gradientWidth: Dp = 24.dp
): Modifier {
    val leftAlpha by animateFloatAsState(
        targetValue = if (pagerState.canScrollBackward) 1f else 0f,
        animationSpec = tween(300),
        label = "LeftFadeAlpha"
    )
    val rightAlpha by animateFloatAsState(
        targetValue = if (pagerState.canScrollForward) 1f else 0f,
        animationSpec = tween(300),
        label = "RightFadeAlpha"
    )
    return fadingEdge(leftAlpha, rightAlpha, gradientWidth)
}

/**
 * Convenience overload that derives fade alphas from a [ScrollState].
 */
@Composable
fun Modifier.fadingEdge(
    scrollState: ScrollState,
    gradientWidth: Dp = 24.dp
): Modifier {
    val leftAlpha by animateFloatAsState(
        targetValue = if (scrollState.canScrollBackward) 1f else 0f,
        animationSpec = tween(300),
        label = "LeftFadeAlpha"
    )
    val rightAlpha by animateFloatAsState(
        targetValue = if (scrollState.canScrollForward) 1f else 0f,
        animationSpec = tween(300),
        label = "RightFadeAlpha"
    )
    return fadingEdge(leftAlpha, rightAlpha, gradientWidth)
}
