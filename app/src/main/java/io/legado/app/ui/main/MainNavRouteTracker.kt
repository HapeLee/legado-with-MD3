package io.legado.app.ui.main

import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    fun onBackStackChanged(backStack: List<NavKey>) {
        _backStack.value = backStack.toList()
    }
}
