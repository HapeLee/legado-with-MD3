package io.legado.app.ui.widget.components.coui

import androidx.compose.runtime.staticCompositionLocalOf
import com.itos.coui.backdrop.CouiBackdrop

/**
 * 由 `AppScaffold` 仅在 FAB 栏位提供的页面内容采样源。
 *
 * 该采样源绑定在内容根节点上，正文不获得它自身，采样结果不包含 FAB。
 * 顶栏 / 标签行仍走 [LocalAppCouiTopBarHost]：它们位于顶栏槽内，与内容根不是同一层级。
 * 页面未创建采样源（无顶栏、未启用模糊、原生采样不可用）时为 null，调用方须降级为实色。
 */
internal val LocalCouiPageBackdrop = staticCompositionLocalOf<CouiBackdrop?> { null }
