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
    /**
     * focus 所在的章；[chapterIndex] 始终是 anchor 所在的章。滚动模式的连续堆叠允许选区
     * 跨到邻章页——旧 View 的 `ContentTextView.upSelectChars` 在滚动模式遍历 relativePage
     * 0..2，而 `TextPageFactory.nextPlusPage` 在章末会给出下一章首页，因此那时选区含邻章
     * 首页的文字；分页模式两边恒相等。
     */
    val focusChapterIndex: Int = chapterIndex,
) {
    // Title offsets and body offsets are independent. Document order puts the title first
    // and the body after it; the chapter is the outermost key.
    private val forward: Boolean
        get() = comparePosition(
            chapterIndex, anchorIsTitle, anchor,
            focusChapterIndex, focusIsTitle, focus,
        ) <= 0
    val startChapterIndex: Int get() = if (forward) chapterIndex else focusChapterIndex
    val endChapterIndex: Int get() = if (forward) focusChapterIndex else chapterIndex
    private val startIsTitle: Boolean get() = if (forward) anchorIsTitle else focusIsTitle
    private val endIsTitle: Boolean get() = if (forward) focusIsTitle else anchorIsTitle
    val start: Int get() = if (forward) anchor else focus
    val endInclusive: Int get() = if (forward) focus else anchor
    val includesTitle: Boolean get() = anchorIsTitle || focusIsTitle
    val bodyStart: Int? get() = when {
        anchorIsTitle && focusIsTitle -> null
        includesTitle -> 0
        else -> start
    }

    fun contains(element: ReaderElement.Text, pageChapterIndex: Int): Boolean =
        comparePosition(
            pageChapterIndex, element.emphasized, element.chapterPosition,
            startChapterIndex, startIsTitle, start,
        ) >= 0 && comparePosition(
            pageChapterIndex, element.emphasized, element.chapterPosition,
            endChapterIndex, endIsTitle, endInclusive,
        ) <= 0

    fun moveStart(
        position: Int,
        isTitle: Boolean = false,
        chapter: Int = startChapterIndex,
    ): ReaderSelection =
        if (forward) copy(anchor = position, anchorIsTitle = isTitle, chapterIndex = chapter)
        else copy(focus = position, focusIsTitle = isTitle, focusChapterIndex = chapter)

    fun moveEnd(
        position: Int,
        isTitle: Boolean = false,
        chapter: Int = endChapterIndex,
    ): ReaderSelection =
        if (forward) copy(focus = position, focusIsTitle = isTitle, focusChapterIndex = chapter)
        else copy(anchor = position, anchorIsTitle = isTitle, chapterIndex = chapter)

    fun visualStartEndpoint(): ReaderSelectionEndpoint =
        if (forward) ReaderSelectionEndpoint.ANCHOR else ReaderSelectionEndpoint.FOCUS

    fun visualEndEndpoint(): ReaderSelectionEndpoint =
        if (forward) ReaderSelectionEndpoint.FOCUS else ReaderSelectionEndpoint.ANCHOR

    fun moveEndpoint(
        endpoint: ReaderSelectionEndpoint,
        position: Int,
        isTitle: Boolean = false,
        chapter: Int = when (endpoint) {
            ReaderSelectionEndpoint.ANCHOR -> chapterIndex
            ReaderSelectionEndpoint.FOCUS -> focusChapterIndex
        },
    ): ReaderSelection = when (endpoint) {
        ReaderSelectionEndpoint.ANCHOR ->
            copy(anchor = position, anchorIsTitle = isTitle, chapterIndex = chapter)

        ReaderSelectionEndpoint.FOCUS ->
            copy(focus = position, focusIsTitle = isTitle, focusChapterIndex = chapter)
    }

    fun selectedText(page: ReaderPage): String {
        return selectedText(listOf(page))
    }

    /**
     * Collects a selection across every available page without duplicating page-boundary
     * glyphs. Pages from neighbouring chapters are included when the selection spans them.
     */
    fun selectedText(pages: List<ReaderPage>): String {
        val ordered = pages.asSequence()
            .flatMap { page ->
                page.elements.asSequence()
                    .filterIsInstance<ReaderElement.Text>()
                    .map { page.id.chapterIndex to it }
            }
            .filter { (chapter, text) -> contains(text, chapter) }
            .distinctBy { (chapter, text) ->
                Triple(chapter, text.emphasized, text.chapterPosition) to text.value
            }
            .sortedWith(documentOrderByChapter)
            .toList()
        return buildString {
            var previous: Pair<Int, ReaderElement.Text>? = null
            ordered.forEach { (chapter, text) ->
                previous?.let { (priorChapter, prior) ->
                    if (priorChapter != chapter ||
                        prior.emphasized != text.emphasized ||
                        (prior.paragraphIndex >= 0 && text.paragraphIndex >= 0 &&
                                prior.paragraphIndex != text.paragraphIndex)
                    ) append('\n')
                }
                append(text.value)
                previous = chapter to text
            }
        }
    }

    fun bounds(page: ReaderPage): List<ReaderRect> = page.elements
        .filterIsInstance<ReaderElement.Text>()
        .filter { contains(it, page.id.chapterIndex) }
        .sortedWith(documentOrder)
        .map(ReaderElement.Text::bounds)

    private companion object {
        val documentOrder = compareBy<ReaderElement.Text> { !it.emphasized }.thenBy { it.chapterPosition }
        val documentOrderByChapter = compareBy<Pair<Int, ReaderElement.Text>> { it.first }
            .thenBy { !it.second.emphasized }
            .thenBy { it.second.chapterPosition }

        fun comparePosition(
            leftChapter: Int,
            leftIsTitle: Boolean,
            left: Int,
            rightChapter: Int,
            rightIsTitle: Boolean,
            right: Int,
        ): Int {
            if (leftChapter != rightChapter) return leftChapter.compareTo(rightChapter)
            return if (leftIsTitle == rightIsTitle) left.compareTo(right)
            else if (leftIsTitle) -1 else 1
        }
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

    /**
     * [allowChapterCrossing] 只在滚动模式传 true：视口里堆叠的就是当前页与下一章首页
     * （旧 View 同样把这一页纳入选区分词，见 [ReaderSelection.focusChapterIndex]）。
     * 分页模式保持单章，与旧 View 的 `last = if (isScroll) 2 else 0` 一致。
     */
    fun extend(
        selection: ReaderSelection,
        page: ReaderPage,
        x: Float,
        y: Float,
        allowChapterCrossing: Boolean = false,
    ): ReaderSelection {
        if (!allowChapterCrossing && selection.chapterIndex != page.id.chapterIndex) return selection
        return (page.elementAt(x, y) as? ReaderElement.Text)?.let {
            selection.copy(
                focus = it.chapterPosition,
                focusIsTitle = it.emphasized,
                focusChapterIndex = page.id.chapterIndex,
            )
        } ?: selection
    }
}
