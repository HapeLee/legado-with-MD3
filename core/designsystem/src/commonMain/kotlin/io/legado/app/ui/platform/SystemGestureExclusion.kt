package io.legado.app.ui.platform

import androidx.compose.ui.Modifier

/**
 * 把「声明系统手势排除区」下沉为 expect/actual 原语（M1-3v）。
 *
 * ### 为什么需要
 *
 * `widget/components/lazylist/VerticalFastScroller.kt`（随本切片从 `:core:ui` 搬进
 * commonMain）在快速滚动条可拖动时用 `Modifier.systemGestureExclusion()` 让那根窄条
 * 不被系统返回手势抢走。该扩展**只存在于 Android 的 `androidx.compose.foundation`**：
 * 实测解 CMP `foundation-desktop-1.12.0.jar`，整个制品没有任何 `*Exclu*` 类
 * （Android 侧最终转发到 `View.setSystemGestureExclusionRects`）。
 * 全仓只有这一处用到它（两个调用点，都在同一个文件），所以契约面只留一个 Modifier 工厂。
 *
 * ### 为什么是 expect/actual，而不是 `StatusBarInsets` / `LiquidGlassEffects` 那种 Provider
 *
 * 判据不是「沿用上次的写法」，而是**这个能力有没有真实的可替换实现与失败语义**：
 *
 * - `LiquidGlassEffects` 未注入 ⇒ 关闭液态玻璃，那是一个由宿主决定的可变状态；
 * - 这里没有第三个实现，也没有失败语义——非 Android 平台**不存在**「系统手势拦截」这个
 *   对手方，恒等返回 `this` 就是正确语义，不是静默降级伪装；
 * - 一个零状态、纯编译期的 `Modifier` 工厂也无法用 DI 表达（`Modifier` 不是可注入的值）。
 *
 * 符合 AGENTS.md「`expect/actual` 只用于真正的平台原语」。仓内先例：
 * `:core:platform` 的 `JsonCodec` / `PlatformTime` / `Collator`。
 *
 * 名字带 `Compat` 是刻意的：让调用点一眼看出这是跨端替身，别与 Android 原生的
 * `Modifier.systemGestureExclusion()` 混用（后者在 commonMain 里根本解析不到）。
 */
expect fun Modifier.systemGestureExclusionCompat(): Modifier
