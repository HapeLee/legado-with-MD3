package io.legado.app.feature.reader.core.navigation

import io.legado.app.feature.reader.core.model.ReaderPage
import io.legado.app.feature.reader.core.model.ReaderPageId
import io.legado.app.feature.reader.core.model.ReaderPageWindow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class ReaderRenderStateTest {
    @Test
    fun defaultsToAnEmptyWindowWithoutAnError() {
        val state = ReaderRenderState()

        assertNull(state.paginationError)
        assertEquals(ReaderPageWindow(), state.pageWindow)
    }

    @Test
    fun retainsThePreparedPageReferenceAndErrorAsOneImmutableSnapshot() {
        val page = ReaderPage(
            id = ReaderPageId(chapterIndex = 2, pageIndex = 3),
            chapterTitle = "chapter",
            text = "content",
            widthPx = 100,
            heightPx = 200,
            contentTopPx = 0f,
            contentBottomPx = 200f,
            elements = emptyList(),
            revision = 1L,
        )
        val state = ReaderRenderState(ReaderPageWindow(current = page), "failed")

        assertSame(page, state.pageWindow.current)
        assertEquals("failed", state.paginationError)
    }
}
