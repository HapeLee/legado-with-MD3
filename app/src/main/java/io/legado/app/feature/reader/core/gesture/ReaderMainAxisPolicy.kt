package io.legado.app.feature.reader.core.gesture

import io.legado.app.feature.reader.core.transition.ReaderTransitionMode
import kotlin.math.abs

/** Gesture ownership after touch slop and bookmark arbitration. */
object ReaderMainAxisPolicy {
    // Paged modes use X for direction, regardless of the diagonal angle.
    fun canStartPageTurn(deltaX: Float, mode: ReaderTransitionMode): Boolean =
        mode != ReaderTransitionMode.SCROLL && deltaX != 0f

    /**
     * 滚动模式认领纵向滚动的判定：只看 Y 分量是否单独越过 touch slop，不跟 X 比大小。
     * SCROLL 模式下横向没有翻页，若再要求 Y 主导，斜滑（|dx| >= |dy|）就会无人认领、
     * 页面滑不动；改成 Y 分量过 slop 即认领，之后只用 Y 分量滚动。横向手势的 Y 抖动
     * 通常达不到 slop，仍不认领；纯纵滑与之前一致。
     */
    fun isVerticalScroll(deltaY: Float, touchSlop: Float): Boolean =
        abs(deltaY) >= touchSlop
}
