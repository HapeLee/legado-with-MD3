package io.legado.app.data.repository.manga.translation

import android.graphics.Rect

/** Join touching source lettering, never merely overlapping context padding. */
internal fun mangaBackgroundGroups(sources: List<Rect>, bounds: List<Rect>): List<List<Int>> {
    require(sources.size == bounds.size)
    val groups = sources.indices.map { mutableListOf(it) }.toMutableList()
    var changed: Boolean
    do {
        changed = false
        outer@ for (i in groups.indices) for (j in i + 1 until groups.size) {
            val a = groups[i]; val b = groups[j]
            if (a.size + b.size > 8 || a.none { x -> b.any { y ->
                    val near = Rect(sources[x]).apply { inset(-2, -2) }
                    Rect.intersects(near, sources[y])
                } }) continue
            val union = Rect(bounds[a.first()]).apply { (a + b).forEach { union(bounds[it]) } }
            if (union.width().toLong() * union.height() > 1024 * 1024) continue
            a += b; a.sort(); groups.removeAt(j); changed = true; break@outer
        }
    } while (changed)
    return groups
}

/** Smooth only repaired pixels around contact seams. Original ink never participates. */
internal class MangaBackgroundContacts(private val width: Int, private val height: Int) {
    private val owner = IntArray(width * height)
    private var nextOwner = 0

    private fun similar(a: Int, b: Int): Boolean =
        kotlin.math.abs((a ushr 16 and 255) - (b ushr 16 and 255)) <= 48 &&
            kotlin.math.abs((a ushr 8 and 255) - (b ushr 8 and 255)) <= 48 &&
            kotlin.math.abs((a and 255) - (b and 255)) <= 48

    fun add(mask: BooleanArray) {
        require(mask.size == owner.size)
        nextOwner++
        mask.forEachIndexed { i, selected -> if (selected) owner[i] = nextOwner }
    }

    fun blend(colors: IntArray) {
        require(colors.size == owner.size)
        val distance = IntArray(owner.size) { -1 }
        val queue = IntArray(owner.size)
        var tail = 0
        fun near(i: Int, visit: (Int) -> Unit) {
            val x = i % width
            if (x > 0) visit(i - 1)
            if (x + 1 < width) visit(i + 1)
            if (i >= width) visit(i - width)
            if (i + width < owner.size) visit(i + width)
        }
        for (i in owner.indices) if (owner[i] != 0) {
            var seam = false
            near(i) { j -> if (owner[j] != 0 && owner[i] != owner[j] && similar(colors[i], colors[j])) seam = true }
            if (seam) { distance[i] = 0; queue[tail++] = i }
        }
        if (tail == 0) return
        var head = 0
        while (head < tail) {
            val i = queue[head++]
            if (distance[i] >= 5) continue
            near(i) { j -> if (owner[j] != 0 && distance[j] < 0) { distance[j] = distance[i] + 1; queue[tail++] = j } }
        }
        val original = colors.copyOf()
        val next = colors.copyOf()
        repeat(12) {
            for (k in 0 until tail) {
                val i = queue[k]
                var red = 0; var green = 0; var blue = 0; var count = 0
                near(i) { j -> if (owner[j] != 0 && similar(original[i], original[j])) {
                    red += colors[j] ushr 16 and 255; green += colors[j] ushr 8 and 255; blue += colors[j] and 255; count++
                } }
                if (count > 0) {
                    val weight = (6 - distance[i]) / 6f
                    fun channel(sum: Int, shift: Int): Int =
                        ((sum.toFloat() / count) * weight + (original[i] ushr shift and 255) * (1 - weight)).toInt()
                    next[i] = (original[i] and -0x1000000) or (channel(red, 16) shl 16) or (channel(green, 8) shl 8) or channel(blue, 0)
                }
            }
            for (k in 0 until tail) colors[queue[k]] = next[queue[k]]
        }
    }
}
