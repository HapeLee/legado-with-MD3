package io.legado.app.data.repository.manga

import android.graphics.Point
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class MangaTextRepair(val mask: BooleanArray, val background: Int, val unrepairedGlyphs: Int)

/** OCR-constrained colour segmentation and local inpainting; never fills the OCR rectangle. */
internal object MangaTextInpainter {
    fun repair(
        pixels: IntArray, width: Int, height: Int, polygons: List<List<Point>>,
        checkCancellation: () -> Unit = {}, textMask: BooleanArray? = null,
    ): MangaTextRepair {
        require(width > 0 && height > 0 && pixels.size == width * height)
        require(textMask == null || textMask.size == pixels.size)
        val mask = BooleanArray(pixels.size)
        var background = -1
        var unrepairedGlyphs = 0
        groupPolygons(polygons, checkCancellation).forEach { group ->
            checkCancellation()
            // Leave enough background beyond the two-pixel antialias dilation.
            val left = (group.bounds.left - 6).coerceIn(0, width - 1)
            val right = (group.bounds.right + 6).coerceIn(left + 1, width)
            val top = (group.bounds.top - 6).coerceIn(0, height - 1)
            val bottom = (group.bounds.bottom + 6).coerceIn(top + 1, height)
            val histogram = IntArray(4096)
            for (y in top until bottom) for (x in left until right) {
                if (x < left + 3 || x >= right - 3 || y < top + 3 || y >= bottom - 3) {
                    val c = pixels[y * width + x]
                    histogram[((c shr 12) and 0xf00) or ((c shr 8) and 0xf0) or ((c shr 4) and 15)]++
                }
            }
            val bucket = histogram.indices.maxBy { histogram[it] }
            var r = 0L; var g = 0L; var b = 0L; var count = 0
            for (y in top until bottom) for (x in left until right) {
                if (x < left + 3 || x >= right - 3 || y < top + 3 || y >= bottom - 3) {
                    val c = pixels[y * width + x]
                    val key = ((c shr 12) and 0xf00) or ((c shr 8) and 0xf0) or ((c shr 4) and 15)
                    if (key == bucket) { r += c shr 16 and 255; g += c shr 8 and 255; b += c and 255; count++ }
                }
            }
            if (count == 0) return@forEach
            val bg = argb((r / count).toInt(), (g / count).toInt(), (b / count).toInt())
            background = bg
            val localWidth = right - left
            val localHeight = bottom - top
            val candidate = BooleanArray(localWidth * localHeight)
            for (y in top until bottom) for (x in left until right) {
                val c = pixels[y * width + x]
                // CTD is a hint for faint ink, never a veto on OCR-confirmed strokes.
                val threshold = if (textMask?.get(y * width + x) == true) 12 else 24
                candidate[(y - top) * localWidth + x - left] = distance(c, bg) > threshold
            }
            // Reject components connected to the exterior halo (panel/bubble borders, line art).
            val visited = BooleanArray(candidate.size)
            val queue = IntArray(candidate.count { it })
            var repairedGlyph = false
            var rejectedInk = false
            for (start in candidate.indices) {
                if (!candidate[start] || visited[start]) continue
                var head = 0; var tail = 1; var touchesEdge = false; var insideCount = 0
                queue[0] = start; visited[start] = true
                while (head < tail) {
                    val at = queue[head++]
                    val x = at % localWidth; val y = at / localWidth
                    if (x == 0 || y == 0 || x == localWidth - 1 || y == localHeight - 1) touchesEdge = true
                    if (head % 256 == 0) checkCancellation()
                    if (group.polygons.any { insidePolygon(x + left + .5f, y + top + .5f, it) }) insideCount++
                    for (dy in -1..1) for (dx in -1..1) {
                        val nx = x + dx; val ny = y + dy
                        if (nx !in 0 until localWidth || ny !in 0 until localHeight) continue
                        val next = ny * localWidth + nx
                        if (candidate[next] && !visited[next]) { visited[next] = true; queue[tail++] = next }
                    }
                }
                // Wider context must not turn an enclosed bubble border into text.
                if (!touchesEdge && insideCount > 0 && insideCount >= tail * .5 && tail <= candidate.size * .65) {
                    repairedGlyph = true
                    repeat(tail) { index ->
                        val x = queue[index] % localWidth + left
                        val y = queue[index] / localWidth + top
                        // Include faint antialiased edges before sampling the background.
                        for (dy in -2..2) for (dx in -2..2) {
                            val nx = x + dx; val ny = y + dy
                            if (nx in left + 1 until right - 1 && ny in top + 1 until bottom - 1) mask[ny * width + nx] = true
                        }
                    }
                } else if (insideCount > 0) {
                    // A different accepted component does not make this region complete.
                    rejectedInk = true
                }
            }
            if (!repairedGlyph || rejectedInk) unrepairedGlyphs++
        }
        inpaint(pixels, width, height, mask, checkCancellation)
        return MangaTextRepair(mask, background, unrepairedGlyphs)
    }

