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
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.book.read.ConfigUpdate
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.TinySliderSettingItem
import io.legado.app.ui.widget.components.tabRow.CardTabRow
import kotlinx.coroutines.launch

@Composable
fun PaddingConfigSheet(
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
            onIntent = onIntent,
            modifier = Modifier
                .padding(bottom = 16.dp),
        )
    }
}

@Composable
fun PaddingConfigContent(
    onIntent: (ReadBookIntent) -> Unit,
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
                    PaddingSliders(
                        top = headerPaddingTop, bottom = headerPaddingBottom,
                        left = headerPaddingLeft, right = headerPaddingRight,
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
                }

                1 -> Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    PaddingSliders(
                        top = paddingTop, bottom = paddingBottom,
                        left = paddingLeft, right = paddingRight,
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
                }

                2 -> Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    PaddingSliders(
                        top = footerPaddingTop, bottom = footerPaddingBottom,
                        left = footerPaddingLeft, right = footerPaddingRight,
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
