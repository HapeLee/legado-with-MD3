package io.legado.app.data.repository.manga.translation

import android.graphics.Bitmap
import io.legado.app.domain.model.manga.MangaTranslatedRegion
import kotlin.math.roundToInt

/** Count source columns from explicit source evidence, never from translated rows or balloon shape. */
internal fun mangaSourceColumnCount(region: MangaTranslatedRegion): Int? {
    if (!region.vertical) return null
    region.sourceColumnCount?.let { return it }
    val explicit = region.original.lines().count { it.isNotBlank() }
    if (explicit > 1) return explicit.coerceAtMost(64)
    if (region.sourceLines.size < 2) return null
    val columns = mutableListOf<Pair<Float, Float>>()
    for (line in region.sourceLines.sortedBy { it.bounds.left }) {
        val box = line.bounds
        val index = columns.indexOfFirst { (left, right) ->
            minOf(right, box.right) - maxOf(left, box.left) >= minOf(right - left, box.right - box.left) * .5f
        }
        if (index < 0) columns += box.left to box.right
        else columns[index] = minOf(columns[index].first, box.left) to maxOf(columns[index].second, box.right)
    }
    return columns.size.takeIf { it in 1..64 }
}

/** Projection on original pixels is a fallback for plans lacking source line metadata. */
internal fun measureMangaSourceColumns(bitmap: Bitmap, region: MangaTranslatedRegion): Int? {
    val rect = region.textBox.pixels(bitmap.width, bitmap.height)
    if (rect.width().toLong() * rect.height() > 1024 * 1024) return null
    val background = MangaPageComposer.background(bitmap, rect, 1)
    val pixels = IntArray(rect.width() * rect.height())
    bitmap.getPixels(pixels, 0, rect.width(), rect.left, rect.top, rect.width(), rect.height())
    val font = region.typesetting?.fontSize?.let { it * bitmap.height / 1000 }
        ?: region.sourceFontSize?.let { it * bitmap.height / 1000 }
    val gap = maxOf(1, ((font ?: 16f) * .08f).roundToInt())
    val active = BooleanArray(rect.width()) { x ->
        (0 until rect.height()).count { y -> mangaInkContrast(pixels[y * rect.width() + x], background, 64) } >= maxOf(2, rect.height() / 40)
    }
    val spans = mutableListOf<Int>()
    var first = -1; var last = -1
    for (x in active.indices) if (active[x]) {
        if (first >= 0 && x - last > gap + 1) { spans += last - first + 1; first = -1 }
        if (first < 0) first = x
        last = x
    }
    if (first >= 0) spans += last - first + 1
    if (font != null && spans.any { it > font * 2.2f }) return null
    return spans.size.takeIf { it in 1..8 }
}
