package io.legado.app.feature.reader.core.transition.ioscurl.mesh.kernel

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import io.legado.app.feature.reader.core.transition.ioscurl.math.AppleMath
import io.legado.app.feature.reader.core.transition.ioscurl.math.BinaryMath
import io.legado.app.feature.reader.core.transition.ioscurl.math.BinaryMath.fma
import io.legado.app.feature.reader.core.transition.ioscurl.mesh.CurlColor
import io.legado.app.feature.reader.core.transition.ioscurl.mesh.CurlMeshFrame
import io.legado.app.feature.reader.core.transition.ioscurl.mesh.CurlMeshInput
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlRect

/** Quantize straight RGBA once, premultiply RGB, then pack four binary16 channels. */
private fun packPremultipliedColor(color: CurlColor): Long {
    val channels = doubleArrayOf(color.red, color.green, color.blue, color.alpha)
    var packed = 0L
    for (channel in channels.indices) {
        val premultiplied = if (channel == 3) channels[channel] else channels[channel] * color.alpha
        val quantized =
            fma(premultiplied, KernelTuning.CHANNEL_MAX, 0.5)
                .coerceIn(0.0, KernelTuning.CHANNEL_MAX)
                .toInt()
        val normalized = quantized.toFloat() * KernelTuning.NORMALIZED_CHANNEL_SCALE
        packed = packed or (floatHalf(normalized) shl (channel * KernelLayout.HALF_BITS))
    }
    return packed
}

