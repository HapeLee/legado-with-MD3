// SPDX-License-Identifier: Apache-2.0
// Adapted from ciddwd/overlay-translator, commit 64a7aa2c9605c88dd35bdeea42ab5c0f1c18ce5e.
// Modifications: Legado package, geometry types and bounded Android pipeline integration.
package io.legado.app.data.repository.manga.translation

import java.text.BreakIterator
import java.util.Locale
import kotlin.math.ceil

/**
 * Fits translated text into the actual bubble mask instead of its outer rectangle.
 *
 * Every returned row or column is backed by a complete rectangular run of mask pixels. The
 * renderer can therefore draw inside [Run.bounds] without relying on two edge samples.
 */
internal object MangaShapeTextLayout {

    enum class Orientation {
        HORIZONTAL,
        VERTICAL_RIGHT_TO_LEFT,
        VERTICAL_LEFT_TO_RIGHT,
    }

    enum class Reason {
        FIT,
        EMPTY_TEXT,
        INVALID_MASK,
        MASK_TOO_SMALL,
        NO_FIT,
    }

    data class Run(
        val text: String,
        val bounds: MangaShapeRect,
    )

    data class Result(
        val accepted: Boolean,
        val reason: Reason,
        val orientation: Orientation,
        val fontSizePx: Int,
        val runs: List<Run>,
    )

    /** Preserve the source's visual anchor when the complete lettering can move safely. */
    fun anchor(result: Result, width: Int, height: Int, mask: BooleanArray?, centerX: Float, centerY: Float): Result {
        if (!result.accepted || result.runs.isEmpty()) return result
        val left = result.runs.minOf { it.bounds.left }; val right = result.runs.maxOf { it.bounds.right }
        val top = result.runs.minOf { it.bounds.top }; val bottom = result.runs.maxOf { it.bounds.bottom }
        val dx = (centerX - (left + right) / 2f).toInt().coerceIn(-left, width - right)
        val dy = (centerY - (top + bottom) / 2f).toInt().coerceIn(-top, height - bottom)
        for (step in 16 downTo 1) {
            val x = dx * step / 16; val y = dy * step / 16
            val moved = result.runs.map { run -> run.copy(bounds = run.bounds.let {
                MangaShapeRect(it.left + x, it.top + y, it.right + x, it.bottom + y)
            }) }
            if (mask == null || moved.all { run ->
                val r = run.bounds
                (r.top until r.bottom).all { row -> (r.left until r.right).all { col -> mask[row * width + col] } }
            }) return result.copy(runs = moved)
        }
        return result
    }

    fun interface TextMeasurer {
        fun measure(text: String, fontSizePx: Int): Float
    }

    /** null means an explicit rectangular area; a supplied empty mask is always rejected. */
    fun layout(width:Int,height:Int,mask:BooleanArray?,text:String,orientation:Orientation,
               minimumFontSizePx:Int,maximumFontSizePx:Int,textMeasurer:TextMeasurer):Result =
        layout(width,height,mask,text,orientation,minimumFontSizePx,maximumFontSizePx,textMeasurer,false)

    fun layout(
        width: Int,
        height: Int,
        mask: BooleanArray?,
        text: String,
        orientation: Orientation,
        minimumFontSizePx: Int,
        maximumFontSizePx: Int,
        textMeasurer: TextMeasurer,
        preferSingleLine: Boolean,
        preferredColumnCount: Int? = null,
    ): Result {
        require(width >= 0 && height >= 0)
        require(mask == null || mask.size == width * height)
        require(minimumFontSizePx > 0)
        require(maximumFontSizePx >= minimumFontSizePx)
        val normalized = normalize(text)
        if (normalized.isEmpty()) return rejected(Reason.EMPTY_TEXT, orientation)
        if (width == 0 || height == 0 || mask?.none { it } == true) {
            return rejected(Reason.INVALID_MASK, orientation)
        }
        if (width < minimumFontSizePx || height < minimumFontSizePx) {
            return rejected(Reason.MASK_TOO_SMALL, orientation)
        }

        if(preferSingleLine && orientation==Orientation.HORIZONTAL) {
            for(size in maximumFontSizePx downTo minimumFontSizePx) {
                val runs=layoutHorizontal(width,height,mask,normalized,size,textMeasurer,1)
                if(runs!=null)return Result(true,Reason.FIT,orientation,size,runs)
            }
        }
        if (preferredColumnCount != null && orientation != Orientation.HORIZONTAL) {
            require(preferredColumnCount in 1..64)
            for (size in maximumFontSizePx downTo minimumFontSizePx) {
                val runs = layoutVertical(width, height, mask, normalized, size, orientation, preferredColumnCount)
                if (runs != null) return Result(true, Reason.FIT, orientation, size, runs)
            }
        }
        for (size in maximumFontSizePx downTo minimumFontSizePx) {
            val candidate = when (orientation) {
                Orientation.HORIZONTAL -> layoutHorizontal(
                    width = width,
                    height = height,
                    mask = mask,
                    text = normalized,
                    fontSizePx = size,
                    textMeasurer = textMeasurer,
                )
                Orientation.VERTICAL_RIGHT_TO_LEFT,
                Orientation.VERTICAL_LEFT_TO_RIGHT -> layoutVertical(
                    width = width,
                    height = height,
                    mask = mask,
                    text = normalized,
                    fontSizePx = size,
                    orientation = orientation,
                )
            }
            if (candidate != null) {
                return Result(
                    accepted = true,
                    reason = Reason.FIT,
                    orientation = orientation,
                    fontSizePx = size,
                    runs = candidate,
                )
            }
        }
        return rejected(Reason.NO_FIT, orientation)
    }

