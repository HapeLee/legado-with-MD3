package io.legado.app.feature.reader.core.transition.ioscurl.mesh.generation

import kotlin.math.max
import kotlin.math.PI
import kotlin.math.withSign
import io.legado.app.feature.reader.core.transition.ioscurl.math.BinaryMath.fma

/**
 * Rectangle endpoints ordered for the current tangent direction.
 * first/last need not be numeric min/max; ordering is chosen by the projection builder.
 */
internal data class OrientedClipBounds(
    val firstX: Double,
    val firstY: Double,
    val lastX: Double,
    val lastY: Double,
) {
    /**
     * Intersects origin + t*tangent with the oriented rectangle, writing its parameter interval.
     * inverseX/inverseY are reciprocal tangent components; infinities encode axis-aligned lines.
     */
    fun intersectLine(
        originX: Double,
        originY: Double,
        inverseX: Double,
        inverseY: Double,
        result: LineInterval,
    ) {
        var lower = (firstX - originX) * inverseX
        var upper = (lastX - originX) * inverseX
        var vertical = (firstY - originY) * inverseY
        if (vertical > lower && inverseY.isFinite() || !inverseX.isFinite()) lower = vertical
        vertical = (lastY - originY) * inverseY
        if (vertical < upper && inverseY.isFinite() || !inverseX.isFinite()) upper = vertical
        result.lower = lower
        result.upper = upper
    }
}

/**
 * Reusable mutable lower/upper parameters for a cross-section/rectangle intersection.
 */
internal class LineInterval(var lower: Double = 0.0, var upper: Double = 0.0)

/**
 * Per-frame projection linking curl-distance samples to geometry and texture coordinates.
 *
 * @property origin Curl reference origin in logical coordinates.
 * @property textureOrigin Corresponding source origin in scaled page coordinates.
 * @property textureOffset Source-relative texture origin.
 * @property innerTextureBounds Inset bounds for opaque strip endpoints.
 * @property outerTextureBounds Outset bounds for transparent antialiasing fringes.
 * @property curlStart Progress-dependent reference distance along the curl normal.
 * @property split Distance separating front-facing and back-facing samples.
 * @property frontExtent Distance from the curl reference to the flat-front extent.
 * @property frontAngle Bend interval before the front/back split.
 * @property backAngle Bend interval after that split.
 */
internal data class CurlProjection(
    val frame: MeshBuildContext,
    val origin: MeshPoint,
    val textureOrigin: MeshPoint,
    val textureOffset: MeshPoint,
    val innerTextureBounds: OrientedClipBounds,
    val outerTextureBounds: OrientedClipBounds,
    val curlStart: Double,
    val split: Double,
    val frontExtent: Double,
    val frontAngle: Double,
    val backAngle: Double,
    val palette: MeshPalette,
) {
    val basis
        get() = frame.logical

    val texture
        get() = frame.texture

    val radius
        get() = frame.radius

    val minimumAngle
        get() = frame.minimumAngle

    val maximumAngle
        get() = frame.maximumAngle
}

/**
 * Owns reusable corner projections and sample buffers.
 * Its sample ranges refer to these buffers and are valid only until the next build.
 */
internal class CurlProjectionBuilder {
    val projectedCorners = SampleBuffer(8)
    val adaptiveSamples = SampleBuffer(26)
    val curvePositions = SampleBuffer()
    var samples = CurveSampleRanges.EMPTY
}

/**
 * Selects orientation-dependent corners, computes the moving curl reference and builds visible sample ranges.
 */
