package io.legado.app.feature.reader.core.source

import io.legado.app.feature.reader.core.layout.ReaderParagraphDecoration
import io.legado.app.feature.reader.core.layout.ReaderTextAlignment
import io.legado.app.feature.reader.core.cast.CastMarkers

/** Optional appearance, checked against the final body. Never a second source of text. */
data class ReaderLocalPresentation(
    val content: String,
    val spans: List<Span>,
    val paragraphs: List<Paragraph>,
    val rules: List<Int>,
    val anchors: Map<String, Int>,
) {
    data class Span(val start: Int, val endExclusive: Int, val style: ReaderInlineSourceStyle)
    /** Geometry uses the parser's neutral 10px font and is scaled only while measuring. */
    data class Paragraph(
        val start: Int,
        val endExclusive: Int,
        val firstMargin: Float,
        val restMargin: Float,
        val alignment: ReaderTextAlignment?,
        val decorations: List<ReaderParagraphDecoration>,
        val preserveBodyIndent: Boolean,
        val rule: Boolean = false,
    )

    fun applyTo(source: ReaderChapterSource): ReaderChapterSource {
        if (source.semanticContent != content || CastMarkers.findMarkers(content).isNotEmpty()) return source
        val ends = source.paragraphs.associate { it.start to it.endExclusive }
        var spanCursor = 0
        var hintCursor = 0
        val blocks = source.blocks.map { block ->
            if (block !is ReaderChapterSourceBlock.Paragraph || block.items.none {
                it is ReaderChapterInlineSource.Text || it is ReaderChapterInlineSource.Image
            }) return@map block
            val end = ends[block.chapterPosition] ?: return@map block
            while (hintCursor < paragraphs.size && paragraphs[hintCursor].endExclusive <= block.chapterPosition) hintCursor++
            val hint = paragraphs.getOrNull(hintCursor)?.takeIf { it.start < end }
            block.copy(items = block.items.flatMap { item ->
                if (item !is ReaderChapterInlineSource.Text) return@flatMap listOf(item)
                val from = item.chapterPosition
                val until = from + item.value.length
                while (spanCursor < spans.size && spans[spanCursor].endExclusive <= from) spanCursor++
                var tail = spanCursor
                while (tail < spans.size && spans[tail].start < until) tail++
                val relevant = spans.subList(spanCursor, tail)
                val cuts = (listOf(from, until) + relevant.flatMap { listOf(it.start, it.endExclusive) }
                    .filter { it > from && it < until }).distinct().sorted()
                var current = 0
                cuts.zipWithNext { start, stop ->
                    while (current < relevant.size && relevant[current].endExclusive <= start) current++
                    val appearance = relevant.getOrNull(current)?.takeIf { start >= it.start && stop <= it.endExclusive }
                    item.copy(value = item.value.substring(start - from, stop - from), chapterPosition = start,
                        style = appearance?.style ?: item.style)
                }
            }, presentation = hint)
        }.toMutableList()
        for (position in rules) {
            val index = blocks.indexOfFirst { !(it is ReaderChapterSourceBlock.Text && it.isTitle) && it.chapterPosition >= position }
            val rule = ReaderChapterSourceBlock.Paragraph(emptyList(), position,
                Paragraph(position, position, 0f, 0f, null, emptyList(), false, rule = true))
            blocks.add(if (index < 0) blocks.size else index, rule)
        }
        return source.copy(blocks = blocks, anchorPositions = anchors)
    }
}
