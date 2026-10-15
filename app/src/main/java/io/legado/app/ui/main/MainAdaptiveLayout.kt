@file:OptIn(androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi::class)

package io.legado.app.ui.main

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.waterfall
import androidx.compose.material3.adaptive.HingeInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import io.legado.app.ui.widget.components.navigation.MultiPaneMode
import io.legado.app.ui.widget.components.navigation.shouldUseListDetail
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

internal const val MainPaneMinWidth = 360f
internal const val MainPaneMinHeight = 320f
internal const val MainPaneGap = 24f

// IME affects the editor's insets, not the number of panes or navigation hierarchy.
internal val mainAdaptiveInsets: WindowInsets
    @Composable get() = WindowInsets.systemBars.union(WindowInsets.displayCutout)
        .union(WindowInsets.waterfall)

/** Bounds and hinge coordinates use dp in the current app window, never device dimensions. */
@Stable
internal data class MainAdaptiveLayoutEnvironment(
    val bounds: Rect,
    val windowSize: DpSize,
    val hinges: ImmutableList<HingeInfo>,
    val mode: MultiPaneMode,
    val navigationRailWidth: Dp,
)

@Stable
internal data class MainPaneLayout(
    val listBounds: Rect,
    val detailBounds: Rect,
    val usesHinge: Boolean,
)

internal fun mainContentBounds(
    size: DpSize,
    leftInset: Dp,
    topInset: Dp,
    rightInset: Dp,
    bottomInset: Dp,
): Rect {
    val left = leftInset.value.coerceIn(0f, size.width.value)
    val top = topInset.value.coerceIn(0f, size.height.value)
    return Rect(
        left, top,
        (size.width.value - rightInset.value).coerceIn(left, size.width.value),
        (size.height.value - bottomInset.value).coerceIn(top, size.height.value)
    )
}

@Composable
internal fun currentMainAdaptiveLayoutEnvironment(
    multiPaneMode: String,
    navigationRailWidth: Dp,
): MainAdaptiveLayoutEnvironment {
    val localDensity = LocalDensity.current
    val density = localDensity.density
    val direction = LocalLayoutDirection.current
    val size = LocalWindowInfo.current.containerSize
    val insets = mainAdaptiveInsets
    val posture = currentWindowAdaptiveInfoV2().windowPosture
    val windowSize = with(localDensity) { DpSize(size.width.toDp(), size.height.toDp()) }
    return MainAdaptiveLayoutEnvironment(
        bounds = mainContentBounds(
            windowSize,
            (insets.getLeft(localDensity, direction) / density).dp,
            (insets.getTop(localDensity) / density).dp,
            (insets.getRight(localDensity, direction) / density).dp,
            (insets.getBottom(localDensity) / density).dp,
        ),
        windowSize = windowSize,
        hinges = posture.hingeList.map { hinge ->
            HingeInfo(
                Rect(
                    hinge.bounds.left / density, hinge.bounds.top / density,
                    hinge.bounds.right / density, hinge.bounds.bottom / density
                ),
                hinge.isFlat, hinge.isVertical, hinge.isSeparating, hinge.isOccluding,
            )
        }.toImmutableList(),
        mode = MultiPaneMode.parse(multiPaneMode),
        navigationRailWidth = navigationRailWidth,
    )
}

/** Leave a gutter around separating folds, including zero-width folds without occlusion. */
internal fun MainAdaptiveLayoutEnvironment.safeRegions(): List<Rect> {
    var regions = listOf(bounds)
    for (hinge in hinges.filter { it.isSeparating || it.isOccluding }) {
        regions = regions.flatMap { region ->
            val fold = hinge.bounds
            if (hinge.isVertical && fold.left < region.right && fold.right > region.left &&
                fold.bottom > region.top && fold.top < region.bottom
            ) {
                listOf(
                    Rect(
                        region.left,
                        region.top,
                        (fold.left - MainPaneGap / 2).coerceIn(region.left, region.right),
                        region.bottom
                    ),
                    Rect(
                        (fold.right + MainPaneGap / 2).coerceIn(region.left, region.right),
                        region.top,
                        region.right,
                        region.bottom
                    ),
                ).filter { it.width > 0f }
            } else if (!hinge.isVertical && fold.top < region.bottom && fold.bottom > region.top &&
                fold.right > region.left && fold.left < region.right
            ) {
                listOf(
                    Rect(
                        region.left,
                        region.top,
                        region.right,
                        (fold.top - MainPaneGap / 2).coerceIn(region.top, region.bottom)
                    ),
                    Rect(
                        region.left,
                        (fold.bottom + MainPaneGap / 2).coerceIn(region.top, region.bottom),
                        region.right,
                        region.bottom
                    ),
                ).filter { it.height > 0f }
            } else listOf(region)
        }
    }
    return regions
}

internal fun MainAdaptiveLayoutEnvironment.paneLayout(isContainer: Boolean): MainPaneLayout? {
    if (mode == MultiPaneMode.Off) return null
    if (mode == MultiPaneMode.Landscape && windowSize.width <= windowSize.height) return null
    val listMinWidth = MainPaneMinWidth + if (isContainer) navigationRailWidth.value else 0f
    val regions = safeRegions()
    // A fold is a natural split, even when the entire window is below a canonical breakpoint.
    if (regions.size > 1) {
        for (list in regions) {
            for (detail in regions) {
                val ordered = list.right <= detail.left || list.bottom <= detail.top
                if (ordered && list.width >= listMinWidth && detail.width >= MainPaneMinWidth &&
                    list.height >= MainPaneMinHeight && detail.height >= MainPaneMinHeight
                ) return MainPaneLayout(list, detail, usesHinge = true)
            }
        }
        return null
    }
    val region = regions.singleOrNull() ?: return null
    val minimumWidth = listMinWidth + MainPaneGap + MainPaneMinWidth
    if (region.width < minimumWidth || region.height < MainPaneMinHeight) return null
    if (!shouldUseListDetail(
            mode.value, WindowSizeClass(region.width, region.height),
            IntSize(windowSize.width.value.toInt(), windowSize.height.value.toInt())
        )
    ) return null
    val preferred = if (isContainer) maxOf(420f, listMinWidth) else listMinWidth
    val listWidth = preferred.coerceAtMost(region.width - MainPaneGap - MainPaneMinWidth)
    return MainPaneLayout(
        Rect(region.left, region.top, region.left + listWidth, region.bottom),
        Rect(region.left + listWidth + MainPaneGap, region.top, region.right, region.bottom),
        usesHinge = false,
    )
}

internal fun MainAdaptiveLayoutEnvironment.singlePaneBounds(): Rect =
    safeRegions().maxByOrNull { it.width * it.height } ?: bounds
