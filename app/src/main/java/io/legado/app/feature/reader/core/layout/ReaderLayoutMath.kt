package io.legado.app.feature.reader.core.layout

/** 宽度是原始数组：整章每字一份，装箱会在排版期翻倍字形级的临时占用。 */
data class GlyphClusters(val text: List<String>, val widthsPx: FloatArray)

fun clusterGlyphs(text: String, measuredWidthsPx: FloatArray, start: Int = 0): GlyphClusters {
    require(start >= 0 && start + text.length <= measuredWidthsPx.size)
    val clusters = ArrayList<String>()
    // 簇数只会少于或等于字符数：先按上限开数组，扫完再收缩。
    val widths = FloatArray(text.length)
    var index = 0
    var clusterCount = 0
    while (index < text.length) {
        val clusterStart = index++
        widths[clusterCount++] = measuredWidthsPx[start + clusterStart]
        while (index < text.length && measuredWidthsPx[start + index] == 0f &&
            text[index].code !in setOf(0x200B, 0x200C, 0x200D, 0x2060)
        ) index++
        clusters += text.substring(clusterStart, index)
    }
    return GlyphClusters(clusters, widths.copyOf(clusterCount))
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
