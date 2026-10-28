package io.legado.app.feature.reader.core.transition.ioscurl.math

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import io.legado.app.feature.reader.core.transition.ioscurl.math.BinaryMath.copySign
import io.legado.app.feature.reader.core.transition.ioscurl.math.BinaryMath.fma

/** Recovered libsystem_m polynomials. Scalar sin/cos and paired sincos intentionally differ. */
internal object AppleMath {
    // 1.5 * 2^52: binary64 round-to-nearest bias used by argument reduction.
    private const val DOUBLE_ROUNDING_BIAS = 6_755_399_441_055_744.0
    private const val FLOAT_QUARTER_PI_BITS = 0x3f49_0fdb
    private const val FLOAT_SMALL_ANGLE_BITS = 0x3980_0000
    private const val FLOAT_FOUR_BITS = 0x4080_0000
    private const val FLOAT_EIGHT_BITS = 0x4100_0000
    private const val FLOAT_SIXTY_FOUR_BITS = 0x4280_0000

    // Polynomial coefficients deliberately retain binary64 precision. Only
    // coefficients consumed as Float are shortened to an equivalent literal.
    private fun reduced(rh: Float, rl: Float, quadrant: Int): Float {
        val r = rh - rl
        val y =
            if (quadrant and 1 != 0) {
                val q = r * r
                var p = fma(q, 2.43724e-5f, -0.0013886789f)
                p = fma(q, p, 0.041666634f)
                p = q * p
                val rr = r.toDouble() * r
                fma(rr, p.toDouble(), fma(rr, -0.5, 1.0)).toFloat()
            } else {
                val error = (rh - r) - rl
                val q = r * r
                val p = fma(q, 2.7166636e-6f, -0.00019839357f)
                var z = fma(q, 0.00833333f, 5.040594e-9f)
                z = fma(q * q, p, z)
                z = q * z
                z = fma(q, -0.16666667f, z)
                fma(r, z, error) + r
            }
        return Float.fromBits(y.toRawBits() xor ((quadrant ushr 1) shl 31))
    }

    private fun large(x: Float, cosine: Boolean): Float {
        var a = x.toDouble() * 0.3183098863810301
        val b = x.toDouble() * -1.972394111648692e-10
        if (cosine) a += 0.5
        val n = round(a)
        val r = (a - n) + b
        val signed = Double.fromBits(r.toRawBits() xor (n.toLong() shl 63))
        val q = r * r
        val p4 = q - 4.16477198291663
        val p5 = q - 3.6493923712207774
        val p6 = fma(q, p4, 15.485466333375713)
        val p7 = fma(q, p5, 2.652203472786763)
        return ((signed * p6) * (0.07649248058790409 * p7)).toFloat()
    }

    fun sin(x: Float): Float {
        val ab = x.toRawBits() and Int.MAX_VALUE
        if (ab < FLOAT_QUARTER_PI_BITS) {
            if (ab < FLOAT_SMALL_ANGLE_BITS) return fma(x, 67108864f, x) * 1.4901161e-8f
            val q = x * x
            var p = fma(q, 2.7166636e-6f, -0.00019839357f)
            p = fma(q, p, 0.00833333f)
            p = fma(q, p, 5.040594e-9f)
            p = q * p
            p = fma(q, -0.16666667f, p)
            return fma(x, p, x)
        }
        if (ab >= FLOAT_EIGHT_BITS)
            return if (ab < FLOAT_SIXTY_FOUR_BITS) large(x, false) else kotlin.math.sin(x)
        val n = round(x * 0.63661975f)
        return reduced(fma(-n, 1.5707964f, x), n * -4.371139e-8f, n.toInt())
    }

    fun cos(input: Float): Float {
        val x = abs(input)
        val ab = x.toRawBits()
        if (ab < FLOAT_SMALL_ANGLE_BITS) return (67108864f - x) * 1.4901161e-8f
        if (ab < FLOAT_QUARTER_PI_BITS) {
            val q = x * x
            var p = fma(q, 2.43724e-5f, -0.0013886789f)
            p = fma(q, p, 0.041666634f)
            p = q * p

            val xx = x.toDouble() * x
            return fma(p.toDouble(), xx, fma(xx, -0.5, 1.0)).toFloat()
        }
        if (ab >= FLOAT_FOUR_BITS)
            return if (ab < FLOAT_SIXTY_FOUR_BITS) large(x, true) else kotlin.math.cos(x)
        val n = round(x * 0.63661975f)
        return reduced(fma(-n, 1.5707964f, x), n * -4.371139e-8f, n.toInt() + 1)
    }

