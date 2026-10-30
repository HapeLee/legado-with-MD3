package io.legado.app.feature.reader.core.transition.ioscurl.control

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan
import io.legado.app.feature.reader.core.transition.ioscurl.math.AppleMath
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlAxis
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlBegin
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlBinding
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlConfig
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlDirection
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlEndpoints
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlKind
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlPoint
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlRect
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlValues

/** UIKit 16H81 scalar control algorithm. Does not know page bitmaps or platform events. */
object CurlControl {
    private const val HALF_PI = PI / 2
    // Legacy combined left/top binding mask retained by the reference equations.
    private val MINIMUM_CORNER_BINDING = CurlBinding.Left.nativeValue or CurlBinding.Top.nativeValue
    private const val INITIAL_TILT_RADIANS = PI / 18
    // Recovered dimensionless ratios. These fractions round to the same binary64
    // values as the original coefficients; short decimal approximations do not.
    private const val MIDDLE_START_RADIUS_RATIO = 5.0 / 246.0
    private const val EDGE_MANUAL_START_RADIUS_RATIO = 5.0 / 123.0
    private const val EDGE_AUTOMATIC_START_RADIUS_RATIO = 25.0 / 164.0
    private const val MIDDLE_END_RADIUS_RATIO = 1.0 / 984.0
    private const val EDGE_END_RADIUS_RATIO = 25.0 / 82.0
    private const val MANUAL_RADIUS_FLOOR_RATIO = 8.0 / 91.0
    private const val AUTOMATIC_MIDDLE_RADIUS_POINTS = 150.0
    private const val BEGIN_DURATION_SECONDS = 0.25
    private const val AUTOMATIC_DURATION_SECONDS = 0.4
    private const val MIN_SETTLE_SPEED_POINTS_PER_SECOND = 2048.0
    private const val MAX_SETTLE_SPEED_POINTS_PER_SECOND = 4096.0

    fun type(c: CurlConfig): Int {
        val controlType = c.kind.nativeValue
        val bindingCode = c.binding.nativeValue
        val directionCode = c.direction.nativeValue
        return if (
            (bindingCode == CurlBinding.Left.nativeValue ||
                bindingCode == CurlBinding.Top.nativeValue) &&
                directionCode == CurlDirection.Reverse.nativeValue ||
                (bindingCode == CurlBinding.Right.nativeValue ||
                    bindingCode == CurlBinding.Bottom.nativeValue) &&
                    directionCode == CurlDirection.Forward.nativeValue
        )
            if (CurlKind.isAutomatic(controlType)) CurlKind.ReverseAutomatic.nativeValue
            else CurlKind.ReverseManual.nativeValue
        else controlType
    }

    private fun vertical(c: CurlConfig) = c.binding.axis == CurlAxis.Vertical

    private fun travel(c: CurlConfig): Double {
        val size = if (vertical(c)) c.height else c.width
        return if (c.binding.middle) size else size + size
    }

    private fun base(c: CurlConfig): Double {
        val bindingCode = c.binding.nativeValue
        val directionCode = c.direction.nativeValue
        return when {
            bindingCode == CurlBinding.Left.nativeValue ||
                bindingCode == MINIMUM_CORNER_BINDING ||
                bindingCode == CurlBinding.MiddleHorizontal.nativeValue &&
                    directionCode == CurlDirection.Forward.nativeValue -> PI
            bindingCode == CurlBinding.Right.nativeValue ||
                bindingCode == CurlBinding.MiddleHorizontal.nativeValue &&
                    directionCode == CurlDirection.Reverse.nativeValue -> 0.0
            bindingCode == CurlBinding.Top.nativeValue ||
                bindingCode == CurlBinding.MiddleVertical.nativeValue &&
                    directionCode == CurlDirection.Forward.nativeValue -> 1.5 * PI
            else -> HALF_PI
        }
    }

    fun frame(c: CurlConfig): CurlRect {
        var w = c.width
        var h = c.height
        var x = 0.0
        var y = 0.0
        if (c.binding == CurlBinding.MiddleHorizontal) {
            w *= 0.5
            if (c.direction == CurlDirection.Forward) x = w
        }
        if (c.binding == CurlBinding.MiddleVertical) {
            h *= 0.5
            if (c.direction == CurlDirection.Forward) y = h
        }
        return CurlRect(x, y, w, h)
    }

