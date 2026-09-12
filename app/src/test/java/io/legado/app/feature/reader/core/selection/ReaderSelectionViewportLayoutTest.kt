package io.legado.app.feature.reader.core.selection

import io.legado.app.feature.reader.core.gesture.ReaderPageViewportLayout
import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderPage
import io.legado.app.feature.reader.core.model.ReaderPageId
import io.legado.app.feature.reader.core.model.ReaderPageWindow
import io.legado.app.feature.reader.core.model.ReaderRect
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderSelectionViewportLayoutTest {
    private val style = ReaderTextStyle(0xff000000.toInt(), 16f)

    @Test fun scrollLayoutHitsVisiblePreviousCurrentAndNextPagesInLocalCoordinates() {
        val previous = page(0, 0, 0)
        val current = page(0, 1, 1)
        val next = page(0, 2, 2)
        val layout = ReaderPageViewportLayout.scroll(
            ReaderPageWindow(previous, current, next),
            scrollOffsetPx = 20f,
        )

        assertEquals(previous.id, layout.pageAt(5f, 10f)?.page?.id)
        assertEquals(90f, layout.pageAt(5f, 10f)?.localY(10f))
        assertEquals(current.id, layout.pageAt(5f, 30f)?.page?.id)
        assertEquals(10f, layout.pageAt(5f, 30f)?.localY(30f))
        assertEquals(next.id, layout.pageAt(5f, 130f)?.page?.id)
        assertEquals(10f, layout.pageAt(5f, 130f)?.localY(130f))
        assertNull(layout.pageAt(101f, 30f))
    }

    @Test fun selectionBoundsAreTranslatedForEveryVisiblePage() {
        val current = page(0, 0, 0)
        val next = page(0, 1, 1)
        val layout = ReaderPageViewportLayout.scroll(
            ReaderPageWindow(current = current, next = next),
            scrollOffsetPx = -20f,
        )

        assertEquals(
            listOf(ReaderRect(0f, -20f, 10f, 0f), ReaderRect(0f, 80f, 10f, 100f)),
            layout.selectionBounds(ReaderSelection(0, 0, 1)).map { it.bounds },
        )
    }

    @Test
    fun scrollLayoutExposesNextPlusAtTheCumulativeStackOffset() {
        val current = page(0, 0, 0)
        val next = page(0, 1, 1)
        val nextPlus = page(0, 2, 2)
        val layout = ReaderPageViewportLayout.scroll(
            ReaderPageWindow(current = current, next = next, nextPlus = nextPlus),
            scrollOffsetPx = -20f,
        )

        val placement = layout.pageAt(5f, 190f)!!
        assertEquals(nextPlus.id, placement.page.id)
        assertEquals(10f, placement.localY(190f))
    }

    @Test
    fun scrollWindowSelectionTextIncludesVisibleNextPlusPage() {
        val current = page(0, 0, 0)
        val next = page(0, 1, 1)
        val nextPlus = page(0, 2, 2)
        val window = ReaderPageWindow(current = current, next = next, nextPlus = nextPlus)
        val selection = ReaderSelection(chapterIndex = 0, anchor = 2, focus = 2)

        assertEquals(
            "2",
            selection.selectedText(window.selectionPages()),
        )
    }

    @Test fun adjacentPageElementHitUsesItsLocalCoordinates() {
        val current = page(0, 0, 0)
        val next = page(0, 1, 1)
        val layout = ReaderPageViewportLayout.scroll(
            ReaderPageWindow(current = current, next = next),
            scrollOffsetPx = -20f,
        )

        val placement = layout.pageAt(5f, 90f)!!
        assertEquals(next.id, placement.page.id)
        assertEquals(
            next.elements.single(),
            placement.page.elementAt(5f, placement.localY(90f)),
        )
    }

    @Test fun pagedLayoutNeverExposesAdjacentPages() {
        val current = page(0, 1, 1)
        val layout = ReaderPageViewportLayout.paged(
            ReaderPageWindow(previous = page(0, 0, 0), current = current, next = page(0, 2, 2)),
        )

        assertEquals(listOf(current.id), layout.placements.map { it.page.id })
    }

    private fun page(chapter: Int, index: Int, position: Int) = ReaderPage(
        id = ReaderPageId(chapter, index),
        chapterTitle = "",
        text = position.toString(),
        widthPx = 100,
        heightPx = 100,
        contentTopPx = 0f,
        contentBottomPx = 100f,
        elements = listOf(
            ReaderElement.Text(
                bounds = ReaderRect(0f, 0f, 10f, 20f),
                baselinePx = 15f,
                value = position.toString(),
                style = style,
                selected = false,
                emphasized = false,
                chapterPosition = position,
            ),
        ),
        revision = index.toLong(),
    )
}
