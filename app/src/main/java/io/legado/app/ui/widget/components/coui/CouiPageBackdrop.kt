package io.legado.app.ui.widget.components.coui

import androidx.compose.runtime.staticCompositionLocalOf
import com.itos.coui.backdrop.CouiBackdrop

/**
 * 由 `AppScaffold` 只在「独立栏位」提供的页面内容采样源：FAB 栏位与页面级浮层（`overlay`）栏位。
 *
 * 该采样源绑定在正文根节点上，因此**栏位里的内容不会进入采样结果**——这正是它可用的前提：
 * COUI 的玻璃材质会把源图层画进自己的 patch，采样源一旦包含消费者自身，源图层就成为自己的
 * 子节点，hwui 的 `prepareTree` 会无限递归直到 RenderThread 栈溢出（native 闪退）。
 * 正文自己（`content` 槽位）读不到这个 Local，不能拿它给自己的浮层取样。
 *
 * 顶栏 / 标签行仍走 [LocalAppCouiTopBarHost]：它们位于顶栏槽内，与正文根不是同一层级。
 * 页面未创建采样源（无顶栏、未启用模糊、原生采样不可用）时为 null，调用方须降级为实色。
 */
internal val LocalCouiPageBackdrop = staticCompositionLocalOf<CouiBackdrop?> { null }
