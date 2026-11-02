package io.legado.app.feature.reader.core.transition.ioscurl.mesh.generation

import io.legado.app.feature.reader.core.transition.ioscurl.math.BinaryMath
import io.legado.app.feature.reader.core.transition.ioscurl.math.BinaryMath.fma
import io.legado.app.feature.reader.core.transition.ioscurl.mesh.CurlColor

/**
 * Premultiplied RGBA stored in four binary16 lanes of one Long.
 * Red occupies bits 0..15, then green, blue and alpha in increasing order.
 */
@JvmInline
internal value class PackedHalfColor(val bits: Long) {
    /**
     * Decodes lane 0..3 as Float; callers must supply a valid channel index.
     */
    fun channel(channel: Int): Float =
        halfFloat((bits ushr (channel * 16)) and HalfColorFormat.HALF_MASK)

    val alpha
        get() = channel(3)

    companion object {
        val TRANSPARENT = PackedHalfColor(0L)
        val WHITE = PackedHalfColor(0x3c00_3c00_3c00_3c00L)
    }
}

/**
 * Scales all premultiplied channels, including alpha, to fade color without a dark fringe.
 */
internal fun modulateHalfColor(color: PackedHalfColor, amount: Float): PackedHalfColor {
    var packed = 0L
    for (channel in 0..3) packed =
        packed or (floatHalf(color.channel(channel) * amount) shl (channel * 16))
    return PackedHalfColor(packed)
}

/**
 * Blends base and lit colors per channel with the supplied lighting weight, then repacks binary16.
 */
internal fun blendHalfColors(
    base: PackedHalfColor,
    lit: PackedHalfColor,
    shade: Double,
): PackedHalfColor {
    val weight = shade.toFloat()
    val inverse = 1f - weight
    var packed = 0L
    for (channel in 0..3) {
        val shaded = lit.channel(channel) * weight
        val value = fma(base.channel(channel), inverse, shaded)
        packed = packed or (floatHalf(value) shl (channel * 16))
    }
    return PackedHalfColor(packed)
}

/**
 * Bit layout shared by color packing and vertex serialization.
 */
internal object HalfColorFormat {
    const val HALF_BITS = 16
    const val HALF_MASK = 0xffffL
}

/**
 * Premultiplies RGB by alpha, rounds to 8-bit channel levels, then stores normalized values as binary16.
 * The intermediate 8-bit quantization is intentional and affects visual compatibility.
 */
internal fun packPremultipliedColor(color: CurlColor): PackedHalfColor {
    val channels = doubleArrayOf(color.red, color.green, color.blue, color.alpha)
    var packed = 0L
    for (channel in channels.indices) {
        val premultiplied = if (channel == 3) channels[channel] else channels[channel] * color.alpha
        val quantized =
            fma(premultiplied, MeshConstants.CHANNEL_MAX, 0.5)
                .coerceIn(0.0, MeshConstants.CHANNEL_MAX)
                .toInt()
        val normalized = quantized.toFloat() * MeshConstants.NORMALIZED_CHANNEL_SCALE
        packed = packed or (floatHalf(normalized) shl (channel * HalfColorFormat.HALF_BITS))
    }
    return PackedHalfColor(packed)
}

/**
 * Packed per-frame material colors reused by page and shadow builders.
 */
internal data class MeshPalette(
    val front: PackedHalfColor,
    val backShade: PackedHalfColor,
    val back: PackedHalfColor,
    val shadow: PackedHalfColor,
)

/**
 * Decodes the low 16 bits as an IEEE 754 binary16 value.
 */
internal fun halfFloat(h: Long) = BinaryMath.halfToFloat(h.toInt())

/**
 * Encodes Float as binary16 using round-to-nearest-even and returns unsigned bits in a Long.
 */
internal fun floatHalf(f: Float) = BinaryMath.floatToHalf(f).toLong()
