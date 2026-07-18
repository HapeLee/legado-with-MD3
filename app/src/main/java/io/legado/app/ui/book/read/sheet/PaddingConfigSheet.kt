package io.legado.app.ui.book.read.sheet

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.book.read.ReadSheetConfigUiState
import io.legado.app.ui.book.read.ConfigUpdate
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.pager.pagerHeight
import io.legado.app.ui.widget.components.pager.rememberPagerAnimatedHeight
import io.legado.app.ui.widget.components.pager.rememberPagerFlingPassThroughConnection
import io.legado.app.ui.widget.components.settingItem.TinySliderSettingItem
import io.legado.app.ui.widget.components.tabRow.CardTabRow
import kotlinx.coroutines.launch

@Composable
fun PaddingConfigSheet(
    config: ReadSheetConfigUiState,
    onDismissRequest: () -> Unit,
    onIntent: (ReadBookIntent) -> Unit,
) {
    AppModalBottomSheet(
        show = true,
        onDismissRequest = {
            onIntent(ReadBookIntent.SaveReadStyleConfig)
            onDismissRequest()
        },
        title = stringResource(R.string.padding),
    ) {
        PaddingConfigContent(
            config = config,
            onIntent = onIntent,
            modifier = Modifier
                .padding(bottom = 16.dp),
        )
    }
}

@Composable
fun PaddingConfigContent(
    config: ReadSheetConfigUiState,
    onIntent: (ReadBookIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val tabTitles = listOf(
        stringResource(R.string.main_body),
        stringResource(R.string.header),
        stringResource(R.string.footer),
    )
    val pagerState = rememberPagerState(pageCount = { 3 })
    var selectedTab by remember { mutableIntStateOf(0) }
    var clickScrollCount by remember { mutableIntStateOf(0) }
    val childPagerNestedScrollConnection = rememberPagerFlingPassThroughConnection(
        state = pagerState,
        orientation = Orientation.Horizontal,
    )
    val pageHeights = remember { mutableStateMapOf<Int, Int>() }
    val animatedHeight by rememberPagerAnimatedHeight(pagerState, pageHeights)

    var paddingTop by remember(config.paddingTop) { mutableFloatStateOf(config.paddingTop.toFloat()) }
    var paddingBottom by remember(config.paddingBottom) { mutableFloatStateOf(config.paddingBottom.toFloat()) }
    var paddingLeft by remember(config.paddingLeft) { mutableFloatStateOf(config.paddingLeft.toFloat()) }
    var paddingRight by remember(config.paddingRight) { mutableFloatStateOf(config.paddingRight.toFloat()) }

    var headerPaddingTop by remember(config.headerPaddingTop) { mutableFloatStateOf(config.headerPaddingTop.toFloat()) }
    var headerPaddingBottom by remember(config.headerPaddingBottom) { mutableFloatStateOf(config.headerPaddingBottom.toFloat()) }
    var headerPaddingLeft by remember(config.headerPaddingLeft) { mutableFloatStateOf(config.headerPaddingLeft.toFloat()) }
    var headerPaddingRight by remember(config.headerPaddingRight) { mutableFloatStateOf(config.headerPaddingRight.toFloat()) }

    var footerPaddingTop by remember(config.footerPaddingTop) { mutableFloatStateOf(config.footerPaddingTop.toFloat()) }
    var footerPaddingBottom by remember(config.footerPaddingBottom) { mutableFloatStateOf(config.footerPaddingBottom.toFloat()) }
    var footerPaddingLeft by remember(config.footerPaddingLeft) { mutableFloatStateOf(config.footerPaddingLeft.toFloat()) }
    var footerPaddingRight by remember(config.footerPaddingRight) { mutableFloatStateOf(config.footerPaddingRight.toFloat()) }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (clickScrollCount == 0) selectedTab = page
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        CardTabRow(
            tabTitles = tabTitles,
            selectedTabIndex = selectedTab,
            onTabSelected = { index ->
                selectedTab = index
                clickScrollCount++
                scope.launch {
                    try {
                        pagerState.animateScrollToPage(
                            page = index,
                            animationSpec = tween(300, easing = FastOutSlowInEasing),
                        )
                    } finally {
                        clickScrollCount = (clickScrollCount - 1).coerceAtLeast(0)
                    }
                }
            },
            modifier = Modifier.padding(bottom = 8.dp),
        )

        HorizontalPager(
            state = pagerState,
            verticalAlignment = Alignment.Top,
            pageNestedScrollConnection = childPagerNestedScrollConnection,
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds()
                .pagerHeight(animatedHeight),
        ) { page ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { pageHeights[page] = it.height },
            ) {
                when (page) {
                    0 -> PaddingSliders(
                        top = paddingTop,
                        bottom = paddingBottom,
                        left = paddingLeft,
                        right = paddingRight,
                        onTopChange = {
                            paddingTop = it
                            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.PaddingTop(it.toInt())))
                        },
                        onBottomChange = {
                            paddingBottom = it
                            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.PaddingBottom(it.toInt())))
                        },
                        onLeftChange = {
                            paddingLeft = it
                            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.PaddingLeft(it.toInt())))
                        },
                        onRightChange = {
                            paddingRight = it
                            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.PaddingRight(it.toInt())))
                        },
                    )

                    1 -> PaddingSliders(
                        top = headerPaddingTop,
                        bottom = headerPaddingBottom,
                        left = headerPaddingLeft,
                        right = headerPaddingRight,
                        onTopChange = {
                            headerPaddingTop = it
                            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.HeaderPaddingTop(it.toInt())))
                        },
                        onBottomChange = {
                            headerPaddingBottom = it
                            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.HeaderPaddingBottom(it.toInt())))
                        },
                        onLeftChange = {
                            headerPaddingLeft = it
                            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.HeaderPaddingLeft(it.toInt())))
                        },
                        onRightChange = {
                            headerPaddingRight = it
                            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.HeaderPaddingRight(it.toInt())))
                        },
                    )

                    2 -> PaddingSliders(
                        top = footerPaddingTop,
                        bottom = footerPaddingBottom,
                        left = footerPaddingLeft,
                        right = footerPaddingRight,
                        onTopChange = {
                            footerPaddingTop = it
                            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.FooterPaddingTop(it.toInt())))
                        },
                        onBottomChange = {
                            footerPaddingBottom = it
                            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.FooterPaddingBottom(it.toInt())))
                        },
                        onLeftChange = {
                            footerPaddingLeft = it
                            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.FooterPaddingLeft(it.toInt())))
                        },
                        onRightChange = {
                            footerPaddingRight = it
                            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.FooterPaddingRight(it.toInt())))
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun PaddingSliders(
    top: Float, bottom: Float, left: Float, right: Float,
    onTopChange: (Float) -> Unit, onBottomChange: (Float) -> Unit,
    onLeftChange: (Float) -> Unit, onRightChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        TinySliderSettingItem(
            title = stringResource(R.string.padding_top),
            value = top,
            valueRange = 0f..300f,
            onValueChange = onTopChange,
        )
        TinySliderSettingItem(
            title = stringResource(R.string.padding_bottom),
            value = bottom,
            valueRange = 0f..300f,
            onValueChange = onBottomChange,
        )
        TinySliderSettingItem(
            title = stringResource(R.string.padding_left),
            value = left,
            valueRange = 0f..300f,
            onValueChange = onLeftChange,
        )
        TinySliderSettingItem(
            title = stringResource(R.string.padding_right),
            value = right,
            valueRange = 0f..300f,
            onValueChange = onRightChange,
        )
    }
}
