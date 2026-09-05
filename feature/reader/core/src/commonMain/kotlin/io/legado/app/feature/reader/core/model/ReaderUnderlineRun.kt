package io.legado.app.feature.reader.core.model

import kotlin.math.abs

data class ReaderUnderlineRun(
    val bounds: ReaderRect,
    val underline: ReaderUnderline,
)

/** Coalesces adjacent styled text exactly once per visual line. */
fun ReaderPage.underlineRuns(): List<ReaderUnderlineRun> {
    val runs = mutableListOf<ReaderUnderlineRun>()
    var previousUnderlinedText: ReaderElement.Text? = null
    elements.filterIsInstance<ReaderElement.Text>().forEach { text ->
        val underline = text.style.underline
        if (underline == null) {
            previousUnderlinedText = null
            return@forEach
        }
        val previous = runs.lastOrNull()
        if (
            previous != null && previousUnderlinedText != null && previous.underline == underline &&
            abs(previous.bounds.top - text.bounds.top) < 0.5f &&
            abs(previous.bounds.bottom - text.bounds.bottom) < 0.5f &&
            // Glyph bounds intentionally exclude letter- and justification-spacing.  A
            // continuous rule on the same visual row must span those gaps, just as the old
            // TextLine renderer did, otherwise waves restart for every character.
            text.bounds.left >= previous.bounds.right
        ) {
            runs[runs.lastIndex] = previous.copy(
                bounds = previous.bounds.copy(right = text.bounds.right),
            )
        } else {
            runs += ReaderUnderlineRun(text.bounds, underline)
        }
        previousUnderlinedText = text
    }
    return runs
}
