package io.legado.app.feature.reader.core.gesture

import kotlin.math.abs

/** Resolves a drag only after one axis strictly dominates the other. */
object ReaderMainAxisPolicy {
    fun isHorizontalDominant(deltaX: Float, deltaY: Float): Boolean =
        abs(deltaX) > abs(deltaY)

    fun isVerticalDominant(deltaX: Float, deltaY: Float): Boolean =
        abs(deltaY) > abs(deltaX)
}