    internal fun respectsKinsoku(lines: List<String>): Boolean =
        lines.withIndex().all { (index, line) ->
            line.isNotEmpty() &&
                (index == 0 || line.first() !in PROHIBITED_LINE_START) &&
                (index == lines.lastIndex || line.last() !in PROHIBITED_LINE_END)
        }

    private fun layoutHorizontal(
        width: Int,
        height: Int,
        mask: BooleanArray?,
        text: String,
        fontSizePx: Int,
        textMeasurer: TextMeasurer,
        lineLimit: Int = Int.MAX_VALUE,
    ): List<Run>? {
        val lineHeight = ceil(fontSizePx * HORIZONTAL_LINE_HEIGHT_RATIO).toInt().coerceAtLeast(1)
        val maximumLines = minOf(height / lineHeight,lineLimit)
        if (maximumLines == 0) return null
        val clusters = clusters(text)
        for (lineCount in 1..maximumLines) {
            val top = (height - lineCount * lineHeight) / 2
            val slots = (0 until lineCount).mapNotNull { line ->
                horizontalSlot(
                    width = width,
                    height = height,
                    mask = mask,
                    top = top + line * lineHeight,
                    bottom = top + (line + 1) * lineHeight,
                    inset = ceil(fontSizePx * SHAPE_INSET_RATIO).toInt(),
                )
            }
            if (slots.size != lineCount) continue
            val lines = wrapHorizontal(
                clusters = clusters,
                widths = slots.map { it.width.toFloat() },
                fontSizePx = fontSizePx,
                textMeasurer = textMeasurer,
            ) ?: continue
            if (!respectsKinsoku(lines)) continue
            return lines.zip(slots).map { (line, slot) ->
                val measuredWidth = ceil(textMeasurer.measure(line, fontSizePx)).toInt()
                    .coerceAtMost(slot.width)
                val left = slot.left + (slot.width - measuredWidth) / 2
                Run(
                    text = line,
                    bounds = MangaShapeRect(
                        left = left,
                        top = slot.top,
                        right = left + measuredWidth,
                        bottom = slot.bottom,
                    ),
                )
            }
        }
        return null
    }

    private fun layoutVertical(
        width: Int,
        height: Int,
        mask: BooleanArray?,
        text: String,
        fontSizePx: Int,
        orientation: Orientation,
        preferredColumnCount: Int? = null,
    ): List<Run>? {
        val columnWidth = ceil(fontSizePx * VERTICAL_COLUMN_WIDTH_RATIO).toInt().coerceAtLeast(1)
        val cellHeight = ceil(fontSizePx * VERTICAL_CELL_HEIGHT_RATIO).toInt().coerceAtLeast(1)
        val maximumColumns = width / columnWidth
        if (maximumColumns == 0) return null
        val content = mangaVerticalGlyphs(text).filterNot { it.isBlank() }
        for (columnCount in preferredColumnCount?.let { if (it <= maximumColumns) it..it else IntRange.EMPTY } ?: (1..maximumColumns)) {
            val left = (width - columnCount * columnWidth) / 2
            val naturalSlots = (0 until columnCount).mapNotNull { column ->
                verticalSlot(
                    width = width,
                    height = height,
                    mask = mask,
                    left = left + column * columnWidth,
                    right = left + (column + 1) * columnWidth,
                    inset = ceil(fontSizePx * SHAPE_INSET_RATIO).toInt(),
                )
            }
            if (naturalSlots.size != columnCount) continue
            val slots = if (orientation == Orientation.VERTICAL_RIGHT_TO_LEFT) {
                naturalSlots.reversed()
            } else {
                naturalSlots
            }
            val columns = wrapVertical(
                clusters = content,
                capacities = slots.map { it.height / cellHeight },
            ) ?: continue
            if (!respectsKinsoku(columns)) continue
            return columns.zip(slots).map { (column, slot) ->
                val contentHeight = columnClusters(column).size * cellHeight
                val top = slot.top + (slot.height - contentHeight) / 2
                Run(
                    text = column,
                    bounds = MangaShapeRect(
                        left = slot.left,
                        top = top,
                        right = slot.right,
                        bottom = top + contentHeight,
                    ),
                )
            }
        }
        return null
    }

