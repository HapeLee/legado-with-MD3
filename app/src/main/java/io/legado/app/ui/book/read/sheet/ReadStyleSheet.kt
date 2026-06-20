package io.legado.app.ui.book.read.sheet

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.book.read.ConfigUpdate
import io.legado.app.ui.book.read.ReadBookButtonConfigItem
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.book.read.ReadBookStyleConfig
import io.legado.app.ui.widget.components.tabRow.CardTabRow
import kotlinx.coroutines.launch

private val SwipeThreshold = 300.dp

@Composable
fun ReadStyleContent(
    onOpenPaddingConfig: () -> Unit,
    onOpenHeaderFooterConfig: () -> Unit,
    onOpenMoreConfig: () -> Unit,
    onOpenBgTextConfig: (Int) -> Unit,
    onOpenTextTitle: () -> Unit,
    onOpenFontSelect: () -> Unit,
    onToggleDayNight: () -> Unit,
    onPageChanged: (Int) -> Unit = {},
    readMenuCustomIcons: Map<String, String> = emptyMap(),
    bottomBarButtons: List<ReadBookButtonConfigItem> = emptyList(),
    modifier: Modifier = Modifier,
    onIntent: (ReadBookIntent) -> Unit,
    styleConfig: ReadBookStyleConfig = ReadBookStyleConfig(),
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 2 })
    var currentPage by remember { mutableIntStateOf(0) }
    var systemMenuPage by remember { mutableIntStateOf(0) }
    var parentUserScrollEnabled by remember { mutableStateOf(true) }
    var lastActionTime by remember { mutableLongStateOf(0L) }
    val currentOnOpenMoreConfig by rememberUpdatedState(onOpenMoreConfig)

    val density = LocalDensity.current
    var isAnimatingParent by remember { mutableStateOf(false) }
    val nestedScrollConnection = remember(pagerState, density) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (isAnimatingParent) {
                    return available
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (isAnimatingParent) {
                    return available
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (isAnimatingParent) {
                    return available
                }
                return Velocity.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (isAnimatingParent) {
                    return available
                }
                val position = pagerState.currentPage + pagerState.currentPageOffsetFraction
                //Log.d("fansangg", "onPreFling: available.x = ${available.x}, systemMenuPage = $systemMenuPage, parentPage = ${pagerState.currentPage}, parentOffset = ${pagerState.currentPageOffsetFraction}, position = $position")
                if (systemMenuPage == 0 && pagerState.currentPage == 1 && position < 0.99f) {
                    val thresholdPx = with(density) { SwipeThreshold.toPx() }
                    val targetPage = if (available.x > thresholdPx || position < 0.8f) 0 else 1
                    isAnimatingParent = true
                    parentUserScrollEnabled = false
                    scope.launch {
                        try {
                            pagerState.animateScrollToPage(
                                page = targetPage,
                                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                            )
                        }  finally {
                            isAnimatingParent = false
                            parentUserScrollEnabled = true
                        }
                    }
                    return available
                }
                if (systemMenuPage == 2 && pagerState.currentPage == 1) {
                    val thresholdPx = with(density) { SwipeThreshold.toPx() }
                    if (available.x < -thresholdPx) {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastActionTime > 800L) {
                            lastActionTime = currentTime
                            currentOnOpenMoreConfig()
                        }
                        return available
                    }
                }
                return Velocity.Zero
            }
        }
    }

    val pageHeights = remember { mutableStateMapOf<Int, Int>() }
    val animatedHeight by rememberPagerAnimatedHeight(pagerState, pageHeights)

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            currentPage = page
            onPageChanged(page)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth(),
    ) {
        HorizontalPager(
            state = pagerState,
            verticalAlignment = Alignment.Top,
            userScrollEnabled = parentUserScrollEnabled,
            modifier = Modifier
                .weight(1f, fill = false)
                .clipToBounds()
                .pagerHeight(animatedHeight),
        ) { page ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { size ->
                        pageHeights[page] = size.height
                    }
            ) {
                when (page) {
                    0 -> GlobalThemePage(
                        onToggleDayNight = onToggleDayNight,
                        onOpenBgTextConfig = onOpenBgTextConfig,
                        onOpenTextTitle = onOpenTextTitle,
                        onOpenPaddingConfig = onOpenPaddingConfig,
                        onShareLayoutChange = { shareLayout ->
                            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.ShareLayout(shareLayout)))
                        },
                        onStyleSelect = { index ->
                            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.StyleSelect(index)))
                        },
                        modifier = Modifier.padding(horizontal = 16.dp),
                        onIntent = onIntent,
                        styleConfig = styleConfig,
                    )

                    1 -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .nestedScroll(nestedScrollConnection)
                        ) {
                            SystemMenuPage(
                                customIcons = readMenuCustomIcons,
                                bottomBarButtons = bottomBarButtons,
                                onIntent = onIntent,
                                onPageChanged = { systemMenuPage = it }
                            )
                        }
                    }
                }
            }
        }

        val tabTitles = listOf(
            stringResource(R.string.read_config_global_theme),
            stringResource(R.string.read_config_menu_system),
            stringResource(R.string.header_footer),
            stringResource(R.string.more_setting),
        )
        CardTabRow(
            tabTitles = tabTitles,
            selectedTabIndex = currentPage,
            onTabSelected = { index ->
                when (index) {
                    0, 1 -> {
                        scope.launch {
                            pagerState.animateScrollToPage(
                                page = index,
                                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                            )
                        }
                    }
                    2 -> onOpenHeaderFooterConfig()
                    3 -> onOpenMoreConfig()
                }
            },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
        )
    }
}

internal fun Modifier.pagerHeight(height: Dp) = this.layout { measurable, constraints ->
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
internal fun rememberPagerAnimatedHeight(
    pagerState: androidx.compose.foundation.pager.PagerState,
    pageHeights: Map<Int, Int>
): androidx.compose.runtime.State<Dp> {
    val density = LocalDensity.current
    return remember(pagerState, density) {
        derivedStateOf {
            val pageCount = pagerState.pageCount
            val position = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                .coerceIn(0f, (pageCount - 1).coerceAtLeast(0).toFloat())
            val floorPage = position.toInt().coerceIn(0, pageCount - 1)
            val ceilPage = (floorPage + 1).coerceIn(0, pageCount - 1)

            val floorHeight = pageHeights[floorPage] ?: 0
            val ceilHeight = pageHeights[ceilPage] ?: floorHeight

            val fraction = position - floorPage

            val startHeight = if (floorHeight > 0) floorHeight else (pageHeights.values.firstOrNull() ?: 0)
            val endHeight = if (ceilHeight > 0) ceilHeight else startHeight

            val interpolated = startHeight + (endHeight - startHeight) * fraction

            if (interpolated > 0) {
                with(density) { interpolated.toDp() }
            } else {
                Dp.Unspecified
            }
        }
    }
}