    fun reference(c: CurlConfig): CurlPoint {
        val controlType = type(c)
        if (CurlKind.isAutomatic(controlType)) return c.initial
        val bindingCode = c.binding.nativeValue
        val directionCode = c.direction.nativeValue
        return when {
            bindingCode == CurlBinding.Left.nativeValue ||
                bindingCode == MINIMUM_CORNER_BINDING ||
                bindingCode == CurlBinding.MiddleHorizontal.nativeValue &&
                    directionCode == CurlDirection.Forward.nativeValue ->
                c.initial.copy(x = c.width)
            bindingCode == CurlBinding.Right.nativeValue ||
                bindingCode == CurlBinding.MiddleHorizontal.nativeValue &&
                    directionCode == CurlDirection.Reverse.nativeValue -> c.initial.copy(x = 0.0)

            bindingCode == CurlBinding.Top.nativeValue ||
                bindingCode == CurlBinding.MiddleVertical.nativeValue &&
                    directionCode == CurlDirection.Forward.nativeValue ->
                c.initial.copy(y = c.height)
            else -> c.initial.copy(y = 0.0)
        }
    }

    fun endpoints(c: CurlConfig): CurlEndpoints {
        val controlType = type(c)
        val auto = CurlKind.isAutomatic(controlType)
        val size = if (vertical(c)) c.height else c.width
        val b = base(c)
        val fromRadius =
            if (c.binding.middle)
                if (auto) AUTOMATIC_MIDDLE_RADIUS_POINTS else size * MIDDLE_START_RADIUS_RATIO
            else
                size *
                    if (auto) EDGE_AUTOMATIC_START_RADIUS_RATIO else EDGE_MANUAL_START_RADIUS_RATIO
        val toRadius =
            if (c.binding.middle) if (auto) 1.0 else size * MIDDLE_END_RADIUS_RATIO
            else size * EDGE_END_RADIUS_RATIO
        val delta =
            if (auto) if (b > HALF_PI) INITIAL_TILT_RADIANS else -INITIAL_TILT_RADIANS
            else -INITIAL_TILT_RADIANS
        val time = if (!CurlKind.isReverse(controlType)) 0.0 else 1.0
        return CurlEndpoints(
            CurlValues(time, fromRadius, b + delta),
            CurlValues(1 - time, toRadius, b),
        )
    }

    fun begin(c: CurlConfig): CurlBegin {
        val e = endpoints(c)
        val controlType = type(c)
        val explicit = CurlKind.isAutomatic(controlType)
        return CurlBegin(
            e.from.renderValues(),
            if (explicit) e.to else update(c, c.initial),
            if (explicit) AUTOMATIC_DURATION_SECONDS else BEGIN_DURATION_SECONDS,
            explicit,
            controlType,
        )
    }

    fun duration(c: CurlConfig, velocity: Double, complete: Boolean, time: Double): Double {
        if (velocity == 0.0) return BEGIN_DURATION_SECONDS
        var p = time.toFloat().toDouble()
        if (complete) p = 1 - p
        if (CurlKind.isReverse(type(c))) p = 1 - p
        return p * travel(c) /
            abs(velocity)
                .coerceIn(MIN_SETTLE_SPEED_POINTS_PER_SECOND, MAX_SETTLE_SPEED_POINTS_PER_SECOND)
    }

    fun compatible(
        oldType: Int,
        oldDirection: Int,
        oldState: Int,
        newType: Int,
        newDirection: Int,
    ) =
        (CurlKind.isAutomatic(oldType) || oldState == 5) &&
            (!CurlKind.isReverse(oldType)) == (!CurlKind.isReverse(newType)) &&
            oldDirection == newDirection

