package io.legado.app.feature.reader.core.transition.ioscurl.mesh.generation

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import io.legado.app.feature.reader.core.transition.ioscurl.math.BinaryMath.fma

/**
 * Growable primitive buffer for normal-axis distances.
 * clear resets the logical size without releasing storage. Ordering is established by collectors.
 */
internal class SampleBuffer(capacity: Int = 128) {
    private var distances = DoubleArray(capacity)
    var size = 0
        private set

    /**
     * Reads a populated sample slot; callers keep the index within size.
     */
    operator fun get(sample: Int): Double = distances[sample]

    /**
     * Replaces an existing slot without changing size.
     */
    operator fun set(sample: Int, distance: Double) {
        distances[sample] = distance
    }

    /**
     * Discards samples while retaining capacity for the next frame.
     */
    fun clear() {
        size = 0
    }

    /**
     * Appends a distance, growing capacity geometrically when necessary.
     */
    fun add(distance: Double) {
        if (size == distances.size) distances = distances.copyOf(maxOf(1, size * 2))
        distances[size++] = distance
    }
}

/**
 * Collects descending bend samples around center, clamped to the visible interval.
 * The center is deliberately duplicated to separate the two sides of the strip.
 */
internal fun collectCurveSamples(
    center: Double,
    lower: Double,
    upper: Double,
    step: Double,
    destination: SampleBuffer,
): Int {
    destination.clear()
    if (step == 0.0) {
        destination.add(center)
        destination.add(center)
        return 2
    }
    val first = max(floor(((center - upper) / step).toFloat()), -12f).toLong().toInt()
    val last = min(ceil(((center - lower) / step).toFloat()), 12f).toLong().toInt()
    for (index in first..last) {
        val candidate = fma(-step, index.toFloat().toDouble(), center)
        val capped = if (upper < candidate) upper else candidate
        val value = if (lower > capped) lower else capped
        destination.add(value)
        // Keep two coincident sections at the fold split; merging must not deduplicate them.
        if (index == 0) destination.add(value)
    }
    return destination.size
}

/**
 * Collects clamped descending samples for the front shadow region, limiting the subdivision count.
 */
internal fun collectFrontSamples(
    start: Double,
    lower: Double,
    upper: Double,
    step: Double,
    destination: SampleBuffer,
): Int {
    destination.clear()
    val quantized = max(floor((max(upper - start, 0.0) / -step).toFloat()), -100f)
    val first = quantized.toLong().toInt()
    for (index in first..0) {
        val candidate = fma(-step, index.toFloat().toDouble(), start)
        val capped = if (upper < candidate) upper else candidate
        val value = if (lower > capped) lower else capped
        destination.add(value)
    }
    return destination.size
}

/**
 * Samples the cast-shadow span using coarse steps and ten finer steps near its lower end.
 * Returns null when the curl does not overlap the receiving region.
 */
internal fun collectShadowSamples(
    lower: Double,
    upper: Double,
    curlStart: Double,
    tangentStart: Double,
    coarseStep: Double,
    samples: SampleBuffer,
): ShadowSampleRange? {
    samples.clear()
    if (!(curlStart > lower)) return null
    val join = if (lower > tangentStart) lower else tangentStart
    var last: Double
    while (true) {
        val candidate = fma(-coarseStep, samples.size.toFloat().toDouble(), upper)
        last = if (candidate <= join) join else candidate
        samples.add(last)
        if (candidate <= join || samples.size == 50) break
    }
    val fineStep = (last - lower) * 0.1
    if (fineStep > 0.0) {
        val coarseEnd = last
        for (index in 1..10) {
            val candidate = fma(-fineStep, index.toFloat().toDouble(), coarseEnd)
            last = if (candidate <= lower) lower else candidate
            samples.add(last)
            if (candidate <= lower) break
        }
    }
    return ShadowSampleRange(lower, upper, last)
}

/**
 * Collects up to two strictly interior breakpoints in descending order.
 */
internal fun collectInteriorEdges(
    destination: SampleBuffer,
    lower: Double,
    upper: Double,
    first: Double,
    second: Double,
): Int {
    destination.clear()
    if (first > lower && first < upper) destination.add(first)
    if (second > lower && second < upper) {
        if (destination.size == 1 && second > first) {
            destination[0] = second
            destination.add(first)
        } else destination.add(second)
    }
    return destination.size
}

/**
 * Merges two descending sequences without removing equal distances.
 * Duplicates preserve strip seams; onEdge receives the edge index and its output index.
 * The destination must not alias either input buffer.
 */
internal inline fun mergeDescendingSamples(
    samples: SampleBuffer,
    edges: SampleBuffer,
    destination: SampleBuffer,
    onEdge: (Int, Int) -> Unit = { _, _ -> },
): Int {
    destination.clear()
    var sample = 0
    var edge = 0
    while (sample < samples.size || edge < edges.size) {
        val takeSample =
            edge == edges.size || (sample < samples.size && samples[sample] > edges[edge])
        val distance =
            if (takeSample) samples[sample++]
            else {
                onEdge(edge, destination.size)
                edges[edge++]
            }
        destination.add(distance)
    }
    return destination.size
}

/**
 * Projects the two receiving-rectangle edge corners onto the curl normal and keeps interior breakpoints.
 */
internal fun collectProjectedEdges(
    projection: CurlProjection,
    bounds: ShadowBounds,
    destination: SampleBuffer,
    lower: Double,
    upper: Double,
): Int {
    val p = projection
    val b = bounds
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
    return collectInteriorEdges(destination, lower, upper, first, second)
}

/**
 * Insertion-sorts the small corner buffer in descending order without allocating a boxed list.
 */
internal fun sortProjectedBreakpoints(samples: SampleBuffer) {
    for (sorted in 1 until samples.size) {
        val key = samples[sorted]
        var insertion = sorted
        while (insertion > 0 && key > samples[insertion - 1]) {
            samples[insertion] = samples[insertion - 1]
            insertion--
        }
        samples[insertion] = key
    }
}
