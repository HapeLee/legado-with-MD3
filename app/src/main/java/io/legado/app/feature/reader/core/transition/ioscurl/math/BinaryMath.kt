package io.legado.app.feature.reader.core.transition.ioscurl.math

/** Binary operations without a Kotlin standard-library equivalent on Android API 21. */
internal object BinaryMath {

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

    // Math.fma requires Android API 33. Keep the compensated fallback for API 21+.
    // This is not a fully IEEE 754 fused operation: intermediate overflow and
    // underflow can differ from Math.fma. Do not replace it with plain a * b + c,
    // which also discards compensation for ordinary finite inputs.
    /**
     * Compensated Double multiply-add fallback for Android API 21+.
     * Uses a split product and a compensated sum to reduce rounding error for ordinary
     * finite inputs. Intermediate overflow and underflow can differ from IEEE fused FMA.
     */
    fun fma(a: Double, b: Double, c: Double): Double {
        val p = a * b
        val sum = p + c
        if (!p.isFinite() || !sum.isFinite()) return sum
        // Split each multiplicand into high and low parts, then reconstruct the product residual.
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

    /**
     * Float multiply-add using a Double product and a low-order correction before final Float rounding.
     * The correction avoids double-rounding errors at Float midpoints.
     */
    fun fma(a: Float, b: Float, c: Float): Float {
        val product = a.toDouble() * b.toDouble()
        val sum = product + c.toDouble()

        val virtual = sum - product
        val error = (product - (sum - virtual)) + (c.toDouble() - virtual)
        if (error == 0.0 || !sum.isFinite()) return sum.toFloat()
        var bits = sum.toRawBits()
        if (bits and 1L == 0L) {
            bits += if ((error > 0) == (sum > 0)) 1L else -1L
        }
        return Double.fromBits(bits).toFloat()
    }

    // android.util.Half requires API 26; preserve round-to-nearest-even conversion.
    /**
     * Decodes the low 16 bits of h as IEEE 754 binary16, including subnormals, signed zero, infinities and NaNs.
     */
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

    /**
     * Encodes Float as binary16 bits in an Int using round-to-nearest-even.
     * Overflow becomes infinity; NaNs become a signed canonical quiet half-NaN.
     */
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
        // Subnormal half values require restoring the implicit leading bit before rounding.
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
