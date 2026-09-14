package io.legado.app.host.desktop.nav

import androidx.navigation3.runtime.NavKey

/**
 * desktop host 的导航契约（M1-4b）。
 *
 * ### 为什么 nav key 在 `commonMain` 而不是 `desktopMain`
 *
 * `:host:desktop` 是 KMP 模块（`legado.kmp.compose` ⇒ Android + JVM 两个 target），
 * 所以放在这里的类型**会同时被 android 与 desktop 两个 target 编译**。M1-4b 要证明的正是
 * 「导航契约与 back stack 逻辑可以脱离 Android 平台类型存在」：本文件与
 * [DesktopNavHost] 都不 import 任何 `android.*` / `androidx.compose.ui.platform` 之外的东西。
 *
 * ### 形态与 Android 侧的 `MainRoute` 保持一致
 *
 * `:app` 的 [io.legado.app.ui.main.MainRoute] 是 `@Serializable sealed interface … : NavKey`，
 * 这里的形态照抄（`sealed interface … : NavKey` + 无参 `data object`），但**刻意不加
 * `@Serializable`**：
 *
 * - Android 侧需要它，是因为 `rememberNavBackStack` 用 `SavedStateConfiguration` 把 key 序列化
 *   进 `rememberSaveable`（进程重建后恢复返回栈）；
 * - desktop 侧本片不走 `rememberNavBackStack`——`NavBackStack` 的构造函数是 public
 *   （`javap` 实测 `NavBackStack()` / `NavBackStack(vararg T)`），host 直接持有实例即可。
 *
 * 把 nav key 上提成三端共享契约、并补上 `@Serializable`（真正需要状态恢复时），属于
 * M3「共享 Nav3 graph」的切片，不在这里顺手做——那会同时牵进 `kotlinx-serialization` 插件、
 * `savedstate` 序列化配置与 Android 侧 `rememberNavBackStack` 的启动参数。
 */
sealed interface DesktopRoute : NavKey {

    /** host 入口页：列出可进入的规则模块。desktop 应用的第一个目的地。 */
    data object Home : DesktopRoute

    /** 词典规则管理页，渲染 [io.legado.app.feature.dict.rule.DictRuleScreen]。 */
    data object DictRules : DesktopRoute
}
