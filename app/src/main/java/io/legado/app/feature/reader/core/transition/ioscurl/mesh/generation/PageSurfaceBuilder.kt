package io.legado.app.feature.reader.core.transition.ioscurl.mesh.generation

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import io.legado.app.feature.reader.core.transition.ioscurl.math.BinaryMath.fma

/**
 * Reusable buffers for the curved page, independent back content and front shading strips.
 */
internal class PageSurfaceBuilder {
    val curve = SurfaceStrips()
    val back = SurfaceStrips()
    val front = SurfaceStrips()
    val frontSamples = SampleBuffer()
    val frontEdges = SampleBuffer(2)
    val frontPositions = SampleBuffer()

    /**
     * Resets emitted geometry; sample buffers are reset by their respective collectors.
     */
    fun clear() {
        curve.clear()
        back.clear()
        front.clear()
    }
}

/**
 * Turns each normal-distance sample into a tangent cross-section, texture coordinates and lighting.
 * Inner endpoints carry color; outer endpoints form transparent antialiasing fringes.
 */
internal fun PageSurfaceBuilder.buildSurfaceStrips(
    projection: CurlProjection,
    curveSamples: CurveSampleRanges,
) {
    val p = projection
    val materialNormalX = p.texture.normalX
    val materialNormalY = p.texture.normalY
    val collapseWeight = 1.0 / (abs(materialNormalX / materialNormalY) + 1.0)
    val firstVisible = curveSamples.firstVisible
    val lastVisible = curveSamples.lastVisible
    val lastSample = curveSamples.count - 1
    val backStart = curveSamples.backStart
    val count = curveSamples.count
    val inverseX = 1.0 / p.texture.tangentX
    val inverseY = 1.0 / p.texture.tangentY
    val normalX = p.basis.normalX
    val normalY = p.basis.normalY
    val tangentX = p.basis.tangentX
    val tangentY = p.basis.tangentY
    val textureTangentX = p.texture.tangentX
    val textureTangentY = p.texture.tangentY
    val materialX = p.textureOrigin.x
    val materialY = p.textureOrigin.y
    val textureX = p.textureOffset.x
    val textureY = p.textureOffset.y
    val originX = p.origin.x
    val originY = p.origin.y
    val curlStart = p.curlStart
    val radius = p.radius
    val startAngle = p.minimumAngle
    val endAngle = p.maximumAngle
    val inner = LineInterval()
    val outer = LineInterval()
    repeat(count) { sample ->
        val index = sample
        val distance = curveSamples.positions[index]
        val deltaU = materialNormalX * distance
        val deltaV = materialNormalY * distance
        val clipX = materialX + deltaU
        val clipY = materialY + deltaV
        p.innerTextureBounds.intersectLine(clipX, clipY, inverseX, inverseY, inner)
        p.outerTextureBounds.intersectLine(clipX, clipY, inverseX, inverseY, outer)
        var sine = 0.0
        val displacement =
            curlDisplacement(distance, curlStart, radius, startAngle, endAngle) {
                sine = it
            }
        val curledDistance = distance + displacement.toDouble()
        // Collapse offscreen opaque sections while retaining their outer fringe endpoints for strip continuity.
        if (index < firstVisible || index > lastVisible) {
            val collapsed = fma(collapseWeight, outer.upper - outer.lower, outer.lower)
            inner.lower = collapsed
            inner.upper = collapsed
        }
        // Transparent terminal sections fade the beginning and end of the page strips.
        val endpoint = index == 0 || index == lastSample
        val sectionColor =
            if (endpoint) PackedHalfColor.TRANSPARENT
            else if (index < backStart) p.palette.front
            // Front-facing and transparent terminal sections never use back-face lighting.
            else blendHalfColors(p.palette.back, p.palette.backShade, surfaceLighting(sine))

        curve.appendSection(
            index,
            fma(normalX, curledDistance, originX),
            fma(normalY, curledDistance, originY),
            textureX + deltaU,
            textureY + deltaV,
            tangentX,
            tangentY,
            textureTangentX,
            textureTangentY,
            outer.lower.toFloat(),
            outer.upper.toFloat(),
            inner.lower.toFloat(),
            inner.upper.toFloat(),
            sectionColor,
        )
    }
}

/**
 * Maps the curve-sine value to the calibrated back-face lighting blend.
 * This piecewise response is a visual calibration rather than a physical lighting model.
 */
internal fun surfaceLighting(sine: Double): Double {
    val value = max(min(fma(sine, 2.5, -1.5000000596046448), 1.0), 0.0)
    return if (!(value > 0.755f.toDouble())) fma(value, 0.35f.toDouble(), (value * value) * 0.375)
    else {
        val shifted = value + -(0.82f.toDouble())
        fma(shifted * shifted, 15.0, 0.4f.toDouble())
    }
}

/**
 * Copies back-facing geometry for a separately textured back pass, replacing its tint with white.
 * Transparent back materials also need matching fringe geometry.
 */
internal fun PageSurfaceBuilder.copyBackStrips(
    projection: CurlProjection,
    curveSamples: CurveSampleRanges,
) {
    if (!projection.frame.input.backEnabled) return
    val first = curveSamples.backStart * 2
    val count = curveSamples.backCount * 2
    val tint = PackedHalfColor.WHITE
    back.center.copyFrom(curve.center, first, count)
    for (vertex in first until first + count) back.center.color(vertex, tint)
    val alpha = projection.palette.back.alpha
    if (alpha != 0f) return
    val fringes = curveSamples.backFringeVertices
    back.lower.copyFrom(curve.lower, first, fringes)
    back.upper.copyFrom(curve.upper, first, fringes)
    for (sample in curveSamples.backStart until curveSamples.count) {
        back.lower.color(sample * 2 + 1, tint)
        back.upper.color(sample * 2, tint)
    }
}

