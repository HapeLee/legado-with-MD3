package io.legado.app.feature.reader.core.layout

/** Platform-free Chinese line breaking. Widths are supplied by a platform text measurer. */
class ChineseLineBreaker(
    private val clusters: List<String>,
    private val widthsPx: FloatArray,
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
            // 行首不留词间空格：本行已经放下内容时，溢出到这里的空格留在本行行尾（尾随空格
            // 不可见），由紧随其后的词首字符触发正常的整词回退断行。若在这里直接断行，
            // 空格会被整块携带到下一行，英文正文每次断在词间都会在行首多出一个空格。
            val keepSpaceAtLineEnd = isWordSpace(cluster) && index > clusterStarts.last()
            if (lineWidth > currentWidthLimit && !keepSpaceAtLineEnd) {
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
                    // 同一标点连打（`Yes！！！！！”` 里那一串）整块比行尾剩余空间宽时就地断行、
                    // 本行填满：串内相邻两个标点之间是合法断点。不同收尾标点之间（`。”`）
                    // 归下面的 PULL_PREVIOUS 回退管。
                    index > 0 && cluster in closing && clusters[index - 1] == cluster -> Mode.NORMAL
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
     * 词首（或断点本身）前紧贴开引号/开括号时，只回退到词首会把开标点留在行尾——正是
     * [isForbiddenBreak] 判定的非法断点。此时连开标点（含连续多个，如 `“(`）一起回退：
     * 开标点落在下一行行首合法，单词也得以整词保留（#2449：`[Energy]` 被拆成
     * `[E|nergy`）。开标点串一直贴到本行行首时无处再退——再退本行就空了且断点非法——
     * 保留原断点，按字符回退（同超长词）。
     */
    private fun safeBreakStart(candidate: Int, lineStart: Int): Int {
        val start = latinWordStartBefore(candidate) ?: candidate
        if (start <= lineStart) return candidate
        var breakStart = start
        while (breakStart > lineStart + 1 && clusters[breakStart - 1] in opening) breakStart--
        return if (clusters[breakStart - 1] in opening) candidate else breakStart
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
        /**
         * 避头尾：收尾标点不落行首、开引号不留行尾。
         * 唯一例外是**同一个标点连打**（`！！！` 中间），见 [breakLines] 里那条 `Mode.NORMAL`；
         * [ReaderPaginationSession] 收窄行尾时依赖同一谓词，两边必须同口径。
         */
        internal fun isForbiddenBreak(previous: String, next: String): Boolean =
            (next in closing && previous != next) || previous in opening

        /** 词间空格（含不换行空格、全角空格等）：断行时只能留在行尾，不能出现在行首。 */
        internal fun isWordSpace(cluster: String): Boolean =
            cluster.isNotEmpty() && cluster.all { it.isWhitespace() || Character.isSpaceChar(it) }

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
