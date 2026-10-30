package io.legado.app.data.repository.manga.translation

import android.graphics.Bitmap
import android.graphics.Rect
import io.legado.app.domain.model.manga.MangaTextBox
import io.legado.app.domain.model.manga.MangaTranslatedRegion
import io.legado.app.domain.model.manga.MangaTranslationPlan
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** An enclosed flat balloon is a stronger spatial anchor than a partial AI lettering box. */
internal fun recoverMangaBubbles(bitmap: Bitmap, plan: MangaTranslationPlan): MangaTranslationPlan {
    var budget = min(4L * 1024 * 1024, bitmap.width.toLong() * bitmap.height * 2)
    return plan.copy(regions = plan.regions.map { region ->
        val original = region.recognitionBox ?: region.textBox
        val source = original.pixels(bitmap.width, bitmap.height)
        val short = min(bitmap.width, bitmap.height)
        val padX = max(64, max(source.width() * 2, short / 5)).coerceAtMost(256)
        val padY = max(96, max(source.height() * 2, short / 2)).coerceAtMost(512)
        val crop = Rect(max(0, source.left - padX), max(0, source.top - padY),
            min(bitmap.width, source.right + padX), min(bitmap.height, source.bottom + padY))
        val count = crop.width().toLong() * crop.height()
        if (count > 1024 * 1024 || count > budget) return@map region
        budget -= count
        val pixels = IntArray(count.toInt())
        bitmap.getPixels(pixels, 0, crop.width(), crop.left, crop.top, crop.width(), crop.height())
        val background = MangaPageComposer.background(bitmap, source)
        val others = plan.regions.filter { it.id != region.id }.map { (it.recognitionBox ?: it.textBox).pixels(bitmap.width, bitmap.height) }
        val bubble = findBubble(crop, pixels, source, background, region.original, others) ?: return@map region
        val masks = bubble.lettering(background, region.vertical) ?: return@map region
        val enclosing = Rect(masks.first()).apply { masks.drop(1).forEach(::union) }
        fun normalized(rect: Rect) = MangaTextBox((rect.left + 0.01f) * 1000 / bitmap.width,
            (rect.top + 0.01f) * 1000 / bitmap.height, rect.right * 1000f / bitmap.width, rect.bottom * 1000f / bitmap.height)
        val recovered = region.copy(textBox = normalized(enclosing), eraseBoxes = masks.map(::normalized),
            recognitionBox = original, sourceFontSize = null)
        val anchored = MangaPageComposer.anchor(bitmap, MangaTranslationPlan(listOf(recovered))).regions.single()
        val layout = bubble.layout(anchored, bitmap.height) ?: return@map region
        anchored.copy(layoutBox = normalized(layout))
    })
}

private class BubbleInterior(
    val crop: Rect,
    val pixels: IntArray,
    val bounds: Rect,
    val left: IntArray,
    val right: IntArray,
    val score: Float,
) {
    fun lettering(background: Int, vertical: Boolean): List<Rect>? {
        val length = if (vertical) crop.width() else crop.height()
        val starts = IntArray(length) { Int.MAX_VALUE }
        val ends = IntArray(length) { -1 }
        var ink = 0; var inside = 0
        // Keep the balloon outline and its antialiasing out of all erasure anchors.
        for (y in bounds.top + 3 until bounds.bottom - 3) {
            for (x in left[y] + 3 until right[y] - 3) {
                inside++
                if (!mangaInkContrast(pixels[y * crop.width() + x], background, 25)) continue
                ink++
                val axis = if (vertical) x else y
                val cross = if (vertical) y else x
                starts[axis] = min(starts[axis], cross); ends[axis] = max(ends[axis], cross + 1)
            }
        }
        if (ink == 0 || ink > inside * 0.45f) return null
        val result = mutableListOf<Rect>()
        var current: Rect? = null
        var last = -1
        val gap = (min(bounds.width(), bounds.height()) / 40).coerceIn(1, 4)
        for (axis in starts.indices) {
            if (ends[axis] < 0) continue
            val part = if (vertical) Rect(axis, starts[axis], axis + 1, ends[axis]) else Rect(starts[axis], axis, ends[axis], axis + 1)
            if (current == null || axis - last > gap + 1) {
                current = part
                result += part
            } else current.union(part)
            last = axis
        }
        if (result.size > 128) return null
        for (mask in result) {
            mask.inset(-1, -1)
            if (mask.top < bounds.top || mask.bottom > bounds.bottom) return null
            // A rectangular mask must fit every row of a curved cap, not just its middle.
            if ((mask.top until mask.bottom).any { mask.left < left[it] + 1 || mask.right > right[it] - 1 }) return null
            mask.offset(crop.left, crop.top)
        }
        return result
    }

    fun layout(region: MangaTranslatedRegion, imageHeight: Int): Rect? {
        val candidates = mutableListOf<Rect>()
        val widest = (bounds.top until bounds.bottom).maxOf { right[it] - left[it] }
        // Trim thin tails/endpoints before centering, independently of writing direction.
        var bodyTop = bounds.top; var bodyBottom = bounds.bottom
        while (bodyTop < bodyBottom && right[bodyTop] - left[bodyTop] < widest * 0.35f) bodyTop++
        while (bodyBottom > bodyTop && right[bodyBottom - 1] - left[bodyBottom - 1] < widest * 0.35f) bodyBottom--
        val center = (bodyTop + bodyBottom) / 2
        for (fraction in listOf(0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 0.95f)) {
            val height = ((bodyBottom - bodyTop) * fraction).toInt()
            val top = center - height / 2
            val bottom = top + height
            val l = (top until bottom).maxOf { left[it] } + 3
            val r = (top until bottom).minOf { right[it] } - 3
            if (r - l > 12 && height > 12) candidates += Rect(l, top, r, bottom)
        }
        val vertical = MangaTextTypesetter.vertical(region.translation, region.vertical)
        val preferred = checkNotNull(region.sourceFontSize) * imageHeight / 1000
        var best = candidates.maxByOrNull { it.width() * it.height() } ?: return null
        var size = 0f
        for (area in candidates) {
            if (MangaTextTypesetter.atSize(region.translation, vertical, area.width(), area.height(), preferred) != null) {
                return Rect(area).apply { offset(crop.left, crop.top) }
            }
            val fit = try { MangaTextTypesetter.fit(region.translation, vertical, area.width(), area.height(), preferred) }
                catch (_: IllegalArgumentException) { null }
            if (fit != null && fit.fontSize > size) { best = area; size = fit.fontSize }
        }
        return Rect(best).apply { offset(crop.left, crop.top) }
    }
}

