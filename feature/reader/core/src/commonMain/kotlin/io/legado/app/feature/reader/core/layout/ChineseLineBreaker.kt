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
            val currentWidthLimit = if (widths.isEmpty()) firstWidthLimit else widthLimit
            if (lineWidth > currentWidthLimit) {
                var mode = when {
                    index > 0 && clusters[index - 1] in opening -> Mode.PULL_PREVIOUS
                    cluster in closing -> Mode.PULL_PREVIOUS
                    else -> Mode.NORMAL
                }
                var rewindClusters = 0
                var rewindCharacters = 0
                // 本引擎不做行尾标点压缩（旧 ZhLayout 的 CPS_*），凡 PULL_PREVIOUS 会把
                // 收尾标点留到下一行行首的，都必须回退到更早的非标点边界
                val needsRecheck = mode == Mode.PULL_PREVIOUS && (
                    (index > 0 && clusters[index - 1] in closing) ||
                        (index < clusters.lastIndex && clusters[index + 1] in closing)
                    )
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
                    starts += textLength + cluster.length
                    clusterStarts += index + 1
                    widths += lineWidth
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

    private enum class Mode { NORMAL, PULL_PREVIOUS, REWIND }

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
