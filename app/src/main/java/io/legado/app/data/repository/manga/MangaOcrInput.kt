package io.legado.app.data.repository.manga

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect

/** ML Kit requires both dimensions >= 32. Coordinates remain in the original tile's scale. */
internal class MangaOcrInput(val bitmap: Bitmap, val bounds: Rect) : AutoCloseable {
    override fun close() = bitmap.recycle()
}

internal fun mangaOcrInput(tile: Bitmap, detection: Rect): MangaOcrInput {
    val bounds = Rect(detection)
    require(bounds.intersect(0, 0, tile.width, tile.height) && !bounds.isEmpty)
    fun start(first: Int, last: Int, limit: Int): Int {
        val size = maxOf(32, last - first).coerceAtMost(limit)
        return (first - (size - (last - first)) / 2).coerceIn(0, limit - size)
    }
    val width = maxOf(32, bounds.width()).coerceAtMost(tile.width)
    val height = maxOf(32, bounds.height()).coerceAtMost(tile.height)
    val left = start(bounds.left, bounds.right, tile.width)
    val top = start(bounds.top, bounds.bottom, tile.height)
    bounds.set(left, top, left + width, top + height)
    val input = Bitmap.createBitmap(maxOf(32, width), maxOf(32, height), Bitmap.Config.ARGB_8888)
    try {
        Canvas(input).apply {
            drawColor(Color.WHITE)
            // No scaling: only tiles smaller than 32 need white padding on the right/bottom.
            drawBitmap(tile, bounds, Rect(0, 0, width, height), null)
        }
        return MangaOcrInput(input, bounds)
    } catch (error: Throwable) {
        input.recycle()
        throw error
    }
}
