package io.legado.app.feature.reader.core.selection

import androidx.compose.runtime.Stable
import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderPage
import io.legado.app.feature.reader.core.model.ReaderPageWindow
import io.legado.app.feature.reader.core.model.ReaderRect
import java.text.BreakIterator
import java.util.Locale

enum class ReaderSelectionEndpoint {
    ANCHOR,
    FOCUS,
}

fun ReaderPageWindow.selectionPages(): List<ReaderPage> =
    listOfNotNull(previous, current, next, nextPlus)

@Stable
data class ReaderSelection(
    val chapterIndex: Int,
    val anchor: Int,
    val focus: Int,
    val anchorIsTitle: Boolean = false,
    val focusIsTitle: Boolean = anchorIsTitle,
) {
    // Title offsets and body offsets are independent. Document order puts the title first.
    private val forward: Boolean get() = comparePosition(anchor, anchorIsTitle, focus, focusIsTitle) <= 0
    val start: Int get() = if (forward) anchor else focus
    val endInclusive: Int get() = if (forward) focus else anchor
    val includesTitle: Boolean get() = anchorIsTitle || focusIsTitle
    val bodyStart: Int? get() = when {
        anchorIsTitle && focusIsTitle -> null
        includesTitle -> 0
        else -> start
    }

    fun contains(element: ReaderElement.Text): Boolean =
        comparePosition(element.chapterPosition, element.emphasized, start, if (forward) anchorIsTitle else focusIsTitle) >= 0 &&
            comparePosition(element.chapterPosition, element.emphasized, endInclusive, if (forward) focusIsTitle else anchorIsTitle) <= 0

    fun moveStart(position: Int, isTitle: Boolean = false): ReaderSelection =
        if (forward) copy(anchor = position, anchorIsTitle = isTitle)
        else copy(focus = position, focusIsTitle = isTitle)

    fun moveEnd(position: Int, isTitle: Boolean = false): ReaderSelection =
        if (forward) copy(focus = position, focusIsTitle = isTitle)
        else copy(anchor = position, anchorIsTitle = isTitle)

    fun visualStartEndpoint(): ReaderSelectionEndpoint =
        if (forward) ReaderSelectionEndpoint.ANCHOR else ReaderSelectionEndpoint.FOCUS

    fun visualEndEndpoint(): ReaderSelectionEndpoint =
        if (forward) ReaderSelectionEndpoint.FOCUS else ReaderSelectionEndpoint.ANCHOR

    fun moveEndpoint(
        endpoint: ReaderSelectionEndpoint,
        position: Int,
        isTitle: Boolean = false,
    ): ReaderSelection = when (endpoint) {
        ReaderSelectionEndpoint.ANCHOR -> copy(anchor = position, anchorIsTitle = isTitle)
        ReaderSelectionEndpoint.FOCUS -> copy(focus = position, focusIsTitle = isTitle)
    }

    fun selectedText(page: ReaderPage): String {
        return selectedText(listOf(page))
    }

    /** Collects a selection across every available page without duplicating page-boundary glyphs. */
    fun selectedText(pages: List<ReaderPage>): String {
        val elements = pages.asSequence()
            .filter { it.id.chapterIndex == chapterIndex }
            .flatMap { it.elements.asSequence().filterIsInstance<ReaderElement.Text>() }
            .filter(::contains)
            .distinctBy { Triple(it.emphasized, it.chapterPosition, it.value) }
            .sortedWith(documentOrder)
            .toList()
        return buildString {
            var previous: ReaderElement.Text? = null
            elements.forEach { element ->
                previous?.let { prior ->
                    if (prior.emphasized != element.emphasized ||
                        (prior.paragraphIndex >= 0 && element.paragraphIndex >= 0 &&
                            prior.paragraphIndex != element.paragraphIndex)
                    ) append('\n')
                }
                append(element.value)
                previous = element
            }
        }
    }

    fun bounds(page: ReaderPage): List<ReaderRect> = if (page.id.chapterIndex != chapterIndex) emptyList() else page.elements
        .filterIsInstance<ReaderElement.Text>()
        .filter(::contains)
        .sortedWith(documentOrder)
        .map(ReaderElement.Text::bounds)

    private companion object {
        val documentOrder = compareBy<ReaderElement.Text> { !it.emphasized }.thenBy { it.chapterPosition }

        fun comparePosition(left: Int, leftIsTitle: Boolean, right: Int, rightIsTitle: Boolean): Int =
            if (leftIsTitle == rightIsTitle) left.compareTo(right)
            else if (leftIsTitle) -1 else 1
    }
}

