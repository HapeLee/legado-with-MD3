package io.legado.app.feature.reader.core.transition.ioscurl.mesh.generation

import kotlin.math.PI
import io.legado.app.feature.reader.core.transition.ioscurl.math.BinaryMath.fma

/**
 * How a shadow segment ends: receiving-page edge, terminal sample or interior subdivision.
 */
internal enum class ShadowBoundary {
    FirstEdge,
    Terminal,
    SecondEdge,
    Interior,
}

/**
 * Origin, two radial endpoints and seed color for a rounded penumbra corner.
 */
internal data class ShadowCorner(
    val origin: MeshPoint,
    val start: MeshPoint,
    val end: MeshPoint,
    val color: PackedHalfColor,
)

/**
 * One outward fade band with lower/upper strips, an optional end cap and up to four rounded corners.
 */
internal class PenumbraBand {
    val lower = VertexBuffer()
    val upper = VertexBuffer()
    val cap = VertexBuffer(4)
    val corners = List(4) { VertexBuffer(60) }

    /** Resets frame-local output while keeping allocated buffers for reuse. */
    fun clear() {
        lower.clear()
        upper.clear()
        cap.clear()
        corners.forEach { it.clear() }
    }
}

/**
 * Owns reusable cast-shadow samples, segment boundaries and three outward penumbra bands.
 * Build establishes receiving bounds even when the back face and shadow are disabled.
 */
internal class ShadowBuilder {
    var bounds = ShadowBounds.EMPTY
    var clip = OrientedClipBounds(0.0, 0.0, 0.0, 0.0)
    var profile = ShadowIntensityProfile.forTime(0.0)
    var topology = ShadowSegments.EMPTY
    val samples = SampleBuffer()
    val edges = SampleBuffer(2)
    val merged = SampleBuffer()
    val positions = SampleBuffer()
    val spreads = SampleBuffer()
    var segmentCounts = IntArray(0)
    var segmentCount = 0
    var capped = false
    val corners = ArrayList<ShadowCorner>(4)
    val fadeSteps = DoubleArray(3)
    val bands = List(3) { PenumbraBand() }

    /** Resets frame-local output while keeping allocated buffers for reuse. */
    fun clear() {
        topology = ShadowSegments.EMPTY
        samples.clear()
        edges.clear()
        merged.clear()
        positions.clear()
        spreads.clear()
        segmentCount = 0
        capped = false
        corners.clear()
        bands.forEach { it.clear() }
    }
}

/**
 * Receiving-rectangle corners reordered to follow the curl normal and tangent.
 * lower/upper bound the normal projection; firstEdge/secondEdge identify tangent-side changes.
 */
internal data class ShadowBounds(
    val lowerX: Double,
    val lowerY: Double,
    val upperX: Double,
    val upperY: Double,
    val firstEdgeX: Double,
    val firstEdgeY: Double,
    val secondEdgeX: Double,
    val secondEdgeY: Double,
) {
    companion object {
        val EMPTY = ShadowBounds(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)

        /**
         * Orders rectangle corners according to the tangent signs, rather than numerical min/max.
         */
        fun fromRectangle(
            left: Double,
            top: Double,
            width: Double,
            height: Double,
            tangentX: Double,
            tangentY: Double,
        ): ShadowBounds {
            val right = left + width
            val bottom = top + height
            val decreasingY = tangentX < 0.0
            val increasingX = tangentY > 0.0
            return ShadowBounds(
                lowerX = if (increasingX) left else right,
                lowerY = if (decreasingY) top else bottom,
                upperX = if (increasingX) right else left,
                upperY = if (decreasingY) bottom else top,
                firstEdgeX = if (decreasingY) right else left,
                firstEdgeY = if (increasingX) top else bottom,
                secondEdgeX = if (decreasingY) left else right,
                secondEdgeY = if (increasingX) bottom else top,
            )
        }
    }
}

/**
 * Inner/outer shadow intensity limits in the 8-bit channel domain.
 */
internal data class ShadowIntensityProfile(val innerLimit: Double, val outerLimit: Double) {
    companion object {
        private val minimum =
            ShadowIntensityProfile(
                MeshConstants.INNER_SAMPLE_LIMIT * MeshConstants.SAMPLE_LIMIT_RATIO,
                MeshConstants.OUTER_SAMPLE_LIMIT * MeshConstants.SAMPLE_LIMIT_RATIO,
            )
        private val maximum =
            ShadowIntensityProfile(MeshConstants.INNER_SAMPLE_LIMIT, MeshConstants.OUTER_SAMPLE_LIMIT)

        /**
         * Ramps shadow intensity with normalized turn progress, clamped to calibrated minimum and maximum limits.
         */
        fun forTime(time: Double): ShadowIntensityProfile {
            val shade = time * 1.35f.toDouble()
            return when {
                shade > 1.0 -> maximum
                shade < MeshConstants.SAMPLE_LIMIT_RATIO -> minimum

                else -> ShadowIntensityProfile(time * 229.50000405311584, time * 243.00000429153442)
            }
        }
    }
}

