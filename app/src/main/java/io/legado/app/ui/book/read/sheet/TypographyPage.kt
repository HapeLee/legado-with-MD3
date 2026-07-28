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
import androidx.compose.runtime.mutableStateOf
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
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.book.read.ConfigUpdate
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.book.read.ReadSheetConfigUiState
import io.legado.app.ui.widget.components.pager.pagerHeight
import io.legado.app.ui.widget.components.pager.rememberPagerAnimatedHeight
import io.legado.app.ui.widget.components.pager.rememberPagerFlingPassThroughConnection
import io.legado.app.ui.widget.components.tabRow.CardTabRow
import kotlinx.coroutines.launch

/**
 * 排版配置页 — 统一的 5 Tab 排版设置入口。
 *
 * Tabs: 正文 / 标题 / 页眉 / 页脚 / 边距
 *
 * 模态弹窗（FontSelectSheet / ColorPickerSheet / CustomTipDialog）状态上抛至本层级，
 * 在 HorizontalPager 之外渲染，避免 clipToBounds 裁剪问题。
 */
@Composable
fun TypographyPage(
    config: ReadSheetConfigUiState,
    onIntent: (ReadBookIntent) -> Unit,
    onOpenFontSelect: () -> Unit,
    onOpenTitleFontSelect: () -> Unit,
    onOpenShadowSet: () -> Unit,
    onOpenUnderlineConfig: () -> Unit,
    onOpenHighlightRule: () -> Unit,
    sameTitleRemoved: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val tabTitles = listOf(
        stringResource(R.string.main_body),   // 正文
        stringResource(R.string.body_title),   // 标题
        stringResource(R.string.header),       // 页眉
        stringResource(R.string.footer),       // 页脚
        stringResource(R.string.padding),      // 边距
    )
    val pagerState = rememberPagerState(pageCount = { 5 })
    var selectedTab by remember { mutableIntStateOf(0) }

    // Hoisted modal sheet state — rendered outside the pager to avoid clipToBounds clipping
    var activeColorPicker by remember { mutableStateOf<TypographyColorTarget?>(null) }
    var activeHeaderFontSelect by remember { mutableStateOf(false) }
    var activeFooterFontSelect by remember { mutableStateOf(false) }
    var activeCustomTip by remember { mutableStateOf<CustomTipTarget?>(null) }

    // Lifted tip state — shared between Header Tab and Footer Tab for cross-tab clearRepeat.
    // Keys ensure re-initialization when ReadBookConfig changes from external sources.
    var headerMode by remember(ReadBookConfig.headerMode) { mutableIntStateOf(ReadBookConfig.headerMode) }
    var headerLeft by remember(ReadBookConfig.tipHeaderLeft) { mutableIntStateOf(ReadBookConfig.tipHeaderLeft) }
    var headerMiddle by remember(ReadBookConfig.tipHeaderMiddle) { mutableIntStateOf(ReadBookConfig.tipHeaderMiddle) }
    var headerRight by remember(ReadBookConfig.tipHeaderRight) { mutableIntStateOf(ReadBookConfig.tipHeaderRight) }
    var footerMode by remember(ReadBookConfig.footerMode) { mutableIntStateOf(ReadBookConfig.footerMode) }
    var footerLeft by remember(ReadBookConfig.tipFooterLeft) { mutableIntStateOf(ReadBookConfig.tipFooterLeft) }
    var footerMiddle by remember(ReadBookConfig.tipFooterMiddle) { mutableIntStateOf(ReadBookConfig.tipFooterMiddle) }
    var footerRight by remember(ReadBookConfig.tipFooterRight) { mutableIntStateOf(ReadBookConfig.tipFooterRight) }
    var showHeaderLine by remember(ReadBookConfig.showHeaderLine) { mutableStateOf(ReadBookConfig.showHeaderLine) }
    var showFooterLine by remember(ReadBookConfig.showFooterLine) { mutableStateOf(ReadBookConfig.showFooterLine) }

    fun clearRepeat(repeat: Int) {
        if (repeat == ReadBookConfig.tipNone) return
        if (headerLeft == repeat) {
            headerLeft = ReadBookConfig.tipNone
            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TipHeaderLeft(ReadBookConfig.tipNone)))
        }
        if (headerMiddle == repeat) {
            headerMiddle = ReadBookConfig.tipNone
            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TipHeaderMiddle(ReadBookConfig.tipNone)))
        }
        if (headerRight == repeat) {
            headerRight = ReadBookConfig.tipNone
            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TipHeaderRight(ReadBookConfig.tipNone)))
        }
        if (footerLeft == repeat) {
            footerLeft = ReadBookConfig.tipNone
            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TipFooterLeft(ReadBookConfig.tipNone)))
        }
        if (footerMiddle == repeat) {
            footerMiddle = ReadBookConfig.tipNone
            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TipFooterMiddle(ReadBookConfig.tipNone)))
        }
        if (footerRight == repeat) {
            footerRight = ReadBookConfig.tipNone
            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TipFooterRight(ReadBookConfig.tipNone)))
        }
    }

    fun handleTipChange(target: CustomTipTarget, value: Int) {
        if (value == ReadBookConfig.tipCustom) {
            activeCustomTip = target
            return
        }
        clearRepeat(value)
        when (target) {
            CustomTipTarget.HEADER_LEFT -> {
                headerLeft = value
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TipHeaderLeft(value)))
            }
            CustomTipTarget.HEADER_MIDDLE -> {
                headerMiddle = value
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TipHeaderMiddle(value)))
            }
            CustomTipTarget.HEADER_RIGHT -> {
                headerRight = value
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TipHeaderRight(value)))
            }
            CustomTipTarget.FOOTER_LEFT -> {
                footerLeft = value
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TipFooterLeft(value)))
            }
            CustomTipTarget.FOOTER_MIDDLE -> {
                footerMiddle = value
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TipFooterMiddle(value)))
            }
            CustomTipTarget.FOOTER_RIGHT -> {
                footerRight = value
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TipFooterRight(value)))
            }
        }
    }

    val childPagerNestedScrollConnection = rememberPagerFlingPassThroughConnection(
        state = pagerState,
        orientation = Orientation.Horizontal,
    )

    val pageHeights = remember { mutableStateMapOf<Int, Int>() }
    val animatedHeight by rememberPagerAnimatedHeight(pagerState, pageHeights)

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            selectedTab = page
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        CardTabRow(
            tabTitles = tabTitles,
            selectedTabIndex = selectedTab,
            onTabSelected = { index ->
                scope.launch {
                    pagerState.animateScrollToPage(
                        page = index,
                        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                    )
                }
            },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
        )

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
                    0 -> TypographyBodyTab(
                        config = config,
                        onIntent = onIntent,
                        onOpenFontSelect = onOpenFontSelect,
                        onOpenShadowSet = onOpenShadowSet,
                        onOpenUnderlineConfig = onOpenUnderlineConfig,
                        onOpenHighlightRule = onOpenHighlightRule,
                        onOpenColorPicker = { activeColorPicker = it },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )

                    1 -> TypographyTitleTab(
                        config = config,
                        onIntent = onIntent,
                        onOpenTitleFontSelect = onOpenTitleFontSelect,
                        onOpenColorPicker = { activeColorPicker = it },
                        sameTitleRemoved = sameTitleRemoved,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )

                    2 -> TypographyHeaderTab(
                        config = config,
                        onIntent = onIntent,
                        onOpenHeaderFontSelect = { activeHeaderFontSelect = true },
                        onOpenColorPicker = { activeColorPicker = it },
                        onOpenCustomTip = { target: CustomTipTarget -> activeCustomTip = target },
                        headerMode = headerMode,
                        headerLeft = headerLeft,
                        headerMiddle = headerMiddle,
                        headerRight = headerRight,
                        onHeaderModeChange = {
                            headerMode = it
                            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.HeaderMode(it)))
                        },
                        onTipChange = ::handleTipChange,
                        showHeaderLine = showHeaderLine,
                        onShowHeaderLineChange = {
                            showHeaderLine = it
                            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.ShowHeaderLine(it)))
                        },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )

                    3 -> TypographyFooterTab(
                        config = config,
                        onIntent = onIntent,
                        onOpenFooterFontSelect = { activeFooterFontSelect = true },
                        onOpenColorPicker = { activeColorPicker = it },
                        onOpenCustomTip = { target: CustomTipTarget -> activeCustomTip = target },
                        footerMode = footerMode,
                        footerLeft = footerLeft,
                        footerMiddle = footerMiddle,
                        footerRight = footerRight,
                        onFooterModeChange = {
                            footerMode = it
                            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.FooterMode(it)))
                        },
                        onTipChange = ::handleTipChange,
                        showFooterLine = showFooterLine,
                        onShowFooterLineChange = {
                            showFooterLine = it
                            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.ShowFooterLine(it)))
                        },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )

                    4 -> TypographyMarginTab(
                        config = config,
                        onIntent = onIntent,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }

    // --- Hoisted modal sheets (rendered outside the pager) ---

    // Color picker
    activeColorPicker?.let { target ->
        TypographyColorPickerSheet(
            target = target,
            config = config,
            onDismiss = { activeColorPicker = null },
            onIntent = onIntent,
        )
    }

    // Header font select
    if (activeHeaderFontSelect) {
        TypographyHeaderFontSelectSheet(
            config = config,
            onDismiss = { activeHeaderFontSelect = false },
            onIntent = onIntent,
        )
    }

    // Footer font select
    if (activeFooterFontSelect) {
        TypographyFooterFontSelectSheet(
            config = config,
            onDismiss = { activeFooterFontSelect = false },
            onIntent = onIntent,
        )
    }

    // Custom tip dialog
    activeCustomTip?.let { target ->
        TypographyCustomTipDialog(
            target = target,
            onDismiss = { activeCustomTip = null },
            onIntent = onIntent,
        )
    }
}
