package io.legado.app.feature.reader.platform

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.LruCache
import androidx.core.graphics.PathParser
import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderPage
import io.legado.app.feature.reader.core.model.ReaderRect
import io.legado.app.feature.reader.core.model.ReaderUnderline
import io.legado.app.feature.reader.core.model.underlineRuns

/** Immutable Android draw data prepared once for a page snapshot revision. */
internal data class ReaderPageDecorationDrawCache(
    val contentRules: List<ReaderRuleDrawCommand>,
    val halfHighlights: List<ReaderHalfHighlightDrawCommand>,
    val styledUnderlines: List<ReaderUnderlineDrawCommand>,
    val overlayRules: List<ReaderRuleDrawCommand>,
) {
    companion object {
        fun create(page: ReaderPage) = ReaderPageDecorationDrawCache(
            contentRules = page.elements.filterIsInstance<ReaderElement.Rule>()
                .filterNot(ReaderElement.Rule::overlayStyledUnderline)
                .map(::ReaderRuleDrawCommand),
            halfHighlights = page.underlineRuns().filter { it.underline.mode == 7 }.map { run ->
                ReaderHalfHighlightDrawCommand(run.bounds, run.underline.colorArgb)
            },
            styledUnderlines = page.underlineRuns().filterNot { it.underline.mode == 7 }
                .map { run ->
                ReaderUnderlineDrawCommand(run.bounds, run.underline)
            },
            overlayRules = page.elements.filterIsInstance<ReaderElement.Rule>()
                .filter(ReaderElement.Rule::overlayStyledUnderline)
                .map(::ReaderRuleDrawCommand),
        )
    }
}

internal class ReaderHalfHighlightDrawCommand(
    private val bounds: ReaderRect,
    colorArgb: Int,
) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorArgb }

    fun draw(canvas: Canvas) {
        canvas.drawRect(
            bounds.left,
            bounds.top + bounds.height * 0.5f,
            bounds.right,
            bounds.bottom,
            paint,
        )
    }
}

internal class ReaderRuleDrawCommand(private val rule: ReaderElement.Rule) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = rule.colorArgb
        strokeWidth = rule.widthPx.coerceAtLeast(1f)
        if (rule.dashed) {
            pathEffect = DashPathEffect(
                floatArrayOf(rule.dashOnPx.coerceAtLeast(0.1f), rule.dashOffPx.coerceAtLeast(0.1f)),
                0f,
            )
        }
    }

    fun draw(canvas: Canvas) {
        canvas.drawLine(rule.bounds.left, rule.bounds.top, rule.bounds.right, rule.bounds.bottom, paint)
    }
}

internal class ReaderUnderlineDrawCommand(
    private val bounds: ReaderRect,
    private val underline: ReaderUnderline,
) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = underline.colorArgb
        strokeWidth = underline.widthPx.coerceAtLeast(1f)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val wavePath = if (underline.mode == 3) createWavePath(bounds, underline) else null
    private val svgPath = if (underline.mode == 5) ReaderSvgPathCache.parse(underline.svgPath) else null

    fun draw(canvas: Canvas) {
        val start = bounds.left
        val end = bounds.right
        val y = bounds.bottom + underline.offsetPx
        when (underline.mode) {
            1 -> canvas.drawLine(start, y, end, y, paint)
            2 -> drawDashed(canvas, start, end, y)
            3 -> wavePath?.let { canvas.drawPath(it, paint) }
            4 -> {
                canvas.drawLine(start, y, end, y, paint)
                val secondY = y + underline.doubleLineGapPx + underline.widthPx
                canvas.drawLine(start, secondY, end, secondY, paint)
            }
            5 -> svgPath?.takeIf { end > start }?.let { path ->
                canvas.save()
                canvas.translate(start, y - SVG_BASELINE_Y)
                canvas.scale((end - start) / SVG_BASE_WIDTH, 1f)
                canvas.drawPath(path, paint)
                canvas.restore()
            }
            6 -> canvas.drawLine(
                start,
                bounds.top + bounds.height * 0.52f,
                end,
                bounds.top + bounds.height * 0.52f,
                paint
            )
        }
    }

    private fun drawDashed(canvas: Canvas, start: Float, end: Float, y: Float) {
        val dashOn = underline.dashOnPx.coerceAtLeast(MIN_SEGMENT_PX)
        val dashOff = underline.dashOffPx.coerceAtLeast(MIN_SEGMENT_PX)
        var x = start
        while (x < end) {
            canvas.drawLine(x, y, (x + dashOn).coerceAtMost(end), y, paint)
            x += dashOn + dashOff
        }
    }

    private companion object {
        const val MIN_SEGMENT_PX = 0.1f
        const val SVG_BASE_WIDTH = 100f
        const val SVG_BASELINE_Y = 50f

        fun createWavePath(bounds: ReaderRect, underline: ReaderUnderline): Path {
            val y = bounds.bottom + underline.offsetPx
            val waveLength = underline.waveLengthPx.coerceAtLeast(MIN_SEGMENT_PX)
            return Path().apply {
                moveTo(bounds.left, y)
                var x = bounds.left
                while (x < bounds.right) {
                    val next = (x + waveLength).coerceAtMost(bounds.right)
                    quadTo((x + next) / 2f, y - underline.waveAmplitudePx, next, y)
                    x = next
                    if (x < bounds.right) {
                        val nextDown = (x + waveLength).coerceAtMost(bounds.right)
                        quadTo((x + nextDown) / 2f, y + underline.waveAmplitudePx, nextDown, y)
                        x = nextDown
                    }
                }
            }
        }
    }
}

internal object ReaderSvgPathCache {
    private val cache = LruCache<String, Path>(32)

    fun parse(pathData: String): Path? {
        if (pathData.isBlank()) return null
        cache.get(pathData)?.let { return it }
        val path = runCatching { PathParser.createPathFromPathData(pathData) }.getOrNull() ?: return null
        cache.put(pathData, path)
        return path
    }

    fun clear() = cache.evictAll()
}
