package io.legado.app.feature.reader.core.layout

/** Platform-free Chinese line breaking. Widths are supplied by a platform text measurer. */
class ChineseLineBreaker(
    private val clusters: List<String>,
    private val widthsPx: List<Float>,
    private val indentCharacters: Int,
    widthPx: Int,
    private val ideographWidthPx: Float,
    letterSpacingPx: Float,
    firstLineWidthPx: Int = widthPx,
) {
    private val starts = mutableListOf(0)
    private val clusterStarts = mutableListOf(0)
    private val widths = mutableListOf<Float>()
    private val widthLimit = widthPx + letterSpacingPx
    private val firstWidthLimit = firstLineWidthPx + letterSpacingPx

    val lineStarts get() = starts.toIntArray()
    val lineClusterStarts get() = clusterStarts.toIntArray()
    val lineWidthsPx get() = widths.toFloatArray()
    val lineCount get() = widths.size

    init {
        require(clusters.size == widthsPx.size)
        breakLines()
    }

    private fun breakLines() {
        if (clusters.isEmpty()) return
        var lineWidth = 0f
        var textLength = 0
        clusters.forEachIndexed { index, cluster ->
            val currentWidth = widthsPx[index]
            lineWidth += currentWidth
            var carriedWidth = 0f
            var carriedCharacters = 0
            var carriedClusters = 0
            var hungLine = false
            val currentWidthLimit = if (widths.isEmpty()) firstWidthLimit else widthLimit
            if (lineWidth > currentWidthLimit) {
                val lineStart = clusterStarts.last()
                fun carryFrom(candidate: Int) {
                    val start = safeBreakStart(candidate, lineStart)
                    val carriedRange = start..index
                    carriedWidth = carriedRange.sumOf { widthsPx[it].toDouble() }.toFloat()
                    carriedCharacters = carriedRange.sumOf { clusters[it].length }
                    carriedClusters = carriedRange.count()
                    addStart(
                        character = textLength - carriedCharacters + cluster.length,
                        cluster = start,
                    )
                }
                // 旧 ZhLayout 把行尾标点的处置分成两类：可压缩的窄标点回退到更早的合法边界
                // （BREAK_MORE_CHAR），全角标点则直接悬挂在本行右边界之外（CPS_1/2/3：
                // `offset = 0f`，行宽允许超过 width）。下面三个判定与旧版逐条对应。
                fun compressibleAt(at: Int): Boolean =
                    at in widthsPx.indices && widthsPx[at] < ideographWidthPx

                val previousClosing = index > 0 && clusters[index - 1] in closing
                val previousOpening = index > 0 && clusters[index - 1] in opening
                val secondPreviousOpening = index > 1 && clusters[index - 2] in opening
                val nextClosing = index < clusters.lastIndex && clusters[index + 1] in closing
                val compressibleAround = when {
                    previousOpening && secondPreviousOpening ->
                        compressibleAt(index - 1) || compressibleAt(index - 2)

                    cluster in closing && previousClosing ->
                        compressibleAt(index) || compressibleAt(index - 1)

                    cluster in closing && secondPreviousOpening ->
                        compressibleAt(index) || compressibleAt(index - 2)

                    else -> false
                }
                // 悬挂条件同时排除了「下一字仍是行尾标点」——旧版此时会 reCheck 成回退。
                val hangs = index > 0 && !compressibleAround && !nextClosing &&
                        ((previousOpening && secondPreviousOpening) ||
                                (cluster in closing && (previousClosing || secondPreviousOpening)))
                var mode = when {
                    hangs -> Mode.HANG
                    index > 0 && (previousOpening || cluster in closing) -> Mode.PULL_PREVIOUS
                    else -> Mode.NORMAL
                }
                var rewindClusters = 0
                // 可压缩标点会把收尾标点留到下一行行首，必须回退到更早的非标点边界
                val needsRecheck = mode == Mode.PULL_PREVIOUS && (previousClosing || nextClosing)
                if (needsRecheck && index > 2) {
                    val lineStart = if (widths.isEmpty()) indentCharacters else clusterStarts.last()
                    mode = Mode.NORMAL
                    for (candidate in index downTo lineStart + 1) {
                        if (candidate != index) {
                            rewindClusters++
                        }
                        if (clusters[candidate] !in closing && clusters[candidate - 1] !in opening) {
                            mode = Mode.REWIND
                            break
                        }
                    }
                }
                when (mode) {
                    Mode.NORMAL -> carryFrom(index)
                    Mode.PULL_PREVIOUS -> carryFrom(index - 1)
                    Mode.HANG -> {
                        // 标点留在本行（行宽超出右边界，旧版正是靠这个避免标点落到下一行行首），
                        // 下一行从它之后重新开始，本行不向下一行携带任何宽度。
                        carriedWidth = 0f
                        addStart(textLength + cluster.length, index + 1)
                        carriedCharacters = 0
                        carriedClusters = 0
                        hungLine = true
                    }
                    Mode.REWIND -> carryFrom(index - rewindClusters)
                }
                widths += lineWidth - carriedWidth
                lineWidth = carriedWidth
            }
            if (index == clusters.lastIndex) {
                if (starts.size == widths.size + 1) {
                    // 悬挂已把标点留在本行、并让下一行从它之后开始，旧 ZhLayout 在
                    // breakCharCnt == 0 时同样不再补一行。未发生断行的普通收尾仍要补行。
                    if (!hungLine) {
                        starts += textLength + cluster.length
                        clusterStarts += index + 1
                        widths += lineWidth
                    }
                } else if (carriedClusters > 0) {
                    starts += starts.last() + carriedCharacters
                    clusterStarts += clusterStarts.last() + carriedClusters
                    widths += lineWidth
                }
            }
            textLength += cluster.length
        }
    }

    private fun addStart(character: Int, cluster: Int) {
        starts += character
        clusterStarts += cluster
    }

    /**
     * Keeps a candidate line start from splitting a Latin word that fits on a fresh line.
     *
     * 回退到词首不得制造新的避头尾违规：词首前一个 cluster 若是开引号/开括号（`opening`），
     * 回退会让它留在行尾——正是 [isForbiddenBreak] 判定的非法断点（`ReaderPaginator` 的
     * 收窄回退也依赖该谓词）。此时退回原有断点，宁可保留旧的中文字符级断行，也不制造违规。
     */
    private fun safeBreakStart(candidate: Int, lineStart: Int): Int {
        val wordStart = latinWordStartBefore(candidate) ?: return candidate
        if (wordStart <= lineStart) return candidate
        if (clusters[wordStart - 1] in opening) return candidate
        return wordStart
    }

    /**
     * Returns the start of the Latin word containing [index], or null when the overflow
     * is not inside one. A word that already starts on this visual line is deliberately not
     * rewound by the caller, so an overlong word still falls back to character-level breaks.
     */
    private fun latinWordStartBefore(index: Int): Int? {
        if (index == 0 ||
            !clusters[index].isLatinWordPart() ||
            !clusters[index - 1].isLatinWordPart()
        ) return null

        var start = index - 1
        while (start > 0 && clusters[start - 1].isLatinWordPart()) start--
        return start
    }

    private fun String.isLatinWordPart(): Boolean {
        var hasLatinLetter = false
        var offset = 0
        while (offset < length) {
            val codePoint = codePointAt(offset)
            val type = Character.getType(codePoint)
            when {
                Character.isLetter(codePoint) -> {
                    if (Character.UnicodeScript.of(codePoint) != Character.UnicodeScript.LATIN) {
                        return false
                    }
                    hasLatinLetter = true
                }
                Character.isDigit(codePoint) ||
                    type == Character.NON_SPACING_MARK.toInt() ||
                    type == Character.COMBINING_SPACING_MARK.toInt() ||
                    codePoint == '\''.code || codePoint == 0x2019 -> Unit
                else -> return false
            }
            offset += Character.charCount(codePoint)
        }
        return hasLatinLetter || all { it.isDigit() || it == '\'' || it == '\u2019' }
    }

    private enum class Mode { NORMAL, PULL_PREVIOUS, REWIND, HANG }

    companion object {
        internal fun isForbiddenBreak(previous: String, next: String): Boolean =
            next in closing || previous in opening

        private val closing = setOf(
            "！", "，", "。", "、", "；", "：", "？", "”", "’", "）", "］", "｝", "》",
            "〉", "〕", "】", "〗", "」", "』", "﹂", "﹄", "…", "—", "～", "·",
            "!", ",", ".", ":", ";", "?", ")", "]", "}", ">",
        )
        private val opening = setOf(
            "“", "‘", "（", "［", "｛", "《", "〈", "〔", "【", "〖", "『", "「", "﹁", "﹃",
            "(", "[", "{", "<",
        )
    }
}