    fun update(c: CurlConfig, point: CurlPoint): CurlValues {
        val bindingCode = c.binding.nativeValue
        val directionCode = c.direction.nativeValue
        val controlType = type(c)
        val auto = CurlKind.isAutomatic(controlType)
        val mid = c.binding.middle
        val ref = reference(c)
        val x = point.x.coerceIn(0.0, c.width)
        val y = point.y.coerceIn(0.0, c.height)
        var ox = ref.x
        var oy = ref.y
        if (bindingCode and CurlBinding.Left.nativeValue != 0) ox = -c.width
        else if (bindingCode == CurlBinding.MiddleHorizontal.nativeValue)
            ox = if (directionCode == CurlDirection.Reverse.nativeValue) c.width else 0.0
        else if (bindingCode and CurlBinding.Right.nativeValue != 0) ox = c.width + c.width
        if (bindingCode and CurlBinding.Top.nativeValue != 0 && bindingCode != 3) oy = -c.height
        else if (bindingCode == CurlBinding.MiddleVertical.nativeValue)
            oy = if (directionCode == CurlDirection.Reverse.nativeValue) c.height else 0.0
        else if (bindingCode and CurlBinding.Bottom.nativeValue != 0) oy = c.height + c.height
        val dist = travel(c)
        val p = (abs(if (vertical(c)) y - oy else x - ox) / dist).coerceIn(0.0, 1.0)
        val progress = 1 - p
        val margin = if (auto) 0.0 else 0.1
        val ceiling = if (auto || mid) 1.0 else 0.9
        val rawAngle =
            when {
                bindingCode == CurlBinding.Left.nativeValue ||
                    bindingCode == MINIMUM_CORNER_BINDING ||
                    bindingCode == CurlBinding.MiddleHorizontal.nativeValue &&
                        directionCode == CurlDirection.Forward.nativeValue ->
                    atan2(
                        ref.y - y,
                        ref.x - min(x, c.width - dist * margin),
                    ) + PI

                bindingCode == CurlBinding.Right.nativeValue ||
                    bindingCode == CurlBinding.MiddleHorizontal.nativeValue &&
                        directionCode == CurlDirection.Reverse.nativeValue ->
                    atan2(y - ref.y, max(x, dist * margin) - ref.x)
                bindingCode == CurlBinding.Top.nativeValue ||
                    bindingCode == CurlBinding.MiddleVertical.nativeValue &&
                        directionCode == CurlDirection.Forward.nativeValue -> {
                    val v =
                        atan2(
                            ref.x - x,
                            min(y, c.height - dist * margin) - ref.y,
                        ) + HALF_PI
                    if (v < 0) v + 2 * PI else v
                }

                else -> {
                    val v =
                        atan2(
                            x - ref.x,
                            ref.y - max(y, dist * margin),
                        ) + 1.5 * PI
                    if (v - 2 * PI > 0) v - 2 * PI else v
                }
            }
        val w =
            if (bindingCode == CurlBinding.MiddleHorizontal.nativeValue) c.width * 0.5 else c.width
        val h =
            if (bindingCode == CurlBinding.MiddleVertical.nativeValue) c.height * 0.5 else c.height
        val maxda =
            if (vertical(c))
                atan2(
                        h.toFloat(),
                        (w * 0.5).toFloat(),
                    )
                    .toDouble()
            else atan2(w.toFloat(), (h * 0.5).toFloat()).toDouble()
        val b = base(c)
        val da = (rawAngle - b).coerceIn(-maxda, maxda)
        val angle = b + da
        val e = endpoints(c)
        val peak = dist * EDGE_AUTOMATIC_START_RADIUS_RATIO
        val k = if (mid) 0.5 else 0.25
        var radius =
            if (progress < k) e.from.radius + (peak - e.from.radius) * (progress / k)
            else
                peak +
                    ((progress - k) / (1 - k)) *
                        (dist * (if (mid) MIDDLE_END_RADIUS_RATIO else EDGE_END_RADIUS_RATIO) -
                            peak)
        var bound =
            if (auto)
                min(
                    e.from.radius,
                    e.to.radius,
                )
            else
                (if (vertical(c)) c.height else c.width) *
                    (if (mid) 0.5 else 1.0) *
                    MANUAL_RADIUS_FLOOR_RATIO
        if (mid) {
            val actual = hypot(x - ox, y - oy) / dist
            if (actual <= 0.25) bound *= 1 + (0.25 - actual) * -4
        }
        radius = min(dist * 0.2, max(min(min(e.from.radius, e.to.radius), bound), radius))
        val result = inputTime(c, progress, dist, radius, bound, angle, da, x, y, ref.x, ref.y)
        return CurlValues(result.first.coerceIn(margin, ceiling), result.second, angle)
    }

    private data class Transform(
        val a: Double = 1.0,
        val b: Double = 0.0,
        val c: Double = 0.0,
        val d: Double = 1.0,
        val x: Double = 0.0,
        val y: Double = 0.0,
    ) {
        fun concat(o: Transform) =
            Transform(
                a * o.a + b * o.c,
                a * o.b + b * o.d,
                c * o.a + d * o.c,
                c * o.b + d * o.d,
                (x * o.a + y * o.c) + o.x,
                (x * o.b + y * o.d) + o.y,
            )

        fun point(px: Double, py: Double) = CurlPoint(x + (px * a + py * c), y + (px * b + py * d))

        fun rect(r: CurlRect): CurlRect {
            val p =
                listOf(
                    point(r.x, r.y),
                    point(r.x + r.width, r.y),
                    point(r.x, r.y + r.height),
                    point(r.x + r.width, r.y + r.height),
                )
            val l = p.minOf { it.x }
            val t = p.minOf { it.y }
            return CurlRect(
                l,
                t,
                p.maxOf { it.x } - l,
                p.maxOf { it.y } - t,
            )
        }
    }

