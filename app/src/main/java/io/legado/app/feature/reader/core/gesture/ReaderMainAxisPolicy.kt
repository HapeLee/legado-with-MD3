package io.legado.app.feature.reader.core.gesture

import io.legado.app.feature.reader.core.transition.ReaderTransitionMode
import kotlin.math.abs

/** Gesture ownership after touch slop and bookmark arbitration. */
object ReaderMainAxisPolicy {
    // Paged modes use X for direction, regardless of the diagonal angle.
    fun canStartPageTurn(deltaX: Float, mode: ReaderTransitionMode): Boolean =
        mode != ReaderTransitionMode.SCROLL && deltaX != 0f

    fun isVerticalDominant(deltaX: Float, deltaY: Float): Boolean =
        abs(deltaY) > abs(deltaX)

    /**
     * 滚动模式认领纵向滚动的判定：只看 Y 分量是否单独越过 touch slop，不跟 X 比大小。
     * 斜滑 |dx| >= |dy| 时按 [isVerticalDominant] 判定会落空——scrollDrag 与 horizontalTurn
     * 都 false（SCROLL 模式下横向又没有翻页），手势谁也不认领，页面滑不动；改成 Y 分量过
     * slop 就认领，之后用 Y 分量滚。纯横滑 Y 分量只有手指抖动的几个像素，达不到 slop，
     * 仍什么都不做；纯纵滑与之前完全一样。
     */
    fun isVerticalScroll(deltaY: Float, touchSlop: Float): Boolean =
        abs(deltaY) >= touchSlop
}
