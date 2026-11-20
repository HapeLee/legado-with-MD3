package io.legado.app.ui.main

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import io.legado.app.ui.widget.components.text.AppText
import io.oxygenx.theme.OxygenTheme
import io.oxygenx.ui.navigation.TopAppBarState
import io.oxygenx.ui.navigation.rememberTopAppBarState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf

/**
 * 某个主页面贡献给横划顶栏的内容。
 *
 * 操作区与顶栏下方内容都属于页面内部状态（搜索、分组、菜单），因此由页面自己构造，
 * 再交给主壳唯一的那条顶栏渲染；横划时顶栏本身不动，只有这些槽位按页跟手平移。
 */
@Immutable
class MainPagerPageChrome(
    val subtitle: String?,
    val actions: @Composable RowScope.() -> Unit,
    val bottomContent: @Composable ColumnScope.() -> Unit,
)

/** 主页面 → 顶栏槽位的注册表，键是主壳 pager 的页下标。 */
@Stable
class MainPagerTopBarHost {
    var pages: PersistentMap<Int, MainPagerPageChrome> by mutableStateOf(persistentMapOf())
        private set

    fun update(page: Int, chrome: MainPagerPageChrome) {
        if (pages[page] === chrome) return
        pages = pages.put(page, chrome)
    }

    fun remove(page: Int) {
        if (!pages.containsKey(page)) return
        pages = pages.remove(page)
    }
}

/** 非空表示横划顶栏接管了主页面顶栏，页面不应再渲染自己的顶栏。 */
val LocalMainPagerTopBarHost = staticCompositionLocalOf<MainPagerTopBarHost?> { null }

/**
 * 主页面列表的滚动方向状态：内容上滑（手指往上推）时底部页签收起，下拉时重新展开。
 *
 * 由主壳统一监听 nestedScroll，页面只读取，不必各自接 [`LazyGridState`]。
 */
@Stable
class MainPagerBottomBarState {
    var hidden by mutableStateOf(false)
        private set

    fun onScroll(delta: Float) {
        when {
            delta < -SCROLL_THRESHOLD_PX -> hidden = true
            delta > SCROLL_THRESHOLD_PX -> hidden = false
        }
    }

    fun reveal() {
        hidden = false
    }

    private companion object {
        /** 过滤回弹和抖动产生的小位移。 */
        const val SCROLL_THRESHOLD_PX = 2f
    }
}

val LocalMainPagerBottomBarState = staticCompositionLocalOf<MainPagerBottomBarState?> { null }

/** 当前页面在主壳 pager 中的下标。 */
val LocalMainPageIndex = staticCompositionLocalOf { 0 }

/**
 * 把本页的副标题、操作区与顶栏下方内容注册给主壳顶栏。
 *
 * 页面仍是组合所有者：lambda 在顶栏组合位置执行，读到的依然是本页 state，因此搜索、
 * 分组菜单等交互不需要上移。
 */
@Composable
fun MainPagerPageChromeEffect(
    host: MainPagerTopBarHost,
    pageIndex: Int,
    subtitle: String?,
    actions: @Composable RowScope.() -> Unit,
    bottomContent: @Composable ColumnScope.() -> Unit = {},
) {
    val chrome = MainPagerPageChrome(subtitle, actions, bottomContent)
    SideEffect { host.update(pageIndex, chrome) }
    DisposableEffect(host, pageIndex) {
        onDispose { host.remove(pageIndex) }
    }
}

/**
 * 横划顶栏的收拢状态。启用时把 [TopAppBarState.nestedScrollConnection] 挂到主壳根节点，
 * 收拢进度与大顶栏同一条动画；未启用返回 null。
 */
@Composable
fun rememberMainPagerTopBarAppBarState(enabled: Boolean): TopAppBarState? =
    if (enabled) rememberTopAppBarState() else null

/**
 * 主页面唯一的横划顶栏：OxygenX `PagerTopBar` 的宿主封装。
 *
 * 与 catalog 一致，顶栏固定在主壳顶部，不随 pager 平移；标题、副标题与操作区按页从
 * [host] 读取，横划时由 `PagerTopBar` 内部跟手横移与淡出。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainPagerTopBar(
    pagerState: PagerState,
    titles: ImmutableList<String>,
    host: MainPagerTopBarHost,
    appBarState: TopAppBarState?,
    modifier: Modifier = Modifier,
) {
    val currentChrome = host.pages[pagerState.currentPage]
    Column(modifier.windowInsetsPadding(TopAppBarDefaults.windowInsets)) {
        io.oxygenx.material3.ProvideMaterial3Content {
            io.oxygenx.ui.navigation.PagerTopBar(
                pagerState = pagerState,
                title = { page ->
                    // 标题排版由 PagerTopBar 用 Oxygen 的 LocalTextStyle 下发（displayMedium
                    // 随 collapsedFraction 插值），AppText 在 Oxygen 引擎下读的是 M3 的
                    // LocalTextStyle，必须显式取 Oxygen 的样式与内容色，否则会退化成正文字号。
                    AppText(
                        text = titles.getOrElse(page) { "" },
                        style = OxygenTheme.textStyle,
                        color = OxygenTheme.contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                subtitle = { page ->
                    val text = host.pages[page]?.subtitle
                    if (!text.isNullOrBlank()) {
                        AppText(
                            text = text,
                            style = OxygenTheme.textStyle,
                            color = OxygenTheme.contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                hideSubtitleOnCollapse = false,
                state = appBarState,
                actions = { page ->
                    val chrome = host.pages[page]
                    if (chrome != null) chrome.actions(this)
                },
            )
        }
        // 页签 / 内联搜索的高度变化走同一条动画，换页或显隐时不会顶栏硬跳。
        Column(modifier = Modifier.animateContentSize()) {
            currentChrome?.bottomContent?.invoke(this)
        }
    }
}