internal fun CurlMeshKernel.build(input: CurlMeshInput): CurlMeshFrame {
    require(
        input.width.isFinite() &&
            input.height.isFinite() &&
            input.width in 1.0..KernelTuning.MAX_DIMENSION &&
            input.height in 1.0..KernelTuning.MAX_DIMENSION
    )
    require(
        input.scale.isFinite() &&
            input.scale in KernelTuning.MIN_SCALE..KernelTuning.MAX_SCALE &&
            input.values.radius > 0 &&
            input.values.radius <= KernelTuning.MAX_DIMENSION
    )
    require(
        input.values.time.isFinite() &&
            input.values.angle.isFinite() &&
            input.startAngle.isFinite() &&
            input.endAngle.isFinite()
    )
    require(input.shadowSoftness.isFinite() && input.shadowSoftness in 1.0..3.0)
    resetWorkspace()

    val control = KernelLayout.Workspace.CONTROL
    val material = KernelLayout.Workspace.MATERIAL
    val context = KernelLayout.Workspace.CONTEXT
    val layerState = KernelLayout.Workspace.LAYER_STATE
    val drawState = KernelLayout.Workspace.DRAW_STATE
    val surface = KernelLayout.Workspace.SURFACE
    val callbackTable = KernelLayout.Workspace.CALLBACK_TABLE
    KernelTuning.FADE_WEIGHTS.forEachIndexed { index, value ->
        writeDouble(FADE_WEIGHTS + index * KernelLayout.DOUBLE_BYTES, value)
    }
    KernelTuning.FADE_STEPS.forEachIndexed { index, value ->
        writeDouble(FADE_STEPS + index * KernelLayout.DOUBLE_BYTES, value * input.shadowSoftness)
    }
    writeDouble(CLAMP_STEPS, KernelTuning.INNER_SAMPLE_LIMIT)
    writeDouble(
        CLAMP_STEPS + KernelLayout.DOUBLE_BYTES,
        KernelTuning.INNER_SAMPLE_LIMIT * KernelTuning.SAMPLE_LIMIT_RATIO,
    )
    writeDouble(CLAMP_STEPS2, KernelTuning.OUTER_SAMPLE_LIMIT)
    writeDouble(
        CLAMP_STEPS2 + KernelLayout.DOUBLE_BYTES,
        KernelTuning.OUTER_SAMPLE_LIMIT * KernelTuning.SAMPLE_LIMIT_RATIO,
    )

    val pixelWidth = ceil(input.width * input.scale).toInt()
    val pixelHeight = ceil(input.height * input.scale).toInt()
    val source =
        input.surfaceRect ?: CurlRect(0.0, 0.0, pixelWidth.toDouble(), pixelHeight.toDouble())
    val target = input.targetBounds ?: CurlRect(0.0, 0.0, source.width, source.height)
    val requestedClip = input.clipRect ?: target
    val clipLeft = max(target.x, requestedClip.x)
    val clipTop = max(target.y, requestedClip.y)
    clip =
        CurlRect(
            clipLeft,
            clipTop,
            max(
                0.0,
                min(target.x + target.width, requestedClip.x + requestedClip.width) - clipLeft,
            ),
            max(
                0.0,
                min(target.y + target.height, requestedClip.y + requestedClip.height) - clipTop,
            ),
        )

    writeDouble(control + KernelLayout.Control.TIME, input.values.time.coerceIn(0.0, 1.0))
    writeDouble(control + KernelLayout.Control.ANGLE, input.values.angle)
    writeDouble(control + KernelLayout.Control.RADIUS, input.values.radius)
    writeDouble(control + KernelLayout.Control.START_ANGLE, input.startAngle)
    writeDouble(control + KernelLayout.Control.END_ANGLE, max(input.startAngle, input.endAngle))
    var (sine, cosine) = AppleMath.sincos(input.values.angle.toFloat())
    if (
        abs(sine.toDouble()) < KernelTuning.AXIS_SNAP_EPSILON ||
            abs(cosine.toDouble()) < KernelTuning.AXIS_SNAP_EPSILON
    ) {
        sine = roundAwayFromZero(sine)
        cosine = roundAwayFromZero(cosine)
    }
    writeDouble(control + KernelLayout.Control.NORMAL_X, cosine.toDouble())
    writeDouble(control + KernelLayout.Control.NORMAL_Y, sine.toDouble())
    writeDouble(control + KernelLayout.Control.TANGENT_X, -sine.toDouble())
    writeDouble(control + KernelLayout.Control.TANGENT_Y, cosine.toDouble())
    val shadowBounds =
        input.shadowBounds
            ?: if (input.width <= 2 || input.height <= 2) CurlRect(1.0, 1.0, 0.0, 0.0)
            else CurlRect(1.0, 1.0, input.width - 2, input.height - 2)
    putRect(control + KernelLayout.Control.SHADOW_BOUNDS, shadowBounds)
    writeByte(material + KernelLayout.Material.FRONT_ENABLED, if (input.frontEnabled) 1 else 0)
    writeByte(material + KernelLayout.Material.BACK_ENABLED, if (input.backEnabled) 1 else 0)

    // Keep FMA evaluation order and signed zero: algebraic simplification changes
    // rounding at axis-aligned poses and breaks the reference vertex hashes.
    val inverseScale = input.scale * (1 / fma(input.scale, input.scale, -(0.0 * 0.0)))
    writeDouble(material + KernelLayout.Material.INVERSE_TRANSFORM, inverseScale)
    writeDouble(material + KernelLayout.Material.INVERSE_Y_FROM_Y, inverseScale)
    writeDouble(material + KernelLayout.Material.INVERSE_Y_FROM_X, -0.0)
    writeDouble(material + KernelLayout.Material.INVERSE_X_FROM_Y, -0.0)
    writeDouble(material + KernelLayout.Material.INVERSE_Z_SCALE, 1.0)
    writeDouble(material + KernelLayout.Material.INVERSE_W_SCALE, 1.0)
    putRect(
        material + KernelLayout.Material.PAGE_BOUNDS,
        CurlRect(0.0, 0.0, input.width, input.height),
    )
    putRect(material + KernelLayout.Material.SOURCE_BOUNDS, source)
    var normalX = fma(input.scale, cosine.toDouble(), 0.0 * sine.toDouble())
    var normalY = fma(0.0, cosine.toDouble(), input.scale * sine.toDouble())
    var tangentX = fma(input.scale, -sine.toDouble(), 0.0 * cosine.toDouble())
    val tangentY = fma(0.0, -sine.toDouble(), input.scale * cosine.toDouble())
    if (
        abs(tangentX) < KernelTuning.TANGENT_SNAP_EPSILON ||
            abs(tangentY) < KernelTuning.TANGENT_SNAP_EPSILON
    ) {
        val length = sqrt(fma(tangentX, tangentX, tangentY * tangentY).toFloat())
        tangentX =
            if (abs(tangentX) < KernelTuning.TANGENT_SNAP_EPSILON) 0.0
            else BinaryMath.copySign(length, tangentX.toFloat()).toDouble()
        val snappedTangentY =
            if (abs(tangentY) < KernelTuning.TANGENT_SNAP_EPSILON) 0.0
            else BinaryMath.copySign(length, tangentY.toFloat()).toDouble()
        val snappedNormalX = fma(inverseScale, normalX, -0.0 * normalY)
        val snappedNormalY = fma(-0.0, normalX, inverseScale * normalY)
        val snappedTangentX = fma(0.0, normalX, -(inverseScale * normalY))
        writeDouble(control + KernelLayout.Control.NORMAL_X, snappedNormalX)
        writeDouble(control + KernelLayout.Control.NORMAL_Y, snappedNormalY)
        writeDouble(control + KernelLayout.Control.TANGENT_X, snappedTangentX)
        writeDouble(control + KernelLayout.Control.TANGENT_Y, snappedNormalX)
        normalX = fma(input.scale, snappedNormalX, 0.0 * snappedNormalY)
        normalY = fma(0.0, snappedNormalX, input.scale * snappedNormalY)
        writeDouble(material + KernelLayout.Material.TANGENT_Y, snappedTangentY)
    } else writeDouble(material + KernelLayout.Material.TANGENT_Y, tangentY)
    writeDouble(material + KernelLayout.Material.NORMAL_X, normalX)
    writeDouble(material + KernelLayout.Material.NORMAL_Y, normalY)
    writeDouble(material + KernelLayout.Material.TANGENT_X, tangentX)
    writeLongBits(
        material + KernelLayout.Material.FRONT_COLOR,
        packPremultipliedColor(input.material.front),
    )
    writeLongBits(
        material + KernelLayout.Material.BACK_COLOR,
        packPremultipliedColor(input.material.back),
    )
    writeLongBits(
        material + KernelLayout.Material.BACK_SHADE,
        packPremultipliedColor(input.material.backShade),
    )
    writeLongBits(
        material + KernelLayout.Material.SHADOW_COLOR,
        packPremultipliedColor(input.material.shadow),
    )
    writeLongBits(material + KernelLayout.Material.FRONT_MULTIPLIER, KernelLayout.HALF_RGBA_WHITE)
    writeByte(material + KernelLayout.Material.LIGHTING_ENABLED, 1)
    writeDouble(material + KernelLayout.Material.OPACITY, 1.0)
    writeLongBits(context + KernelLayout.Context.CALLBACK_TABLE, callbackTable)
    writeLongBits(context + KernelLayout.Context.DRAW_STATE, drawState)
    writeLongBits(context + KernelLayout.Context.LAYER_STATE, layerState)
    writeLongBits(
        layerState + KernelLayout.LayerState.COLOR_MULTIPLIER,
        KernelLayout.HALF_RGBA_WHITE,
    )
    writeByte(drawState + KernelLayout.DrawState.BLEND_FUNCTION, 3)
    writeByte(drawState + KernelLayout.DrawState.FLAGS, 1)
    longArrayOf(source.x.toLong(), source.y.toLong(), source.width.toLong(), source.height.toLong())
        .forEachIndexed { index, value ->
            writeWord(
                surface + KernelLayout.Surface.PIXEL_BOUNDS + index * KernelLayout.FLOAT_BYTES,
                value,
            )
        }
    contextArgument = context
    controlInputAddress = control
    materialInputAddress = material
    sourceSurfaceAddress = surface
    outputTransformArgument = 0
    execute()
    return CurlMeshFrame(source.width.toInt(), source.height.toInt(), batches.toList())
}

private fun CurlMeshKernel.putRect(address: Long, rect: CurlRect) {
    writeDouble(address, rect.x)
    writeDouble(address + KernelLayout.Rect.Y, rect.y)
    writeDouble(address + KernelLayout.Rect.WIDTH, rect.width)
    writeDouble(address + KernelLayout.Rect.HEIGHT, rect.height)
}

private fun roundAwayFromZero(value: Float) =
    if (value < 0) ceil(value - 0.5f) else floor(value + 0.5f)
