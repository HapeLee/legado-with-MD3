package io.legado.app.feature.reader.core.selection

import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderPage
import io.legado.app.feature.reader.core.model.ReaderPageId
import io.legado.app.feature.reader.core.model.ReaderRect
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderSelectionTest {
    private val style = ReaderTextStyle(0, 16f)
    private val page = ReaderPage(
        ReaderPageId(0, 0), "", "甲乙丙", 100, 100, 0f, 20f,
        listOf(
            ReaderElement.Text(ReaderRect(0f, 0f, 10f, 20f), 15f, "甲", style, false, false, chapterPosition = 0),
            ReaderElement.Text(ReaderRect(10f, 0f, 20f, 20f), 15f, "乙", style, false, false, chapterPosition = 1),
            ReaderElement.Text(ReaderRect(20f, 0f, 30f, 20f), 15f, "丙", style, false, false, chapterPosition = 2),
        ), 1L,
    )

    @Test fun reverseSelectionNormalizesAndPreservesTextOrder() {
        val selection = ReaderSelection(0, 2, 0)
        assertEquals(0, selection.start)
        assertEquals(2, selection.endInclusive)
        assertEquals("甲乙丙", selection.selectedText(page))
    }

    @Test fun hitTestStartsAndExtendsSelection() {
        val started = ReaderSelectionPolicy.start(page, 5f, 10f)!!
        val extended = ReaderSelectionPolicy.extend(started, page, 25f, 10f)
        assertEquals("甲乙丙", extended.selectedText(page))
    }

    @Test fun snapToTextKeepsDirectHits() {
        assertEquals(1, ReaderSelectionPolicy.snapToText(page, 15f, 10f)!!.chapterPosition)
    }

    @Test fun snapToTextSnapsHandleDragsBelowTheRowToTheNearestGlyph() {
        assertEquals(0, ReaderSelectionPolicy.snapToText(page, 5f, 28f)!!.chapterPosition)
    }

    @Test fun snapToTextSnapsPastTheRowEdgeToTheNearestGlyph() {
        assertEquals(2, ReaderSelectionPolicy.snapToText(page, 35f, 28f)!!.chapterPosition)
    }

    @Test fun snapToTextIgnoresTouchesFarFromAnyText() {
        assertNull(ReaderSelectionPolicy.snapToText(page, 5f, 60f))
    }

    @Test fun longPressSelectsTheWholeWordAcrossVisualLines() {
        val values = listOf("read", "er", " ", "canvas")
        var position = 0
        val elements = values.mapIndexed { index, value ->
            ReaderElement.Text(
                bounds = ReaderRect(
                    if (index == 1) 0f else index * 20f,
                    if (index == 1) 20f else 0f,
                    if (index == 1) 20f else index * 20f + 20f,
                    if (index == 1) 40f else 20f,
                ),
                baselinePx = if (index == 1) 35f else 15f,
                value = value,
                style = style,
                selected = false,
                emphasized = false,
                chapterPosition = position.also { position += value.length },
                paragraphIndex = 0,
            )
        }
        val wrapped = page.copy(text = values.joinToString(""), elements = elements)

        val selection = ReaderSelectionPolicy.startWord(wrapped, 5f, 30f)!!

        assertEquals("reader", selection.selectedText(wrapped))
        assertEquals(0, selection.start)
        assertEquals(4, selection.endInclusive)
    }

    @Test fun longPressKeepsWordSelectionInsideTheHitParagraph() {
        val first = page.elements.filterIsInstance<ReaderElement.Text>().map {
            it.copy(paragraphIndex = 0)
        }
        val second = listOf(
            ReaderElement.Text(
                ReaderRect(0f, 30f, 20f, 50f), 45f, "word", style, false, false,
                chapterPosition = 4, paragraphIndex = 1,
            ),
        )
        val paragraphs = page.copy(elements = first + second)

        val selection = ReaderSelectionPolicy.startWord(paragraphs, 5f, 40f)!!

        assertEquals("word", selection.selectedText(paragraphs))
    }

    @Test
    fun longPressSnapsAcrossLetterSpacingGaps() {
        val spaced = page.copy(
            elements = listOf(
                ReaderElement.Text(
                    ReaderRect(0f, 0f, 10f, 20f), 15f, "甲", style, false, false,
                    chapterPosition = 0,
                ),
                ReaderElement.Text(
                    ReaderRect(14f, 0f, 24f, 20f), 15f, "乙", style, false, false,
                    chapterPosition = 1,
                ),
            )
        )

        val selection = ReaderSelectionPolicy.startWord(spaced, 12f, 10f)

        assertEquals(0, selection?.anchor)
        assertEquals("甲乙", selection?.selectedText(spaced))
    }

    @Test fun handlesKeepSemanticStartWhenSelectionIsReversed() {
        val reversed = ReaderSelection(0, 2, 0)
        assertEquals(1, reversed.moveStart(1).start)
        assertEquals(1, reversed.moveEnd(1).endInclusive)
    }

    @Test fun draggedEndpointIdentityStaysStableAfterHandlesCross() {
        val selection = ReaderSelection(0, 0, 2)
        val draggedEndpoint = selection.visualStartEndpoint()

        val crossed = selection.moveEndpoint(draggedEndpoint, 3)
        val continued = crossed.moveEndpoint(draggedEndpoint, 4)

        assertEquals(2, continued.start)
        assertEquals(4, continued.endInclusive)
        assertEquals(4, continued.anchor)
        assertEquals(2, continued.focus)
    }

    private val titledPage = page.copy(elements = listOf(
        ReaderElement.Text(ReaderRect(0f, 30f, 10f, 50f), 45f, "题", style, false, true, chapterPosition = 0),
    ) + page.elements)

    @Test fun bodySelectionDoesNotIncludeTitleWithTheSameOffset() {
        val selection = ReaderSelectionPolicy.start(titledPage, 5f, 10f)!!
        assertEquals("甲", selection.selectedText(titledPage))
        assertEquals(1, selection.bounds(titledPage).size)
    }

    @Test fun titleSelectionDoesNotIncludeBodyWithTheSameOffset() {
        val selection = ReaderSelectionPolicy.start(titledPage, 5f, 40f)!!
        assertEquals("题", selection.selectedText(titledPage))
        assertEquals(1, selection.bounds(titledPage).size)
    }

    @Test fun crossTitleAndBodySelectionPreservesDocumentOrderInBothDirections() {
        val title = ReaderSelectionPolicy.start(titledPage, 5f, 40f)!!
        val forward = ReaderSelectionPolicy.extend(title, titledPage, 15f, 10f)
        val body = ReaderSelectionPolicy.start(titledPage, 15f, 10f)!!
        val backward = ReaderSelectionPolicy.extend(body, titledPage, 5f, 40f)
        assertEquals("题\n甲乙", forward.selectedText(titledPage))
        assertEquals(forward.selectedText(titledPage), backward.selectedText(titledPage))
    }

    @Test fun draggingIntoAnotherChapterDoesNotChangeSelection() {
        val selection = ReaderSelection(0, 0, 0)
        val otherChapter = page.copy(id = ReaderPageId(1, 0))
        assertEquals(selection, ReaderSelectionPolicy.extend(selection, otherChapter, 25f, 10f))
    }

    @Test fun handlesCanCrossTheTitleBodyBoundaryWithoutConfusingOffsets() {
        val title = ReaderSelectionPolicy.start(titledPage, 5f, 40f)!!
        assertNull(title.bodyStart)
        val forward = title.moveEnd(1)
        assertEquals("题\n甲乙", forward.selectedText(titledPage))
        assertEquals(0, forward.bodyStart)
        assertEquals("甲乙", forward.moveStart(0).selectedText(titledPage))
        val backward = ReaderSelection(0, 1, 0, focusIsTitle = true)
        assertEquals("甲乙", backward.moveStart(0).selectedText(titledPage))
        assertEquals("题", backward.moveEnd(0, isTitle = true).selectedText(titledPage))
    }

    @Test fun selectionBoundsFollowDocumentOrderInsteadOfPhysicalColumnHeight() {
        val shuffled = page.copy(elements = page.elements.reversed())
        val selection = ReaderSelection(0, 2, 0)
        assertEquals("甲乙丙", selection.selectedText(shuffled))
        assertEquals(page.elements.map { it.bounds }, selection.bounds(shuffled))
    }

    @Test fun copyingPreservesParagraphBreaksButNotVisualLineWraps() {
        val elements = page.elements.filterIsInstance<ReaderElement.Text>().mapIndexed { index, text ->
            text.copy(
                paragraphIndex = if (index < 2) 0 else 1,
                chapterPosition = if (index < 2) index else 3,
                bounds = ReaderRect(0f, index * 20f, 10f, (index + 1) * 20f),
            )
        }
        val paragraphs = page.copy(elements = elements)
        assertEquals("甲乙\n丙", ReaderSelection(0, 0, 3).selectedText(paragraphs))
        assertEquals("乙\n丙", ReaderSelection(0, 3, 1).selectedText(paragraphs))
        assertEquals("甲乙", ReaderSelection(0, 0, 1).selectedText(paragraphs))
    }

    @Test fun copyingAcrossPagesUsesDocumentOrderAndRemovesBoundaryDuplicates() {
        val first = page.copy(
            elements = page.elements.take(2),
            id = ReaderPageId(0, 0),
        )
        val second = page.copy(
            elements = listOf(
                page.elements[1],
                page.elements[2],
            ),
            id = ReaderPageId(0, 1),
        )

        assertEquals("甲乙丙", ReaderSelection(0, 0, 2).selectedText(listOf(second, first)))
    }
}
