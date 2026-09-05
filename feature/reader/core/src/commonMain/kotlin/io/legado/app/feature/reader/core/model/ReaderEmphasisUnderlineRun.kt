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
fun ReaderPage.emphasisUnderlineRuns(): List<ReaderEmphasisUnderlineRun> {
    val lines = elements.filterIsInstance<ReaderElement.Text>()
        .groupBy { it.bounds.top to it.bounds.bottom }
    return lines.values.mapNotNull { line ->
        val style = line.firstNotNullOfOrNull(ReaderElement.Text::emphasisUnderline)
            ?: return@mapNotNull null
        ReaderEmphasisUnderlineRun(
            startPx = line.minOf { it.bounds.left },
            endPx = line.maxOf { it.bounds.right },
            yPx = line.maxOf { it.bounds.bottom } - style.bottomOffsetPx,
            style = style,
        )
    }
}