    private fun horizontalSlot(
        width: Int,
        height: Int,
        mask: BooleanArray?,
        top: Int,
        bottom: Int,
        inset: Int,
    ): MangaShapeRect? {
        if (top < 0 || bottom > height || bottom <= top) return null
        if (mask == null) return if (width > inset * 2) MangaShapeRect(inset, top, width - inset, bottom) else null
        val valid = BooleanArray(width) { true }
        for (y in top until bottom) {
            val row = y * width
            for (x in 0 until width) {
                valid[x] = valid[x] && mask[row + x]
            }
        }
        val interval = longestTrueInterval(valid) ?: return null
        val left = interval.first + inset
        val right = interval.second - inset
        return if (right > left) MangaShapeRect(left, top, right, bottom) else null
    }

    private fun verticalSlot(
        width: Int,
        height: Int,
        mask: BooleanArray?,
        left: Int,
        right: Int,
        inset: Int,
    ): MangaShapeRect? {
        if (left < 0 || right > width || right <= left) return null
        if (mask == null) return if (height > inset * 2) MangaShapeRect(left, inset, right, height - inset) else null
        val valid = BooleanArray(height) { true }
        for (x in left until right) {
            for (y in 0 until height) {
                valid[y] = valid[y] && mask[y * width + x]
            }
        }
        val interval = longestTrueInterval(valid) ?: return null
        val top = interval.first + inset
        val bottom = interval.second - inset
        return if (bottom > top) MangaShapeRect(left, top, right, bottom) else null
    }

    private fun longestTrueInterval(values: BooleanArray): Pair<Int, Int>? {
        var bestStart = -1
        var bestEnd = -1
        var start = -1
        for (index in 0..values.size) {
            val set = index < values.size && values[index]
            if (set && start < 0) start = index
            if (!set && start >= 0) {
                if (index - start > bestEnd - bestStart) {
                    bestStart = start
                    bestEnd = index
                }
                start = -1
            }
        }
        return if (bestStart >= 0) bestStart to bestEnd else null
    }

    private fun wrapHorizontal(
        clusters: List<String>,
        widths: List<Float>,
        fontSizePx: Int,
        textMeasurer: TextMeasurer,
    ): List<String>? {
        val output = mutableListOf<String>()
        var start = skipSpaces(clusters, 0)
        widths.forEach { width ->
            if (start >= clusters.size) return@forEach
            val end = fittingBreak(
                clusters = clusters,
                start = start,
                maximumWidth = width,
            ) { candidate ->
                textMeasurer.measure(candidate, fontSizePx)
            }
            if (end <= start) return null
            val line = clusters.subList(start, end).joinToString("").trim()
            if (line.isEmpty()) return null
            output += line
            start = skipSpaces(clusters, end)
        }
        return output.takeIf { start >= clusters.size }
    }

    private fun wrapVertical(
        clusters: List<String>,
        capacities: List<Int>,
    ): List<String>? {
        val output = mutableListOf<String>()
        var start = 0
        capacities.forEachIndexed { index, capacity ->
            if (start >= clusters.size) return@forEachIndexed
            if (capacity <= 0) return null
            val remaining=clusters.size-start
            val laterCapacity=capacities.drop(index+1).sum()
            val minimumTake=maxOf(1,remaining-laterCapacity)
            val maximumTake=minOf(remaining,capacity)
            if(minimumTake>maximumTake)return null
            val ideal=ceil(remaining.toDouble()/(capacities.size-index)).toInt()
                .coerceIn(minimumTake,maximumTake)
            var end=(minimumTake..maximumTake).filter { canBreak(clusters,start+it) }
                .minByOrNull { kotlin.math.abs(it-ideal) }?.let { start+it }
                ?: (start+maximumTake)
            if (end < clusters.size) {
                val preferred = (end downTo start + 1).firstOrNull { canBreak(clusters, it) }
                if (preferred != null) end = preferred
            }
            if (end <= start) return null
            output += clusters.subList(start, end).joinToString("")
            start = end
        }
        return output.takeIf { start >= clusters.size }
    }

