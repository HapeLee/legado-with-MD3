package io.legado.app.ui.widget.components.pager

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

fun Modifier.pagerHeight(height: Dp) = this.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val layoutHeight = if (height != Dp.Unspecified) {
        height.roundToPx().coerceIn(constraints.minHeight, constraints.maxHeight)
    } else {
        placeable.height
    }
    layout(placeable.width, layoutHeight) {
        placeable.placeRelative(0, 0)
    }
}

@Composable
fun rememberPagerAnimatedHeight(
    pagerState: PagerState,
    pageHeights: Map<Int, Int>,
    fallbackHeight: Dp = Dp.Unspecified,
    heightAnimationSpec: FiniteAnimationSpec<Dp>? = null,
): State<Dp> {
    val density = LocalDensity.current
    val fallbackHeightPx = with(density) {
        fallbackHeight.takeIf { it != Dp.Unspecified }?.roundToPx() ?: 0
    }
    val targetHeight = remember(pagerState, pageHeights, density, fallbackHeightPx) {
        derivedStateOf {
            val pageCount = pagerState.pageCount
            if (pageCount <= 0) {
                return@derivedStateOf Dp.Unspecified
            }
            val position = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                .coerceIn(0f, (pageCount - 1).toFloat())
            val floorPage = position.toInt().coerceIn(0, pageCount - 1)
            val ceilPage = (floorPage + 1).coerceIn(0, pageCount - 1)

            val floorHeight = pageHeights[floorPage] ?: 0
            val ceilHeight = pageHeights[ceilPage] ?: floorHeight
            val fraction = position - floorPage

            val knownHeight = pageHeights.values.firstOrNull { it > 0 } ?: fallbackHeightPx
            val unknownPageHeight = fallbackHeightPx.takeIf { it > 0 } ?: knownHeight
            val startHeight = if (floorHeight > 0) floorHeight else unknownPageHeight
            val endHeight = if (ceilHeight > 0) ceilHeight else unknownPageHeight
            val interpolated = startHeight + (endHeight - startHeight) * fraction

            if (interpolated > 0) {
                with(density) { interpolated.toDp() }
            } else {
                Dp.Unspecified
            }
        }
    }
    if (heightAnimationSpec == null) {
        return targetHeight
    }
    return if (targetHeight.value == Dp.Unspecified) {
        targetHeight
    } else {
        animateDpAsState(
            targetValue = targetHeight.value,
            animationSpec = heightAnimationSpec,
        )
    }
}
