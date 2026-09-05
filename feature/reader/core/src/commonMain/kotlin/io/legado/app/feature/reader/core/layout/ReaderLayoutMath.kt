package io.legado.app.feature.reader.core.layout

data class GlyphClusters(val text: List<String>, val widthsPx: List<Float>)

fun clusterGlyphs(text: String, measuredWidthsPx: FloatArray, start: Int = 0): GlyphClusters {
    require(start >= 0 && start + text.length <= measuredWidthsPx.size)
    val clusters = ArrayList<String>()
    val widths = ArrayList<Float>()
    var index = 0
    while (index < text.length) {
        val clusterStart = index++
        widths += measuredWidthsPx[start + clusterStart]
        while (index < text.length && measuredWidthsPx[start + index] == 0f &&
            text[index].code !in setOf(0x200B, 0x200C, 0x200D, 0x2060)
        ) index++
        clusters += text.substring(clusterStart, index)
    }
    return GlyphClusters(clusters, widths)
}

data class ReaderContentBounds(
    val widthPx: Int,
    val heightPx: Int,
    val rightPx: Int,
    val bottomPx: Int,
)

fun calculateContentBounds(
    viewportWidthPx: Int,
    viewportHeightPx: Int,
    paddingLeftPx: Int,
    paddingTopPx: Int,
    paddingRightPx: Int,
    paddingBottomPx: Int,
    columns: Int = 1,
): ReaderContentBounds {
    require(columns > 0)
    val columnWidth = viewportWidthPx / columns
    val width = (columnWidth - paddingLeftPx - paddingRightPx).coerceAtLeast(0)
    val height = (viewportHeightPx - paddingTopPx - paddingBottomPx).coerceAtLeast(0)
    return ReaderContentBounds(width, height, columnWidth - paddingRightPx, paddingTopPx + height)
}
