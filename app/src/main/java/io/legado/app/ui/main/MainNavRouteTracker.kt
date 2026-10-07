package io.legado.app.ui.main

import androidx.compose.runtime.Stable
import androidx.navigation3.runtime.NavKey
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface BookPageResult {
    @Stable
    data class ChapterSelected(val index: Int, val position: Int) : BookPageResult
    data object TocCancelled : BookPageResult
    data object InfoEdited : BookPageResult
    data object RulesClosed : BookPageResult
    data object BookDeleted : BookPageResult
}

/**
 * 导航栈的进程内快照（完整栈 + 栈顶路由）。
 *
 * 它存在的原因：`MainActivity` 里的 back stack 是 `rememberNavBackStack` 建的可变列表，
 * 本身不是 Compose 状态。任何**叠在导航之外**的宿主（最典型的是全局朗读胶囊，它是 Activity
 * 级叠层）想按「现在显示的是哪个界面」改变行为，就必须有一个可观察的来源，否则只能读到
 * 某个普通 `var` 的陈旧值 —— 表现为「进了听书页胶囊还盖在上面，等下一次朗读进度事件才消失」。
 *
 * 已有 [MainNavigator.navigateToRoute] / [MainNavigator.navigateBack] 会写入；不经过它们的
 * 路径（预测性返回、系统返回手势、初始路由）由 Activity 侧兜底同步。
 */
class MainNavRouteTracker {

    private val _backStack = MutableStateFlow<List<NavKey>>(emptyList())

    /** 完整导航栈；栈顶即当前显示的界面。 */
    val backStack: StateFlow<List<NavKey>> = _backStack.asStateFlow()

    val currentRoute: NavKey? get() = _backStack.value.lastOrNull()

    private val _bookPageResults =
        MutableStateFlow<PersistentMap<NavKey, BookPageResult>>(persistentMapOf())
    val bookPageResults = _bookPageResults.asStateFlow()

    fun reportBookPageResult(parent: NavKey, result: BookPageResult) {
        _bookPageResults.value = _bookPageResults.value.putting(parent, result)
    }

    fun takeBookPageResult(parent: NavKey): BookPageResult? {
        val result = _bookPageResults.value[parent] ?: return null
        _bookPageResults.value = _bookPageResults.value.removing(parent)
        return result
    }

    // Keep results until the retained reader is on top and its effect collector is ready.
    private val _bookInfoResults =
        MutableStateFlow<PersistentMap<MainRouteReadBook, Boolean>>(persistentMapOf())
    val bookInfoResults = _bookInfoResults.asStateFlow()

    fun reportBookInfoResult(reader: MainRouteReadBook, bookDeleted: Boolean) {
        _bookInfoResults.value = _bookInfoResults.value.putting(reader, bookDeleted)
    }

    fun takeBookInfoResult(reader: MainRouteReadBook): Boolean? {
        val result = _bookInfoResults.value[reader] ?: return null
        _bookInfoResults.value = _bookInfoResults.value.removing(reader)
        return result
    }

    fun onBackStackChanged(backStack: List<NavKey>) {
        _backStack.value = backStack.toList()
        var results = _bookInfoResults.value
        results.keys.filterNot { it in backStack }.forEach { reader ->
            results = results.removing(reader)
        }
        _bookInfoResults.value = results
        var pageResults = _bookPageResults.value
        pageResults.keys.filterNot { it in backStack }.forEach { parent ->
            pageResults = pageResults.removing(parent)
        }
        _bookPageResults.value = pageResults
    }

    /**
     * 进程内最后一次看到的阅读页路由（栈顶不是阅读页时给 null）。
     *
     * 用它的理由：MIUI 之类会把任务栈清掉却留着进程（朗读的前台服务还在跑），再点图标时
     * 系统给的是一个 `savedInstanceState == null` 的全新 MainActivity。这时阅读页的活动
     * 状态确实没了，但导航栈的进程内快照还在——不接上它，用户看到的就是
     * 「朗读中挂后台，回来落到书架」。用户自己退回书架时栈顶已经不是阅读页，
     * 这里返回 null，不会把阅读页硬塞回去。
     */
    fun lastReadBookRoute(): MainRouteReadBook? = currentRoute as? MainRouteReadBook
}
