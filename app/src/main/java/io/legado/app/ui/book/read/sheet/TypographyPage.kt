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
import io.legado.app.constant.ReadTipType
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

    /**
     * 同一个 tip 只能占一个位置：换到新位置前先把旧位置清空。
     *
     * 位置取值一律从 [config] 快照读，不镜像本地 state——镜像进 `remember` 只在首次组合
     * 读一次，上游改动（另一 Tab、外部改配置）不会重新 seed，弹层就会显示旧值。
     */
    fun clearRepeat(repeat: Int) {
        if (repeat == ReadTipType.tipNone) return
        if (config.tipHeaderLeft == repeat) {
            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TipHeaderLeft(ReadTipType.tipNone)))
        }
        if (config.tipHeaderMiddle == repeat) {
            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TipHeaderMiddle(ReadTipType.tipNone)))
        }
        if (config.tipHeaderRight == repeat) {
            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TipHeaderRight(ReadTipType.tipNone)))
        }
        if (config.tipFooterLeft == repeat) {
            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TipFooterLeft(ReadTipType.tipNone)))
        }
        if (config.tipFooterMiddle == repeat) {
            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TipFooterMiddle(ReadTipType.tipNone)))
        }
        if (config.tipFooterRight == repeat) {
            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TipFooterRight(ReadTipType.tipNone)))
        }
    }

    fun handleTipChange(target: CustomTipTarget, value: Int) {
        if (value == ReadTipType.tipCustom) {
            activeCustomTip = target
            return
        }
        clearRepeat(value)
        val update = when (target) {
            CustomTipTarget.HEADER_LEFT -> ConfigUpdate.TipHeaderLeft(value)
            CustomTipTarget.HEADER_MIDDLE -> ConfigUpdate.TipHeaderMiddle(value)
            CustomTipTarget.HEADER_RIGHT -> ConfigUpdate.TipHeaderRight(value)
            CustomTipTarget.FOOTER_LEFT -> ConfigUpdate.TipFooterLeft(value)
            CustomTipTarget.FOOTER_MIDDLE -> ConfigUpdate.TipFooterMiddle(value)
            CustomTipTarget.FOOTER_RIGHT -> ConfigUpdate.TipFooterRight(value)
        }
        onIntent(ReadBookIntent.UpdateConfig(update))
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
                        headerMode = config.headerMode,
                        headerLeft = config.tipHeaderLeft,
                        headerMiddle = config.tipHeaderMiddle,
                        headerRight = config.tipHeaderRight,
                        onHeaderModeChange = {
                            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.HeaderMode(it)))
                        },
                        onTipChange = ::handleTipChange,
                        showHeaderLine = config.showHeaderLine,
                        onShowHeaderLineChange = {
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
                        footerMode = config.footerMode,
                        footerLeft = config.tipFooterLeft,
                        footerMiddle = config.tipFooterMiddle,
                        footerRight = config.tipFooterRight,
                        onFooterModeChange = {
                            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.FooterMode(it)))
                        },
                        onTipChange = ::handleTipChange,
                        showFooterLine = config.showFooterLine,
                        onShowFooterLineChange = {
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
            config = config,
            onDismiss = { activeCustomTip = null },
            onIntent = onIntent,
        )
    }
}
