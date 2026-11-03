package io.legado.app.feature.reader.core.transition.ioscurl.mesh.generation

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.min
import kotlin.math.sqrt
import io.legado.app.feature.reader.core.transition.ioscurl.math.BinaryMath.fma

/**
 * Seeds clipped shadow cross-sections and extrudes three fading bands on each side.
 * Tracks segment restarts at receiving-page edge crossings to avoid bridging unrelated contours.
 */
internal fun ShadowBuilder.buildPenumbraStrips(projection: CurlProjection) {
    seedShadowCrossSections(projection)
    val topology = topology
    val count = topology.sampleCount
    val counts = IntArray(topology.ends.size + 1)
    var segment = 0
    var lowerX = 0.0
    var lowerY = 0.0
    var upperX = 0.0
    var upperY = 0.0
    for (sample in 0 until count) {
        counts[segment]++
        var matched = false
        var kind = ShadowBoundary.Interior
        for (edge in topology.ends.indices) {
            if (topology.ends[edge] == sample) {
                kind = topology.kinds[edge]
                matched = true
                segment++
                break
            }
        }
        // Every sampled cross-section contributes a pair of vertices to a triangle strip.
        val pair = sample * 2
        if (sample < count - 1) {

            if (!matched || kind == ShadowBoundary.SecondEdge) {
                val skip =
                    matched &&
                        kind == ShadowBoundary.SecondEdge &&
                        sample < topology.lastSampleIndex
                shadowDirection(bands[0].lower, pair + 1, skip, false) { x, y ->
                    lowerX = x
                    lowerY = y
                }
            }
            if (!matched || kind == ShadowBoundary.FirstEdge) {
                val skip =
                    matched &&
                        kind == ShadowBoundary.FirstEdge &&
                        sample < topology.lastSampleIndex
                shadowDirection(bands[0].upper, pair, skip, true) { x, y ->
                    upperX = x
                    upperY = y
                }
            }
        }
        val spread = spreads[sample]
        appendPenumbraBands(pair, lowerX, lowerY, spread, false)
        appendPenumbraBands(pair, upperX, upperY, spread, true)
    }
    segmentCounts = counts
    segmentCount = segment + 1
}

/**
 * Projects each curl sample onto the receiving rectangle and stores its seed color and blur spread.
 */
private fun ShadowBuilder.seedShadowCrossSections(projection: CurlProjection) {
    val p = projection
    val normalX = p.basis.normalX
    val normalY = p.basis.normalY
    val tangentX = p.basis.tangentX
    val tangentY = p.basis.tangentY
    val originX = p.origin.x
    val originY = p.origin.y
    val curlStart = p.curlStart
    val radius = p.radius
    val endAngle = p.maximumAngle
    val inverseRadius = 1.0 / radius
    val spreadRadius = radius * (fma(radius, 0.3f.toDouble(), 30.0) / (radius + 50.0))
    val inverseSpread = 1.0 / spreadRadius
    val innerLimit = profile.innerLimit
    val limitDelta = profile.outerLimit - innerLimit
    val color = p.palette.shadow
    val interval = LineInterval()
    for (sample in 0 until topology.sampleCount) {
        val distance = positions[sample]
        val lineX = fma(normalX, distance, originX)
        val lineY = fma(normalY, distance, originY)
        val displacement =
            curlDisplacement(
                    distance,
                    curlStart,
                    radius,
                    p.minimumAngle,
                    endAngle,
                )
                .toDouble()
        val projectedX = fma(normalX, displacement, lineX)
        val projectedY = fma(normalY, displacement, lineY)
        clip.intersectLine(lineX, lineY, 1.0 / tangentX, 1.0 / tangentY, interval)
        val angle = min((curlStart - distance) * inverseRadius, kotlin.math.PI / 2).toFloat()
        val squared = angle * angle
        val sine =
            fma(
                squared,
                fma(squared, 0.0054653967f, -0.1534627f),
                0.9844168f,
            ) * angle
        val spread = spreadRadius * sine.toDouble()
        spreads.add(spread)
        val intensity = fma(limitDelta, fma(-spread, inverseSpread, 1.0), innerLimit)
        val shadedColor =
            modulateHalfColor(
                color,
                (intensity * MeshConstants.NORMALIZED_CHANNEL_SCALE_DOUBLE).toFloat(),
            )
        val lower = interval.lower
        val upper = interval.upper
        bands[0]
            .lower
            .write(
                sample * 2 + 1,
                fma(tangentX, lower, projectedX).toFloat(),
                fma(tangentY, lower, projectedY).toFloat(),
                0f,
                0f,
                shadedColor,
            )
        bands[0]
            .upper
            .write(
                sample * 2,
                fma(tangentX, upper, projectedX).toFloat(),
                fma(tangentY, upper, projectedY).toFloat(),
                0f,
                0f,
                shadedColor,
            )
    }
}