/**
 * Normal-distance limits and the final sampled distance of a cast-shadow span.
 */
internal data class ShadowSampleRange(
    val lower: Double,
    val upper: Double,
    val last: Double,
)

/**
 * Shadow samples grouped at receiving-page edge crossings.
 *
 * @property positions Descending distances, including repeated seam and terminal samples.
 * @property ends Boundary indices into positions.
 * @property kinds Boundary classification corresponding to each entry of ends.
 */
internal data class ShadowSegments(
    val positions: SampleBuffer,
    val ends: IntArray,
    val kinds: Array<ShadowBoundary>,
) {
    val sampleCount
        get() = positions.size

    /** Last non-sentinel sample; the final repeated distance closes the strip. */
    val lastSampleIndex
        get() = sampleCount - 2

    companion object {
        val EMPTY = ShadowSegments(SampleBuffer(), IntArray(0), emptyArray())
    }
}

/**
 * Merges edge breakpoints while recording boundary indices and classifying segment ends.
 * Repeated distances are retained because the strip builder uses them to restart segments.
 */
internal fun buildShadowSegments(
    merged: SampleBuffer,
    edges: SampleBuffer,
    destination: SampleBuffer,
    terminal: Double,
    firstEdge: Double,
    secondEdge: Double,
): ShadowSegments {
    val ends = IntArray(edges.size + 1)
    mergeDescendingSamples(merged, edges, destination) { edge, output -> ends[edge] = output }
    ends[edges.size] = destination.size - 2
    val kinds =
        Array(ends.size) { segment ->
            when (destination[ends[segment]]) {
                terminal -> ShadowBoundary.Terminal
                firstEdge -> ShadowBoundary.FirstEdge
                secondEdge -> ShadowBoundary.SecondEdge
                else -> ShadowBoundary.Interior
            }
        }
    return ShadowSegments(destination, ends, kinds)
}

/**
 * Clears the previous frame, establishes bounds, samples the shadow and generates penumbra strips and contours.
 */
internal fun ShadowBuilder.build(projection: CurlProjection) {
    clear()
    val frame = projection.frame
    val rectangle = frame.shadowBounds
    val time = frame.time
    val enabled = frame.input.backEnabled
    MeshConstants.FADE_STEPS.forEachIndexed { index, step ->
        fadeSteps[index] = step * frame.input.shadowSoftness
    }
    val p = projection
    bounds =
        ShadowBounds.fromRectangle(
            rectangle.x,
            rectangle.y,
            rectangle.width,
            rectangle.height,
            p.basis.tangentX,
            p.basis.tangentY,
        )
    val b = bounds
    clip = OrientedClipBounds(b.firstEdgeX, b.firstEdgeY, b.secondEdgeX, b.secondEdgeY)
    if (!enabled) return
    val lower =
        fma(p.basis.normalX, b.lowerX - p.origin.x, p.basis.normalY * (b.lowerY - p.origin.y))
    val tangentStart = fma(p.radius, PI / 2 - p.maximumAngle, p.curlStart)
    val step = (p.radius * p.backAngle) * 0.02
    val range =
        collectShadowSamples(lower, p.split, p.curlStart, tangentStart, step, samples) ?: return
    // Duplicate the terminal distance so the last strip section can close independently.
    samples.add(range.last)
    val first =
        fma(
            p.basis.normalX,
            b.firstEdgeX - p.origin.x,
            p.basis.normalY * (b.firstEdgeY - p.origin.y),
        )
    val second =
        fma(
            p.basis.normalX,
            b.secondEdgeX - p.origin.x,
            p.basis.normalY * (b.secondEdgeY - p.origin.y),
        )
    collectInteriorEdges(edges, range.lower, range.upper, first, second)
    mergeDescendingSamples(samples, edges, merged)
    topology =
        buildShadowSegments(
            merged,
            edges,
            positions,
            range.last,
            first,
            second,
        )
    profile = ShadowIntensityProfile.forTime(time)
    buildPenumbraStrips(projection)
    buildPenumbraContours(projection)
}
