package io.legado.app.feature.reader.platform

import io.legado.app.feature.reader.core.semantic.ReaderSemanticLocation
import io.legado.app.feature.reader.core.semantic.SemanticRange
import org.jsoup.Jsoup

/** Transactional, conservative edits using the renderer's own temporary text-node projection. */
internal object ReaderHtmlTextEditor {
    sealed interface Result {
        data class Applied(val html: String) : Result
        data class Unsupported(val reason: String) : Result
    }

    fun replace(html: String, range: SemanticRange, replacement: String): Result {
        val doc = Jsoup.parseBodyFragment(html)
        doc.outputSettings().prettyPrint(false)
        val spans = mutableListOf<JsoupReaderHtmlParser.TextSpan>()
        val before = JsoupReaderHtmlParser.project(doc.body(), textSpans = spans).semantic
        if (!ReaderSemanticLocation.validateRange(before, range)) return Result.Unsupported("Invalid UTF-16 range")
        val affected = if (range.start == range.endExclusive) {
            listOfNotNull(spans.firstOrNull { range.start in it.start..it.start + it.value.length })
        } else spans.filter { it.start < range.endExclusive && it.start + it.value.length > range.start }
        if (affected.isEmpty()) return Result.Unsupported("No editable text")
        var covered = range.start
        for (span in affected) {
            // Whitespace normalization is not invertible. Never guess a raw-node offset.
            if (span.node.wholeText != span.value || span.start > covered) {
                return Result.Unsupported("Normalized whitespace or structural boundary")
            }
            covered = maxOf(covered, span.start + span.value.length)
        }
        if (covered < range.endExclusive) return Result.Unsupported("Image or paragraph boundary")
        affected.forEachIndexed { index, span ->
            val from = (range.start - span.start).coerceIn(0, span.value.length)
            val to = (range.endExclusive - span.start).coerceIn(from, span.value.length)
            span.node.text(span.value.replaceRange(from, to, if (index == 0) replacement else ""))
        }
        val output = doc.body().html()
        val expected = before.replaceRange(range.start, range.endExclusive, replacement)
        if (JsoupReaderHtmlParser.semanticText(output) != expected) return Result.Unsupported("Edit changes semantic structure")
        return Result.Applied(output)
    }
}
