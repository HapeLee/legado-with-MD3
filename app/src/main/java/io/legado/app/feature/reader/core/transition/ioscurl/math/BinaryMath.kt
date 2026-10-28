package io.legado.app.feature.reader.core.transition.ioscurl.math

/**
 * Software fused operations: no JNI, Android API level or host libm rounding dependency. Curl
 * inputs stay far from overflow/underflow. The exact product residual and sum residual are retained
 * until the final rounding, instead of replacing FMA by a*b+c.
 */
internal object BinaryMath {
    // Dekker's split factor, 2^27 + 1, separates a binary64 significand.
    private const val DOUBLE_SPLITTER = 134_217_729.0
    private const val HALF_EXPONENT_BIAS = 15
    private const val FLOAT_EXPONENT_BIAS = 127
    private const val HALF_MANTISSA_BITS = 10
    private const val FLOAT_MANTISSA_BITS = 23
    private const val HALF_MANTISSA_MASK = 0x03ff
    private const val HALF_EXPONENT_MASK = 0x1f
    private const val FLOAT_EXPONENT_MASK = 0xff
    private const val HALF_SIGN_MASK = 0x8000
    private const val HALF_IMPLICIT_BIT = 1 shl HALF_MANTISSA_BITS
    private const val MANTISSA_SHIFT = FLOAT_MANTISSA_BITS - HALF_MANTISSA_BITS

    fun fma(a: Double, b: Double, c: Double): Double {
        val p = a * b
        val sum = p + c
        if (!p.isFinite() || !sum.isFinite()) return sum
        val aa = a * DOUBLE_SPLITTER
        val ah = aa - (aa - a)
        val al = a - ah
        val bb = b * DOUBLE_SPLITTER
        val bh = bb - (bb - b)
        val bl = b - bh
        val productError = ((ah * bh - p) + ah * bl + al * bh) + al * bl
        val virtual = sum - p
        val sumError = (p - (sum - virtual)) + (c - virtual)
        val tail = productError + sumError
        return sum + tail
    }

    fun fma(a: Float, b: Float, c: Float): Float {
        val product = a.toDouble() * b.toDouble()
        val sum = product + c.toDouble()
        // Round to odd before conversion, preventing double rounding at a float midpoint.
        val virtual = sum - product
        val error = (product - (sum - virtual)) + (c.toDouble() - virtual)
        if (error == 0.0 || !sum.isFinite()) return sum.toFloat()
        var bits = sum.toRawBits()
        if (bits and 1L == 0L) {
            bits += if ((error > 0) == (sum > 0)) 1L else -1L
        }
        return Double.fromBits(bits).toFloat()
    }

    fun copySign(value: Double, sign: Double): Double =
        Double.fromBits(
            (value.toRawBits() and Long.MAX_VALUE) or (sign.toRawBits() and Long.MIN_VALUE)
        )

    fun copySign(value: Float, sign: Float): Float =
        Float.fromBits(
            (value.toRawBits() and Int.MAX_VALUE) or (sign.toRawBits() and Int.MIN_VALUE)
        )

    fun halfToFloat(h: Int): Float {
        val sign = (h and HALF_SIGN_MASK) shl 16
        val exponent = h ushr HALF_MANTISSA_BITS and HALF_EXPONENT_MASK
        var mantissa = h and HALF_MANTISSA_MASK
        val bits =
            if (exponent == 0) {
                if (mantissa == 0) sign
                else {
                    var shift = 0
                    while (mantissa and HALF_IMPLICIT_BIT == 0) {
                        mantissa = mantissa shl 1
                        shift++
                    }
                    sign or ((113 - shift) shl 23) or ((mantissa and HALF_MANTISSA_MASK) shl 13)
                }
            } else
                sign or
                    ((if (exponent == HALF_EXPONENT_MASK) 255 else exponent + 112) shl 23) or
                    (mantissa shl MANTISSA_SHIFT)
        return Float.fromBits(bits)
    }

    fun floatToHalf(value: Float): Int {
        val bits = value.toRawBits()
        val sign = bits ushr 16 and HALF_SIGN_MASK
        var mantissa = bits and 0x7fffff
        var exponent =
            (bits ushr FLOAT_MANTISSA_BITS and FLOAT_EXPONENT_MASK) - FLOAT_EXPONENT_BIAS +
                HALF_EXPONENT_BIAS
        if (exponent >= 31)
            return sign or
                0x7c00 or
                (if (mantissa != 0 && bits ushr 23 and 255 == 255) 0x200 else 0)
        if (exponent <= 0) {
            if (exponent < -10) return sign
            mantissa = mantissa or 0x800000
            val shift = 14 - exponent
            var q = mantissa ushr shift
            val r = mantissa and ((1 shl shift) - 1)
            val half = 1 shl (shift - 1)
            if (r > half || r == half && q and 1 != 0) q++
            return sign or q
        }
        var q = mantissa ushr MANTISSA_SHIFT
        val r = mantissa and 8191
        if (r > 4096 || r == 4096 && q and 1 != 0) q++
        if (q == 1024) {
            q = 0
            exponent++
        }
        return sign or (exponent shl 10) or q
    }
}
