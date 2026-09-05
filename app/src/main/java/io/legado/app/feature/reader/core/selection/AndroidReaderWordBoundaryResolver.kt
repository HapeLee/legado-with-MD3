package io.legado.app.feature.reader.core.selection

import java.text.BreakIterator
import java.util.Locale

/** Preserves the Android reader's existing locale-aware BreakIterator behavior. */
object AndroidReaderWordBoundaryResolver : ReaderWordBoundaryResolver {
    override fun rangeAt(text: String, offset: Int): ReaderWordBoundaryResult {
        if (offset !in text.indices) return ReaderWordBoundaryResult.Unsupported
        val boundary = BreakIterator.getWordInstance(Locale.getDefault()).apply { setText(text) }
        var start = boundary.first()
        var end = boundary.next()
        while (end != BreakIterator.DONE && offset !in start until end) {
            start = end
            end = boundary.next()
        }
        return if (end == BreakIterator.DONE) {
            ReaderWordBoundaryResult.Unsupported
        } else {
            ReaderWordBoundaryResult.Resolved(ReaderWordRange(start, end))
        }
    }
}
