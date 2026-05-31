package io.legado.app.ui.book.read.sheet

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.TinySliderSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySwitchSettingItem
import io.legado.app.ui.widget.components.tabRow.CardTabRow
import io.legado.app.utils.postEvent
import kotlinx.coroutines.launch

@Composable
fun PaddingConfigSheet(
    onDismissRequest: () -> Unit,
) {
    AppModalBottomSheet(
        show = true,
        onDismissRequest = {
            ReadBookConfig.save()
            onDismissRequest()
        },
        title = stringResource(R.string.padding),
    ) {
        PaddingConfigContent(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        )
    }
}

@Composable
fun PaddingConfigContent(
    modifier: Modifier = Modifier,
) {
    // Body padding
    var paddingTop by remember { mutableFloatStateOf(ReadBookConfig.paddingTop.toFloat()) }
    var paddingBottom by remember { mutableFloatStateOf(ReadBookConfig.paddingBottom.toFloat()) }
    var paddingLeft by remember { mutableFloatStateOf(ReadBookConfig.paddingLeft.toFloat()) }
    var paddingRight by remember { mutableFloatStateOf(ReadBookConfig.paddingRight.toFloat()) }
    // Header padding
    var headerPaddingTop by remember { mutableFloatStateOf(ReadBookConfig.headerPaddingTop.toFloat()) }
    var headerPaddingBottom by remember { mutableFloatStateOf(ReadBookConfig.headerPaddingBottom.toFloat()) }
    var headerPaddingLeft by remember { mutableFloatStateOf(ReadBookConfig.headerPaddingLeft.toFloat()) }
    var headerPaddingRight by remember { mutableFloatStateOf(ReadBookConfig.headerPaddingRight.toFloat()) }
    // Footer padding
    var footerPaddingTop by remember { mutableFloatStateOf(ReadBookConfig.footerPaddingTop.toFloat()) }
    var footerPaddingBottom by remember { mutableFloatStateOf(ReadBookConfig.footerPaddingBottom.toFloat()) }
    var footerPaddingLeft by remember { mutableFloatStateOf(ReadBookConfig.footerPaddingLeft.toFloat()) }
    var footerPaddingRight by remember { mutableFloatStateOf(ReadBookConfig.footerPaddingRight.toFloat()) }
    // Line toggles
    var showHeaderLine by remember { mutableStateOf(ReadBookConfig.showHeaderLine) }
    var showFooterLine by remember { mutableStateOf(ReadBookConfig.showFooterLine) }

    val scope = rememberCoroutineScope()
    val tabTitles = listOf(
        stringResource(R.string.header),
        stringResource(R.string.main_body),
        stringResource(R.string.footer),
    )
    val pagerState = rememberPagerState(pageCount = { 3 })
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { selectedTab = it }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        CardTabRow(
            tabTitles = tabTitles,
            selectedTabIndex = selectedTab,
            onTabSelected = { index ->
                selectedTab = index
                scope.launch { pagerState.animateScrollToPage(index) }
            },
            modifier = Modifier.padding(bottom = 8.dp),
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            when (page) {
                0 -> Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    TinySwitchSettingItem(
                        title = stringResource(R.string.showLine),
                        checked = showHeaderLine,
                        onCheckedChange = {
                            showHeaderLine = it
                            ReadBookConfig.showHeaderLine = it
                            postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                        },
                    )
                    PaddingSliders(
                        top = headerPaddingTop, bottom = headerPaddingBottom,
                        left = headerPaddingLeft, right = headerPaddingRight,
                        onTopChange = {
                            headerPaddingTop = it; ReadBookConfig.headerPaddingTop =
                            it.toInt(); postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                        },
                        onBottomChange = {
                            headerPaddingBottom = it; ReadBookConfig.headerPaddingBottom =
                            it.toInt(); postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                        },
                        onLeftChange = {
                            headerPaddingLeft = it; ReadBookConfig.headerPaddingLeft =
                            it.toInt(); postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                        },
                        onRightChange = {
                            headerPaddingRight = it; ReadBookConfig.headerPaddingRight =
                            it.toInt(); postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                        },
                    )
                }

                1 -> Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    PaddingSliders(
                        top = paddingTop, bottom = paddingBottom,
                        left = paddingLeft, right = paddingRight,
                        onTopChange = {
                            paddingTop = it; ReadBookConfig.paddingTop = it.toInt(); postEvent(EventBus.UP_CONFIG,
                            arrayListOf(10, 5)
                        )
                        },
                        onBottomChange = {
                            paddingBottom = it; ReadBookConfig.paddingBottom =
                            it.toInt(); postEvent(EventBus.UP_CONFIG, arrayListOf(10, 5))
                        },
                        onLeftChange = {
                            paddingLeft = it; ReadBookConfig.paddingLeft = it.toInt(); postEvent(EventBus.UP_CONFIG,
                            arrayListOf(10, 5)
                        )
                        },
                        onRightChange = {
                            paddingRight = it; ReadBookConfig.paddingRight = it.toInt(); postEvent(EventBus.UP_CONFIG,
                            arrayListOf(10, 5)
                        )
                        },
                    )
                }

                2 -> Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    TinySwitchSettingItem(
                        title = stringResource(R.string.showLine),
                        checked = showFooterLine,
                        onCheckedChange = {
                            showFooterLine = it
                            ReadBookConfig.showFooterLine = it
                            postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                        },
                    )
                    PaddingSliders(
                        top = footerPaddingTop, bottom = footerPaddingBottom,
                        left = footerPaddingLeft, right = footerPaddingRight,
                        onTopChange = {
                            footerPaddingTop = it; ReadBookConfig.footerPaddingTop =
                            it.toInt(); postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                        },
                        onBottomChange = {
                            footerPaddingBottom = it; ReadBookConfig.footerPaddingBottom =
                            it.toInt(); postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                        },
                        onLeftChange = {
                            footerPaddingLeft = it; ReadBookConfig.footerPaddingLeft =
                            it.toInt(); postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                        },
                        onRightChange = {
                            footerPaddingRight = it; ReadBookConfig.footerPaddingRight =
                            it.toInt(); postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PaddingSliders(
    top: Float, bottom: Float, left: Float, right: Float,
    onTopChange: (Float) -> Unit, onBottomChange: (Float) -> Unit,
    onLeftChange: (Float) -> Unit, onRightChange: (Float) -> Unit,
) {
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