    private class MaskGroup(val bounds: Rect, val polygons: MutableList<List<Point>>)

    /** Neighboring OCR boxes share context so a connected stroke is not cut at each box's halo. */
    private fun groupPolygons(polygons: List<List<Point>>, checkCancellation: () -> Unit): List<MaskGroup> {
        val groups = mutableListOf<MaskGroup>()
        for (polygon in polygons.filter { it.size >= 3 }) {
            checkCancellation()
            val group = MaskGroup(Rect(polygon.minOf { it.x }, polygon.minOf { it.y },
                polygon.maxOf { it.x }, polygon.maxOf { it.y }), mutableListOf(polygon))
            // Merging may bridge two previously separated groups; repeat until stable.
            do {
                var merged = false
                val iterator = groups.iterator()
                while (iterator.hasNext()) {
                    checkCancellation()
                    val previous = iterator.next()
                    val halo = Rect(group.bounds).apply { inset(-12, -12) }
                    if (Rect.intersects(halo, previous.bounds)) {
                        group.bounds.union(previous.bounds)
                        group.polygons.addAll(previous.polygons)
                        iterator.remove()
                        merged = true
                    }
                }
            } while (merged)
            groups += group
        }
        return groups
    }

    private fun inpaint(pixels: IntArray, width: Int, height: Int, mask: BooleanArray, checkCancellation: () -> Unit) {
        val known = BooleanArray(pixels.size) { !mask[it] }
        val queued = BooleanArray(pixels.size)
        val queue = IntArray(mask.count { it })
        var head = 0; var tail = 0
        fun hasKnown(x: Int, y: Int): Boolean =
            (x > 0 && known[y * width + x - 1]) || (x + 1 < width && known[y * width + x + 1]) ||
                (y > 0 && known[(y - 1) * width + x]) || (y + 1 < height && known[(y + 1) * width + x])
        mask.indices.forEach { at ->
            if (mask[at] && hasKnown(at % width, at / width)) { queue[tail++] = at; queued[at] = true }
        }
        while (head < tail) {
            if (head % 256 == 0) checkCancellation()
            val at = queue[head++]; val x = at % width; val y = at / width
            var r = 0.0; var g = 0.0; var b = 0.0; var weight = 0.0
            for (ny in max(0, y - 3)..min(height - 1, y + 3)) for (nx in max(0, x - 3)..min(width - 1, x + 3)) {
                val next = ny * width + nx
                if (!known[next]) continue
                val w = 1.0 / ((nx - x) * (nx - x) + (ny - y) * (ny - y)).coerceAtLeast(1)
                val c = pixels[next]
                r += (c shr 16 and 255) * w; g += (c shr 8 and 255) * w; b += (c and 255) * w; weight += w
            }
            if (weight == 0.0) continue
            // Truncation darkens a uniform background on each propagation step.
            pixels[at] = argb((r / weight).roundToInt(), (g / weight).roundToInt(), (b / weight).roundToInt())
            known[at] = true
            for (ny in max(0, y - 1)..min(height - 1, y + 1)) for (nx in max(0, x - 1)..min(width - 1, x + 1)) {
                val next = ny * width + nx
                if (mask[next] && !queued[next]) { queued[next] = true; queue[tail++] = next }
            }
        }
    }

    private fun argb(r: Int, g: Int, b: Int) = (255 shl 24) or (r shl 16) or (g shl 8) or b
    private fun distance(a: Int, b: Int) = maxOf(abs((a shr 16 and 255) - (b shr 16 and 255)),
        abs((a shr 8 and 255) - (b shr 8 and 255)), abs((a and 255) - (b and 255)))

    private fun insidePolygon(x: Float, y: Float, points: List<Point>): Boolean {
        var inside = false
        var previous = points.last()
        points.forEach { point ->
            if ((point.y > y) != (previous.y > y) &&
                x < (previous.x - point.x) * (y - point.y) / (previous.y - point.y) + point.x) inside = !inside
            previous = point
        }
        return inside
    }
}