object ReaderSelectionPolicy {
    fun start(page: ReaderPage, x: Float, y: Float): ReaderSelection? =
        (page.elementAt(x, y) as? ReaderElement.Text)?.let {
            ReaderSelection(page.id.chapterIndex, it.chapterPosition, it.chapterPosition, it.emphasized)
        }

    /**
     * Selection handles hang below the text row, so a handle drag often moves through the
     * leading where [ReaderPage.elementAt] misses. Snap a miss to the nearest row by vertical
     * distance, then to the glyph closest to the finger's x within that row.
     */
    fun snapToText(page: ReaderPage, x: Float, y: Float): ReaderElement.Text? {
        (page.elementAt(x, y) as? ReaderElement.Text)?.let { return it }
        val textElements = page.elements.filterIsInstance<ReaderElement.Text>()
        if (textElements.isEmpty()) return null
        fun verticalDistance(bounds: ReaderRect): Float =
            (y - bounds.bottom).coerceAtLeast(0f).coerceAtLeast(bounds.top - y)
        val nearest = textElements.minByOrNull { verticalDistance(it.bounds) } ?: return null
        if (verticalDistance(nearest.bounds) > nearest.bounds.height) return null
        return textElements
            .filter { it.bounds.bottom > nearest.bounds.top && it.bounds.top < nearest.bounds.bottom }
            .minByOrNull { element ->
                val bounds = element.bounds
                when {
                    x < bounds.left -> bounds.left - x
                    x > bounds.right -> x - bounds.right
                    else -> 0f
                }
            }
    }

    /** Matches the View reader's long-press behavior: select one word in the hit paragraph. */
    fun startWord(
        page: ReaderPage,
        x: Float,
        y: Float,
        locale: Locale = Locale.getDefault(),
    ): ReaderSelection? {
        // Glyph bounds intentionally omit letter- and justification-spacing. Long presses in
        // those visual gaps should start selection just like handle drags do.
        val hit = snapToText(page, x, y) ?: return null
        val paragraph = page.elements.filterIsInstance<ReaderElement.Text>()
            .filter {
                it.emphasized == hit.emphasized &&
                    (hit.paragraphIndex < 0 || it.paragraphIndex == hit.paragraphIndex)
            }
            .sortedBy(ReaderElement.Text::chapterPosition)
        val hitIndex = paragraph.indexOf(hit)
        if (hitIndex < 0) return null

        val text = paragraph.joinToString(separator = "", transform = ReaderElement.Text::value)
        val hitOffset = paragraph.take(hitIndex).sumOf { it.value.length }
        val boundary = BreakIterator.getWordInstance(locale).apply { setText(text) }
        var start = boundary.first()
        var end = boundary.next()
        while (end != BreakIterator.DONE && hitOffset !in start until end) {
            start = end
            end = boundary.next()
        }
        if (end == BreakIterator.DONE) {
            return ReaderSelection(page.id.chapterIndex, hit.chapterPosition, hit.chapterPosition, hit.emphasized)
        }

        var offset = 0
        var first: ReaderElement.Text? = null
        var last: ReaderElement.Text? = null
        paragraph.forEach { element ->
            val elementEnd = offset + element.value.length
            if (offset < end && elementEnd > start) {
                if (first == null) first = element
                last = element
            }
            offset = elementEnd
        }
        return ReaderSelection(
            chapterIndex = page.id.chapterIndex,
            anchor = first?.chapterPosition ?: hit.chapterPosition,
            focus = last?.chapterPosition ?: hit.chapterPosition,
            anchorIsTitle = hit.emphasized,
        )
    }

    fun extend(selection: ReaderSelection, page: ReaderPage, x: Float, y: Float): ReaderSelection {
        if (selection.chapterIndex != page.id.chapterIndex) return selection
        return (page.elementAt(x, y) as? ReaderElement.Text)?.let {
            selection.copy(focus = it.chapterPosition, focusIsTitle = it.emphasized)
        } ?: selection
    }
}
