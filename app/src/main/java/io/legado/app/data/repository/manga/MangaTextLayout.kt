package io.legado.app.data.repository.manga

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import io.legado.app.domain.gateway.MangaLayoutDirection
import kotlinx.serialization.Serializable
import kotlin.math.ceil
import kotlin.math.roundToInt

@Serializable
internal data class MangaDrawRun(val text: String, val x: Float, val y: Float, val width: Int,
    val height: Int, val fontSize: Float, val rotation: Boolean = false, val verticalFeatures: Boolean = false)
@Serializable
internal data class MangaLayoutPlan(val fontSize: Float, val vertical: Boolean, val runs: List<MangaDrawRun>)

/** Measured, serializable placements. Geometry and drawing share the same fallback-aware shaper. */
internal object MangaTextLayout {
    const val VERSION = "contour-vertical-4"
    private const val MAX_UNITS = 1024
    private const val MAX_WORK = 120_000
    fun paint(size: Float = 16f) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private fun shaped(text: String, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(true)
            .apply { if (Build.VERSION.SDK_INT >= 28) setUseLineSpacingFromFallbacks(true) }.build()

    fun plan(text: String, area: MangaLayoutArea, pageWidth: Int, sourceSize: Float, language: String,
        direction: MangaLayoutDirection, fontScale: Float = 1f, checkCancellation: () -> Unit = {}): MangaLayoutPlan? {
        if (text.isBlank() || text.length > 8192 || area.bounds.isEmpty) return null
        require(fontScale.isFinite() && fontScale in .5f..2f)
        area.prepare(checkCancellation)
        // Page-resolution scaling alone can demand 40px type for a 16px source line.
        // Keep the lower bound relative to the source while retaining a pixel floor.
        val min = maxOf(6f, minOf(maxOf(10f, pageWidth * 10f / 384f), sourceSize * .65f)) * fontScale
        val max = minOf(256f, maxOf(min, sourceSize * 1.5f * fontScale), maxOf(area.bounds.width(), area.bounds.height()).toFloat())
        if (max < min) return null
        val canVertical = direction == MangaLayoutDirection.VERTICAL ||
            direction == MangaLayoutDirection.AUTO && language in setOf("zh", "ja", "ko") && area.bounds.height() > area.bounds.width() * 1.25f
        val work = intArrayOf(0)
        for (candidate in 0..32) {
            checkCancellation()
            val size = max - (max - min) * candidate / 32f
            val horizontal = if (direction != MangaLayoutDirection.VERTICAL) candidate(text, area, size, false, work, checkCancellation) else null
            val vertical = if (canVertical) candidate(text, area, size, true, work, checkCancellation) else null
            if (horizontal != null || vertical != null) {
                if (direction != MangaLayoutDirection.AUTO) return (horizontal ?: vertical)?.plan
                val aspect = area.bounds.height().toDouble() / area.bounds.width().coerceAtLeast(1)
                val horizontalPenalty = (aspect - 1.25).coerceAtLeast(0.0) * .25
                return listOfNotNull(horizontal?.copy(cost = horizontal.cost + horizontalPenalty), vertical)
                    .minBy { it.cost }.plan
            }
            if (work[0] >= MAX_WORK) return null
        }
        return null
    }

    private data class UnitMeasure(val unit: MangaTextUnit, val width: Int, val height: Int, val size: Float)
    private data class Slot(val cross: Int, val span: IntRange)
    private data class Segment(val start: Int, val end: Int)
    private data class Candidate(val plan: MangaLayoutPlan, val cost: Double)

    private fun candidate(text: String, area: MangaLayoutArea, size: Float, vertical: Boolean, work: IntArray,
        checkCancellation: () -> Unit): Candidate? {
        val paint = paint(size)
        val units = MangaVerticalText.units(text, vertical, paint)
        if (units.size > MAX_UNITS) return null
        val measured = units.map { unit ->
            if (unit.text == "\n") UnitMeasure(unit, 0, 0, size) else {
                paint.textSize = size
                paint.fontFeatureSettings = if (vertical && unit.direction == MangaGlyphDirection.UPRIGHT) "'vert' 1, 'vrt2' 1" else null
                val fm = paint.fontMetricsInt
                val ink = Rect().also { paint.getTextBounds(unit.display, 0, unit.display.length, it) }
                val width = ceil(maxOf(paint.measureText(unit.display), ink.width().toFloat())).toInt() + 4
                val layout = shaped(unit.display, paint, width + 4)
                UnitMeasure(unit, width + 4, maxOf(layout.height, ink.height() + 4, fm.bottom - fm.top), size)
            }
        }
        val pitch = if (vertical) maxOf(ceil(size * 1.12f).toInt(), measured.filter { it.unit.direction != MangaGlyphDirection.SIDEWAYS }.maxOfOrNull { it.width } ?: 1,
            measured.filter { it.unit.direction == MangaGlyphDirection.SIDEWAYS }.maxOfOrNull { it.height } ?: 1)
        else maxOf(ceil(size * 1.12f).toInt(), measured.maxOfOrNull { it.height } ?: 1)
        val crossLength = if (vertical) area.bounds.width() else area.bounds.height()
        val maxLines = minOf(64, (crossLength - area.margin * 2) / pitch)
        if (maxLines <= 0) return null
        val along = if (vertical) area.bounds.height() else area.bounds.width()
        fun advance(unit: UnitMeasure) = when {
            unit.unit.text == "\n" -> 0
            !vertical || unit.unit.text.isBlank() -> maxOf(0, unit.width - 8)
            unit.unit.direction == MangaGlyphDirection.SIDEWAYS -> unit.width
            else -> pitch
        }
        val total = measured.sumOf { advance(it) }
        val firstCount = maxOf(1, ceil(total.toDouble() / along).toInt())
        val counts = ((firstCount..minOf(maxLines, firstCount + 8)).toList() + maxLines).distinct().filter { it in 1..maxLines }
        var best: MangaLayoutPlan? = null
        var bestCost = Double.POSITIVE_INFINITY
        val bboxCenter = if (vertical) area.bounds.centerX() else area.bounds.centerY()
        val centers = listOf(area.crossCenter(vertical), bboxCenter).distinct()
        for (count in counts) for (center in centers) for (offset in listOf(0, -pitch / 3, pitch / 3).distinct()) {
            checkCancellation()
            val crossStart = center - count * pitch / 2 + offset
            val slots = (0 until count).mapNotNull { index ->
                val cross = crossStart + (if (vertical) count - index - 1 else index) * pitch
                area.span(cross, cross + pitch, vertical)?.let { Slot(cross, it) }
            }
            if (slots.size != count) continue
            val costs = Array(count + 1) { DoubleArray(units.size + 1) { Double.POSITIVE_INFINITY } }
            val previous = Array(count + 1) { IntArray(units.size + 1) { -1 } }
            costs[0][0] = 0.0
            for (line in 0 until count) {
                checkCancellation()
                val capacity = slots[line].span.count()
                for (start in units.indices) {
                    if (!costs[line][start].isFinite()) continue
                    var width = 0f
                    for (end in start + 1..units.size) {
                        if (++work[0] >= MAX_WORK) return best?.let { Candidate(it, bestCost) }
                        if (work[0] % 256 == 0) checkCancellation()
                        val at = measured[end - 1]
                        val newline = at.unit.text == "\n"
                        width += if (newline) 0 else advance(at)
                        if (width > capacity) break
                        val contentEnd = if (newline) end - 1 else end
                        val blank = contentEnd == start
                        if (!blank && !MangaVerticalText.legalBreak(units, start, contentEnd)) { if (newline) break else continue }
                        // Consume every explicit paragraph break, including empty paragraphs.
                        val ragged = (capacity - width) / capacity
                        val cost = costs[line][start] + ragged * ragged + if (!blank && contentEnd - start == 1 && end < units.size) .8 else 0.0
                        if (cost < costs[line + 1][end]) {
                            costs[line + 1][end] = cost
                            previous[line + 1][end] = start
                        }
                        if (newline) break
                    }
                }
            }
            if (!costs[count][units.size].isFinite() || costs[count][units.size] >= bestCost) continue
            val segments = mutableListOf<Segment>()
            var end = units.size
            for (line in count downTo 1) {
                val start = previous[line][end]
                if (start < 0) break
                segments += Segment(start, end)
                end = start
            }
            if (end != 0 || segments.size != count) continue
            segments.reverse()
            val runs = mutableListOf<MangaDrawRun>()
            var valid = true
            segments.forEachIndexed { line, segment ->
                val slot = slots[line]
                val content = measured.subList(segment.start, segment.end).filter { it.unit.text != "\n" }
                if (vertical) {
                    val length = content.sumOf { advance(it) }
                    var y = slot.span.first + (slot.span.count() - length) / 2f
                    content.forEach { m ->
                        val sideways = m.unit.direction == MangaGlyphDirection.SIDEWAYS
                        val h = advance(m)
                        val width = if (sideways) m.height else m.width
                        runs += MangaDrawRun(m.unit.display, slot.cross + (pitch - width) / 2f, y + if (sideways) 0f else (pitch - m.height) / 2f,
                            if (sideways) m.width else width, m.height, m.size, sideways, m.unit.direction == MangaGlyphDirection.UPRIGHT)
                        y += h
                    }
                } else {
                    val string = content.joinToString("") { it.unit.text }
                    if (string.isNotEmpty()) {
                        val p = paint(size)
                        val layout = shaped(string, p, slot.span.count())
                        val ink = Rect().also { p.getTextBounds(string, 0, string.length, it) }
                        if (layout.lineCount != 1 || layout.height > pitch ||
                            layout.getLineLeft(0) + ink.left < 0 || layout.getLineLeft(0) + ink.right > slot.span.count()) valid = false
                        runs += MangaDrawRun(string, slot.span.first.toFloat(), slot.cross + (pitch - layout.height) / 2f,
                            slot.span.count(), layout.height, size)
                    }
                }
            }
            if (valid && runs.isNotEmpty()) { best = MangaLayoutPlan(size, vertical, runs); bestCost = costs[count][units.size] }
        }
        return best?.let { Candidate(it, bestCost) }
    }

    /** Render without clipping, then inspect actual ink against the contour. A failed check preserves the source. */
    fun render(plan: MangaLayoutPlan, area: MangaLayoutArea, bounds: Rect, color: Int, checkCancellation: () -> Unit): Bitmap? {
        val guard = ceil(plan.fontSize).toInt() + 4
        val width = bounds.width() + guard * 2
        val height = bounds.height() + guard * 2
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.translate((guard - bounds.left).toFloat(), (guard - bounds.top).toFloat())
            for (run in plan.runs) {
                checkCancellation()
                val paint = paint(run.fontSize).apply {
                    this.color = color
                    if (run.verticalFeatures) fontFeatureSettings = "'vert' 1, 'vrt2' 1"
                }
                val layout = shaped(run.text, paint, run.width)
                if (layout.lineCount != 1 || layout.height > run.height) { bitmap.recycle(); return null }
                val saved = canvas.save()
                if (run.rotation) { canvas.translate(run.x + run.height, run.y); canvas.rotate(90f) }
                else canvas.translate(run.x, run.y)
                layout.draw(canvas)
                canvas.restoreToCount(saved)
            }
            val row = IntArray(width)
            for (y in 0 until height) {
                checkCancellation()
                bitmap.getPixels(row, 0, width, 0, y, width, 1)
                for (x in row.indices) if (Color.alpha(row[x]) != 0 &&
                    (x == 0 || y == 0 || x == width - 1 || y == height - 1 || !area.contains(x + bounds.left - guard, y + bounds.top - guard))) {
                    bitmap.recycle(); return null
                }
            }
            return Bitmap.createBitmap(bitmap, guard, guard, bounds.width(), bounds.height()).also { if (it !== bitmap) bitmap.recycle() }
        } catch (error: Throwable) { bitmap.recycle(); throw error }
    }
}
