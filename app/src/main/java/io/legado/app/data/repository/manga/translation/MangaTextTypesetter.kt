package io.legado.app.data.repository.manga.translation

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.icu.text.BreakIterator
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

/** One measurement is used both to check available space and to draw the final lettering. */
internal class MangaTextLayout(
    val fontSize: Float,
    private val paint: TextPaint,
    private val inset: Int,
    val horizontal: StaticLayout?,
    val columns: List<List<String>>,
    private val cellWidth: Float = 0f,
    private val cellHeight: Float = 0f,
) {
    fun draw(canvas: Canvas, area: Rect, color: Int) {
        val outline = if ((Color.red(color) + Color.green(color) + Color.blue(color)) / 3 > 128) Color.BLACK else Color.WHITE
        fun outlined(draw: () -> Unit) {
            paint.color = outline; paint.style = Paint.Style.STROKE
            draw()
            paint.color = color; paint.style = Paint.Style.FILL
            draw()
        }
        canvas.save()
        try {
            canvas.clipRect(area)
            if (horizontal != null) {
                canvas.translate((area.left + inset).toFloat(), area.top + (area.height() - horizontal.height) / 2f)
                outlined { horizontal.draw(canvas) }
            } else {
                val left = area.exactCenterX() - columns.size * cellWidth / 2
                val top = area.exactCenterY() - columns.maxOf { it.size } * cellHeight / 2
                val ink = Rect()
                columns.forEachIndexed { column, chars ->
                    chars.forEachIndexed { row, char ->
                        paint.getTextBounds(char, 0, char.length, ink)
                        val x = left + (columns.size - column - 0.5f) * cellWidth - ink.exactCenterX()
                        val baseline = top + (row + 0.5f) * cellHeight - ink.exactCenterY()
                        outlined { canvas.drawText(char, x, baseline, paint) }
                    }
                }
            }
        } finally { canvas.restore() }
    }
}

internal object MangaTextTypesetter {
    private val typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    private const val closing = "、。，．！？!?：:；;)]）］｝〉》」』】〕〗〙〛’”ーぁぃぅぇぉっゃゅょァィゥェォッャュョ"
    private const val opening = "([（［｛〈《「『【〔〖〘〚‘“"

    /** Compare visible body glyphs across scripts instead of assuming equal em sizes look equal. */
    fun inkRatio(text: String, vertical: Boolean): Float {
        val sample = if (vertical(text, true)) "国" else text.filter { it.isLetterOrDigit() }.toSet().joinToString("").take(128).ifEmpty { "Hg" }
        val paint = TextPaint().apply { typeface = MangaTextTypesetter.typeface; textSize = 100f }
        val bounds = Rect()
        paint.getTextBounds(sample, 0, sample.length, bounds)
        return (if (vertical) bounds.width().toFloat() / sample.codePointCount(0, sample.length).coerceAtLeast(1) else bounds.height().toFloat())
            .div(100f).coerceAtLeast(0.1f)
    }

    fun vertical(text: String, sourceVertical: Boolean): Boolean {
        if (!sourceVertical) return false
        var letters = 0; var cjk = 0
        text.codePoints().forEach { code ->
            if (Character.isLetter(code)) {
                letters++
                when (Character.UnicodeScript.of(code)) {
                    Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA,
                    Character.UnicodeScript.KATAKANA, Character.UnicodeScript.HANGUL -> cjk++
                    else -> Unit
                }
            }
        }
        return cjk > 0 && cjk * 2 >= letters
    }

    /** Search to a quarter pixel with at most sixteen probes, rather than 10% jumps. */
    fun fit(text: String, vertical: Boolean, width: Int, height: Int, preferredSize: Float): MangaTextLayout {
        require(preferredSize.isFinite() && preferredSize > 0) { "Invalid preferred text size" }
        val preferred = preferredSize
        atSize(text, vertical, width, height, preferred)?.let { return it }
        val minimum = minOf(8f, preferred)
        var best = requireNotNull(atSize(text, vertical, width, height, minimum)) { "Translation does not fit its text area" }
        var low = minimum; var high = preferred
        repeat(16) {
            if (high - low <= 0.25f) return best
            val middle = (low + high) / 2
            val candidate = atSize(text, vertical, width, height, middle)
            if (candidate == null) high = middle else { low = middle; best = candidate }
        }
        return best
    }

    // This is the inlined integer 2, supported by StaticLayout since API 23; no API 29 class is loaded.
    @SuppressLint("InlinedApi")
    fun atSize(text: String, vertical: Boolean, width: Int, height: Int, size: Float): MangaTextLayout? {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = MangaTextTypesetter.typeface
            textSize = size
            strokeWidth = max(1f, size * 0.08f)
            strokeJoin = Paint.Join.ROUND
        }
        val inset = ceil(max(2f, paint.strokeWidth / 2 + 1)).toInt()
        val availableWidth = width - inset * 2
        val availableHeight = height - inset * 2
        if (availableWidth < 1 || availableHeight < 1) return null
        if (!vertical) {
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, availableWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(true)
                .setBreakStrategy(LineBreaker.BREAK_STRATEGY_BALANCED)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE).build()
            if (layout.height > availableHeight || (0 until layout.lineCount).any { layout.getLineWidth(it) > availableWidth + 0.1f }) return null
            return MangaTextLayout(size, paint, inset, layout, emptyList())
        }
        val paragraphs = text.replace("\r\n", "\n").replace('\r', '\n').split('\n').filter { it.isNotBlank() }.map(::graphemes)
        if (paragraphs.isEmpty()) return null
        val ink = Rect()
        var cellWidth = size * 1.15f; var cellHeight = size * 1.2f
        paragraphs.flatten().forEach { char ->
            paint.getTextBounds(char, 0, char.length, ink)
            cellWidth = max(cellWidth, max(ink.width().toFloat(), paint.measureText(char)) + paint.strokeWidth + 2)
            cellHeight = max(cellHeight, ink.height() + paint.strokeWidth + 2)
        }
        val capacity = (availableHeight / cellHeight).toInt()
        if (capacity < 1) return null
        val columns = mutableListOf<List<String>>()
        for (paragraph in paragraphs) {
            var start = 0
            while (start < paragraph.size) {
                var end = (start + capacity).coerceAtMost(paragraph.size)
                while (end > start && end < paragraph.size &&
                    (closing.contains(paragraph[end]) || opening.contains(paragraph[end - 1]))) end--
                if (end == start) return null
                columns += paragraph.subList(start, end)
                if (columns.size * cellWidth > availableWidth) return null
                start = end
            }
        }
        return MangaTextLayout(size, paint, inset, null, columns, cellWidth, cellHeight)
    }

    private fun graphemes(text: String): List<String> {
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(text) }
        return buildList {
            var start = iterator.first()
            var end = iterator.next()
            while (end != BreakIterator.DONE) {
                text.substring(start, end).takeIf { it.isNotBlank() }?.let(::add)
                start = end; end = iterator.next()
            }
        }
    }
}
