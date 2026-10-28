@file:Suppress("FunctionName", "UNUSED_PARAMETER")

package io.legado.app.feature.reader.core.transition.ioscurl.mesh.kernel

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import io.legado.app.feature.reader.core.transition.ioscurl.math.AppleMath
import io.legado.app.feature.reader.core.transition.ioscurl.math.BinaryMath
import io.legado.app.feature.reader.core.transition.ioscurl.math.BinaryMath.fma
import io.legado.app.feature.reader.core.transition.ioscurl.mesh.CurlDrawBatch

internal fun CurlMeshKernel.readLongBits(p: Long) = memory.long(p)

internal fun CurlMeshKernel.readUnsignedWord(p: Long) = memory.word(p)

internal fun CurlMeshKernel.readUnsignedByte(p: Long) = memory.byte(p)

internal fun CurlMeshKernel.readDouble(p: Long) = Double.fromBits(memory.long(p))

internal fun CurlMeshKernel.readFloat(p: Long) = Float.fromBits(memory.word(p).toInt())

internal fun CurlMeshKernel.writeLongBits(p: Long, v: Long) = memory.long(p, v)

internal fun CurlMeshKernel.writeWord(p: Long, v: Long) = memory.word(p, v)

internal fun CurlMeshKernel.writeHalfWord(p: Long, v: Long) = memory.short(p, v)

internal fun CurlMeshKernel.writeByte(p: Long, v: Long) = memory.byte(p, v)

internal fun CurlMeshKernel.writeDouble(p: Long, v: Double) = memory.long(p, v.toRawBits())

internal fun CurlMeshKernel.writeFloat(p: Long, v: Float) = memory.word(p, v.toRawBits().toLong())

internal fun halfFloat(h: Long) = BinaryMath.halfToFloat(h.toInt())

internal fun floatHalf(f: Float) = BinaryMath.floatToHalf(f).toLong()

internal fun sqrtFloat(f: Float) = sqrt(f)

internal fun CurlMeshKernel.compareFloatingPoint(a: Double, b: Double) {
    if (a.isNaN() || b.isNaN()) {
        negativeResult = false
        zeroResult = false
        unsignedCarry = true
        arithmeticOverflow = true
    } else {
        negativeResult = a < b
        zeroResult = a == b
        unsignedCarry = a >= b
        arithmeticOverflow = false
    }
}

internal fun CurlMeshKernel.updateSubtractionFlags(left: Long, right: Long, bits: Long) {
    val mask = if (bits == 32L) KernelLayout.WORD_MASK else -1L
    val sign = 1L shl (bits.toInt() - 1)
    val a = left and mask
    val b = right and mask
    val r = (a - b) and mask
    negativeResult = r and sign != 0L
    zeroResult = r == 0L
    unsignedCarry = (a xor Long.MIN_VALUE) >= (b xor Long.MIN_VALUE)
    arithmeticOverflow = (a xor b) and (a xor r) and sign != 0L
}

internal fun CurlMeshKernel.updateAdditionFlags(left: Long, right: Long, bits: Long) {
    val mask = if (bits == 32L) KernelLayout.WORD_MASK else -1L
    val sign = 1L shl (bits.toInt() - 1)
    val a = left and mask
    val b = right and mask
    val r = (a + b) and mask
    negativeResult = r and sign != 0L
    zeroResult = r == 0L
    unsignedCarry = (r xor Long.MIN_VALUE) < (a xor Long.MIN_VALUE)
    arithmeticOverflow = (a xor b).inv() and (a xor r) and sign != 0L
}

private fun sinPoly(theta: Double): Float {
    val a = theta.toFloat()
    val a2 = a * a
    return a * fma(a2, fma(a2, 0.0054653967f, -0.1534627f), 0.9844168f)
}

internal fun CurlMeshKernel.curlDisplacement(
    x: Double,
    center: Double,
    radius: Double,
    sineOut: Long,
    lo: Double,
    hi: Double,
): Float {
    val theta = (center - x) / radius + PI / 2
    val t = max(lo, min(theta, hi))
    val sine = sinPoly(t).toDouble()
    var result = 0f
    if (!(theta < lo)) {
        val displacement: Double
        if (theta < hi) displacement = ((theta - sine) * radius).toFloat().toDouble()
        else {
            val a = hi.toFloat()
            val a2 = a * a
            val ns =
                a *
                    fma(
                        a2,
                        fma(a2, -0.0054653967f, 0.1534627f),
                        -0.9844168f,
                    )
            val edge = ((hi + ns.toDouble()) * radius).toFloat().toDouble()
            val distance = fma(PI / 2 - hi, radius, center) - x
            val slope =
                fma(
                    a2,
                    fma(a2, fma(a2, 0.000969668f, -0.03922769f), 0.4953497f),
                    0.0013932586f,
                )
            displacement = fma(distance, slope.toDouble(), edge)
        }
        val correction = ((sinPoly(lo).toDouble() - lo) * radius).toFloat()
        result = (displacement + correction.toDouble()).toFloat()
    }
    if (sineOut != 0L) writeDouble(sineOut, sine)
    return result
}

