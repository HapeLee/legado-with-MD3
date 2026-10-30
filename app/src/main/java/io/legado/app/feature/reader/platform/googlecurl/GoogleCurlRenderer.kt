package io.legado.app.feature.reader.platform.googlecurl

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import io.legado.app.feature.reader.core.transition.ReaderTurnDirection
import io.legado.app.feature.reader.core.transition.googlecurl.GoogleCurlGeometry

/** Travelling front-face wave. Page rasterization and transaction timing belong to the host. */
internal class GoogleCurlRenderer : AutoCloseable {
    private val columns = 64
    private val rows = 48
    private val wave = GoogleCurlGeometry(.08f)
    private val vertices = FloatArray((columns + 1) * (rows + 1) * 2)
    private val colors = IntArray(vertices.size / 2)
    private val texture = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pageRect = RectF()
    private var current: Bitmap? = null
    private var target: Bitmap? = null

    fun prepare(current: Bitmap, target: Bitmap) {
        close()
        this.current = current.readable()
        this.target = target.readable()
    }

    fun draw(canvas: Canvas, direction: ReaderTurnDirection, progress: Float,
        width: Float, height: Float, density: Float): Boolean {
        val current = current ?: return false
        val target = target ?: return false
        pageRect.set(0f, 0f, width, height)
        val p = progress.coerceIn(0f, 1f)
        if (p <= 0f || p >= 1f) {
            canvas.drawBitmap(if (p <= 0f) current else target, null, pageRect, texture)
            return true
        }
        val forward = direction == ReaderTurnDirection.NEXT
        val sheet = if (forward) current else target
        val background = if (forward) target else current
        wave.update(if (forward) p else 1f - p)
        var edge = Float.NEGATIVE_INFINITY
        for (column in 0..columns) {
            val u = column.toFloat() / columns
            val brightness = wave.project(u, 0f, width, height, vertices, column * 2)
            val color = Color.rgb(brightness, brightness, brightness)
            colors[column] = color
            edge = maxOf(edge, vertices[column * 2])
            for (row in 1..rows) {
                val index = row * (columns + 1) + column
                wave.project(u, row.toFloat() / rows, width, height, vertices, index * 2)
                colors[index] = color
            }
        }
        canvas.drawBitmap(background, null, pageRect, texture)
        val shadowWidth = (22f * density).coerceAtLeast(.001f)
        val alpha = (255 * .25f * (edge / shadowWidth).coerceIn(0f, 1f)).toInt()
        shadow.shader = LinearGradient(edge, 0f, edge + shadowWidth, 0f,
            Color.argb(alpha, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRect(edge, 0f, edge + shadowWidth, height, shadow)
        canvas.drawBitmapMesh(sheet, columns, rows, vertices, 0, colors, 0, texture)
        return true
    }

    private fun Bitmap.readable(): Bitmap =
        if (config == Bitmap.Config.HARDWARE) requireNotNull(copy(Bitmap.Config.ARGB_8888, false)) else this

    override fun close() {
        // Recorded Canvas commands can still reference a bitmap. Release references, never recycle it here.
        current = null
        target = null
        shadow.shader = null
    }
}
