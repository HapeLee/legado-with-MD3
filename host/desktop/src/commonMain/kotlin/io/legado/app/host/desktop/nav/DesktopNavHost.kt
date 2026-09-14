package io.legado.app.host.desktop.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry

/**
 * 当前 entry 的 `ViewModelStore`（由 [DesktopNavHost] 提供）。Android 侧对应物是
 * `lifecycle-viewmodel-navigation3` 的 `rememberViewModelStoreNavEntryDecorator()`。
 */
val LocalDesktopEntryViewModelStore = staticCompositionLocalOf<ViewModelStore?> { null }

/**
 * desktop 侧的**薄导航宿主**：渲染返回栈栈顶 entry，并为每个 destination 维护
 * saveable 状态与 ViewModel 作用域（M1-4b）。
 *
 * ### 为什么必须自己写，而不是像 Android 那样用 `NavDisplay`
 *
 * 以下结论来自解 `androidx.navigation3:*:1.2.0-beta01` 的桌面变体（`javap` 实测，不是推测）：
 *
 * 1. `navigation3-ui` 的 desktop 变体叫 **`navigation3-ui-jvmstubs`**，里面
 *    **没有 `NavDisplay(backStack, entryProvider, …)` 这个 Composable 本体**——只有
 *    `NavDisplay.transitionSpec` / `popTransitionSpec` / `predictivePopTransitionSpec`
 *    三个 metadata 工厂方法。⇒ 桌面端**连编译 `NavDisplay(…)` 都过不去**；jvmStubs 的用途是
 *    让「写了 NavDisplay metadata 的共享代码」在非 Android 平台能编译，不是让 NavDisplay 可用。
 * 2. `androidx.lifecycle:lifecycle-viewmodel-navigation3` **只有 `-android` 变体**
 *    （Gradle Module Metadata 实测），Android 侧 `MainActivity` 正是靠它的
 *    `rememberViewModelStoreNavEntryDecorator()` 给每个 entry 建 `ViewModelStore`。
 * 3. 但 `androidx.lifecycle:lifecycle-viewmodel`（KMP）的 desktop 变体里
 *    **`ViewModelStore` 是 public 且 `clear()` 也是 public** ⇒ 第 2 条的缺失**可以自己补**，
 *    见下面 [DesktopNavHost] 内对 `stores` 的维护。`ViewModel.clear()` 本身是 internal
 *    （`javap` 显示 `clear$lifecycle_viewmodel`），所以只能经 `ViewModelStore.clear()` 触发。
 *
 * ### 与 Android `NavDisplay` 的职责对照（M1-4b 的核心产出）
 *
 * | `NavDisplay` 的职责 | Android 侧来源 | desktop 现状 |
 * |---|---|---|
 * | 渲染栈顶 entry | `navigation3-ui` | ✅ 本文件 |
 * | entry 间 `saveable` 状态保留 | `runtime` 的 `SaveableStateHolderNavEntryDecorator` | ✅ `rememberSaveableStateHolder`（runtime 那个 decorator 的 `decorate`/`onPop` 是 internal，只能自己接） |
 * | 每个 entry 一个 `ViewModelStore` | `lifecycle-viewmodel-navigation3`（android-only） | ✅ 本文件用 public 的 `ViewModelStore` 自己维护 |
 * | 入场/退场动画、predictive back、scene strategy | `navigation3-ui` | ❌ 无（要自己写 AnimatedContent 与 scene 策略） |
 * | 同栈结果回传（picker） | `runtime` 的 result 通道 | ❌ 未接 |
 *
 * 结论：**Nav3 可共享的是 runtime（key / back stack / entry / entryProvider），UI 与
 * entry 级 VM 装饰器不可共享——后者可用 KMP 的 `ViewModelStore` 自行补上，前者只能自建渲染层。**
 */
@Composable
fun DesktopNavHost(
    backStack: NavBackStack<DesktopRoute>,
    entryProvider: (DesktopRoute) -> NavEntry<DesktopRoute>,
) {
    val key = backStack.lastOrNull() ?: return

    // destination -> ViewModelStore。刻意用**普通** mutableMap（不是 mutableStateMap）：
    // 它只在 composition 里读写、且写入发生在「当前 entry」上，不该触发重组；
    // 用快照 map 反而会在组合期间写状态。
    val stores = remember { mutableMapOf<DesktopRoute, ViewModelStore>() }

    // entry 离栈即清：这一步就是 Android 侧 rememberViewModelStoreNavEntryDecorator
    // 在 pop 时做的事（清掉 store ⇒ 触发 ViewModel.clear() ⇒ 取消 viewModelScope）。
    LaunchedEffect(backStack) {
        snapshotFlow { backStack.toList() }.collect { stack ->
            val alive = stack.toSet()
            val iterator = stores.entries.iterator()
            while (iterator.hasNext()) {
                val store = iterator.next()
                if (store.key !in alive) {
                    store.value.clear()
                    iterator.remove()
                }
            }
        }
    }

    val store = stores.getOrPut(key) { ViewModelStore() }

    CompositionLocalProvider(LocalDesktopEntryViewModelStore provides store) {
        // 每个 destination 一份 saveable 状态容器，语义同 Android 侧的
        // rememberSaveableStateHolderNavEntryDecorator()。
        val stateHolder = rememberSaveableStateHolder()
        stateHolder.SaveableStateProvider(key) {
            entryProvider(key).Content()
        }
    }
}

/**
 * 取当前 entry 的 ViewModel（同一 entry 内多次调用返回同一实例）。
 *
 * Android 侧对应 `koinViewModel()` + NavDisplay 的 VM 装饰器；desktop 侧没有那两个东西，
 * 所以这里显式把「作用域」写出来：store 由 [DesktopNavHost] 按 destination 持有，
 * entry 离栈时被 `clear()`。
 *
 * @param key 同一 entry 内多个 VM 时的区分键，默认按 VM 用途各取一个即可。
 */
@Composable
fun <T : ViewModel> desktopEntryViewModel(
    key: String = "entry-view-model",
    create: () -> T,
): T {
    val store = LocalDesktopEntryViewModelStore.current
        ?: error(
            "desktopEntryViewModel 必须在 DesktopNavHost 的 entry 内调用：" +
                "它依赖宿主提供的 entry 级 ViewModelStore。"
        )
    return store.getOrPut(key, create)
}
