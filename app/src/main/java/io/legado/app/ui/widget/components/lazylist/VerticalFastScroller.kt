package io.legado.app.ui.widget.components.lazylist

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastLastOrNull
import androidx.compose.ui.util.fastMaxBy
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.lazylist.Scroller.STICKY_HEADER_KEY_PREFIX
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/**
 * Draws vertical fast scroller to a lazy list
 *
 * Set key with [STICKY_HEADER_KEY_PREFIX] prefix to any sticky header item in the list.
 * This code is copied from the repository https://github.com/komikku-app/komikku
 */
@Composable
fun VerticalFastScroller(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    thumbColor: Color = LegadoTheme.colorScheme.primary,
    topContentPadding: Dp = Dp.Hairline,
    bottomContentPadding: Dp = Dp.Hairline,
    endContentPadding: Dp = Dp.Hairline,
    content: @Composable () -> Unit,
) {
    SubcomposeLayout(modifier = modifier) { constraints ->
        val contentPlaceable = subcompose("content", content).map { it.measure(constraints) }
        val contentHeight = contentPlaceable.fastMaxBy { it.height }?.height ?: 0
        val contentWidth = contentPlaceable.fastMaxBy { it.width }?.width ?: 0

        val scrollerConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val scrollerPlaceable = subcompose("scroller") {
            val layoutInfo = listState.layoutInfo
            if (layoutInfo.visibleItemsInfo.isEmpty() || layoutInfo.totalItemsCount == 0) return@subcompose

            val thumbTopPadding = with(LocalDensity.current) { topContentPadding.toPx() }
            var thumbOffsetY by remember(thumbTopPadding) { mutableFloatStateOf(thumbTopPadding) }

            val dragInteractionSource = remember { MutableInteractionSource() }
            val isThumbDragged by dragInteractionSource.collectIsDraggedAsState()

            // Keep the thumb in its active (large) form while dragging and for a moment after release
            var recentlyTouched by remember { mutableStateOf(false) }
            LaunchedEffect(isThumbDragged) {
                if (isThumbDragged) {
                    recentlyTouched = true
                } else {
                    delay(ThumbActiveDurationMillis.milliseconds)
                    recentlyTouched = false
                }
            }
            val thumbActive = isThumbDragged || recentlyTouched

            // The capsule fades out a moment after it has shrunk back to its small form,
            // and stays/reappears as long as the list is being used (scrolled or thumb-dragged)
            var capsuleVisible by remember { mutableStateOf(true) }
            LaunchedEffect(isThumbDragged, listState.isScrollInProgress, recentlyTouched) {
                if (isThumbDragged || listState.isScrollInProgress || recentlyTouched) {
                    capsuleVisible = true
                } else {
                    delay(ThumbHideDelayMillis.milliseconds)
                    capsuleVisible = false
                }
            }

            // listState.isScrollInProgress occasionally flickers
            val scrollStateTracker = remember { MutableData(listState.isScrollInProgress) }
            val stableScrollInProgress = scrollStateTracker.value || listState.isScrollInProgress
            scrollStateTracker.value = listState.isScrollInProgress
            val anyScrollInProgress = stableScrollInProgress || isThumbDragged

            val thumbBottomPadding = with(LocalDensity.current) { bottomContentPadding.toPx() }
            val heightPx = contentHeight.toFloat() -
                    thumbTopPadding -
                    thumbBottomPadding -
                    listState.layoutInfo.afterContentPadding
            val thumbHeightPx = with(LocalDensity.current) { ThumbLength.toPx() }
            val trackHeightPx = heightPx - thumbHeightPx
            val scrollHeightPx = contentHeight.toFloat() -
                    listState.layoutInfo.beforeContentPadding -
                    listState.layoutInfo.afterContentPadding -
                    thumbBottomPadding

            val visibleItems = layoutInfo.visibleItemsInfo
            val topItem = visibleItems.fastFirstOrNull {
                it.bottom >= 0 &&
                        (it.key as? String)?.startsWith(STICKY_HEADER_KEY_PREFIX)?.not() ?: true
            } ?: visibleItems.first()
            val bottomItem = visibleItems.fastLastOrNull {
                it.top <= scrollHeightPx &&
                        (it.key as? String)?.startsWith(STICKY_HEADER_KEY_PREFIX)?.not() ?: true
            } ?: visibleItems.last()

            val topHiddenProportion = -1f * topItem.top / topItem.size.coerceAtLeast(1)
            val bottomHiddenProportion = (bottomItem.bottom - scrollHeightPx) / bottomItem.size.coerceAtLeast(1)
            val previousSections = topHiddenProportion + topItem.index
            val remainingSections = bottomHiddenProportion + (layoutInfo.totalItemsCount - (bottomItem.index + 1))
            val scrollableSections = previousSections + remainingSections

            val layoutChangeTracker = remember { MutableData(scrollableSections) }
            val layoutChanged = !anyScrollInProgress && abs(layoutChangeTracker.value - scrollableSections) > 0.1
            layoutChangeTracker.value = scrollableSections

            val estimateConfidence = remember { MutableData(remainingSections) }
            if (layoutChanged) estimateConfidence.value = remainingSections
            val maxRemainingSections = remember(estimateConfidence.value) { scrollableSections }
            estimateConfidence.value = max(estimateConfidence.value, remainingSections)

            if (maxRemainingSections < 0.5) return@subcompose

            // When thumb dragged
            LaunchedEffect(thumbOffsetY) {
                if (layoutInfo.totalItemsCount == 0 || !isThumbDragged) return@LaunchedEffect
                val thumbProportion = (thumbOffsetY - thumbTopPadding) / trackHeightPx
                if (thumbProportion <= 0.001f) {
                    estimateConfidence.value = -1f
                    listState.scrollToItem(index = 0, scrollOffset = 0)
                    return@LaunchedEffect
                }
                val scrollRemainingSections = (1f - thumbProportion) * maxRemainingSections
                val currentSection = layoutInfo.totalItemsCount - scrollRemainingSections
                val scrollSectionIndex = currentSection.toInt().coerceAtMost(layoutInfo.totalItemsCount)
                val expectedScrollItem = visibleItems.find { it.index == scrollSectionIndex } ?: visibleItems.first()
                val scrollRelativeOffset = expectedScrollItem.size * (currentSection - scrollSectionIndex)
                val scrollSectionOffset = (scrollRelativeOffset - scrollHeightPx).roundToInt()
                val scrollItemIndex = scrollSectionIndex.coerceIn(0, layoutInfo.totalItemsCount - 1)
                val scrollItemOffset = scrollSectionOffset + (scrollSectionIndex - scrollItemIndex) * bottomItem.size
                listState.scrollToItem(index = scrollItemIndex, scrollOffset = scrollItemOffset)
            }

            // When list scrolled
            if (layoutInfo.totalItemsCount != 0 && !isThumbDragged) {
                val proportion = 1f - remainingSections / maxRemainingSections
                thumbOffsetY = trackHeightPx * proportion + thumbTopPadding
            }

            // The thumb rests as a small translucent capsule and grows to its full pill form
            // while touched (kept for a moment after release)
            val idleThumbColor = LegadoTheme.colorScheme.outlineVariant.copy(alpha = IdleThumbAlpha)
            val thumbFormProgress by animateFloatAsState(
                targetValue = if (thumbActive) 1f else 0f,
                animationSpec = tween(durationMillis = ThumbFormDurationMillis),
                label = "thumbForm",
            )
            val animatedThumbColor by animateColorAsState(
                targetValue = if (thumbActive) thumbColor else idleThumbColor,
                animationSpec = tween(durationMillis = ThumbFormDurationMillis),
                label = "thumbColor",
            )
            val thumbVisibilityAlpha by animateFloatAsState(
                targetValue = if (capsuleVisible) 1f else 0f,
                animationSpec = tween(durationMillis = ThumbHideFadeMillis),
                label = "thumbVisibility",
            )
            val thumbDraggable = !listState.isScrollInProgress && capsuleVisible
            val thumbWidth =
                IdleThumbThickness + (ThumbThickness - IdleThumbThickness) * thumbFormProgress
            val thumbHeight = IdleThumbLength + (ThumbLength - IdleThumbLength) * thumbFormProgress

            Box(
                modifier = Modifier
                    .offset { IntOffset(0, thumbOffsetY.roundToInt()) }
                    .then(
                        // Recompose opts
                        if (thumbDraggable) {
                            Modifier.draggable(
                                interactionSource = dragInteractionSource,
                                orientation = Orientation.Vertical,
                                state = rememberDraggableState { delta ->
                                    val newOffsetY = thumbOffsetY + delta
                                    thumbOffsetY = newOffsetY.coerceIn(
                                        thumbTopPadding,
                                        thumbTopPadding + trackHeightPx,
                                    )
                                },
                            )
                        } else {
                            Modifier
                        },
                    )
                    .then(
                        // Exclude thumb from gesture area only when needed
                        if (thumbDraggable && !isThumbDragged) {
                            Modifier.systemGestureExclusion()
                        } else {
                            Modifier
                        },
                    )
                    .height(ThumbLength)
                    .padding(start = 8.dp, end = 4.dp)
                    .padding(end = endContentPadding)
                    .width(ThumbThickness),
                contentAlignment = Alignment.CenterEnd,
            ) {
                // The pill keeps a perfect semicircular end cap at every size
                Box(
                    modifier = Modifier
                        .size(thumbWidth, thumbHeight)
                        .alpha(thumbVisibilityAlpha)
                        .background(
                            color = animatedThumbColor,
                            shape = RoundedCornerShape(thumbWidth / 2)
                        ),
                )
            }
        }.map { it.measure(scrollerConstraints) }
        val scrollerWidth = scrollerPlaceable.fastMaxBy { it.width }?.width ?: 0

        layout(contentWidth, contentHeight) {
            contentPlaceable.fastForEach {
                it.place(0, 0)
            }
            scrollerPlaceable.fastForEach {
                it.placeRelative(contentWidth - scrollerWidth, 0)
            }
        }
    }
}

