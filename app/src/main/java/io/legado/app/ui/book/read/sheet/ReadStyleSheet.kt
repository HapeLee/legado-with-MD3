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
import io.legado.app.data.repository.ReadPreferences
import io.legado.app.ui.book.read.ConfigUpdate
import io.legado.app.ui.book.read.ReadBookButtonConfigItem
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.book.read.ReadBookStyleConfig
import io.legado.app.ui.widget.components.pager.pagerHeight
import io.legado.app.ui.widget.components.pager.rememberPagerAnimatedHeight
import io.legado.app.ui.widget.components.pager.rememberPagerFlingPassThroughConnection
import io.legado.app.ui.widget.components.tabRow.CardTabRow
import kotlinx.coroutines.launch

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
    preferences: ReadPreferences,
    modifier: Modifier = Modifier,
    onIntent: (ReadBookIntent) -> Unit,
    styleConfig: ReadBookStyleConfig = ReadBookStyleConfig(),
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 2 })
    var currentPage by remember { mutableIntStateOf(0) }
    val childPagerNestedScrollConnection = rememberPagerFlingPassThroughConnection(
        state = pagerState,
        orientation = Orientation.Horizontal,
    )

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
            pageNestedScrollConnection = childPagerNestedScrollConnection,
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
                        eyeProtectionEnabled = preferences.eyeProtectionEnabled,
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

                    1 -> SystemMenuPage(
                        preferences = preferences,
                        customIcons = readMenuCustomIcons,
                        bottomBarButtons = bottomBarButtons,
                        onIntent = onIntent,
                    )
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
