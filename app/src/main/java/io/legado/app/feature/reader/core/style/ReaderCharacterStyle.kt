package io.legado.app.feature.reader.core.style

import io.legado.app.feature.reader.core.model.ReaderTextBackgroundImage
import io.legado.app.feature.reader.core.model.ReaderUnderline

enum class ReaderStyleTarget { ALL, TITLE, BODY }

data class ReaderCharacterStyle(
    val colorArgb: Int? = null,
    val backgroundArgb: Int? = null,
    val underline: ReaderUnderline? = null,
    val fontPath: String? = null,
    val fontWeight: Int? = null,
    val italic: Boolean? = null,
    val fontSizeOffsetPx: Float = 0f,
    val markingId: String? = null,
    val backgroundImage: ReaderTextBackgroundImage? = null,
)

data class ReaderStyleRange(
    val start: Int,
    val endExclusive: Int,
    val target: ReaderStyleTarget,
    val style: ReaderCharacterStyle,
    val priority: Int = 0,
) {
    fun contains(position: Int, isTitle: Boolean): Boolean =
        position in start until endExclusive && when (target) {
            ReaderStyleTarget.ALL -> true
            ReaderStyleTarget.TITLE -> isTitle
            ReaderStyleTarget.BODY -> !isTitle
        }
}

object ReaderCharacterStyleResolver {
    /** Build the winning style once per interval instead of scanning every range for each glyph. */
    fun compile(ranges: List<ReaderStyleRange>): ReaderCompiledStyleRanges {
        val boundaries = ranges.asSequence()
            .filter { it.start < it.endExclusive }
            .flatMap { sequenceOf(it.start, it.endExclusive) }
            .distinct()
            .sorted()
            .toList()
            .toIntArray()
        val bodyStyles = Array<ReaderCharacterStyle?>(boundaries.size.coerceAtLeast(1) - 1) {
            resolve(ranges, boundaries[it], false)
        }
        val titleStyles = Array<ReaderCharacterStyle?>(bodyStyles.size) {
            resolve(ranges, boundaries[it], true)
        }
        return ReaderCompiledStyleRanges(boundaries, bodyStyles, titleStyles)
    }

    fun resolve(
        ranges: List<ReaderStyleRange>,
        position: Int,
        isTitle: Boolean
    ): ReaderCharacterStyle? {
        var winner: ReaderStyleRange? = null
        for (range in ranges) {
            if (range.contains(position, isTitle) &&
                (winner == null || range.priority >= winner.priority)
            ) {
                // Equal priority keeps the later range, matching the original index tie-break.
                winner = range
            }
        }
        return winner?.style
    }
}

class ReaderCompiledStyleRanges internal constructor(
    private val boundaries: IntArray,
    private val bodyStyles: Array<ReaderCharacterStyle?>,
    private val titleStyles: Array<ReaderCharacterStyle?>,
) {
    /**
     * 上次命中的区间下标。每段内的字形位置单调递增，绝大多数查询会落回同一区间，
     * 先做一次区间命中检查即可省掉二分。非线程安全：一个实例只服务一次 measure。
     */
    private var cursor = 0

    fun resolve(position: Int, isTitle: Boolean): ReaderCharacterStyle? {
        if (boundaries.size < 2) return null
        val last = cursor
        if (position >= boundaries[last] && position < boundaries[last + 1]) {
            return if (isTitle) titleStyles[last] else bodyStyles[last]
        }
        var low = 0
        var high = boundaries.size - 2
        while (low <= high) {
            val middle = (low + high) ushr 1
            when {
                position < boundaries[middle] -> high = middle - 1
                position >= boundaries[middle + 1] -> low = middle + 1
                else -> {
                    cursor = middle
                    return if (isTitle) titleStyles[middle] else bodyStyles[middle]
                }
            }
        }
        return null
    }
}