@Composable
private fun rememberColumnWidthSums(
    columns: GridCells,
    horizontalArrangement: Arrangement.Horizontal,
    contentPadding: PaddingValues,
) = remember<Density.(Constraints) -> List<Int>>(
    columns,
    horizontalArrangement,
    contentPadding,
) {
    { constraints ->
        require(constraints.maxWidth != Constraints.Infinity) {
            "LazyVerticalGrid's width should be bound by parent"
        }
        val horizontalPadding = contentPadding.calculateStartPadding(LayoutDirection.Ltr) +
                contentPadding.calculateEndPadding(LayoutDirection.Ltr)
        val gridWidth = constraints.maxWidth - horizontalPadding.roundToPx()
        with(columns) {
            calculateCrossAxisCellSizes(
                gridWidth,
                horizontalArrangement.spacing.roundToPx(),
            ).toMutableList().apply {
                for (i in 1..<size) {
                    this[i] += this[i - 1]
                }
            }
        }
    }
}

/*
    VerticalGridFastScroller was written with a regularity assumption, so it is slightly inaccurate for layouts with
    varying row sizes.
 */
// TODO: Ideally rewrite VerticalGridFastScroller to use similar logic as VerticalFastScroller
@Composable
fun VerticalGridFastScroller(
    state: LazyGridState,
    columns: GridCells,
    arrangement: Arrangement.Horizontal,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    thumbColor: Color = LegadoTheme.colorScheme.primary,
    topContentPadding: Dp = Dp.Hairline,
    bottomContentPadding: Dp = Dp.Hairline,
    endContentPadding: Dp = Dp.Hairline,
    leadingItemExtent: Int? = null,
    content: @Composable () -> Unit,
) {
    val slotSizesSums = rememberColumnWidthSums(
        columns = columns,
        horizontalArrangement = arrangement,
        contentPadding = contentPadding,
    )
    val firstGridIndex = if (leadingItemExtent == null) 0 else 1
    val leadingExtent = leadingItemExtent ?: 0

    SubcomposeLayout(modifier = modifier) { constraints ->
        val contentPlaceable = subcompose("content", content).map { it.measure(constraints) }
        val contentHeight = contentPlaceable.fastMaxBy { it.height }?.height ?: 0
        val contentWidth = contentPlaceable.fastMaxBy { it.width }?.width ?: 0

        val scrollerConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val scrollerPlaceable = subcompose("scroller") {
            val layoutInfo = state.layoutInfo
            val dragInteractionSource = remember { MutableInteractionSource() }
            val isThumbDragged by dragInteractionSource.collectIsDraggedAsState()
            if (layoutInfo.totalItemsCount == 0 ||
                !isThumbDragged && !state.canScrollBackward && !state.canScrollForward
            ) {
                return@subcompose
            }
            val thumbTopPadding = with(LocalDensity.current) { topContentPadding.toPx() }
            var thumbOffsetY by remember(thumbTopPadding) { mutableFloatStateOf(thumbTopPadding) }

            // Keep the thumb in its active (large) form while dragging and for a moment after release
            var recentlyTouched by remember { mutableStateOf(false) }
            LaunchedEffect(isThumbDragged) {
                if (isThumbDragged) {
                    recentlyTouched = true
                } else {
                    delay(ThumbActiveDurationMillis)
                    recentlyTouched = false
                }
            }
            val thumbActive = isThumbDragged || recentlyTouched

            // The capsule fades out a moment after it has shrunk back to its small form,
            // and stays/reappears as long as the list is being used (scrolled or thumb-dragged)
            var capsuleVisible by remember { mutableStateOf(true) }
            LaunchedEffect(isThumbDragged, state.isScrollInProgress, recentlyTouched) {
                if (isThumbDragged || state.isScrollInProgress || recentlyTouched) {
                    capsuleVisible = true
                } else {
                    delay(ThumbHideDelayMillis)
                    capsuleVisible = false
                }
            }

            val thumbBottomPadding = with(LocalDensity.current) { bottomContentPadding.toPx() }
            val heightPx = contentHeight.toFloat() -
                    thumbTopPadding -
                    thumbBottomPadding -
                    state.layoutInfo.afterContentPadding
            val thumbHeightPx = with(LocalDensity.current) { ThumbLength.toPx() }
            val trackHeightPx = heightPx - thumbHeightPx

            if (trackHeightPx <= 0f) return@subcompose
            val columnCount = remember(slotSizesSums, constraints.maxWidth) {
                slotSizesSums(constraints).size.coerceAtLeast(1)
            }
            val firstBook = layoutInfo.visibleItemsInfo.firstOrNull { it.index >= firstGridIndex }
            val rowsBefore = firstBook?.let { (it.index - firstGridIndex) / columnCount } ?: 0
            val measuredRowHeight = firstBook?.let { first ->
                val last = layoutInfo.visibleItemsInfo.last()
                val rows = 1 + (last.index - firstGridIndex) / columnCount - rowsBefore
                ((last.offset.y + last.size.height - first.offset.y).toFloat() / rows)
            }
            var lastRowHeight by remember(state, firstGridIndex, columnCount) { mutableFloatStateOf(0f) }
            SideEffect { if (measuredRowHeight != null && measuredRowHeight > 0f) lastRowHeight = measuredRowHeight }
            // A tall leading item can fill the viewport before any of the book rows are measured.
            val estimatedRowHeight = measuredRowHeight?.takeIf { it > 0f }
                ?: lastRowHeight.takeIf { it > 0f }
                ?: (heightPx / 3f).coerceAtLeast(1f)
            var dragRowHeight by remember { mutableFloatStateOf(0f) }
            val rowHeight = dragRowHeight.takeIf { it > 0f } ?: estimatedRowHeight
            val bookCount = (layoutInfo.totalItemsCount - firstGridIndex).coerceAtLeast(0)
            val rowCount = if (bookCount == 0) 0 else 1 + (bookCount - 1) / columnCount
            val extraScrollRange = (leadingExtent + rowCount * rowHeight - heightPx).coerceAtLeast(1f)

            // When list scrolled
            val proportion = when {
                !state.canScrollBackward -> 0f
                !state.canScrollForward -> 1f
                else -> {
                    val scrollOffset = if (state.firstVisibleItemIndex < firstGridIndex || firstBook == null) {
                        state.firstVisibleItemScrollOffset.toFloat()
                    } else {
                        leadingExtent + rowsBefore * rowHeight - firstBook.offset.y
                    }
                    (scrollOffset / extraScrollRange).coerceIn(0f, 1f)
                }
            }
            LaunchedEffect(proportion, trackHeightPx, isThumbDragged) {
                if (isThumbDragged) return@LaunchedEffect
                thumbOffsetY = trackHeightPx * proportion + thumbTopPadding
            }

            // The thumb rests as a small translucent capsule and grows to its full pill form
            // while touched (kept for a moment after release)
            val idleThumbColor = LegadoTheme.colorScheme.outlineVariant.copy(alpha = IdleThumbAlpha)
            val thumbFormProgress by animateFloatAsState(
                targetValue = if (thumbActive) 1f else 0f,
                animationSpec = tween(durationMillis = ThumbFormDurationMillis),
                label = "thumbForm",
            )
            val animatedThumbColor by animateColorAsState(
                targetValue = if (thumbActive) thumbColor else idleThumbColor,
                animationSpec = tween(durationMillis = ThumbFormDurationMillis),
                label = "thumbColor",
            )
            val thumbVisibilityAlpha by animateFloatAsState(
                targetValue = if (capsuleVisible) 1f else 0f,
                animationSpec = tween(durationMillis = ThumbHideFadeMillis),
                label = "thumbVisibility",
            )
            val thumbDraggable = isThumbDragged || (!state.isScrollInProgress && capsuleVisible)
            val thumbWidth =
                IdleThumbThickness + (ThumbThickness - IdleThumbThickness) * thumbFormProgress
            val thumbHeight = IdleThumbLength + (ThumbLength - IdleThumbLength) * thumbFormProgress

            Box(
                modifier = Modifier
                    .offset { IntOffset(0, thumbOffsetY.roundToInt()) }
                    .then(
                        // Recompose opts
                        if (thumbDraggable) {
                            Modifier.draggable(
                                interactionSource = dragInteractionSource,
                                orientation = Orientation.Vertical,
                                onDragStarted = { dragRowHeight = estimatedRowHeight },
                                onDragStopped = { dragRowHeight = 0f },
                                state = rememberDraggableState { delta ->
                                    thumbOffsetY = (thumbOffsetY + delta).coerceIn(
                                        thumbTopPadding,
                                        thumbTopPadding + trackHeightPx,
                                    )
                                    val scrollRatio = (thumbOffsetY - thumbTopPadding) / trackHeightPx
                                    val scrollAmt = scrollRatio * extraScrollRange
                                    when {
                                        scrollRatio <= 0f -> state.requestScrollToItem(0)
                                        scrollRatio >= 1f -> state.requestScrollToItem(layoutInfo.totalItemsCount - 1)
                                        scrollAmt < leadingExtent || rowCount == 0 -> {
                                            state.requestScrollToItem(0, scrollAmt.roundToInt())
                                        }
                                        else -> {
                                            val bookScroll = scrollAmt - leadingExtent
                                            val rowNumber = (bookScroll / rowHeight).toInt().coerceIn(0, rowCount - 1)
                                            state.requestScrollToItem(
                                                index = firstGridIndex + columnCount * rowNumber,
                                                scrollOffset = (bookScroll - rowNumber * rowHeight).roundToInt(),
                                            )
                                        }
                                    }
                                },
                            )
                        } else {
                            Modifier
                        },
                    )
                    .then(
                        // Exclude thumb from gesture area only when needed
                        if (thumbDraggable && !isThumbDragged) {
                            Modifier.systemGestureExclusion()
                        } else {
                            Modifier
                        },
                    )
                    .height(ThumbLength)
                    .testTag("fast-scroll-grid-thumb")
                    .padding(start = 8.dp, end = 4.dp)
                    .padding(end = endContentPadding)
                    .width(ThumbThickness),
                contentAlignment = Alignment.CenterEnd,
            ) {
                // The pill keeps a perfect semicircular end cap at every size
                Box(
                    modifier = Modifier
                        .size(thumbWidth, thumbHeight)
                        .alpha(thumbVisibilityAlpha)
                        .background(
                            color = animatedThumbColor,
                            shape = RoundedCornerShape(thumbWidth / 2)
                        ),
                )
            }
        }.map { it.measure(scrollerConstraints) }
        val scrollerWidth = scrollerPlaceable.fastMaxBy { it.width }?.width ?: 0

        layout(contentWidth, contentHeight) {
            contentPlaceable.fastForEach {
                it.place(0, 0)
            }
            scrollerPlaceable.fastForEach {
                it.placeRelative(contentWidth - scrollerWidth, 0)
            }
        }
    }
}

private class MutableData<T>(var value: T)

object Scroller {
    const val STICKY_HEADER_KEY_PREFIX = "sticky:"
}

private val ThumbLength = 48.dp
private val ThumbThickness = 12.dp
private val IdleThumbLength = 36.dp
private val IdleThumbThickness = 4.dp
private const val IdleThumbAlpha = 0.8f
private const val ThumbFormDurationMillis = 250
private const val ThumbActiveDurationMillis = 3000L
private const val ThumbHideDelayMillis = 3000L
private const val ThumbHideFadeMillis = 250

private val LazyListItemInfo.top: Int
    get() = offset

private val LazyListItemInfo.bottom: Int
    get() = offset + size