    fun tan(v: Float): Float {
        var x = v.toDouble()
        val ab = v.toRawBits() and Int.MAX_VALUE
        if (ab < FLOAT_SMALL_ANGLE_BITS) return fma(v, 67108864f, v) * 1.4901161e-8f
        if (ab < FLOAT_QUARTER_PI_BITS) {
            val q = x * x
            var a = fma(q, 0.0010933359443297335, 0.010024745651428834)
            val b = fma(q, 0.021601514863153856, 0.05399723816130635)
            var c = fma(q, 0.13333216629799713, 0.33333334098837764)
            val qq = q * q
            a = fma(qq, 0.003893782386938478, a)
            c = fma(qq, b, c)
            c = fma(qq * qq, a, c)
            return fma(x, q * c, x).toFloat()
        }
        if (abs(x) >= 8) return kotlin.math.tan(v)
        val a = x * 0.6366197727620602
        val b = x * -3.944788223297384e-10
        val biased = a + DOUBLE_ROUNDING_BIAS
        val bits = biased.toRawBits()
        val n = biased - DOUBLE_ROUNDING_BIAS
        x = (a - n) + b

        val q = x * x
        var p5 = fma(q, 5.2786026187254365, 3.821272463971885)
        var p6 = q - 0.3443129754511736
        var p7 = q - 1.535039353621607
        var p16 = q + 0.9130117913337534
        p5 = x * p5
        p6 = fma(q, p6, 0.7533741597749263)
        p7 = fma(q, p7, 0.9278362959973533)
        p16 = fma(q, p16, 0.5880710422549965)
        val p = (p5 * p6) * (p7 * p16)
        return (if (bits and 1L != 0L) -1 / p else p).toFloat()
    }

    fun sincos(x: Float): Pair<Float, Float> {
        val a = x.toDouble() * 0.6366197723675814
        val n = (a + copySign(0.5, x.toDouble())).toInt()
        val r = a - n
        val q = r * r
        var s = (r * 0.00015820674370772955) * ((q - 14.11962047131294) * q + 40.5713390182018)
        s = ((q - 15.469930309596) * q + 244.7234259671355) * s
        var c = ((q - 8.36721486831633) * q + 7.367313974172263) * 0.0009036317034594685
        c = ((q - 14.71749014926) * q + 150.210178361388) * c
        return when (n and 3) {
            0 -> s.toFloat() to c.toFloat()
            1 -> c.toFloat() to -s.toFloat()
            2 -> -s.toFloat() to -c.toFloat()
            else -> -c.toFloat() to s.toFloat()
        }
    }

    private val acosHi =
        doubleArrayOf(
            0.0018866627454690213,
            0.002105758391974362,
            0.008053538408076452,
            0.026496590400740767,
            0.11785201800307596,
            1.4142135559781581,
        )
    private val acosLo =
        doubleArrayOf(
            0.03392107098559122,
            0.017005792259508826,
            0.03113191845966743,
            0.044596625218188204,
            0.07500103158302872,
            0.16666666294795343,
        )

    fun acos(x: Float): Float {
        val a = abs(x.toDouble())
        if (a == 1.0) return if (x > 0) 0f else 3.1415925323963165.toFloat()
        if (a > 1 || a.isNaN()) return Float.NaN
        if (a < 0.001953125) return (PI / 2 - x.toDouble()).toFloat()
        if (a > 0.5) {
            var t = (1f - a.toFloat()).toDouble()
            val c = acosHi
            val p = fma(t, c[0], c[1])
            var q = fma(t, c[2], c[3])
            var r = fma(t, c[4], c[5])
            t = t * t
            q = fma(t, p, q)
            r = fma(t, q, r)
            r *= sqrt((1f - a.toFloat()).toDouble())
            if (x <= 0) r = PI - r
            return r.toFloat()
        }
        val t = x.toDouble() * x
        val c = acosLo
        val p = fma(t, c[0], c[1])
        var q = fma(t, c[2], c[3])
        var r = fma(t, c[4], c[5])
        val tt = t * t
        q = fma(tt, p, q)
        r = fma(tt, q, r)
        r = t * r
        r = fma(x.toDouble(), r, x.toDouble())
        return (PI / 2 - r).toFloat()
    }

