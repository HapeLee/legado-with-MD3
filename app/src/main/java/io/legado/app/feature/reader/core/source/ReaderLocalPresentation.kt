package io.legado.app.feature.reader.core.source

import io.legado.app.feature.reader.core.layout.ReaderParagraphDecorationKind
import io.legado.app.feature.reader.core.layout.ReaderParagraphDecoration
import io.legado.app.feature.reader.core.layout.ReaderTextAlignment
import io.legado.app.feature.reader.core.cast.CastMarkers
import io.legado.app.domain.model.TextProjection
import io.legado.app.feature.reader.core.semantic.ReaderSemanticLocation

/** Appearance ranges follow the actual text mutations; they never define business text. */
data class ReaderLocalPresentation(
    val content: String,
    val spans: List<Span>,
    val paragraphs: List<Paragraph>,
    val rules: List<Int>,
    val anchors: Map<String, Int>,
    val chapterStartAnchors: Set<String> = emptySet(),
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
        val blankLine: Boolean = false,
        val listItemKey: Int? = null,
    )

    internal fun bind(text: String): ReaderLocalPresentation? {
        if (text == content) return this
        val projection = TextProjection(content.length)
        return if (projection.bindWhitespace(content, text)) remapOrNull(projection, text, keepDeletedAnchors = true) else null
    }

    /**
     * Optional presentation must never fail the authoritative chapter text: a projection that no
     * longer matches is dropped (returns null) instead of throwing inside the content pipeline.
     */
    internal fun remapOrNull(
        projection: TextProjection,
        text: String,
        keepDeletedAnchors: Boolean = false,
    ): ReaderLocalPresentation? {
        if (projection.originLength != content.length || projection.length != text.length) return null
        val mappedSpans = spans.flatMap { span -> projection.ranges(span.start, span.endExclusive, styling = true)
            .filter { ReaderSemanticLocation.isValidOffset(text, it.first) && ReaderSemanticLocation.isValidOffset(text, it.last + 1) }
            .map { Span(it.first, it.last + 1, span.style) } }.sortedBy { it.start }
        val merged = mutableListOf<Span>()
        for (span in mappedSpans) {
            if (span.style == ReaderInlineSourceStyle()) continue
            val previous = merged.lastOrNull()
            if (previous != null && previous.endExclusive == span.start && previous.style == span.style) {
                merged[merged.lastIndex] = previous.copy(endExclusive = span.endExclusive)
            } else merged += span
        }
        val mappedParagraphs = paragraphs.flatMap { paragraph ->
            if (paragraph.blankLine || paragraph.rule) {
                projection.boundary(paragraph.start, keepDeleted = true)?.let {
                    listOf(paragraph.copy(start = it, endExclusive = it))
                }.orEmpty()
            } else {
                val ranges = projection.ranges(paragraph.start, paragraph.endExclusive)
                val key = paragraph.listItemKey ?: ranges.firstOrNull()?.first?.takeIf {
                    paragraph.decorations.any { decoration -> decoration.kind == ReaderParagraphDecorationKind.BULLET }
                }
                ranges.map { paragraph.copy(start = it.first, endExclusive = it.last + 1, listItemKey = key) }
            }
        }.sortedBy { it.start }
        val mappedAnchors = anchors.mapNotNull { (id, position) ->
            (if (id in chapterStartAnchors) 0 else projection.boundary(position, keepDeletedAnchors))?.takeIf { ReaderSemanticLocation.isValidOffset(text, it) }?.let { id to it }
        }.toMap()
        return copy(content = text, spans = merged, paragraphs = mappedParagraphs,
            rules = rules.mapNotNull { projection.boundary(it, keepDeleted = true) }, anchors = mappedAnchors)
    }

    internal fun applyTo(source: ReaderChapterSource): ReaderChapterSource {
        val markers = CastMarkers.findMarkers(source.semanticContent)
        val paintedLists = mutableSetOf<Int>()
        val geometry = paragraphs.filterNot { it.blankLine || it.rule }
        var hintCursor = 0
        var spanCursor = 0
        fun safeCut(position: Int): Boolean {
            val found = markers.binarySearchBy(position) { it.start }
            val index = if (found >= 0) found else -found - 2
            return index < 0 || position >= markers[index].end
        }
        val blocks = source.blocks.flatMap { block ->
            if (block !is ReaderChapterSourceBlock.Paragraph || block.items.none {
                it is ReaderChapterInlineSource.Text || it is ReaderChapterInlineSource.Image
            }) return@flatMap listOf(block)
            val end = block.items.maxOf { item -> item.chapterPosition + when (item) {
                is ReaderChapterInlineSource.Text -> item.value.length
                is ReaderChapterInlineSource.Image, is ReaderChapterInlineSource.BlankLine -> 1
                is ReaderChapterInlineSource.BgmScene -> 0
            } }
            while (hintCursor < geometry.size && geometry[hintCursor].endExclusive <= block.chapterPosition) hintCursor++
            var hintEnd = hintCursor
            while (hintEnd < geometry.size && geometry[hintEnd].start < end) hintEnd++
            val hints = geometry.subList(hintCursor, hintEnd)
            val boundaries = (listOf(block.chapterPosition, end) + hints.drop(1).map { it.start }
                .filter { it > block.chapterPosition && it < end }).distinct().sorted()
            boundaries.zipWithNext { from, until ->
                val originalHint = hints.firstOrNull { it.start < until && it.endExclusive > from }
                val hint = if (originalHint?.listItemKey != null && !paintedLists.add(originalHint.listItemKey)) {
                    originalHint.copy(decorations = originalHint.decorations.filterNot { it.kind == ReaderParagraphDecorationKind.BULLET })
                } else originalHint
                val items = block.items.flatMap { item ->
                    if (item !is ReaderChapterInlineSource.Text) return@flatMap if (item.chapterPosition in from until until) listOf(item) else emptyList()
                    val start = maxOf(from, item.chapterPosition)
                    val stop = minOf(until, item.chapterPosition + item.value.length)
                    if (start >= stop) return@flatMap emptyList()
                    while (spanCursor < spans.size && spans[spanCursor].endExclusive <= start) spanCursor++
                    var spanEnd = spanCursor
                    while (spanEnd < spans.size && spans[spanEnd].start < stop) spanEnd++
                    val relevant = spans.subList(spanCursor, spanEnd)
                    val cuts = (listOf(start, stop) + relevant.flatMap { listOf(it.start, it.endExclusive) }
                        .filter { it > start && it < stop && safeCut(it) }).distinct().sorted()
                    cuts.zipWithNext { a, b ->
                        val appearance = relevant.firstOrNull { a >= it.start && b <= it.endExclusive }
                        item.copy(value = item.value.substring(a - item.chapterPosition, b - item.chapterPosition),
                            chapterPosition = a, style = appearance?.style ?: item.style)
                    }
                }
                block.copy(items = items, chapterPosition = from, presentation = hint)
            }
        }
        val decorations = paragraphs.filter { it.blankLine || it.rule } + rules.map {
            Paragraph(it, it, 0f, 0f, null, emptyList(), false, rule = true)
        }
        val pending = decorations.sortedBy { it.start }.iterator()
        var next = if (pending.hasNext()) pending.next() else null
        val result = ArrayList<ReaderChapterSourceBlock>(blocks.size + decorations.size)
        fun addDecoration() {
            val decoration = checkNotNull(next)
            result += ReaderChapterSourceBlock.Paragraph(emptyList(), decoration.start, decoration)
            next = if (pending.hasNext()) pending.next() else null
        }
        for (block in blocks) {
            if (!(block is ReaderChapterSourceBlock.Text && block.isTitle)) {
                while (next?.let { it.start <= block.chapterPosition } == true) addDecoration()
            }
            result += block
        }
        while (next != null) addDecoration()
        return source.copy(blocks = result, anchorPositions = anchors)
    }
}
