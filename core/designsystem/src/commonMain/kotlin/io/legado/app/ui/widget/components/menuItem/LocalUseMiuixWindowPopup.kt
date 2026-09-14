package io.legado.app.ui.widget.components.menuItem

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 「当前子树里的弹层是否由 Miuix 的 window 级弹窗承载」这一事实的传播通道。
 *
 * **为什么单独成文件**（M1-3h，2026-09-10）：它原本声明在 `:core:ui` 的
 * `menuItem/RoundDropdownMenu.kt` 里。搬迁 `AppModalBottomSheet` 进 `:core:designsystem`
 * 时发现它同时被两边需要，而 `:core:ui` 依赖本模块、反向不可行，故把**声明**下沉到本模块。
 *
 * 包名保持 `io.legado.app.ui.widget.components.menuItem` 不变，因此：
 * - `:core:ui` 的 `RoundDropdownMenu.kt` 仍在同包内引用它，**零 import 改动**
 *   （跨模块同包可见性在本仓已有先例：`core:ui` 的 `ui/theme` 直接引用本模块的
 *   `LocalLegadoThemeColors` / `LocalAppUiConfiguration`）；
 * - 消费方（`:app` / `:core:ui` / `:feature:tagrules`）的 import 路径不变。
 *
 * 默认值为 `false`（按 Material3 弹层处理）；Miuix 引擎下由 `AppModalBottomSheet` 在
 * 子树里 provide `true`。
 */
val LocalUseMiuixWindowPopup = staticCompositionLocalOf { false }
