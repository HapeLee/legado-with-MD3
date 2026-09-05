package io.legado.app.feature.reader.core.gesture

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderTapActionTest {
    private val grid = ReaderTapActionGrid(
        ReaderTapAction.PREVIOUS_CHAPTER,
        ReaderTapAction.READ_ALOUD_PREVIOUS_PARAGRAPH,
        ReaderTapAction.NEXT_CHAPTER,
        ReaderTapAction.PREVIOUS_PAGE,
        ReaderTapAction.MENU,
        ReaderTapAction.NEXT_PAGE,
        ReaderTapAction.OPEN_CONTENT_EDIT,
        ReaderTapAction.ADD_BOOKMARK,
        ReaderTapAction.OPEN_SEARCH,
    )

    @Test
    fun `nine regions resolve independently using legacy 33 and 66 percent boundaries`() {
        val expected = listOf(
            ReaderTapAction.PREVIOUS_CHAPTER,
            ReaderTapAction.READ_ALOUD_PREVIOUS_PARAGRAPH,
            ReaderTapAction.NEXT_CHAPTER,
            ReaderTapAction.PREVIOUS_PAGE,
            ReaderTapAction.MENU,
            ReaderTapAction.NEXT_PAGE,
            ReaderTapAction.OPEN_CONTENT_EDIT,
            ReaderTapAction.ADD_BOOKMARK,
            ReaderTapAction.OPEN_SEARCH,
        )
        val points = listOf(10f to 10f, 50f to 10f, 90f to 10f, 10f to 50f, 50f to 50f,
            90f to 50f, 10f to 90f, 50f to 90f, 90f to 90f)

        assertEquals(expected, points.map { (x, y) -> grid.actionAt(x, y, 100f, 100f) })
        assertEquals(ReaderTapAction.MENU, grid.actionAt(33f, 33f, 100f, 100f))
        assertEquals(ReaderTapAction.OPEN_SEARCH, grid.actionAt(66f, 66f, 100f, 100f))
    }

    @Test
    fun `legacy values preserve every configurable action and reject invalid input`() {
        (-1..13).forEach { value ->
            assertEquals(value, ReaderTapAction.fromLegacyValue(value).legacyValue)
        }
        assertEquals(ReaderTapAction.NONE, ReaderTapAction.fromLegacyValue(99))
        assertEquals(ReaderTapAction.NONE, grid.actionAt(-1f, 50f, 100f, 100f))
        assertEquals(ReaderTapAction.NONE, grid.actionAt(50f, 50f, 0f, 100f))
    }

    @Test
    fun `settings adapter keeps the supplied cell order`() {
        val mapped = ReaderTapActionGrid.fromLegacyValues(2, 2, 1, 2, 0, 1, 2, 1, 13)

        assertEquals(ReaderTapAction.PREVIOUS_PAGE, mapped.actionAt(10f, 10f, 90f, 90f))
        assertEquals(ReaderTapAction.MENU, mapped.actionAt(45f, 45f, 90f, 90f))
        assertEquals(ReaderTapAction.TOGGLE_READ_ALOUD_PAUSE, mapped.actionAt(80f, 80f, 90f, 90f))
    }
}
