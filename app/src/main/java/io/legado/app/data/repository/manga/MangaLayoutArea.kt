package io.legado.app.data.repository.manga

import android.graphics.Rect

/** Bands intersect complete glyph boxes, including the margin, rather than sampling a baseline. */
internal class MangaLayoutArea(val bounds: Rect, val margin: Int = 2, private val inside: (Int, Int) -> Boolean = { _, _ -> true }) {
    private var rows: Array<IntRange?>? = null
    private var columns: Array<IntRange?>? = null
    private var centerX = bounds.centerX()
    private var centerY = bounds.centerY()
    fun crossCenter(vertical: Boolean) = if (vertical) centerX else centerY

    fun contains(x: Int, y: Int) = bounds.contains(x, y) && inside(x, y)

    fun prepare(checkCancellation: () -> Unit) {
        if (rows != null) return
        require(bounds.width() in 1..4096 && bounds.height() in 1..4096)
        fun run(start: Int, end: Int, center: Int, test: (Int) -> Boolean): IntRange? {
            var best: IntRange? = null
            var at = start
            while (at < end) {
                while (at < end && !test(at)) at++
                val left = at
                while (at < end && test(at)) at++
                if (at > left) {
                    val next = left until at
                    val previous = best
                    if (previous == null || center in next && center !in previous ||
                        ((center in next) == (center in previous)) && next.count() > previous.count()) best = next
                }
            }
            return best
        }
        rows = Array(bounds.height()) { index ->
            checkCancellation()
            run(bounds.left, bounds.right, bounds.centerX()) { x -> inside(x, bounds.top + index) }
        }
        columns = Array(bounds.width()) { index ->
            checkCancellation()
            run(bounds.top, bounds.bottom, bounds.centerY()) { y -> inside(bounds.left + index, y) }
        }
        fun centroid(spans: Array<IntRange?>, origin: Int, fallback: Int): Int {
            var weight = 0L; var weighted = 0L
            spans.forEachIndexed { i, span ->
                val length = span?.count()?.toLong() ?: 0L
                weight += length; weighted += length * (origin + i)
            }
            return if (weight > 0) (weighted / weight).toInt() else fallback
        }
        centerY = centroid(checkNotNull(rows), bounds.top, centerY)
        centerX = centroid(checkNotNull(columns), bounds.left, centerX)
    }

    fun span(start: Int, end: Int, vertical: Boolean): IntRange? {
        val crossStart = if (vertical) bounds.left else bounds.top
        val crossEnd = if (vertical) bounds.right else bounds.bottom
        if (start - margin < crossStart || end + margin > crossEnd) return null
        val intervals = checkNotNull(if (vertical) columns else rows)
        var low = if (vertical) bounds.top else bounds.left
        var high = (if (vertical) bounds.bottom else bounds.right) - 1
        for (at in start - margin until end + margin) {
            val interval = intervals[at - crossStart] ?: return null
            low = maxOf(low, interval.first); high = minOf(high, interval.last)
        }
        return (low + margin..high - margin).takeIf { it.first <= it.last }
    }
}
