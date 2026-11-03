package io.legado.app.feature.reader.core.transition.ioscurl.mesh.generation

import io.legado.app.feature.reader.core.transition.ioscurl.math.BinaryMath.fma

/**
 * Growable structure-of-arrays storage for vertex positions, source coordinates and packed colors.
 * Only populated slots below size are meaningful; bytes copies them into the public vertex layout.
 */
internal class VertexBuffer(capacity: Int = 128) {
    private var positions = FloatArray(capacity * 2)
    private var textureCoordinates = FloatArray(capacity * 2)
    private var colors = LongArray(capacity)
    var size = 0
        private set

    /** Resets the logical output size while retaining storage. */
    fun clear() {
        size = 0
    }

    /**
     * Reads the x coordinate of a populated vertex.
     */
    fun x(vertex: Int) = positions[vertex * 2]

    /**
     * Reads the y coordinate of a populated vertex.
     */
    fun y(vertex: Int) = positions[vertex * 2 + 1]

    /**
     * Reads the source-relative texture u coordinate of a populated vertex.
     */
    fun u(vertex: Int) = textureCoordinates[vertex * 2]

    /**
     * Reads the source-relative texture v coordinate of a populated vertex.
     */
    fun v(vertex: Int) = textureCoordinates[vertex * 2 + 1]

    /**
     * Reads the packed premultiplied color of a populated vertex.
     */
    fun color(vertex: Int) = PackedHalfColor(colors[vertex])

    /**
     * Replaces only the color of an existing vertex.
     */
    fun color(vertex: Int, color: PackedHalfColor) {
        colors[vertex] = color.bits
    }

    /**
     * Grows all component arrays together so their vertex indices stay aligned.
     */
    private fun reserve(vertices: Int) {
        if (vertices <= colors.size) return
        val capacity = maxOf(vertices, colors.size * 2)
        positions = positions.copyOf(capacity * 2)
        textureCoordinates = textureCoordinates.copyOf(capacity * 2)
        colors = colors.copyOf(capacity)
    }

    /**
     * Writes an indexed vertex and expands the logical size if needed.
     * Unwritten gaps are not initialized for reuse; builders must populate every slot they emit.
     */
    fun write(
        vertex: Int,
        x: Float,
        y: Float,
        u: Float = 0f,
        v: Float = 0f,
        color: PackedHalfColor,
    ) {
        reserve(vertex + 1)
        positions[vertex * 2] = x
        positions[vertex * 2 + 1] = y
        textureCoordinates[vertex * 2] = u
        textureCoordinates[vertex * 2 + 1] = v
        colors[vertex] = color.bits
        size = maxOf(size, vertex + 1)
    }

    /**
     * Copies a populated source range into the same vertex indices of this buffer.
     * The range is not rebased to zero; draw ranges retain their original sample offsets.
     */
    fun copyFrom(source: VertexBuffer, first: Int, count: Int) {
        reserve(first + count)
        source.positions.copyInto(positions, first * 2, first * 2, (first + count) * 2)
        source.textureCoordinates.copyInto(
            textureCoordinates,
            first * 2,
            first * 2,
            (first + count) * 2,
        )
        source.colors.copyInto(colors, first, first, first + count)
        size = maxOf(size, first + count)
    }

    /**
     * Copies a vertex range into a fresh little-endian byte array with a 64-byte stride.
     * Offsets: position x/y/z/w 0/4/8/12, texture u/v 16/20, half-float RGBA 40..47.
     * Reserved bytes are zero; z is 0 and w is 1. See CurlDrawBatch for the renderer contract.
     */
    fun bytes(first: Int, count: Int): ByteArray {
        val output = ByteArray(count * 64)
        for (index in 0 until count) {
            val vertex = first + index
            val offset = index * 64
            output.word(offset, x(vertex).toRawBits())
            output.word(offset + 4, y(vertex).toRawBits())
            output.word(offset + 8, 0f.toRawBits())
            output.word(offset + 12, 1f.toRawBits())
            output.word(offset + 16, u(vertex).toRawBits())
            output.word(offset + 20, v(vertex).toRawBits())
            output.word(offset + 40, colors[vertex].toInt())
            output.word(offset + 44, (colors[vertex] ushr 32).toInt())
        }
        return output
    }
}

/**
 * Writes one 32-bit word in little-endian order, independent of host endianness.
 */
private fun ByteArray.word(offset: Int, bits: Int) {
    for (byte in 0..3) this[offset + byte] = (bits ushr (byte * 8)).toByte()
}

/**
 * Three synchronized triangle strips for one surface: opaque center and transparent lower/upper fringes.
 */
internal class SurfaceStrips {
    val center = VertexBuffer()
    val lower = VertexBuffer()
    val upper = VertexBuffer()

    /** Resets the logical output size while retaining storage. */
    fun clear() {
        center.clear()
        lower.clear()
        upper.clear()
    }
}

/**
 * Appends the paired endpoints of one tangent cross-section to all three strips.
 * px/py is the logical position origin, u/v the source-relative texture origin;
 * dx/dy and du/dv are the geometry and texture tangent directions. Inner endpoints
 * carry material color; outer endpoints share their texture coordinates but have zero alpha.
 */
internal fun SurfaceStrips.appendSection(
    sample: Int,
    px: Double,
    py: Double,
    u: Double,
    v: Double,
    dx: Double,
    dy: Double,
    du: Double,
    dv: Double,
    lowerOuter: Float,
    upperOuter: Float,
    lowerInner: Float,
    upperInner: Float,
    color: PackedHalfColor,
) {
    val lo = lowerOuter.toDouble()
    val hi = upperOuter.toDouble()
    val li = lowerInner.toDouble()
    val ui = upperInner.toDouble()
    val first = sample * 2
    val second = first + 1
    val lowerOuterX = fma(dx, lo, px).toFloat()
    val lowerOuterY = fma(dy, lo, py).toFloat()
    val upperOuterX = fma(dx, hi, px).toFloat()
    val upperOuterY = fma(dy, hi, py).toFloat()
    val lowerInnerX = fma(dx, li, px).toFloat()
    val lowerInnerY = fma(dy, li, py).toFloat()
    val upperInnerX = fma(dx, ui, px).toFloat()
    val upperInnerY = fma(dy, ui, py).toFloat()
    val lowerU = fma(du, li, u).toFloat()
    val lowerV = fma(dv, li, v).toFloat()
    val upperU = fma(du, ui, u).toFloat()
    val upperV = fma(dv, ui, v).toFloat()
    lower.write(first, lowerOuterX, lowerOuterY, lowerU, lowerV, PackedHalfColor.TRANSPARENT)
    lower.write(second, lowerInnerX, lowerInnerY, lowerU, lowerV, color)
    center.write(first, lowerInnerX, lowerInnerY, lowerU, lowerV, color)
    center.write(second, upperInnerX, upperInnerY, upperU, upperV, color)
    upper.write(first, upperInnerX, upperInnerY, upperU, upperV, color)
    upper.write(second, upperOuterX, upperOuterY, upperU, upperV, PackedHalfColor.TRANSPARENT)
}
