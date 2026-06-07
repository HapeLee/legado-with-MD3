package io.legado.app.ui.book.read.config

import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.ReplacementSpan
import io.legado.app.utils.dpToPx

/**
 * 双线下划线 Span
 */
class DoubleUnderlineSpan(
    private val textColor: Int,
    private val underlineColor: Int,
    private val underlineWidth: Float = 1f,
    private val underlineOffset: Float = 6f,
) : ReplacementSpan() {

    private val offsetPx = underlineOffset.toInt().dpToPx()
    private val lineGap = 3.dpToPx()
    private val widthPx = underlineWidth.toInt().dpToPx()

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        if (fm != null) {
            val metrics = paint.fontMetricsInt
            fm.top = metrics.top
            fm.ascent = metrics.ascent
            fm.descent = metrics.descent + offsetPx + lineGap + widthPx
            fm.bottom = metrics.bottom + offsetPx + lineGap + widthPx
        }
        return paint.measureText(text, start, end).toInt()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        val textStr = text.subSequence(start, end).toString()
        paint.color = textColor
        canvas.drawText(textStr, x, y.toFloat(), paint)

        val width = paint.measureText(text, start, end)
        val line1Y = y + offsetPx
        val line2Y = line1Y + lineGap + widthPx
        val linePaint = Paint(paint).apply {
            color = underlineColor
            style = Paint.Style.STROKE
            strokeWidth = underlineWidth.dpToPx()
            isAntiAlias = true
        }
        canvas.drawLine(x, line1Y.toFloat(), x + width, line1Y.toFloat(), linePaint)
        canvas.drawLine(x, line2Y.toFloat(), x + width, line2Y.toFloat(), linePaint)
    }
}
