package io.legado.app.data.repository.manga.translation

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import io.legado.app.domain.model.manga.MangaTextBox
import io.legado.app.domain.model.manga.MangaTranslationPlan
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Color contrast also detects colored lettering whose luminance matches the background. */
internal fun mangaInkContrast(color: Int, background: Int, threshold: Int): Boolean =
    max(abs(Color.red(color) - Color.red(background)),
        max(abs(Color.green(color) - Color.green(background)), abs(Color.blue(color) - Color.blue(background)))) >= threshold

/** Refine whole-block AI anchors on flat backgrounds, retaining detector-owned line masks. */
internal fun refineMangaLettering(bitmap: Bitmap, plan: MangaTranslationPlan): MangaTranslationPlan {
    var budget = bitmap.width.toLong() * bitmap.height * 2
    return plan.copy(regions = plan.regions.map { region ->
        if (region.eraseBoxes != listOf(region.textBox)) return@map region
        val source = region.textBox.pixels(bitmap.width, bitmap.height)
        val area = source.width() * source.height()
        if (area !in 16..1_048_576 || area > budget) return@map region
        budget -= area
        val bg = MangaPageComposer.background(bitmap, source)
        var samples = 0; var matching = 0
        fun sample(x: Int, y: Int) {
            if (x !in 0 until bitmap.width || y !in 0 until bitmap.height) return
            samples++
            if (!mangaInkContrast(bitmap.getPixel(x, y), bg, 25)) matching++
        }
        for (x in source.left until source.right step max(1, source.width() / 32)) {
            sample(x, source.top - 3); sample(x, source.bottom + 2)
        }
        for (y in source.top until source.bottom step max(1, source.height() / 32)) {
            sample(source.left - 3, y); sample(source.right + 2, y)
        }
        // Textured pictures retain their supplied anchors; flat gutters alone are not enough.
        if (samples < 8 || matching < samples * 0.85f) return@map region
        val pixels = IntArray(area)
        bitmap.getPixels(pixels, 0, source.width(), source.left, source.top, source.width(), source.height())
        val visited = BooleanArray(area)
        val queue = IntArray(area)
        val components = mutableListOf<Rect>()
        // A single CJK character can have a separate enclosing radical (for example 回).
        // Treat a large hollow component as a bubble only when the source has several letters.
        val canExcludeOutline = region.original.codePoints().filter { Character.isLetterOrDigit(it) }.count() >= 3
        for (seed in pixels.indices) {
            if (visited[seed] || !mangaInkContrast(pixels[seed], bg, 32)) continue
            var head = 0; var tail = 1
            queue[0] = seed; visited[seed] = true
            val bounds = Rect(source.width(), source.height(), 0, 0)
            while (head < tail) {
                val index = queue[head++]
                val x = index % source.width(); val y = index / source.width()
                bounds.left = min(bounds.left, x); bounds.top = min(bounds.top, y)
                bounds.right = max(bounds.right, x + 1); bounds.bottom = max(bounds.bottom, y + 1)
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx; val ny = y + dy
                    if (nx !in 0 until source.width() || ny !in 0 until source.height()) continue
                    val next = ny * source.width() + nx
                    if (!visited[next] && mangaInkContrast(pixels[next], bg, 32)) { visited[next] = true; queue[tail++] = next }
                }
            }
            val thin = max(2, min(source.width(), source.height()) / 24)
            val rule = bounds.width() >= source.width() * 0.8f && bounds.height() <= thin ||
                bounds.height() >= source.height() * 0.8f && bounds.width() <= thin
            val outline = canExcludeOutline && bounds.width() >= source.width() * 0.8f && bounds.height() >= source.height() * 0.8f &&
                tail < bounds.width().toLong() * bounds.height() * 0.4f
            if (!rule && !outline) components += bounds.apply { offset(source.left, source.top) }
            if (components.size > 2048) return@map region
        }
        if (components.isEmpty()) return@map region
        val lines = mutableListOf<Rect>()
        components.sortedBy { if (region.vertical) it.centerX() else it.centerY() }.forEach { component ->
            val line = lines.firstOrNull {
                val overlap = if (region.vertical) min(it.right, component.right) - max(it.left, component.left)
                    else min(it.bottom, component.bottom) - max(it.top, component.top)
                val short = if (region.vertical) min(it.width(), component.width()) else min(it.height(), component.height())
                overlap >= max(1f, short * 0.3f)
            }
            if (line == null) lines += Rect(component) else line.union(component)
        }
        if (lines.size > 128) return@map region
        val masks = lines.map { line ->
            val left = max(source.left, line.left - 1); val top = max(source.top, line.top - 1)
            val right = min(source.right, line.right + 1); val bottom = min(source.bottom, line.bottom + 1)
            MangaTextBox((left + 0.01f) * 1000 / bitmap.width, (top + 0.01f) * 1000 / bitmap.height,
                right * 1000f / bitmap.width, bottom * 1000f / bitmap.height)
        }
        val lettering = Rect(lines.first()).apply { lines.drop(1).forEach(::union) }
        val tight = MangaTextBox((max(source.left, lettering.left - 1) + 0.01f) * 1000 / bitmap.width,
            (max(source.top, lettering.top - 1) + 0.01f) * 1000 / bitmap.height,
            min(source.right, lettering.right + 1) * 1000f / bitmap.width, min(source.bottom, lettering.bottom + 1) * 1000f / bitmap.height)
        region.copy(eraseBoxes = masks, layoutBox = if (region.layoutBox == region.textBox) tight else region.layoutBox)
    })
}
