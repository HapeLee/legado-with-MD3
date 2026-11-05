package io.legado.app.data.repository.manga.translation

import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Source coverage is independent of translated runs and of holes in a layout-only mask. */
internal fun mangaSourceInkCoverage(colors: IntArray, width: Int, height: Int, background: Int,
    anchor: Rect, source: Rect, vertical: Boolean, allowed: BooleanArray?, protectedAreas: List<Rect>, sourceCharacters: Int,
    patch: Boolean, glyphSize: Float? = null): BooleanArray {
    val foreground = BooleanArray(colors.size) { mangaInkContrast(colors[it], background, 25) }
    val seen = BooleanArray(colors.size)
    val selected = BooleanArray(colors.size)
    val queue = IntArray(colors.size)
    fun isProtected(x: Int, y: Int) = protectedAreas.any { it.contains(x, y) }
    val palette = mutableMapOf<Int, Int>()
    for (y in max(0, anchor.top) until min(height, anchor.bottom)) for (x in max(0, anchor.left) until min(width, anchor.right)) {
        val i = y * width + x
        if (foreground[i] && !isProtected(x, y)) {
            val key = colors[i] and 0xffe0e0e0.toInt()
            palette[key] = (palette[key] ?: 0) + 1
        }
    }
    val inkColor = palette.maxByOrNull { it.value }?.key
    fun difference(a: Int, b: Int): Int = max(abs((a shr 16 and 255) - (b shr 16 and 255)),
        max(abs((a shr 8 and 255) - (b shr 8 and 255)), abs((a and 255) - (b and 255))))
    fun matchesInk(color: Int): Boolean {
        val ink = inkColor ?: return false
        if (difference(color, ink) <= 64) return true
        // A thin antialiased stroke is the same ink composited at a lower opacity, not a
        // different gray drawing. Test the ink-to-background color line in all three channels.
        var dot = 0f; var norm = 0f
        for (shift in listOf(16, 8, 0)) {
            val direction = (ink shr shift and 255) - (background shr shift and 255)
            dot += ((color shr shift and 255) - (background shr shift and 255)) * direction.toFloat()
            norm += direction.toFloat() * direction
        }
        if (norm == 0f) return false
        val alpha = dot / norm
        if (alpha !in .08f..1.35f) return false
        return listOf(16, 8, 0).all { shift ->
            val bg = background shr shift and 255
            abs((color shr shift and 255) - (bg + alpha * ((ink shr shift and 255) - bg))) <= 32f
        }
    }
    val thickness = glyphSize?.toInt()?.coerceAtLeast(1) ?: (if (vertical) anchor.width() else anchor.height()).coerceAtLeast(1)
    for (seed in colors.indices) {
        if (seen[seed] || !foreground[seed]) continue
        var head = 0; var tail = 1; queue[0] = seed; seen[seed] = true
        val bounds = Rect(width, height, 0, 0)
        var anchored = false; var blocked = false; var representative = colors[seed]; var strongest = 0
        while (head < tail) {
            val i = queue[head++]; val x = i % width; val y = i / width
            bounds.left = min(bounds.left, x); bounds.top = min(bounds.top, y)
            bounds.right = max(bounds.right, x + 1); bounds.bottom = max(bounds.bottom, y + 1)
            anchored = anchored || anchor.contains(x, y)
            blocked = blocked || isProtected(x, y)
            val contrast = difference(colors[i], background)
            if (contrast > strongest) { strongest = contrast; representative = colors[i] }
            // Diagonal antialiasing and disconnected CJK radicals must not require four-way contact.
            for (dy in -1..1) for (dx in -1..1) {
                val nx = x + dx; val ny = y + dy
                if (nx !in 0 until width || ny !in 0 until height) continue
                val next = ny * width + nx
                if (!seen[next] && foreground[next]) { seen[next] = true; queue[tail++] = next }
            }
        }
        if (blocked) continue
        val hollowOutline = sourceCharacters >= 3 && bounds.width() >= source.width() * .8f &&
            bounds.height() >= source.height() * .8f && tail < bounds.width().toLong() * bounds.height() * .4f
        if (hollowOutline) continue
        val alongSource = if (vertical) bounds.top >= source.top && bounds.bottom <= source.bottom else bounds.left >= source.left && bounds.right <= source.right
        val crossGap = if (vertical) max(0, max(anchor.left - bounds.right, bounds.left - anchor.right))
            else max(0, max(anchor.top - bounds.bottom, bounds.top - anchor.bottom))
        val crossSpan = if (vertical) bounds.width() else bounds.height()
        val sameInk = matchesInk(representative)
        val clipped = bounds.left == 0 || bounds.top == 0 || bounds.right == width || bounds.bottom == height
        if (glyphSize != null) {
            val alongSpan = if (vertical) bounds.height() else bounds.width()
            // A paragraph box can intersect a balloon border. Its paragraph height must not
            // authorize deleting that border as one enormous glyph, or following it off-crop.
            if (clipped || crossSpan > glyphSize * 2.1f + 2 ||
                crossSpan <= glyphSize * .25f && alongSpan > glyphSize * 3.5f) continue
        }
        val completeSourcePart = source.contains(bounds) && alongSource && crossGap <= thickness * .35f && crossSpan <= thickness * 1.4f
        val dx = max(0, max(anchor.left - bounds.right, bounds.left - anchor.right))
        val dy = max(0, max(anchor.top - bounds.bottom, bounds.top - anchor.bottom))
        val tinyCompletion = tail <= max(4f, thickness * thickness * .09f) && max(dx, dy) <= thickness * .2f
        if (!anchored && (!sameInk || clipped || !(completeSourcePart || tinyCompletion))) continue
        for (n in 0 until tail) {
            val i = queue[n]; val x = i % width; val y = i / width
            // Source ink is valid erasure evidence even when a bubble safety mask has a glyph hole.
            if (allowed == null || allowed[i] || source.contains(x, y)) selected[i] = true
        }
    }
    selectOutlinedSourceInk(colors, width, height, anchor, source, allowed, protectedAreas,
        glyphSize ?: thickness.toFloat(), selected, seen, queue)
    val grown = selected.copyOf()
    val radius = max(1, thickness / 18).coerceAtMost(3)
    for (i in selected.indices) if (selected[i]) {
        val x = i % width; val y = i / width
        for (dy in -radius..radius) for (dx in -radius..radius) {
            val nx = x + dx; val ny = y + dy
            if (nx !in 0 until width || ny !in 0 until height || isProtected(nx, ny)) continue
            val p = ny * width + nx
            if (allowed == null || allowed[p] || source.contains(nx, ny)) grown[p] = true
        }
    }
    if (patch) for (y in max(0, anchor.top) until min(height, anchor.bottom)) for (x in max(0, anchor.left) until min(width, anchor.right)) {
        val i = y * width + x
        if (!isProtected(x, y) && (!foreground[i] || selected[i]) && (allowed == null || allowed[i] || source.contains(x, y))) grown[i] = true
    }
    return grown
}

