package io.legado.app.data.repository.manga

import android.graphics.Point
import android.graphics.Rect

internal data class MangaOcrLine(val text: String, val bounds: Rect, val glyphs: List<List<Point>>)
internal data class MangaOcrRegion(val lines: List<MangaOcrLine>) {
    val bounds: Rect get() = Rect(lines.first().bounds).apply { lines.drop(1).forEach { union(it.bounds) } }
    fun text(source: String): String {
        val vertical = source == "ja" && lines.count { it.bounds.height() > it.bounds.width() } > lines.size / 2
        val sorted = if (vertical) lines.sortedWith(compareByDescending<MangaOcrLine> { it.bounds.right }.thenBy { it.bounds.top })
        else lines.sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }))
        val typical = lines.map { if (vertical) it.bounds.width() else it.bounds.height() }.sorted().let { it[it.size / 2] }
        return buildString {
            sorted.forEachIndexed { index, line ->
                if (index > 0) {
                    val previous = sorted[index - 1].bounds
                    val gap = if (vertical) previous.left - line.bounds.right else line.bounds.top - previous.bottom
                    append(if (gap > typical) "\n\n" else if (source == "ja") "" else " ")
                }
                append(line.text.trim())
            }
        }
    }
}

internal data class MangaOcrStrip(val top: Int, val bottom: Int)

/** 512px overlap on both sides; any line <=1024px tall has a complete observation. */
internal fun mangaOcrStrips(height: Int): List<MangaOcrStrip> {
    require(height > 0)
    return (0 until height step 1024).map { top ->
        MangaOcrStrip((top - 512).coerceAtLeast(0), (top + 1536).coerceAtMost(height))
    }.distinct()
}

private fun sameLine(a: MangaOcrLine, b: MangaOcrLine): Boolean {
    val intersection = Rect(a.bounds)
    if (!intersection.intersect(b.bounds)) return false
    val area = intersection.width().toLong() * intersection.height()
    val smaller = minOf(a.bounds.width().toLong() * a.bounds.height(), b.bounds.width().toLong() * b.bounds.height())
    // Geometry handles partially recognized seam text as well as exact duplicates.
    return area * 100 >= smaller * 65
}

/** Shared OCR lines connect block fragments observed on either side of a strip seam. */
internal fun mergeMangaOcrRegions(
    input: List<MangaOcrRegion>, checkCancellation: () -> Unit = {},
): List<MangaOcrRegion> {
    val groups = mutableListOf<MangaOcrRegion>()
    input.forEach { candidate ->
        checkCancellation()
        var lines = candidate.lines
        var index = 0
        while (index < groups.size) {
            if (index % 128 == 0) checkCancellation()
            val group = groups[index]
            if (lines.any { a -> group.lines.any { b -> sameLine(a, b) } }) {
                lines = lines + groups.removeAt(index).lines
                index = 0 // The union may bridge another pair of fragments.
            } else index++
        }
        val unique = mutableListOf<MangaOcrLine>()
        lines.sortedByDescending { it.bounds.width().toLong() * it.bounds.height() }.forEach { line ->
            if (unique.none { sameLine(it, line) }) unique += line
        }
        groups += MangaOcrRegion(unique)
    }
    return groups.sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }))
}