/**
 * Computes a normalized outward contour normal from neighboring seed vertices.
 * Repeated seam vertices can be skipped; a degenerate direction leaves the previous normal unchanged.
 */
private inline fun shadowDirection(
    vertices: VertexBuffer,
    vertex: Int,
    skipDuplicate: Boolean,
    upper: Boolean,
    use: (Double, Double) -> Unit,
) {
    val next = vertex + if (skipDuplicate) 4 else 2
    val x0 = vertices.x(vertex).toDouble()
    val y0 = vertices.y(vertex).toDouble()
    val x1 = vertices.x(next).toDouble()
    val y1 = vertices.y(next).toDouble()
    val dx = if (upper) y0 - y1 else y1 - y0
    val dy = if (upper) x1 - x0 else x0 - x1
    val inverseLength = 1.0 / sqrt(fma(dx, dx, dy * dy))
    val x = dx * inverseLength
    val y = dy * inverseLength
    if (x.isFinite() && y.isFinite()) use(x, y)
}

/**
 * Extrudes one lower/upper cross-section outward, sharing position and color between adjacent fade bands.
 */
private fun ShadowBuilder.appendPenumbraBands(
    pair: Int,
    directionX: Double,
    directionY: Double,
    spread: Double,
    upper: Boolean,
) {
    val inner = if (upper) 0 else 1
    val outer = 1 - inner
    val seed = if (upper) bands[0].upper else bands[0].lower
    val originX = seed.x(pair + inner).toDouble()
    val originY = seed.y(pair + inner).toDouble()
    val color = seed.color(pair + inner)
    var previousX = originX.toFloat()
    var previousY = originY.toFloat()
    var previousColor = color
    for (band in bands.indices) {
        val destination = if (upper) bands[band].upper else bands[band].lower
        if (band > 0) destination.write(pair + inner, previousX, previousY, color = previousColor)
        val step = fadeSteps[band]
        val x = fma(directionX * step, spread, originX).toFloat()
        val y = fma(directionY * step, spread, originY).toFloat()
        val faded = modulateHalfColor(color, MeshConstants.FADE_WEIGHTS[band].toFloat())
        destination.write(pair + outer, x, y, color = faded)
        previousX = x
        previousY = y
        previousColor = faded
    }
}

/**
 * Closes penumbra ends and corners. Axis-aligned curls use an explicit terminal cap;
 * other orientations use endpoints recorded at segment boundaries.
 */
internal fun ShadowBuilder.buildPenumbraContours(projection: CurlProjection) {
    val normalX = projection.basis.normalX
    capped = normalX == 0.0 || abs(normalX) == 1.0
    if (capped) appendPenumbraEndCap(normalX, projection.basis.normalY)
    else collectPenumbraEndpoints()
    for (corner in corners.indices) appendPenumbraCorner(corner)
}

/**
 * Reads a Float vertex position as a Double point for contour calculations.
 */
private fun VertexBuffer.point(vertex: Int) =
    MeshPoint(x(vertex).toDouble(), y(vertex).toDouble())

/**
 * Selects contour endpoints around first-edge, second-edge and terminal segment boundaries.
 */