    private val sinCoefficients =
        doubleArrayOf(
            1.5896230157654656e-10,
            -2.5050747762857807e-08,
            2.7557313621385722e-06,
            -0.0001984126982958954,
            0.008333333333322118,
            -0.1666666666666663,
        )
    private val cosCoefficients =
        doubleArrayOf(
            -1.1358536521387682e-11,
            2.087570084197473e-09,
            -2.755731417929674e-07,
            2.4801587288851704e-05,
            -0.0013888888888873056,
            0.041666666666666595,
        )

    fun sincos(input: Double): Pair<Double, Double> {
        var x = input
        val ax = abs(x)
        var n = 0
        if (ax < 1.0536712127723509e-8) return x to 1.0
        if (ax >= PI / 4) {
            if (ax >= 524288.6169851497) return kotlin.math.sin(x) to kotlin.math.cos(x)
            var q = x * 0.6366197723675814
            q += DOUBLE_ROUNDING_BIAS
            q -= DOUBLE_ROUNDING_BIAS
            n = q.toInt()
            val a = q * -1.5707963267923333
            val b = q * 2.5633441515839558e-12
            val c = q * -1.0562999066987428e-23
            x += a

            val y = x - b
            x -= y
            x -= b
            x += c
            x = y + x
        }
        val t = x * x
        var p = t * sinCoefficients[0] + sinCoefficients[1]
        for (k in 2..5) p = t * p + sinCoefficients[k]
        val s = x + (x * t) * p
        p = t * cosCoefficients[0] + cosCoefficients[1]
        for (k in 2..5) p = t * p + cosCoefficients[k]
        p = t * p
        p += -0.5

        val c = t * p + 1
        return when (n and 3) {
            0 -> s to c
            1 -> c to -s
            2 -> -s to -c
            else -> -c to s
        }
    }

    // The 32 exp2 residual pairs are copied as numeric constants from the recovered implementation.
    private val expSteps =
        floatArrayOf(
            1f,
            0f,
            1.0218972f,
            -6.792869e-8f,
            1.0442737f,
            6.677582e-8f,
            1.0671405f,
            -8.021994e-8f,
            1.0905077f,
            -1.7301026e-8f,
            1.1143868f,
            -7.036898e-8f,
            1.1387886f,
            6.823633e-8f,
            1.1637249f,
            -5.02266e-8f,
            1.1892071f,
            4.6071282e-8f,
            1.2152474f,
            -3.878926e-8f,
            1.2418578f,
            5.2240814e-8f,
            1.269051f,
            1.6135405e-9f,
            1.2968396f,
            -4.471016e-8f,
            1.3252367f,
            -3.8062637e-8f,
            1.3542556f,
            -1.0784453e-8f,
            1.38391f,
            -6.1251576e-8f,
            1.4142135f,
            2.4690674e-8f,
            1.4451808f,
            3.3184822e-8f,
            1.4768262f,
            -4.396878e-8f,
            1.5091645f,
            -2.3860066e-8f,
            1.5422108f,
            7.550105e-9f,
            1.5759809f,
            -5.182254e-8f,
            1.6104903f,
            8.8113925e-9f,
            1.6457555f,
            -4.492631e-8f,
            1.6817929f,
            -2.1235902e-8f,
            1.7186193f,
            -4.0710116e-8f,
            1.7562522f,
            -7.586838e-9f,
            1.7947091f,
            -9.176099e-9f,
            1.8340081f,
            -8.84121e-9f,
            1.8741677f,
            -3.5894843e-8f,
            1.9152066f,
            7.4163315e-9f,
            1.9571441f,
            -1.25475035e-8f,
        )

    fun exp(x: Float): Float {
        if (x.isNaN()) return x + x
        if (x > 88.72283f) return Float.POSITIVE_INFINITY
        if (x < -103.972084f) return 0f
        val z = x * 1.442695f
        val n = (z * 32f).toLong()
        val fraction = z - n.toFloat() * 0.03125f
        var error = fma(-x, 1.442695f, z)
        error = fma(x, 1.925963e-8f, -error)
        val index = (n.toInt() and 31) * 2
        val base = expSteps[index]
        var p = 0.009618355f * base
        var q = expSteps[index + 1] + error
        val u = fraction + 1.7524261f
        q = fraction + q
        val v = fma(q, u, 17.933971f)
        val u2 = q + 4.018354f
        p = q * p
        p *= u2
        val e = n.toInt() shr 5
        if (e >= -125) {
            val r = fma(p, v, base)
            return Float.fromBits(r.toRawBits() + (e shl 23))
        }
        val r =
            fma(
                p.toDouble(),
                v.toDouble(),
                base.toDouble(),
            )
        return Double.fromBits(r.toRawBits() + (e.toLong() shl 52)).toFloat()
    }
}