/**
 * Builds the front shadow/fade region using the receiving rectangle and the curl displacement.
 * This stage runs after ShadowBuilder has established its orientation-dependent bounds.
 */
internal fun PageSurfaceBuilder.buildFrontStrips(
    projection: CurlProjection,
    shadowBounds: ShadowBounds,
) {
    val enabled = projection.frame.input.frontEnabled
    val time = projection.frame.time
    if (!enabled) {
        return
    }
    val p = projection
    val pageNormalX = p.basis.normalX
    val pageNormalY = p.basis.normalY
    val originX = p.origin.x
    val originY = p.origin.y
    val start = p.curlStart
    val faceEnd = p.frontExtent + start
    val shapeRadius = p.radius
    val shapeEndAngle = p.maximumAngle
    val angle = kotlin.math.PI / 2
    val minimumAngle = p.minimumAngle
    val inverseRadius = 1.0 / shapeRadius
    val lowerProjection =
        fma(
            pageNormalX,
            shadowBounds.lowerX - originX,
            pageNormalY * (shadowBounds.lowerY - originY),
        )
    val upperProjection =
        fma(
            pageNormalX,
            shadowBounds.upperX - originX,
            pageNormalY * (shadowBounds.upperY - originY),
        )
    val lower = if (lowerProjection > start) lowerProjection else start
    val upper = if (upperProjection < faceEnd) upperProjection else faceEnd
    val step = (shapeRadius * p.frontAngle) * 0.01
    collectFrontSamples(start, lower, upper, step, frontSamples)
    collectProjectedEdges(projection, shadowBounds, frontEdges, lower, upper)
    val count = mergeDescendingSamples(frontSamples, frontEdges, frontPositions)
    if (count == 0) {
        return
    }

    val initialDisplacement =
        curlDisplacement(start, start, shapeRadius, minimumAngle, shapeEndAngle).toDouble()
    val initialAngle = (angle - (0.0 / shapeRadius)).toFloat()
    val initialCurve = frontNormalOffset(shapeRadius, initialAngle)
    val initialPosition = start + initialDisplacement
    val initialDistance =
        (faceEnd + fma(initialCurve, 0.4f.toDouble(), -initialPosition)) / shapeRadius
    val initialFade = frontFade(initialDistance + -(0.95f.toDouble()))
    val tint = p.palette.shadow
    val tintScale = 76.5 * min(time * 15.0, 1.0)
    val tangentX = p.basis.tangentX
    val tangentY = p.basis.tangentY
    val interval = LineInterval()
    val clip =
        OrientedClipBounds(
            shadowBounds.firstEdgeX,
            shadowBounds.firstEdgeY,
            shadowBounds.secondEdgeX,
            shadowBounds.secondEdgeY,
        )
    repeat(count) { index ->
        val distance = frontPositions[index]
        val lineX = fma(pageNormalX, distance, originX)
        val lineY = fma(pageNormalY, distance, originY)
        val displacement =
            curlDisplacement(distance, start, shapeRadius, minimumAngle, shapeEndAngle).toDouble()
        clip.intersectLine(lineX, lineY, 1.0 / tangentX, 1.0 / tangentY, interval)
        val localAngle = fma(start - distance, inverseRadius, angle).toFloat()
        val normalOffset = frontNormalOffset(shapeRadius, localAngle)
        val spread = normalOffset * 0.4f.toDouble()
        val shift = displacement - spread
        val x = fma(pageNormalX, shift, lineX)
        val y = fma(pageNormalY, shift, lineY)
        val lowerEdge = interval.lower
        val upperEdge = interval.upper
        val normalized =
            fma(faceEnd + (spread - (distance + displacement)), inverseRadius, -(0.95f.toDouble()))
        val strength = (frontFade(normalized) - initialFade) * tintScale
        val width = upperEdge - lowerEdge
        val halfWidth = width * 0.5
        val alpha =
            if (!(halfWidth < spread)) strength else ((((width * 0.25) / spread) + 0.5) * strength)
        val inner = if (spread < halfWidth) spread else halfWidth
        val color =
            modulateHalfColor(
                tint,
                (alpha * MeshConstants.NORMALIZED_CHANNEL_SCALE_DOUBLE).toFloat(),
            )
        front.appendSection(
            index,
            x,
            y,
            0.0,
            0.0,
            tangentX,
            tangentY,
            0.0,
            0.0,
            (lowerEdge - spread).toFloat(),
            (upperEdge + spread).toFloat(),
            (lowerEdge + inner).toFloat(),
            (upperEdge - inner).toFloat(),
            color,
        )
    }
}

/**
 * Computes a calibrated normal offset from local bend angle and radius.
 */
private fun frontNormalOffset(radius: Double, angle: Float): Double {
    val squared = angle * angle
    return radius * (fma(squared, curlCosineCore(squared), -0.99860674f).toDouble() + 1.0)
}

/**
 * Evaluates the exponential fade profile from a normalized distance, retaining Float exp precision.
 */
private fun frontFade(normalized: Double): Double {
    // A clamped distance has exp(0) == 1, so the result is exactly 2.
    if (normalized <= 0.0) return 2.0
    val distance = max(normalized, 0.0) * 3.0
    val exponential = exp(-((distance * distance).toFloat())).toDouble()
    val squared = exponential * exponential
    return fma(squared, squared, exponential)
}