private fun ShadowBuilder.collectPenumbraEndpoints() {
    val topology = topology
    val seeds = bands[0]
    val outer = bands[2]
    for (edge in topology.ends.indices) {
        val sample = topology.ends[edge]
        val pair = sample * 2
        val interior = sample >= 1 && sample < topology.lastSampleIndex
        val corner =
            when {
                interior && topology.kinds[edge] == ShadowBoundary.FirstEdge ->
                    ShadowCorner(
                        seeds.lower.point(pair + 1),
                        outer.lower.point(pair + 2),
                        outer.lower.point(pair),
                        seeds.lower.color(pair + 1),
                    )
                interior && topology.kinds[edge] == ShadowBoundary.SecondEdge ->
                    ShadowCorner(
                        seeds.upper.point(pair),
                        outer.upper.point(pair + 1),
                        outer.upper.point(pair + 3),
                        seeds.upper.color(pair),
                    )
                topology.kinds[edge] == ShadowBoundary.Terminal ->
                    ShadowCorner(
                        seeds.lower.point(pair + 1),
                        outer.upper.point(pair + 1),
                        outer.lower.point(pair),
                        seeds.lower.color(pair + 1),
                    )
                else ->
                    ShadowCorner(
                        MeshPoint(0.0, 0.0),
                        MeshPoint(0.0, 0.0),
                        MeshPoint(0.0, 0.0),
                        PackedHalfColor.TRANSPARENT,
                    )
            }
        corners.add(corner)
    }
}

/**
 * Closes the terminal cross-section for an axis-aligned curl and records its two corner joins.
 */
private fun ShadowBuilder.appendPenumbraEndCap(normalX: Double, normalY: Double) {
    val pair = (topology.sampleCount - 1) * 2
    val spread = spreads[topology.sampleCount - 1]
    for (side in 0..1) {
        val seeds = if (side == 0) bands[0].lower else bands[0].upper
        val vertex = if (side == 0) pair + 1 else pair
        val origin = seeds.point(vertex)
        val color = seeds.color(vertex)
        var previousX = origin.x
        var previousY = origin.y
        var previousColor = color
        for (band in bands.indices) {
            val step = fadeSteps[band]

            val nextX = spread * (normalX * step) + origin.x
            val nextY = spread * (normalY * step) + origin.y
            val faded = modulateHalfColor(color, MeshConstants.FADE_WEIGHTS[band].toFloat())
            val destination = bands[band].cap
            destination.write(
                side * 2,
                previousX.toFloat(),
                previousY.toFloat(),
                color = previousColor,
            )
            destination.write(side * 2 + 1, nextX.toFloat(), nextY.toFloat(), color = faded)
            previousX = nextX
            previousY = nextY
            previousColor = faded
        }
        val outer = bands[2]
        val start = if (side == 0) outer.cap.point(1) else outer.upper.point(pair + 1)
        val end = if (side == 0) outer.lower.point(pair) else outer.cap.point(3)
        corners.add(ShadowCorner(origin, start, end, color))
    }
}

/**
 * Builds a rounded corner with 30 radial sections, each carrying all three fade bands.
 * The rotation step divides the endpoint angle into 29 intervals, producing 60 vertices per band.
 */
private fun ShadowBuilder.appendPenumbraCorner(corner: Int) {
    val endpoint = corners[corner]
    val x = endpoint.origin.x
    val y = endpoint.origin.y
    var directionX = endpoint.end.x - x
    var directionY = endpoint.end.y - y
    val startX = endpoint.start.x - x
    val startY = endpoint.start.y - y
    val inverseEnd = 1.0 / sqrt(fma(directionX, directionX, directionY * directionY))
    val inverseStart = 1.0 / sqrt(fma(startX, startX, startY * startY))
    val cosine =
        fma(
            directionX * inverseEnd,
            startX * inverseStart,
            (directionY * inverseEnd) * (startY * inverseStart),
        )
    val rotation = LinearTransform.rotation(acos(cosine.toFloat()).toDouble() * (1.0 / 29.0))
    for (ring in 0..29) {
        var previousX = x
        var previousY = y
        var previousColor = endpoint.color
        for (band in bands.indices) {
            val step = fadeSteps[band]
            val nextX = fma(directionX, step, x)
            val nextY = fma(directionY, step, y)
            val faded = modulateHalfColor(endpoint.color, MeshConstants.FADE_WEIGHTS[band].toFloat())
            val destination = bands[band].corners[corner]
            destination.write(
                ring * 2,
                previousX.toFloat(),
                previousY.toFloat(),
                color = previousColor,
            )
            destination.write(ring * 2 + 1, nextX.toFloat(), nextY.toFloat(), color = faded)
            previousX = nextX
            previousY = nextY
            previousColor = faded
        }
        val nextX = rotation.affineX(directionX, directionY)
        val nextY = rotation.affineY(directionX, directionY)
        directionX = nextX
        directionY = nextY
    }
}
