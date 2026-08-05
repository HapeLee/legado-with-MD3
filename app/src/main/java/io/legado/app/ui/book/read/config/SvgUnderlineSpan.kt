package io.legado.app.ui.book.read.config

import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.ReplacementSpan
import io.legado.app.utils.dpToPx

/**
 * SVG 路径下划线 Span
 */
class SvgUnderlineSpan(
    private val textColor: Int,
    private val underlineColor: Int,
    private val underlineWidth: Float = 1f,
    private val svgPath: String,
) : ReplacementSpan() {

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
            fm.descent = metrics.descent
            fm.bottom = metrics.bottom
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
        val textPaint = Paint(paint).apply {
            color = textColor
        }
        canvas.drawText(textStr, x, y.toFloat(), textPaint)

        if (svgPath.isNotBlank()) {
            val path = SvgPathParser.parse(svgPath)
            if (path != null) {
                val textWidth = paint.measureText(textStr)
                val baseWidth = 100f
                val baseY = 50f
                val lineY = y + 6.dpToPx()

                val underlinePaint = Paint(paint).apply {
                    color = underlineColor
                    strokeWidth = underlineWidth.dpToPx()
                    style = Paint.Style.STROKE
                    isAntiAlias = true
                }

                canvas.save()
                canvas.translate(x, lineY - baseY)
                canvas.scale(textWidth / baseWidth, 1f)
                canvas.drawPath(path, underlinePaint)
                canvas.restore()
            }
        }
    }
}
