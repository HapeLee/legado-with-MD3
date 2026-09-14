package io.legado.app.ui.platform

import androidx.compose.ui.Modifier

/**
 * 非 Android 实现：恒等返回 `this`。
 *
 * 桌面端没有「系统手势拦截」这个概念（CMP 的 `foundation-desktop` 制品里根本没有
 * `systemGestureExclusion`），也就不存在需要排除的对手方。所以这里**不声明任何排除区**，
 * 而不是假装声明了一下：快速滚动条照常渲染、照常可拖动，只是没有这一层语义。
 *
 * 将来加 iOS / Web target 时，如果那边确有对应的系统手势机制，在这里补各自的 actual 即可
 * ——这正是用 expect/actual 而不是把 `VerticalFastScroller` 留在 Android 侧的理由。
 */
actual fun Modifier.systemGestureExclusionCompat(): Modifier = this
