package io.legado.app.feature.reader.platform

import io.legado.app.feature.reader.core.source.ReaderChapterInlineSource
import io.legado.app.feature.reader.core.source.ReaderChapterSourceParser
import io.legado.app.feature.reader.core.source.ReaderLocalPresentation
import io.legado.app.feature.reader.core.source.ReaderInlineSourceStyle
import io.legado.app.feature.reader.core.semantic.ReaderSemanticLocation
import io.legado.app.help.book.MixedContentSegments
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** Maps appearance to an existing body by ordered identity; never searches for a nearest match. */
internal object ReaderLocalPresentationResolver {
    fun resolve(html: String, originalContent: String, indent: String, adaptSpecialStyle: Boolean): ReaderLocalPresentation? {
        val reference = ReaderChapterSourceParser.parse(0, "",
            MixedContentSegments.paragraphs(originalContent, indent, false), false, adaptSpecialStyle,
            AndroidReaderHtmlSemanticTextResolver)
        val doc = Jsoup.parseBodyFragment(html)
        val positions = linkedMapOf<Element, Int>()
        val parsed = JsoupReaderPresentationParser.project(doc.body(), elementPositions = positions)
        fun offsets(text: String) = text.indices.filter { !text[it].isWhitespace() && text[it] != '\u00a0' }
        val input = offsets(parsed.text)
        val output = offsets(reference.semanticContent)
        if (input.size != output.size || input.indices.any { parsed.text[input[it]] != reference.semanticContent[output[it]] }) return null
        val expectedImages = reference.blocks.filterIsInstance<io.legado.app.feature.reader.core.source.ReaderChapterSourceBlock.Paragraph>()
            .flatMap { it.items }.filterIsInstance<ReaderChapterInlineSource.Image>().map { it.source }
        val actualImages = parsed.paragraphs.flatMap { it.items }.filterIsInstance<ReaderChapterInlineSource.Image>().map { it.source }
        if (actualImages != expectedImages) return null
        fun boundary(position: Int): Int {
            val found = input.binarySearch(position).let { if (it < 0) -it - 1 else it }
            return output.getOrNull(found) ?: reference.characterCount
        }
        fun range(start: Int, length: Int): Pair<Int, Int>? {
            val first = input.binarySearch(start).let { if (it < 0) -it - 1 else it }
            val last = input.binarySearch(start + length).let { if (it < 0) -it - 1 else it } - 1
            if (last < first) return null
            val begin = output[first]
            val end = output[last] + 1
            if (!ReaderSemanticLocation.isValidOffset(reference.semanticContent, begin) ||
                !ReaderSemanticLocation.isValidOffset(reference.semanticContent, end)) return null
            return begin to end
        }
        val spans = parsed.paragraphs.flatMap { it.items }.filterIsInstance<ReaderChapterInlineSource.Text>().mapNotNull { text ->
            val (start, end) = range(text.chapterPosition, text.value.length) ?: return@mapNotNull null
            ReaderLocalPresentation.Span(start, end, text.style.copy(
                // Author "normal" clears author emphasis, not the reader's configured typeface.
                fontWeight = text.style.fontWeight.takeUnless { it == 400 }, italic = text.style.italic.takeIf { it == true }))
        }
        val activeSpans = spans.filter { it.style != ReaderInlineSourceStyle() }
        val hints = parsed.paragraphs.filterNot { it.rule }.mapNotNull { paragraph ->
            val start = paragraph.items.firstOrNull()?.chapterPosition ?: return@mapNotNull null
            val last = paragraph.items.lastOrNull() ?: return@mapNotNull null
            val length = when (last) { is ReaderChapterInlineSource.Text -> last.value.length; else -> 1 }
            val (from, end) = range(start, last.chapterPosition + length - start) ?: return@mapNotNull null
            ReaderLocalPresentation.Paragraph(from, end, paragraph.firstLineMarginPx, paragraph.restLineMarginPx,
                paragraph.alignment, paragraph.decorations, paragraph.preserveBodyIndent)
        }
        val anchors = positions.entries.flatMap { (element, offset) ->
            listOf(element.id(), element.attr("name")).filter { it.isNotBlank() }.map { it to boundary(offset) }
        }.toMap()
        val rules = parsed.paragraphs.filter { it.rule }.mapNotNull { it.items.firstOrNull()?.chapterPosition }.map {
            val mapped = boundary(it)
            reference.paragraphs.firstOrNull { paragraph -> mapped in paragraph.start until paragraph.endExclusive }?.start ?: mapped
        }
        return ReaderLocalPresentation(reference.semanticContent, activeSpans, hints, rules, anchors)
    }
}
