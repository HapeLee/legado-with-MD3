package io.legado.app.help.book

import io.legado.app.domain.model.BookContentProcessEngine
import io.legado.app.domain.model.TextProcessAction
import io.legado.app.domain.model.TextProcessAnchor
import io.legado.app.feature.reader.core.semantic.ReaderSemanticLocation
import io.legado.app.feature.reader.core.semantic.SemanticRange
import io.legado.app.feature.reader.core.source.ReaderChapterSourceBlock
import io.legado.app.feature.reader.core.source.ReaderChapterSourceParser
import io.legado.app.feature.reader.platform.AndroidReaderHtmlSemanticTextResolver
import io.legado.app.feature.reader.platform.ReaderHtmlTextEditor

/** Adapts a single supported edit back into the existing mixed channel; no raw-HTML fallback. */
internal class ProcessedContentEditor(private val indent: String) : BookContentProcessEngine.RichTextEditor {
    override fun edit(content: String, anchor: TextProcessAnchor, action: TextProcessAction): BookContentProcessEngine.EditResult {
        val paragraphs = MixedContentSegments.paragraphs(content, indent, true)
        fun project(values: List<String>) = ReaderChapterSourceParser.parse(
            0, "", values, false, true, AndroidReaderHtmlSemanticTextResolver,
        )
        val source = project(paragraphs)
        val target = BookContentProcessEngine.resolveRange(source.semanticContent, anchor)
            ?: return BookContentProcessEngine.EditResult.Unsupported("Text anchor not found")
        val selected = SemanticRange(target.first, target.last + 1)
        if (!ReaderSemanticLocation.validateRange(source.semanticContent, selected)) {
            return BookContentProcessEngine.EditResult.Unsupported("Invalid UTF-16 selection")
        }
        val replacement = BookContentProcessEngine.normalizeProcessText(when (action.type) {
            TextProcessAction.TYPE_REPLACE -> action.replacement.orEmpty()
            TextProcessAction.TYPE_DELETE -> ""
            TextProcessAction.TYPE_INSERT_BEFORE, TextProcessAction.TYPE_INSERT_AFTER -> action.text.orEmpty()
            else -> return BookContentProcessEngine.EditResult.Unsupported("Unknown edit action")
        })
        val range = when (action.type) {
            TextProcessAction.TYPE_INSERT_BEFORE -> SemanticRange(selected.start, selected.start)
            TextProcessAction.TYPE_INSERT_AFTER -> SemanticRange(selected.endExclusive, selected.endExclusive)
            else -> selected
        }
        var origin = 0
        for ((index, paragraph) in paragraphs.withIndex()) {
            val single = project(listOf(paragraph))
            val end = origin + single.characterCount
            // Both the anchor and edit must fit inside one channel paragraph.
            if (selected.start >= origin && selected.endExclusive < end) {
                val html = single.blocks.filterIsInstance<ReaderChapterSourceBlock.Html>().singleOrNull()
                val localRange = SemanticRange(range.start - origin, range.endExclusive - origin)
                val updated = if (html != null) {
                    if (selected.endExclusive - origin > html.semanticLength) {
                        return BookContentProcessEngine.EditResult.Unsupported("HTML block separator")
                    }
                    when (val result = ReaderHtmlTextEditor.replace(html.value, localRange, replacement)) {
                        is ReaderHtmlTextEditor.Result.Applied -> "<usehtml>${result.html}</usehtml>"
                        is ReaderHtmlTextEditor.Result.Unsupported -> return BookContentProcessEngine.EditResult.Unsupported(result.reason)
                    }
                } else {
                    // Images and page markers are not raw text. Check the projection before editing.
                    if (single.semanticContent != paragraph + "\n") {
                        return BookContentProcessEngine.EditResult.Unsupported("Non-text paragraph")
                    }
                    paragraph.replaceRange(localRange.start, localRange.endExclusive, replacement)
                }
                val values = paragraphs.toMutableList().also { it[index] = updated }
                val output = values.joinToString("\n")
                val expected = source.semanticContent.replaceRange(range.start, range.endExclusive, replacement)
                val after = project(MixedContentSegments.paragraphs(output, indent, true)).semanticContent
                if (after != expected) return BookContentProcessEngine.EditResult.Unsupported("Edit changes chapter structure")
                return BookContentProcessEngine.EditResult.Applied(output)
            }
            origin = end
        }
        return BookContentProcessEngine.EditResult.Unsupported("Cross-paragraph or cross-channel selection")
    }
}
