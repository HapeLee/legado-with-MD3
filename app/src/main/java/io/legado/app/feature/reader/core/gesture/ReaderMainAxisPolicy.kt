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
}
