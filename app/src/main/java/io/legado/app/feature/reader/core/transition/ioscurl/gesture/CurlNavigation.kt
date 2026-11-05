package io.legado.app.feature.reader.core.transition.ioscurl.gesture

import kotlin.math.abs
import kotlin.math.min
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlAxis
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlPoint

/**
 * Release decision: direction 0 is forward, 1 reverse.
 * suggestedVelocity is the signed primary-axis fling speed, or zero for a position-based decision.
 */
data class CurlRelease(val direction: Int, val suggestedVelocity: Double)

/**
 * Stateless direction and edge-hit policies shared by gesture recognizers.
 * Coordinates use logical page units and velocities use those units per second.
 * Direction codes are 0 forward, 1 reverse and -1 undecided or outside an active area.
 */
object CurlNavigation {
    private const val FLING_SPEED_POINTS_PER_SECOND = 300.0
    private const val PHONE_EDGE_POINTS = 80.0
    private const val PHONE_MIDDLE_EDGE_POINTS = 60.0
    private const val TABLET_EDGE_POINTS = 110.0
    private const val TABLET_MIDDLE_EDGE_POINTS = 82.0

    /**
     * Extracts the scalar component along the navigation axis.
     */
    private fun axis(p: CurlPoint, axis: CurlAxis) = if (axis == CurlAxis.Vertical) p.y else p.x

    /**
     * Prefers a fast fling direction; otherwise requires primary-axis movement to dominate cross-axis movement.
     */
    fun begin(axis: CurlAxis, translation: CurlPoint, velocity: CurlPoint): Int {
        val v = axis(velocity, axis)
        val primary = axis(translation, axis)
        val cross = if (axis == CurlAxis.Vertical) translation.x else translation.y
        if (abs(v) > FLING_SPEED_POINTS_PER_SECOND) return if (v >= 0) 1 else 0
        if (!(abs(primary) > abs(cross))) return -1
        return if (primary >= 0) 1 else 0
    }

    /**
     * Uses fling speed when decisive; otherwise selects direction from the released page half.
     */
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

    /**
     * Maps taps in the leading/trailing edge zones to reverse/forward navigation.
     * The center area and out-of-page points return -1; tablet and center-fold modes use different margins.
     */
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
