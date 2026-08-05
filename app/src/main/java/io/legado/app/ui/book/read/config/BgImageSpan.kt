package io.legado.app.ui.book.read.config

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.text.style.ReplacementSpan
import io.legado.app.ui.book.read.page.entities.TextLine
import io.legado.app.utils.dpToPx

/**
 * 背景图+下划线 Span，用于高亮规则匹配区域
 */
class BgImageSpan(
    private val textColor: Int,
    private val bgImagePath: String,
    private val bgImageFit: Int = 0,
    private val bgImageScale: Float = 1f,
    private val underlineMode: Int = 0,
    private val underlineColor: Int = 0,
    private val underlineWidth: Float = 1f,
    private val underlineSvgPath: String = "",
    private val underlineOffset: Float = 6f,
) : ReplacementSpan() {

    private val offsetPx = underlineOffset.toInt().dpToPx()

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
            fm.descent = metrics.descent + if (underlineMode != 0) offsetPx else 0
            fm.bottom = metrics.bottom + if (underlineMode != 0) offsetPx else 0
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
        val width = paint.measureText(text, start, end)
        val rectWidth = width
        val rectHeight = (bottom - top).toFloat()
        val scale = bgImageScale.coerceIn(0.1f, 5f)

        val bitmap = TextLine.getBgBitmap(bgImagePath)
        if (bitmap != null) {
            val bgPaint = Paint().apply {
                style = Paint.Style.FILL
                isAntiAlias = true
                isFilterBitmap = true
            }
            when (bgImageFit) {
                1 -> {
                    val sw = rectWidth * scale
                    val sh = rectHeight * scale
                    val dx = x + (rectWidth - sw) / 2f
                    val dy = top + (rectHeight - sh) / 2f
                    canvas.save()
                    canvas.clipRect(x, top.toFloat(), x + width, bottom.toFloat())
                    canvas.drawBitmap(bitmap, null, RectF(dx, dy, dx + sw, dy + sh), bgPaint)
                    canvas.restore()
                }
                2 -> {
                    val bw = bitmap.width.toFloat()
                    val bh = bitmap.height.toFloat()
                    val fitScale = (rectWidth / bw).coerceAtLeast(rectHeight / bh) * scale
                    val scaledW = bw * fitScale
                    val scaledH = bh * fitScale
                    val dx = x + (rectWidth - scaledW) / 2f
                    val dy = top + (rectHeight - scaledH) / 2f
                    canvas.save()
                    canvas.clipRect(x, top.toFloat(), x + width, bottom.toFloat())
                    canvas.drawBitmap(bitmap, null, RectF(dx, dy, dx + scaledW, dy + scaledH), bgPaint)
                    canvas.restore()
                }
                else -> {
                    val shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
                    val matrix = Matrix()
                    if (scale != 1f) {
                        matrix.setScale(scale, scale)
                    }
                    matrix.postTranslate(x, top.toFloat())
                    shader.setLocalMatrix(matrix)
                    bgPaint.shader = shader
                    canvas.drawRect(x, top.toFloat(), x + width, bottom.toFloat(), bgPaint)
                }
            }
        }

        paint.color = textColor
        paint.shader = null
        canvas.drawText(text, start, end, x, y.toFloat(), paint)

        if (underlineMode != 0) {
            drawUnderline(canvas, x, x + width, y + offsetPx, paint)
        }
    }

    private fun drawUnderline(canvas: Canvas, startX: Float, endX: Float, lineY: Int, paint: Paint) {
        val ulPaint = Paint(paint).apply {
            color = underlineColor
            style = Paint.Style.STROKE
            strokeWidth = underlineWidth.dpToPx()
            isAntiAlias = true
        }
        when (underlineMode) {
            1 -> canvas.drawLine(startX, lineY.toFloat(), endX, lineY.toFloat(), ulPaint)
            2 -> {
                ulPaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 10f), 0f)
                canvas.drawLine(startX, lineY.toFloat(), endX, lineY.toFloat(), ulPaint)
            }
            3 -> {
                val path = android.graphics.Path()
                val waveAmplitude = 3.dpToPx().toFloat()
                val waveLength = 12.dpToPx().toFloat()
                path.moveTo(startX, lineY.toFloat())
                var currentX = startX
                val endY = lineY.toFloat()
                while (currentX < endX) {
                    val nextX = (currentX + waveLength).coerceAtMost(endX)
                    val midX = (currentX + nextX) / 2
                    path.quadTo(midX, endY - waveAmplitude, nextX, endY)
                    currentX = nextX
                    if (currentX < endX) {
                        val nextX2 = (currentX + waveLength).coerceAtMost(endX)
                        val midX2 = (currentX + nextX2) / 2
                        path.quadTo(midX2, endY + waveAmplitude, nextX2, endY)
                        currentX = nextX2
                    }
                }
                canvas.drawPath(path, ulPaint)
            }
            4 -> {
                val lineGap = 3.dpToPx()
                val line2Y = lineY + lineGap + underlineWidth.dpToPx()
                canvas.drawLine(startX, lineY.toFloat(), endX, lineY.toFloat(), ulPaint)
                canvas.drawLine(startX, line2Y.toFloat(), endX, line2Y.toFloat(), ulPaint)
            }
        }
    }
}
