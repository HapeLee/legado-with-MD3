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
        var previousWidth = 0f
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
                var rewindCharacters = 0
                // 可压缩标点会把收尾标点留到下一行行首，必须回退到更早的非标点边界
                val needsRecheck = mode == Mode.PULL_PREVIOUS && (previousClosing || nextClosing)
                if (needsRecheck && index > 2) {
                    val lineStart = if (widths.isEmpty()) indentCharacters else clusterStarts.last()
                    mode = Mode.NORMAL
                    for (candidate in index downTo lineStart + 1) {
                        if (candidate == index) {
                            previousWidth = 0f
                        } else {
                            rewindClusters++
                            rewindCharacters += clusters[candidate].length
                            previousWidth += widthsPx[candidate]
                        }
                        if (clusters[candidate] !in closing && clusters[candidate - 1] !in opening) {
                            mode = Mode.REWIND
                            break
                        }
                    }
                }
                when (mode) {
                    Mode.NORMAL -> {
                        carriedWidth = currentWidth
                        addStart(textLength, index)
                        carriedCharacters = cluster.length
                        carriedClusters = 1
                    }
                    Mode.PULL_PREVIOUS -> {
                        carriedWidth = currentWidth + previousWidth
                        addStart(textLength - clusters[index - 1].length, index - 1)
                        carriedCharacters = clusters[index - 1].length + cluster.length
                        carriedClusters = 2
                    }
                    Mode.HANG -> {
                        // 标点留在本行（行宽超出右边界，旧版正是靠这个避免标点落到下一行行首），
                        // 下一行从它之后重新开始，本行不向下一行携带任何宽度。
                        carriedWidth = 0f
                        addStart(textLength + cluster.length, index + 1)
                        carriedCharacters = 0
                        carriedClusters = 0
                        hungLine = true
                    }
                    Mode.REWIND -> {
                        carriedWidth = currentWidth + previousWidth
                        addStart(textLength - rewindCharacters, index - rewindClusters)
                        carriedCharacters = rewindCharacters + cluster.length
                        carriedClusters = rewindClusters + 1
                    }
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
            previousWidth = currentWidth
        }
    }

    private fun addStart(character: Int, cluster: Int) {
        starts += character
        clusterStarts += cluster
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