internal fun CurlProjectionBuilder.build(frame: MeshBuildContext): CurlProjection {
    val pageToLogical = frame.pageToLogical
    val material = frame.input.material

    val palette =
        MeshPalette(
            packPremultipliedColor(material.front),
            packPremultipliedColor(material.backShade),
            packPremultipliedColor(material.back),
            packPremultipliedColor(material.shadow),
        )
    val radius = frame.radius
    val minimumAngle = frame.minimumAngle
    val maximumAngle = frame.maximumAngle
    val normalX = frame.logical.normalX
    val normalY = frame.logical.normalY
    val tangentX = frame.logical.tangentX
    val tangentY = frame.logical.tangentY
    val textureNormalX = frame.texture.normalX
    val textureNormalY = frame.texture.normalY
    val textureTangentX = frame.texture.tangentX
    val textureTangentY = frame.texture.tangentY
    val left = frame.source.x
    val top = frame.source.y
    val right = left + frame.source.width
    val bottom = top + frame.source.height
    // Subpixel inset/outset creates a narrow transparent fringe around the sampled page.
    val padding = 0.45f.toDouble()
    val padX =
        if (textureTangentX == 0.0) textureTangentX
        else if (textureTangentX < 0.0) -padding else padding
    val padY =
        if (textureTangentY == 0.0) -textureTangentY
        else if (textureTangentY > 0.0) padding else -padding
    val sourceX = if (textureTangentY > 0.0) left else right
    val sourceEndX = if (textureTangentY > 0.0) right else left
    val sourceY = if (textureTangentX < 0.0) top else bottom
    val sourceEndY = if (textureTangentX < 0.0) bottom else top
    val firstX = if (textureTangentX < 0.0) right else left
    val lastX = if (textureTangentX < 0.0) left else right
    val firstY = if (textureTangentY > 0.0) top else bottom
    val lastY = if (textureTangentY > 0.0) bottom else top
    val origin = pageToLogical.projectPoint(sourceX, sourceY)
    val pageLeft = 0.0
    val pageTop = 0.0
    val pageRight = pageLeft + frame.input.width
    val pageBottom = pageTop + frame.input.height
    val nearX = if (tangentY > 0.0) pageLeft else pageRight
    val farX = if (tangentY > 0.0) pageRight else pageLeft
    val nearY = if (tangentX < 0.0) pageTop else pageBottom
    val farY = if (tangentX < 0.0) pageBottom else pageTop
    val nearProjection = fma(normalX, nearX - origin.x, normalY * (nearY - origin.y))
    val farProjection = fma(normalX, farX - origin.x, normalY * (farY - origin.y))
    // The front/back split occurs at a quarter-turn unless the minimum bend already exceeds it.
    val quarterTurn = PI / 2
    val middleAngle = max(minimumAngle, quarterTurn)
    val startOffset = radius * (quarterTurn - minimumAngle)
    val start = nearProjection - startOffset
    val sineCorrection =
        (minimumAngle - curlSine(minimumAngle).toDouble()) +
            (curlSine(middleAngle).toDouble() - middleAngle)
    val middleOffset = middleAngle + -quarterTurn
    val end = fma(radius, middleOffset + sineCorrection, farProjection)
    val curlStart = start + frame.time * (end - start)
    val split = fma(-radius, middleOffset, curlStart)
    val sourceSpanX = sourceEndX - sourceX
    val sourceSpanY = sourceEndY - sourceY
    projectPageCorners(
        normalX,
        normalY,
        sourceSpanX,
        sourceSpanY,
        padX.toFloat().withSign(sourceSpanX.toFloat()).toDouble(),
        padY.toFloat().withSign(sourceSpanY.toFloat()).toDouble(),
        projectedCorners,
        pageToLogical,
    )
    // Capture bounds in their original corner order before sorting breakpoints by descending distance.
    val visibleUpper = projectedCorners[1]
    val visibleLower = projectedCorners[6]
    sortProjectedBreakpoints(projectedCorners)
    val before = middleAngle - minimumAngle
    val after = maximumAngle - middleAngle
    val step = (radius * (if (before > after) before else after)) * (1.0 / 12.0)
    val upperCandidate = startOffset + curlStart
    val upper = if (visibleUpper < upperCandidate) visibleUpper else upperCandidate
    val lowerCandidate = fma(radius, quarterTurn - maximumAngle, curlStart)
    val lower = if (visibleLower > lowerCandidate) visibleLower else lowerCandidate

    val projection =
        CurlProjection(
            frame,
            origin,
            MeshPoint(sourceX, sourceY),
            MeshPoint(sourceX - left, sourceY - top),
            OrientedClipBounds(padX + firstX, padY + firstY, lastX - padX, lastY - padY),
            OrientedClipBounds(firstX - padX, firstY - padY, padX + lastX, padY + lastY),
            curlStart,
            split,
            startOffset,
            before,
            after,
            palette,
        )
    buildCurveSamples(split, lower, upper, step, visibleUpper, visibleLower)
    return projection
}

/**
 * Projects eight inset/outset corner breakpoints onto the logical curl normal.
 * The initial index order is used to capture visibility bounds before sorting.
 */
private fun projectPageCorners(
    normalX: Double,
    normalY: Double,
    width: Double,
    height: Double,
    padX: Double,
    padY: Double,
    result: SampleBuffer,
    pageToLogical: LinearTransform,
) {
    result.clear()
    val outerX = width + padX
    val innerX = width - padX
    val outerY = height + padY
    val innerY = height - padY
    for (corner in 0..7) {
        val x =
            when (corner) {
                0,
                4 -> outerX
                1,
                5 -> innerX
                2,
                7 -> -padX
                else -> padX
            }
        val y =
            when (corner) {
                0,
                2 -> outerY
                1,
                3 -> innerY
                4,
                7 -> -padY
                else -> padY
            }
        result.add(pageToLogical.projectedOnto(normalX, normalY, x, y))
    }
}

/**
 * Descending normal-distance samples plus front/back and visible index ranges.
 *
 * @property backStart First sample used for the back strips.
 * @property firstVisible First visible sample index, inclusive.
 * @property lastVisible Last visible sample index, inclusive.
 * Each sample becomes two vertices in each strip; repeated split samples are intentional.
 */
internal data class CurveSampleRanges(
    val positions: SampleBuffer,
    val backStart: Int,
    val firstVisible: Int,
    val lastVisible: Int,
) {
    val count
        get() = positions.size

    val backCount
        get() = max(lastVisible - backStart + 1, 0)

    val backFringeVertices
        get() = (count - backStart) * 2

    companion object {
        val EMPTY = CurveSampleRanges(SampleBuffer(), 0, 0, 0)
    }
}

/**
 * Merges adaptive curve samples with projected corners, then finds the visible and back-facing ranges.
 */
private fun CurlProjectionBuilder.buildCurveSamples(
    center: Double,
    lower: Double,
    upper: Double,
    step: Double,
    visibleUpper: Double,
    visibleLower: Double,
) {
    collectCurveSamples(center, lower, upper, step, adaptiveSamples)
    val count = mergeDescendingSamples(adaptiveSamples, projectedCorners, curvePositions)

    var backStart = 1
    while (backStart < count && curvePositions[backStart - 1] > center) backStart++

    var firstVisible = 0
    while (curvePositions[firstVisible] > visibleUpper) firstVisible++
    var lastVisible = firstVisible
    while (lastVisible < count - 1 && curvePositions[lastVisible + 1] >= visibleLower) lastVisible++
    samples = CurveSampleRanges(curvePositions, backStart, firstVisible, lastVisible)
}
