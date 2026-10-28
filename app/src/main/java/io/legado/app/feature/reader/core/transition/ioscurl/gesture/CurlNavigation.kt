package io.legado.app.feature.reader.core.transition.ioscurl.gesture

import kotlin.math.abs
import kotlin.math.min
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlAxis
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlPoint

data class CurlRelease(val direction: Int, val suggestedVelocity: Double)

object CurlNavigation {
    private const val FLING_SPEED_POINTS_PER_SECOND = 300.0
    private const val PHONE_EDGE_POINTS = 80.0
    private const val PHONE_MIDDLE_EDGE_POINTS = 60.0
    private const val TABLET_EDGE_POINTS = 110.0
    private const val TABLET_MIDDLE_EDGE_POINTS = 82.0

    private fun axis(p: CurlPoint, axis: CurlAxis) = if (axis == CurlAxis.Vertical) p.y else p.x

    fun begin(axis: CurlAxis, translation: CurlPoint, velocity: CurlPoint): Int {
        val v = axis(velocity, axis)
        val primary = axis(translation, axis)
        val cross = if (axis == CurlAxis.Vertical) translation.x else translation.y
        if (abs(v) > FLING_SPEED_POINTS_PER_SECOND) return if (v >= 0) 1 else 0
        if (!(abs(primary) > abs(cross))) return -1
        return if (primary >= 0) 1 else 0
    }

    fun end(
        axis: CurlAxis,
        width: Double,
        height: Double,
        location: CurlPoint,
        velocity: CurlPoint,
    ): CurlRelease {
        val v = axis(velocity, axis)
        if (abs(v) > FLING_SPEED_POINTS_PER_SECOND) return CurlRelease(if (v >= 0) 1 else 0, v)
        return CurlRelease(
            if (
                axis(
                    location,
                    axis,
                ) > (if (axis == CurlAxis.Vertical) height else width) * 0.5
            )
                1
            else 0,
            0.0,
        )
    }

    fun tap(
        axis: CurlAxis,
        width: Double,
        height: Double,
        point: CurlPoint,
        middle: Boolean = false,
        tablet: Boolean = false,
    ): Int {
        val b =
            if (tablet) if (middle) TABLET_MIDDLE_EDGE_POINTS else TABLET_EDGE_POINTS
            else if (middle) PHONE_MIDDLE_EDGE_POINTS else PHONE_EDGE_POINTS
        val bw = min(b, width * 0.5)
        val bh = min(b, height * 0.5)
        if (point.x < 0 || point.y < 0 || point.x >= width || point.y >= height) return -1
        if (axis == CurlAxis.Vertical) {
            if (point.y < bh) return 1
            if (point.y >= height - bh) return 0
        } else {
            if (point.x < bw) return 1
            if (point.x >= width - bw) return 0
        }
        return -1
    }
}