private fun findBubble(
    crop: Rect, pixels: IntArray, source: Rect, background: Int, original: String, others: List<Rect>,
): BubbleInterior? {
    val width = crop.width(); val height = crop.height()
    val visited = BooleanArray(pixels.size)
    val queue = IntArray(pixels.size)
    val candidates = mutableListOf<BubbleInterior>()
    val seedArea = Rect(max(crop.left, source.left - 8), max(crop.top, source.top - 8),
        min(crop.right, source.right + 8), min(crop.bottom, source.bottom + 8))
    val singleLetter = original.codePoints().filter { Character.isLetterOrDigit(it) }.count() <= 2
    for (gy in 0..4) for (gx in 0..4) {
        val sx = seedArea.left + (seedArea.width() - 1) * gx / 4 - crop.left
        val sy = seedArea.top + (seedArea.height() - 1) * gy / 4 - crop.top
        val seed = sy * width + sx
        if (visited[seed] || mangaInkContrast(pixels[seed], background, 25)) continue
        val bounds = Rect(width, height, 0, 0)
        val left = IntArray(height) { width }; val right = IntArray(height)
        var head = 0; var tail = 1; var edge = false
        queue[0] = seed; visited[seed] = true
        fun enqueue(index: Int) {
            if (!visited[index] && !mangaInkContrast(pixels[index], background, 25)) { visited[index] = true; queue[tail++] = index }
        }
        while (head < tail) {
            val index = queue[head++]
            val x = index % width; val y = index / width
            if (x == 0 || y == 0 || x == width - 1 || y == height - 1) edge = true
            bounds.left = min(bounds.left, x); bounds.right = max(bounds.right, x + 1)
            bounds.top = min(bounds.top, y); bounds.bottom = max(bounds.bottom, y + 1)
            left[y] = min(left[y], x); right[y] = max(right[y], x + 1)
            if (x > 0) enqueue(index - 1)
            if (x + 1 < width) enqueue(index + 1)
            if (y > 0) enqueue(index - width)
            if (y + 1 < height) enqueue(index + width)
        }
        if (edge || bounds.width() < 16 || bounds.height() < 24 ||
            tail < bounds.width().toLong() * bounds.height() * 0.55f ||
            tail < source.width().toLong() * source.height() * 0.5f) continue
        if (bounds.width() < source.width() * 0.6f || bounds.height() < source.height() * 0.6f) continue
        if (bounds.height() < bounds.width() * 1.4f && bounds.width() < bounds.height() * 1.4f) continue
        if (singleLetter && (bounds.width() < source.width() * 1.2f || bounds.height() < source.height() * 1.2f)) continue
        val edgeRows = min(bounds.width() / 4, bounds.height() / 20).coerceAtLeast(2)
        val widest = (bounds.top until bounds.bottom).maxOf { right[it] - left[it] }
        val topWidth = (bounds.top until bounds.top + edgeRows).sumOf { right[it] - left[it] }.toFloat() / edgeRows
        val bottomWidth = (bounds.bottom - edgeRows until bounds.bottom).sumOf { right[it] - left[it] }.toFloat() / edgeRows
        if (topWidth > widest * 0.9f || bottomWidth > widest * 0.9f) continue
        val global = Rect(bounds).apply { offset(crop.left, crop.top) }
        if (others.any { Rect.intersects(global, it) }) continue
        val intersection = Rect(global)
        if (!intersection.intersect(source)) continue
        val overlap = intersection.width().toFloat() * intersection.height() / (source.width().toFloat() * source.height())
        if (overlap < 0.15f) continue
        val distance = hypot(global.exactCenterX() - source.exactCenterX(), global.exactCenterY() - source.exactCenterY())
        val score = overlap / (1 + distance / hypot(global.width().toFloat(), global.height().toFloat()))
        candidates += BubbleInterior(crop, pixels, bounds, left, right, score)
    }
    val ordered = candidates.sortedByDescending { it.score }
    if (ordered.size > 1 && ordered[0].score < ordered[1].score * 1.4f) return null
    return ordered.firstOrNull()
}