/** A black glyph on a dark background can be indistinguishable from the background. Its
 * enclosing white stroke is additional source evidence, not an invitation to mask a rectangle.
 * Reuse the component scratch buffers after ordinary ink selection has completed.
 */
private fun selectOutlinedSourceInk(colors: IntArray, width: Int, height: Int, anchor: Rect, source: Rect,
    allowed: BooleanArray?, protectedAreas: List<Rect>, glyphSize: Float,
    selected: BooleanArray, seen: BooleanArray, queue: IntArray) {
    seen.fill(false)
    fun bright(i: Int): Boolean = (colors[i] ushr 16 and 255) >= 210 &&
        (colors[i] ushr 8 and 255) >= 210 && (colors[i] and 255) >= 210
    fun protected(x: Int, y: Int) = protectedAreas.any { it.contains(x, y) }
    val envelope = Rect(source).apply { inset(-3, -3) }
    for (seed in colors.indices) {
        if (seen[seed] || !bright(seed)) continue
        var head = 0; var tail = 1
        queue[0] = seed; seen[seed] = true
        val bounds = Rect(width, height, 0, 0)
        var anchored = false; var blocked = false
        while (head < tail) {
            val i = queue[head++]; val x = i % width; val y = i / width
            bounds.left = min(bounds.left, x); bounds.right = max(bounds.right, x + 1)
            bounds.top = min(bounds.top, y); bounds.bottom = max(bounds.bottom, y + 1)
            anchored = anchored || anchor.contains(x, y)
            blocked = blocked || protected(x, y)
            for (dy in -1..1) for (dx in -1..1) {
                val nx = x + dx; val ny = y + dy
                if (nx !in 0 until width || ny !in 0 until height) continue
                val n = ny * width + nx
                if (!seen[n] && bright(n)) { seen[n] = true; queue[tail++] = n }
            }
        }
        if (!anchored || blocked || bounds.left == 0 || bounds.top == 0 || bounds.right == width || bounds.bottom == height ||
            !envelope.contains(bounds) || bounds.width() > glyphSize * 2.1f + 2 || bounds.height() > glyphSize * 2.1f + 2 ||
            bounds.width().toLong() * bounds.height() > 65536) continue
        val w = bounds.width(); val h = bounds.height()
        val border = BooleanArray(w * h)
        for (n in 0 until tail) {
            val i = queue[n]
            border[(i / width - bounds.top) * w + i % width - bounds.left] = true
        }
        val exterior = BooleanArray(border.size)
        val flood = IntArray(border.size); head = 0; var end = 0
        fun visit(i: Int) { if (!border[i] && !exterior[i]) { exterior[i] = true; flood[end++] = i } }
        for (x in 0 until w) { visit(x); visit((h - 1) * w + x) }
        for (y in 0 until h) { visit(y * w); visit(y * w + w - 1) }
        while (head < end) {
            val i = flood[head++]; val x = i % w; val y = i / w
            if (x > 0) visit(i - 1)
            if (x + 1 < w) visit(i + 1)
            if (y > 0) visit(i - w)
            if (y + 1 < h) visit(i + w)
        }
        var core = 0; var protectedInterior = false
        for (i in border.indices) if (!border[i] && !exterior[i]) {
            val x = bounds.left + i % w; val y = bounds.top + i / w
            protectedInterior = protectedInterior || protected(x, y)
            val color = colors[y * width + x]
            if (anchor.contains(x, y) && maxOf(color ushr 16 and 255, color ushr 8 and 255, color and 255) < 160) core++
        }
        if (core < 2 || protectedInterior) continue
        for (i in border.indices) if (!exterior[i]) {
            val x = bounds.left + i % w; val y = bounds.top + i / w; val n = y * width + x
            if (!protected(x, y) && (allowed == null || allowed[n] || source.contains(x, y))) selected[n] = true
        }
    }
}
