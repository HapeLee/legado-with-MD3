package io.legado.app.feature.reader.core.transition.ioscurl.control

import kotlin.math.abs
import io.legado.app.feature.reader.core.transition.ioscurl.math.BinaryMath.fma
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlValues

/**
 * Cubic Bezier timing for the progress, radius and angle animation tracks.
 */
object CurlTiming {
    private const val ANGLE_TRACK_INDEX = 2
    private const val MAX_NEWTON_ITERATIONS = 8
    private const val MAX_BISECTION_ITERATIONS = 1024
    private const val MIN_NEWTON_DERIVATIVE = 1e-6
    private const val TIME_TOLERANCE_SECONDS = 0.001

    // Preserve the original Float-rounded control points without rebuilding/boxing them per frame.
    private val progressPoints = doubleArrayOf(0.44f.toDouble(), 0.0, 0.56f.toDouble(), 1.0)
    private val endingPoints = doubleArrayOf(0.25, 0.1f.toDouble(), 0.25, 1.0)
    private val forwardPoints = doubleArrayOf(0.0, 0.0, 0.44f.toDouble(), 1.0)
    private val reversePoints = doubleArrayOf(0.56f.toDouble(), 0.0, 1.0, 1.0)

    private fun anglePoints(type: Int, ending: Boolean): DoubleArray =
        when {
            ending -> endingPoints
            type < 3 -> forwardPoints
            else -> reversePoints
        }

    /**
     * Returns Bezier control points [x1, y1, x2, y2].
     * property 0 is progress, 1 radius and 2 angle; type uses CurlKind codes.
     * ending selects the angle curve used while settling a released gesture.
     */
    fun points(type: Int, ending: Boolean, property: Int): DoubleArray =
        // Callers may mutate this public result; never expose the cached presets.
        (if (property < ANGLE_TRACK_INDEX) progressPoints else anglePoints(type, ending)).copyOf()

    /**
     * Inverts the Bezier x coordinate, then evaluates its y coordinate.
     * Uses Newton iteration with a bounded bisection fallback for a flat derivative.
     *
     * @param x Normalized elapsed fraction, normally 0..1.
     * @param p Four control-point values in [x1, y1, x2, y2] order.
     * @param duration Animation duration in seconds; callers must supply a positive value.
     * The tolerance corresponds to approximately one millisecond of animation time.
     */
    fun solve(x: Double, p: DoubleArray, duration: Double): Double {
        val eps = TIME_TOLERANCE_SECONDS / duration
        val cx = p[0] * 3
        val bx = ((p[2] - p[0]) - p[0]) * 3
        val ax = (1 - cx) - bx
        val cy = p[1] * 3
        val by = ((p[3] - p[1]) - p[1]) * 3
        val ay = (1 - cy) - by
        fun value(t: Double) = t * fma(t, fma(ay, t, by), cy)
        var t = x
        for (i in 0 until MAX_NEWTON_ITERATIONS) {
            val error = fma(t, fma(t, fma(ax, t, bx), cx), -x)
            if (abs(error) < eps) return value(t)
            val d = fma(t, fma(ax * 3, t, ((p[2] - p[0]) - p[0]) * 6), cx)
            t -= error / d
            if (!(abs(d) >= MIN_NEWTON_DERIVATIVE)) break
        }
        if (x < 0) return value(0.0)
        if (x > 1) return value(1.0)
        var lo = 0.0
        var hi = 1.0
        t = x
        for (i in MAX_BISECTION_ITERATIONS - 1 downTo 0) {
            if (lo >= hi) break
            val error = fma(t, fma(t, fma(ax, t, bx), cx), -x)
            if (abs(error) < eps) break
            if (error < 0) lo = t else hi = t
            t = fma(hi - lo, 0.5, lo)
        }
        return value(t)
    }

    /**
     * Interpolates each curl component with its own timing curve.
     *
     * @param elapsed Normalized elapsed fraction, not seconds; clamped to 0..1.
     * @param duration Positive animation duration in seconds, used to choose solver tolerance.
     */
    fun interpolate(
        from: CurlValues,
        to: CurlValues,
        type: Int,
        ending: Boolean,
        elapsed: Double,
        duration: Double,
    ): CurlValues {
        val x = elapsed.coerceIn(0.0, 1.0)
        // Progress and radius use exactly the same curve and solver inputs.
        val progress = solve(x, progressPoints, duration)
        val angle = solve(x, anglePoints(type, ending), duration)
        return CurlValues(
            fma(to.time - from.time, progress, from.time),
            fma(to.radius - from.radius, progress, from.radius),
            fma(to.angle - from.angle, angle, from.angle),
        )
    }
}