internal fun CurlMeshKernel.clipProjectedLine(
    lowerBoundAddress: Long,
    upperBoundAddress: Long,
    firstX: Double,
    firstY: Double,
    lastX: Double,
    lastY: Double,
    originX: Double,
    originY: Double,
    inverseDirectionX: Double,
    inverseDirectionY: Double,
) {
    var lowerIntersection = (firstX - originX) * inverseDirectionX
    var upperIntersection = (lastX - originX) * inverseDirectionX
    var verticalIntersection = (firstY - originY) * inverseDirectionY
    if (
        verticalIntersection > lowerIntersection && inverseDirectionY.isFinite() ||
            !inverseDirectionX.isFinite()
    )
        lowerIntersection = verticalIntersection
    verticalIntersection = (lastY - originY) * inverseDirectionY
    if (
        verticalIntersection < upperIntersection && inverseDirectionY.isFinite() ||
            !inverseDirectionX.isFinite()
    )
        upperIntersection = verticalIntersection
    writeDouble(lowerBoundAddress, lowerIntersection)
    writeDouble(upperBoundAddress, upperIntersection)
}

private fun CurlMeshKernel.emit(
    base: Long,
    index: Long,
    x: Float,
    y: Float,
    u: Float,
    v: Float,
    color: Long,
) {
    val p = base + index
    writeFloat(p, x)
    writeFloat(p + 4, y)
    writeFloat(p + 8, 0f)
    writeFloat(
        p + 12,
        1f,
    )
    writeFloat(p + 16, u)
    writeFloat(p + 20, v)
    writeLongBits(p + 40, color)
}

internal fun CurlMeshKernel.appendSurfaceStrip(
    center: Long,
    low: Long,
    high: Long,
    index: Long,
    packedColorAddress: Long,
    px: Double,
    py: Double,
    u: Double,
    v: Double,
    dx: Double,
    dy: Double,
    du: Double,
    dv: Double,
    stack: Long,
) {
    val lowerOuterDistance = readFloat(stack).toDouble()
    val upperOuterDistance = readFloat(stack + 4).toDouble()
    val lowerInnerDistance = readFloat(stack + 8).toDouble()
    val upperInnerDistance = readFloat(stack + 12).toDouble()
    val color = readLongBits(packedColorAddress)
    val firstVertexOffset = index * KernelLayout.Vertex.PAIR_BYTES
    val secondVertexOffset = firstVertexOffset or KernelLayout.Vertex.STRIDE_BYTES.toLong()
    val lowerOuterX = fma(dx, lowerOuterDistance, px).toFloat()
    val lowerOuterY = fma(dy, lowerOuterDistance, py).toFloat()
    val upperOuterX = fma(dx, upperOuterDistance, px).toFloat()
    val upperOuterY = fma(dy, upperOuterDistance, py).toFloat()
    val lowerInnerX = fma(dx, lowerInnerDistance, px).toFloat()
    val lowerInnerY = fma(dy, lowerInnerDistance, py).toFloat()
    val upperInnerX = fma(dx, upperInnerDistance, px).toFloat()
    val upperInnerY = fma(dy, upperInnerDistance, py).toFloat()
    val lowerU = fma(du, lowerInnerDistance, u).toFloat()
    val lowerV = fma(dv, lowerInnerDistance, v).toFloat()
    val upperU = fma(du, upperInnerDistance, u).toFloat()
    val upperV = fma(dv, upperInnerDistance, v).toFloat()
    emit(low, firstVertexOffset, lowerOuterX, lowerOuterY, lowerU, lowerV, 0)
    emit(low, secondVertexOffset, lowerInnerX, lowerInnerY, lowerU, lowerV, color)
    emit(
        center,
        firstVertexOffset,
        lowerInnerX,
        lowerInnerY,
        lowerU,
        lowerV,
        color,
    )
    emit(center, secondVertexOffset, upperInnerX, upperInnerY, upperU, upperV, color)
    emit(high, firstVertexOffset, upperInnerX, upperInnerY, upperU, upperV, color)
    emit(
        high,
        secondVertexOffset,
        upperOuterX,
        upperOuterY,
        upperU,
        upperV,
        0,
    )
}