    private fun inputTime(
        c: CurlConfig,
        progress: Double,
        dist: Double,
        initialRadius: Double,
        minr: Double,
        angle: Double,
        da: Double,
        tx: Double,
        ty: Double,
        rx: Double,
        ry: Double,
    ): Pair<Double, Double> {
        val type = type(c)
        val s = c.binding.nativeValue
        val d = c.direction.nativeValue
        var quarterTurns = if (!CurlKind.isReverse(type)) 0 else -2
        if (s and 1 != 0 && d == 0 || s and 1 == 0 && d == 0 && (s == 4 || s and 16 != 0))
            quarterTurns = if (!CurlKind.isReverse(type)) -2 else 0
        else if (vertical(c))
            quarterTurns =
                if (d == 0) if (!CurlKind.isReverse(type)) -3 else -1
                else if (!CurlKind.isReverse(type)) -1 else -3
        var a = Transform()
        val bounds = CurlRect(0.0, 0.0, c.width, c.height)
        var r = bounds
        if (quarterTurns and 1 != 0) {
            a =
                Transform(x = -c.width * 0.5, y = -c.height * 0.5)
                    .concat(
                        Transform(
                            cos(-HALF_PI),
                            sin(-HALF_PI),
                            -sin(-HALF_PI),
                            cos(-HALF_PI),
                        )
                    )
            r = a.rect(bounds)
            a = a.concat(Transform(x = r.width * 0.5, y = r.height * 0.5))
        }
        if (quarterTurns <= -2) a = a.concat(Transform(x = -r.width)).concat(Transform(a = -1.0))
        val th = if (da == 0.0) 0.0 else abs(quarterTurns.toDouble() * HALF_PI + angle)
        val p = a.point(tx, ty)
        val ref = a.point(rx, ry)
        r = a.rect(bounds)

        val px = p.x
        var py = p.y
        if (py < ref.y) py = r.y + ((r.y + r.height) - py)
        var radius = initialRadius
        var center = 0.0
        var half = 0.0
        var twop = 0.0
        var curl: Double
        if (da != 0.0) {
            val (sn, cs) = AppleMath.sincos(th.toFloat())
            val invcs = 1f / cs
            val invcs2 = (invcs * invcs).toDouble()
            val invsn = 1f / sn
            var term = radius * (2 * PI)
            val hsin = AppleMath.sin((th * 0.5).toFloat()).pow(6f).toDouble()
            val z = (invsn * invsn).toDouble() * 2
            term *= ((cs.toDouble() + 1) + z * hsin)
            term += (py * 8) * (cs * cs).toDouble() * sn.toDouble()
            term -= (radius * 8) * (sn * sn).toDouble()
            val angular = (px / sn.toDouble()) * AppleMath.sin((th * 4).toFloat()).toDouble()
            curl = ((angular + term) * invcs2) * 0.125
        } else {
            val p0 = progress * dist
            half = dist * 0.5
            center = p0 - half

            val v = ((half - abs(center)) + radius * -PI) * 0.5
            curl = (if (center < 0) p0 else half) - v
            twop = p0 + p0
        }
        val absolute = abs(th)
        val (ss, cc) = AppleMath.sincos(absolute.toFloat())
        val pagew = if (c.binding.middle) r.width * 0.5 else r.width
        val projected =
            curl * cc.toDouble() +
                (curl * ss.toDouble()) * AppleMath.tan(absolute.toFloat()).toDouble()
        if (projected > pagew) {
            curl = pagew * cc.toDouble()
            val nr =
                if (da != 0.0) {
                    val trig = px * AppleMath.sin((th * 4).toFloat()).toDouble()
                    val (sn, cs) = AppleMath.sincos(th.toFloat())
                    val invcs = 1 / cs.toDouble()
                    val invcsf = invcs.toFloat()
                    val invcs2 = (invcsf * invcsf).toDouble()
                    val numerator =
                        (curl * 8 - (1 / sn.toDouble()) * trig * invcs2) - (py * 8) * sn.toDouble()
                    val tan = AppleMath.tan(th.toFloat())
                    val isn = 1f / sn
                    val ics = 1f / cs
                    var den = invcs * PI + (invcs2 * PI - (tan * tan).toDouble() * 4)
                    val add =
                        ((AppleMath.sin((th * 0.5).toFloat()).pow(6f).toDouble() * (2 * PI)) *
                            (isn * isn).toDouble()) * (ics * ics).toDouble()
                    den += add
                    numerator / (den + den)
                } else {
                    var a1 = (curl + curl) - half
                    a1 -= center

                    var a2 = half + ((curl + curl) - twop)
                    a2 += center
                    (if (center >= 0) a1 else a2) / PI
                }
            radius = max(nr, minr)
        }
        val page = frame(c)
        val diag = hypot(page.width, page.height)
        val diagAngle =
            if (vertical(c)) atan2(page.width, page.height) else atan2(page.height, page.width)
        val projection = diag * AppleMath.cos((absolute - diagAngle).toFloat()).toDouble()
        return curl / (radius + abs(projection)) to radius
    }
}
