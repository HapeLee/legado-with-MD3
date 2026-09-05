package io.legado.app.ui.book.read

import io.legado.app.feature.reader.core.model.ReaderPage
import io.legado.app.feature.reader.core.model.ReaderPageId
import io.legado.app.feature.reader.core.model.ReaderPageWindow
import io.legado.app.feature.reader.core.navigation.ReaderRenderState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ReaderSessionViewModelTest {

    @Test
    fun buffersRenderUpdatesUntilEntranceSettlesAndThenPublishesImmediately() {
        val viewModel = ReaderSessionViewModel()

        viewModel.submit(ReaderRenderUiState(renderState = ReaderRenderState(paginationError = "prepared-before-enter")))
        assertNull(viewModel.uiState.value.paginationError)

        viewModel.submitBackground(ReaderBackgroundState(meanColorArgb = 0x123456, revision = 1L))
        assertEquals(1L, viewModel.uiState.value.background.revision)
        assertNull(viewModel.uiState.value.paginationError)

        val preparedPage = ReaderPage(
            id = ReaderPageId(chapterIndex = 1, pageIndex = 2),
            chapterTitle = "chapter",
            text = "content",
            widthPx = 100,
            heightPx = 200,
            contentTopPx = 0f,
            contentBottomPx = 200f,
            elements = emptyList(),
            revision = 1L,
        )
        viewModel.submitPageWindow(ReaderPageWindow(current = preparedPage))
        assertSame(preparedPage, viewModel.uiState.value.pageWindow.current)
        assertNull(viewModel.uiState.value.paginationError)

        viewModel.onEntranceStateChanged(true)
        assertEquals("prepared-before-enter", viewModel.uiState.value.paginationError)
        assertEquals(1L, viewModel.uiState.value.background.revision)

        viewModel.submit(ReaderRenderUiState(renderState = ReaderRenderState(paginationError = "after-enter")))
        assertEquals("after-enter", viewModel.uiState.value.paginationError)

        viewModel.onEntranceStateChanged(false)
        viewModel.submit(ReaderRenderUiState(renderState = ReaderRenderState(paginationError = "while-exiting")))
        assertEquals("after-enter", viewModel.uiState.value.paginationError)

        viewModel.onEntranceStateChanged(true)
        assertEquals("while-exiting", viewModel.uiState.value.paginationError)
    }
}
