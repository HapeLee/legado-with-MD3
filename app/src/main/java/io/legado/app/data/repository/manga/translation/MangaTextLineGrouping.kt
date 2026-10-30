package io.legado.app.data.repository.manga.translation

import io.legado.app.domain.model.manga.MangaTranslatedRegion
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Conservative line grouping: proximity graph, minimum spanning forest, then long-edge cuts.
 * Inspired by Yakuyomi's merge/split stages; physical container ownership is handled by the caller.
 */
internal object MangaTextLineGrouping {
    private data class Line(val left: Float, val top: Float, val right: Float, val bottom: Float,
        val thickness: Float, val vertical: Boolean, val angle: Float, val corners: FloatArray) {
        val cx get() = (left + right) / 2
        val cy get() = (top + bottom) / 2
    }
    private data class Edge(val a: Int, val b: Int, val gap: Float)

    private fun line(region: MangaTranslatedRegion, width: Int, height: Int): Line {
        val box = region.textBox
        val quad = region.sourceLines.singleOrNull()?.quad
        val corners = quad?.pixelCorners(width, height)
        val edges = quad?.edgeLengths(width, height)
        val angle = if (corners == null) 0f else atan2(corners[3] - corners[1], corners[2] - corners[0])
        val l = box.left * width / 1000; val r = box.right * width / 1000
        val t = box.top * height / 1000; val b = box.bottom * height / 1000
        return Line(l, t, r, b, (if (region.vertical) edges?.first ?: (r - l) else edges?.second ?: (b - t)).coerceAtLeast(1f), region.vertical, angle, corners ?: floatArrayOf(l, t, r, t, r, b, l, b))
    }

    private fun projected(line: Line, angle: Float): FloatArray {
        val x = (0..3).map { i -> line.corners[i * 2] * cos(angle) + line.corners[i * 2 + 1] * sin(angle) }
        val y = (0..3).map { i -> -line.corners[i * 2] * sin(angle) + line.corners[i * 2 + 1] * cos(angle) }
        return floatArrayOf(x.min(), y.min(), x.max(), y.max())
    }

    fun split(regions: List<MangaTranslatedRegion>, width: Int, height: Int): List<List<MangaTranslatedRegion>> {
        if (regions.size <= 1) return listOf(regions)
        require(regions.size <= 256) { "Too many loose OCR lines" }
        val lines = regions.map { line(it, width, height) }
        val candidates = mutableListOf<Edge>()
        for (i in lines.indices) for (j in i + 1 until lines.size) {
            val a = lines[i]; val b = lines[j]
            if (a.vertical != b.vertical || maxOf(a.thickness, b.thickness) / minOf(a.thickness, b.thickness) > 1.6f || abs(a.angle - b.angle) > .22f) continue
            val size = minOf(a.thickness, b.thickness)
            val aa = projected(a, (a.angle + b.angle) / 2); val bb = projected(b, (a.angle + b.angle) / 2)
            val overlapX = minOf(aa[2], bb[2]) - maxOf(aa[0], bb[0])
            val overlapY = minOf(aa[3], bb[3]) - maxOf(aa[1], bb[1])
            val gapX = maxOf(0f, maxOf(aa[0], bb[0]) - minOf(aa[2], bb[2]))
            val gapY = maxOf(0f, maxOf(aa[1], bb[1]) - minOf(aa[3], bb[3]))
            val sizeAlong = if (a.vertical) minOf(aa[3] - aa[1], bb[3] - bb[1]) else minOf(aa[2] - aa[0], bb[2] - bb[0])
            val across = if (a.vertical) gapX else gapY
            val overlapAlong = if (a.vertical) overlapY else overlapX
            val aligned = overlapAlong >= sizeAlong * .25f && across <= size * 2.5f
            val sameLane = if (a.vertical) abs(aa[0] + aa[2] - bb[0] - bb[2]) / 2 <= size * .35f && gapY <= size * 1.1f
                else abs(aa[1] + aa[3] - bb[1] - bb[3]) / 2 <= size * .35f && gapX <= size * 1.1f
            if (aligned || sameLane) candidates += Edge(i, j, if (aligned) across else maxOf(gapX, gapY))
        }
        val parent = IntArray(regions.size) { it }
        fun root(i: Int): Int { var p = i; while (p != parent[p]) { parent[p] = parent[parent[p]]; p = parent[p] }; return p }
        val tree = candidates.sortedWith(compareBy<Edge>({ it.gap }, { it.a }, { it.b })).filter { edge ->
            val a = root(edge.a); val b = root(edge.b)
            if (a == b) false else { parent[a] = b; true }
        }
        val components = lines.indices.groupBy { root(it) }.values
        fun cut(members: List<Int>, edges: List<Edge>): List<List<Int>> {
            if (edges.isEmpty()) return members.map(::listOf)
            val longest = edges.maxBy { it.gap }
            val others = edges.filter { it !== longest }.map { it.gap }.sorted()
            val typical = if (others.isEmpty()) 0f else others[others.size / 2]
            val sizes = members.map { lines[it].thickness }.sorted()
            if (longest.gap <= maxOf(sizes[sizes.size / 2] * 1.15f, typical * 1.8f)) return listOf(members)
            val remaining = edges.filter { it !== longest }
            val visited = mutableSetOf<Int>()
            val parts = mutableListOf<List<Int>>()
            for (seed in members) {
                if (!visited.add(seed)) continue
                val part = mutableListOf(seed); val queue = ArrayDeque<Int>().apply { add(seed) }
                while (queue.isNotEmpty()) {
                    val current = queue.removeFirst()
                    for (e in remaining) {
                        val next = if (e.a == current) e.b else if (e.b == current) e.a else continue
                        if (visited.add(next)) { part += next; queue += next }
                    }
                }
                parts += cut(part, remaining.filter { it.a in part && it.b in part })
            }
            return parts
        }
        return components.flatMap { part -> cut(part, tree.filter { it.a in part && it.b in part }) }
            .sortedBy { it.min() }.map { part -> order(part.map { regions[it] }, width, height) }
    }

    /** Cluster rows/columns before sorting fragments, so small box jitter cannot reorder a lane. */
    fun order(regions: List<MangaTranslatedRegion>, width: Int, height: Int): List<MangaTranslatedRegion> {
        if (regions.size <= 1) return regions
        val vertical = regions.count { it.vertical } > regions.size / 2
        val metrics = regions.map { line(it, width, height) }
        val angle = metrics.map { it.angle }.average().toFloat()
        fun across(i: Int): Float = if (vertical) metrics[i].cx * cos(angle) + metrics[i].cy * sin(angle)
            else -metrics[i].cx * sin(angle) + metrics[i].cy * cos(angle)
        fun along(i: Int): Float = if (vertical) -metrics[i].cx * sin(angle) + metrics[i].cy * cos(angle)
            else metrics[i].cx * cos(angle) + metrics[i].cy * sin(angle)
        val sizes = metrics.map { it.thickness }.sorted()
        val tolerance = sizes[sizes.size / 2] * .6f
        val lanes = mutableListOf<MutableList<Int>>()
        val indices = metrics.indices.sortedBy { if (vertical) -across(it) else across(it) }
        for (i in indices) {
            val lane = lanes.lastOrNull()?.takeIf { abs(across(it.first()) - across(i)) <= tolerance }
                ?: mutableListOf<Int>().also { lanes += it }
            lane += i
        }
        return lanes.flatMap { it.sortedBy(::along) }.map { regions[it] }
    }
}
