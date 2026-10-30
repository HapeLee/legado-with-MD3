package io.legado.app.ui.main

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import io.legado.app.ui.rss.read.MainRouteRssRead
import kotlin.reflect.KClass

/** 阅读与宿主浮层拥有专门的呈现，不参与普通页面的父子双栏。 */
private val standaloneMainRoutes = setOf(
    MainRouteReadBook::class,
    MainRouteReadManga::class,
    MainRouteAudioPlay::class,
    MainRouteVideoPlay::class,
    MainRouteReadAloudPlayer::class,
    MainRouteSearchContent::class,
    MainRouteSourceLogin::class,
    MainRouteWebView::class,
    MainRouteRssRead::class,
)

internal fun supportsMainPanes(type: KClass<out NavKey>): Boolean = type !in standaloneMainRoutes

/**
 * 只接受当前双栏中的来源。左栏选择替换其后分支，右栏选择追加一级。
 * 同一子项再次被选中时保留已有条目；退出组合的旧回调不能修改新的栈。
 */
internal fun selectMainPaneChild(
    backStack: MutableList<NavKey>,
    parent: NavKey,
    child: NavKey,
): Boolean {
    if (!supportsMainPanes(parent::class) || !supportsMainPanes(child::class)) return false
    val parentIndex = backStack.indexOfLast { it == parent }
    if (parentIndex < 0 || parentIndex < backStack.lastIndex - 1) return false
    if (parentIndex < backStack.lastIndex && !supportsMainPanes(backStack.last()::class)) {
        return false
    }
    if (child == parent) {
        while (backStack.lastIndex > parentIndex) backStack.removeAt(backStack.lastIndex)
        return true
    }
    if (backStack.getOrNull(parentIndex + 1) == child) return true
    while (backStack.lastIndex > parentIndex) backStack.removeAt(backStack.lastIndex)
    backStack.add(child)
    return true
}

/** 每个 entry 的动作捕获自己的来源，不用栈顶猜测用户点的是哪一栏。 */
internal class MainEntryNavigation(
    private val source: NavKey,
    private val backStack: MutableList<NavKey>,
    private val panesEnabled: Boolean,
    private val tracker: MainNavRouteTracker,
    private val navigate: (NavKey) -> Unit,
) {
    fun onNavigateToRoute(destination: NavKey) {
        if (!navigateInPanes(destination)) navigate(destination)
    }

    /** 保留已有直接压栈入口在窄窗口中的行为。 */
    fun pushRoute(destination: NavKey) {
        if (!navigateInPanes(destination)) {
            backStack.add(destination)
            tracker.onBackStackChanged(backStack)
        }
    }

    private fun navigateInPanes(destination: NavKey): Boolean {
        if (!panesEnabled) return false
        // 转场中已退场的 entry、以及浮层下面的页面不再接受导航。
        val sourceIndex = backStack.indexOfLast { it == source }
        if (sourceIndex < 0 || sourceIndex < backStack.lastIndex - 1) return true
        if (sourceIndex < backStack.lastIndex && !supportsMainPanes(backStack.last()::class)) {
            return true
        }
        if (selectMainPaneChild(backStack, source, destination)) {
            tracker.onBackStackChanged(backStack)
            return true
        }
        // 从左栏打开全屏内容时先退出旧右栏，使关闭阅读器后回到真实来源。
        // 播放、登录等浮层继续覆盖当前场景，不能移除其下的详情。
        if (supportsMainPanes(source::class) &&
            (destination is MainRouteReadBook || destination is MainRouteReadManga ||
                    destination is MainRouteWebView || destination is MainRouteRssRead)
        ) {
            while (backStack.lastIndex > sourceIndex) backStack.removeAt(backStack.lastIndex)
            tracker.onBackStackChanged(backStack)
        }
        return false
    }
}

internal class MainPaneEntryProviderScope(
    val scope: EntryProviderScope<NavKey>,
    val backStack: MutableList<NavKey>,
    val panesEnabled: Boolean,
    val tracker: MainNavRouteTracker,
    val navigate: (NavKey) -> Unit,
) {
    inline fun <reified T : NavKey> entry(
        metadata: Map<String, Any> = emptyMap(),
        noinline content: @Composable MainEntryNavigation.(T) -> Unit,
    ) {
        val paneMetadata = when {
            T::class == MainRouteHome::class -> ListDetailSceneStrategy.containerPane()
            supportsMainPanes(T::class) -> ListDetailSceneStrategy.listAndDetailPane()
            else -> emptyMap()
        }
        scope.entry<T>(metadata = metadata + paneMetadata) { route ->
            MainEntryNavigation(route, backStack, panesEnabled, tracker, navigate).content(route)
        }
    }
}

internal fun mainPaneEntryProvider(
    backStack: MutableList<NavKey>,
    panesEnabled: Boolean,
    tracker: MainNavRouteTracker,
    navigate: (NavKey) -> Unit,
    builder: MainPaneEntryProviderScope.() -> Unit,
) = entryProvider<NavKey> {
    MainPaneEntryProviderScope(this, backStack, panesEnabled, tracker, navigate).builder()
}