    private fun fittingBreak(
        clusters: List<String>,
        start: Int,
        maximumWidth: Float,
        measure: (String) -> Float,
    ): Int {
        var end = start
        var lastBreak = -1
        while (end < clusters.size) {
            val candidateEnd = end + 1
            val candidate = clusters.subList(start, candidateEnd).joinToString("").trimEnd()
            if (candidate.isNotEmpty() && measure(candidate) > maximumWidth) break
            end = candidateEnd
            if (canBreak(clusters, end)) lastBreak = end
        }
        if (end == clusters.size) return end
        if (lastBreak > start) return lastBreak
        if (end <= start) return start
        var hardBreak = end
        // An overlong word may break at a grapheme boundary. Word-boundary preference must
        // not turn an otherwise fitting word into one letter per row.
        while (hardBreak > start && !canBreak(clusters, hardBreak, allowInsideWord = true)) hardBreak--
        return hardBreak
    }

    private fun canBreak(clusters: List<String>, position: Int, allowInsideWord: Boolean = false): Boolean {
        if (position <= 0 || position >= clusters.size) return true
        val before = clusters[position - 1]
        val after = clusters[position]
        if (before.isBlank()) return true
        if (before.last() in PROHIBITED_LINE_END) return false
        if (after.first() in PROHIBITED_LINE_START) return false
        if (before == "-" || before == "—") return true
        if (!allowInsideWord && before.isAsciiWord() && after.isAsciiWord()) return false
        return true
    }

    private fun skipSpaces(clusters: List<String>, start: Int): Int {
        var index = start
        while (index < clusters.size && clusters[index].isBlank()) index++
        return index
    }

    internal fun normalize(text: String): String =
        Regex("[\\t ]*[\\r\\n]+[\\t ]*").replace(text) { match ->
            val before = match.range.first.takeIf { it > 0 }
                ?.let { Character.codePointBefore(text, it) }
            val after = (match.range.last + 1).takeIf { it < text.length }
                ?.let { Character.codePointAt(text, it) }
            // OCR/translation line breaks are layout hints, not spaces in Chinese or Japanese.
            // Retain a separator for Latin and other scripts whose words need one.
            if (before.isUnspacedScript() || after.isUnspacedScript()) "" else " "
        }.replace(Regex("\\s+"), " ").trim()

    private fun Int?.isUnspacedScript(): Boolean = this != null && when (Character.UnicodeScript.of(this)) {
        Character.UnicodeScript.HAN,
        Character.UnicodeScript.HIRAGANA,
        Character.UnicodeScript.KATAKANA -> true
        else -> false
    }

    private fun clusters(text: String): List<String> {
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
        iterator.setText(text)
        val output = mutableListOf<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            output += text.substring(start, end)
            start = end
            end = iterator.next()
        }
        return output
    }

    private fun columnClusters(text: String): List<String> = mangaVerticalGlyphs(text)

    private fun String.isAsciiWord(): Boolean =
        length == 1 && first().code < 128 && (first().isLetterOrDigit() || first() == '_')

    private fun rejected(
        reason: Reason,
        orientation: Orientation,
    ) = Result(
        accepted = false,
        reason = reason,
        orientation = orientation,
        fontSizePx = 0,
        runs = emptyList(),
    )

    private const val HORIZONTAL_LINE_HEIGHT_RATIO = 1.22f
    private const val VERTICAL_COLUMN_WIDTH_RATIO = 1.18f
    private const val VERTICAL_CELL_HEIGHT_RATIO = 1.12f
    private const val SHAPE_INSET_RATIO = 0.24f

    private val PROHIBITED_LINE_START = setOf(
        '、', '。', '，', '．', '！', '？', '：', '；',
        '）', '］', '｝', '〉', '》', '」', '』', '】',
        '〕', '〗', '〙', '〛', '”', '’', '…', 'ー',
        ',', '.', '!', '?', ':', ';', ')', ']', '}',
    )
    private val PROHIBITED_LINE_END = setOf(
        '（', '［', '｛', '〈', '《', '「', '『', '【',
        '〔', '〖', '〘', '〚', '“', '‘', '(', '[', '{',
    )
}
