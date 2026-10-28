package io.legado.app.feature.reader.core.transition.ioscurl.control

import kotlin.math.abs
import io.legado.app.feature.reader.core.transition.ioscurl.math.BinaryMath.fma
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlValues

/**
 * CA cubic solver and independent time/radius/angle tracks. Input is elapsed time, never eased
 * twice.
 */
object CurlTiming {
    private const val ANGLE_TRACK_INDEX = 2
    private const val MAX_NEWTON_ITERATIONS = 8
    private const val MAX_BISECTION_ITERATIONS = 1024
    private const val MIN_NEWTON_DERIVATIVE = 1e-6
    private const val TIME_TOLERANCE_SECONDS = 0.001

    fun points(type: Int, ending: Boolean, property: Int): DoubleArray =
        when {
                property < ANGLE_TRACK_INDEX -> floatArrayOf(0.44f, 0f, 0.56f, 1f)
                ending -> floatArrayOf(0.25f, 0.1f, 0.25f, 1f)
                type < 3 -> floatArrayOf(0f, 0f, 0.44f, 1f)
                else -> floatArrayOf(0.56f, 0f, 1f, 1f)
            }
            .map { it.toDouble() }
            .toDoubleArray()

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

    fun interpolate(
        from: CurlValues,
        to: CurlValues,
        type: Int,
        ending: Boolean,
        elapsed: Double,
        duration: Double,
    ): CurlValues {
        val a = doubleArrayOf(from.time, from.radius, from.angle)
        val b = doubleArrayOf(to.time, to.radius, to.angle)
        val out =
            DoubleArray(3) { k ->
                fma(
                    b[k] - a[k],
                    solve(elapsed.coerceIn(0.0, 1.0), points(type, ending, k), duration),
                    a[k],
                )
            }
        return CurlValues(out[0], out[1], out[2])
    }
}