internal fun CurlMeshKernel.transformPointInPlace(matrixAddress: Long, pointAddress: Long) {
    val px = readDouble(pointAddress)
    val py = readDouble(pointAddress + 8)
    val w =
        fma(
            px,
            readDouble(matrixAddress + KernelLayout.Matrix.W_FROM_X),
            py * readDouble(matrixAddress + KernelLayout.Matrix.W_FROM_Y),
        ) + readDouble(matrixAddress + KernelLayout.Matrix.W_SCALE)
    val t = if (abs(w - 1) < 1e-10) w else if (w > 0) 1 / w else Double.POSITIVE_INFINITY
    writeDouble(
        pointAddress,
        t *
            (readDouble(matrixAddress + KernelLayout.Matrix.TRANSLATION_X) +
                fma(
                    px,
                    readDouble(matrixAddress),
                    py * readDouble(matrixAddress + KernelLayout.Matrix.X_FROM_Y),
                )),
    )
    writeDouble(
        pointAddress + 8,
        t *
            (readDouble(matrixAddress + KernelLayout.Matrix.TRANSLATION_Y) +
                fma(
                    px,
                    readDouble(matrixAddress + KernelLayout.Matrix.Y_FROM_X),
                    py * readDouble(matrixAddress + KernelLayout.Matrix.Y_FROM_Y),
                )),
    )
}

internal fun CurlMeshKernel.measureTransformScale(
    matrixAddress: Long,
    horizontalScaleAddress: Long,
    verticalScaleAddress: Long,
): Double {
    var x =
        fma(
            readDouble(matrixAddress),
            readDouble(matrixAddress),
            readDouble(matrixAddress + KernelLayout.Matrix.Y_FROM_X) *
                readDouble(matrixAddress + KernelLayout.Matrix.Y_FROM_X),
        )
    var y =
        fma(
            readDouble(matrixAddress + KernelLayout.Matrix.X_FROM_Y),
            readDouble(matrixAddress + KernelLayout.Matrix.X_FROM_Y),
            readDouble(matrixAddress + KernelLayout.Matrix.Y_FROM_Y) *
                readDouble(matrixAddress + KernelLayout.Matrix.Y_FROM_Y),
        )
    val w0 = readDouble(matrixAddress + KernelLayout.Matrix.W_SCALE)
    if (w0 != 1.0) {
        val w = 1 / (w0 * w0)
        x *= w
        y *= w
    }
    x = sqrt(x)
    y = sqrt(y)
    if (horizontalScaleAddress != 0L) writeDouble(horizontalScaleAddress, x)
    if (verticalScaleAddress != 0L)
        writeDouble(
            verticalScaleAddress,
            y,
        )
    return (x + y) * 0.5
}

internal fun CurlMeshKernel.writeRotationMatrix(angle: Double, result: Long) {
    val (sn, cs) = AppleMath.sincos(angle)
    doubleArrayOf(
            cs,
            sn,
            -sn,
            cs,
            0.0,
            0.0,
        )
        .forEachIndexed { i, v -> writeDouble(result + i * 8, v) }
}

internal fun CurlMeshKernel.nextClipRectangle(result: Long): Long {
    if (clipped++ != 0L) return 0
    val ints =
        longArrayOf(
            floor(clip.x).toLong(),
            floor(clip.y).toLong(),
            ceil(clip.x + clip.width).toLong(),
            ceil(clip.y + clip.height).toLong(),
        )
    ints.forEachIndexed { i, v -> writeWord(result + i * 4, v) }
    return if (clip.width > 0 && clip.height > 0) 1 else 0
}

internal fun CurlMeshKernel.appendDrawBatch(
    ctx: Long,
    primitive: Long,
    count: Long,
    indices: Long,
    vertices: Long,
    offset: Long,
    n: Long,
    planes: Long,
): Long {
    if (count == 0L) return 0
    check(
        count in 0..KernelTuning.MAX_VERTICES_PER_BATCH &&
            n in 0..KernelTuning.MAX_VERTICES_PER_BATCH &&
            batches.size < KernelTuning.MAX_DRAW_BATCHES
    )
    val state = readLongBits(ctx + KernelLayout.Context.DRAW_STATE)
    val vertexCount = (if (n != 0L) n else count).toInt()
    val index =
        if (indices == 0L) null
        else
            ShortArray(count.toInt()) {
                (readUnsignedByte(indices + it * 2) or
                        (readUnsignedByte(indices + it * 2 + 1) shl 8))
                    .toShort()
            }
    batches +=
        CurlDrawBatch(
            primitive.toInt(),
            readUnsignedByte(state + KernelLayout.DrawState.PROGRAM).toInt(),
            readUnsignedByte(state + KernelLayout.DrawState.BLEND_FUNCTION).toInt(),
            readUnsignedByte(state + KernelLayout.DrawState.FLAGS) and 1L != 0L,
            vertexCount,
            count.toInt(),
            memory.bytes(vertices, vertexCount * KernelLayout.Vertex.STRIDE_BYTES),
            index,
            clip,
        )
    return 0
}
