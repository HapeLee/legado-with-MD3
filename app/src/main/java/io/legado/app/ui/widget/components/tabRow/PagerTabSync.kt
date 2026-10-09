package io.legado.app.ui.widget.components.tabRow

import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.drop

/**
 * 把「顶部 tab（唯一事实源在宿主 / VM 的 [selectedIndex]）」和 pager 双向接起来：
 *
 * - [selectedIndex] 变了 → 把 pager 滚过去（点 tab，或别处改了选中项）；
 * - 用户滑页落位（[PagerState.settledPage]，不是拖动中途的 `currentPage`）→ 回调 [onPageSelected]。
 *
 * 两个坑这里都堵住了：
 *
 * 1. [onPageSelected] 一律经 [rememberUpdatedState] 读最新值。收集它的 effect 只在 pagerState
 *    变化时重启，若直接在 collect 里读宿主的状态，闭包会一直拿着第一次组合时的值——症状是
 *    「滑到第二页能同步，滑回第一页不回来」，tab 与顶栏 actions 一起卡在第二页。
 * 2. 回调方不要再用「和当前选中项比较」之类基于旧状态的守卫（同样会被旧值挡掉）。
 *    重复回调由状态层自己兜：`copy(selectedTab = 同一个值)` 在 StateFlow 上不会发出新值。
 *
 * 用 `settledPage` 而不是 `currentPage`：拖动过程中页码会越过中点就翻，顶栏 actions 会跟着
 * 手指来回闪。
 */
@Composable
fun rememberTabPagerState(
    selectedIndex: Int,
    pageCount: Int,
    onPageSelected: (Int) -> Unit,
): PagerState {
    val pagerState = rememberPagerState(initialPage = selectedIndex) { pageCount }
    val currentOnPageSelected by rememberUpdatedState(onPageSelected)
    LaunchedEffect(selectedIndex, pageCount) {
        if (pagerState.settledPage != selectedIndex) {
            pagerState.animateScrollToPage(selectedIndex)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            // 首个值是初始页（= selectedIndex，本来就是选中态），不用回报宿主
            .drop(1)
            .collect { page -> currentOnPageSelected(page) }
    }
    return pagerState
}
