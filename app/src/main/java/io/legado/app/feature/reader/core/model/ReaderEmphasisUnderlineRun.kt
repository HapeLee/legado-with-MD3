package io.legado.app.feature.reader.core.model

data class ReaderEmphasisUnderline(
    val colorArgb: Int,
    val widthPx: Float,
    val bottomOffsetPx: Float,
)

data class ReaderEmphasisUnderlineRun(
    val startPx: Float,
    val endPx: Float,
    val yPx: Float,
    val style: ReaderEmphasisUnderline,
)

/** Restores the legacy whole-line underline used for search hits and read-aloud paragraphs. */
fun ReaderPage.emphasisUnderlineRuns(): List<ReaderEmphasisUnderlineRun> =
    underlineRuns(
        styleSelector = { line -> line.firstNotNullOfOrNull(ReaderElement.Text::emphasisUnderline) },
        isHit = { it.emphasisUnderline != null },
    )

/**
 * 运行期命中（搜索结果、朗读段落）的整行下划线，命中判定由调用方提供。
 *
 * 对照旧 View `TextLine.drawTextLine`：`if (useUnderline && (isReadAloud || searchResultColumnCount > 0))`
 * 时执行 `drawLine(lineStart + indentWidth, lineY, lineEnd, lineY)` —— 命中位置只决定
 * 「哪一行要划线」，线的范围始终是整行（首行缩进之后到行尾）。取命中元素自身的边界会把线缩成
 * 命中词的宽度，与旧版不一致。
 */
fun ReaderPage.emphasisUnderlineRunsFor(
    style: ReaderEmphasisUnderline,
    isHit: (ReaderElement.Text) -> Boolean,
): List<ReaderEmphasisUnderlineRun> =
    underlineRuns(styleSelector = { style }, isHit = isHit)

private fun ReaderPage.underlineRuns(
    styleSelector: (List<ReaderElement.Text>) -> ReaderEmphasisUnderline?,
    isHit: (ReaderElement.Text) -> Boolean,
): List<ReaderEmphasisUnderlineRun> {
    val lines = elements.filterIsInstance<ReaderElement.Text>()
        .groupBy { it.bounds.top to it.bounds.bottom }
    return lines.values.mapNotNull { line ->
        if (line.none(isHit)) return@mapNotNull null
        val style = styleSelector(line) ?: return@mapNotNull null
        ReaderEmphasisUnderlineRun(
            startPx = line.minOf { it.bounds.left },
            endPx = line.maxOf { it.bounds.right },
            yPx = line.maxOf { it.bounds.bottom } - style.bottomOffsetPx,
            style = style,
        )
    }
}
