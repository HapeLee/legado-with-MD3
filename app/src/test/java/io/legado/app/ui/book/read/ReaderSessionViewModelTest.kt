package io.legado.app.ui.book.read

import io.legado.app.feature.reader.core.model.ReaderPage
import io.legado.app.feature.reader.core.model.ReaderPageId
import io.legado.app.feature.reader.core.model.ReaderPageWindow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ReaderSessionViewModelTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun resumeInvalidatesAnUnchangedPageWithoutReplacingItsReadingState() = runTest {
        val viewModel = ReaderSessionViewModel()
        viewModel.onEntranceStateChanged(true)
        val page = page()
        val window = ReaderPageWindow(current = page)
        viewModel.submitPageWindow(window)
        val revisions = mutableListOf<Long>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { revisions += it.redrawRevision }
        }

        // Identical publication alone is conflated; resuming still needs a fresh draw.
        viewModel.submitPageWindow(window.copy())
        assertEquals(listOf(0L), revisions)
        viewModel.requestRedraw()
        assertEquals(listOf(0L, 1L), revisions)
        assertSame(page, viewModel.uiState.value.pageWindow.current)

        viewModel.submit(ReaderRenderUiState(pageWindow = window))
        assertEquals(listOf(0L, 1L), revisions)
        viewModel.requestRedraw()
        assertEquals(listOf(0L, 1L, 2L), revisions)
        collector.cancel()
    }

    @Test
    fun entranceCommitAndLaterSubmissionsPreserveTheResumeRedrawRevision() {
        val viewModel = ReaderSessionViewModel()
        val window = ReaderPageWindow(current = page())
        viewModel.submit(ReaderRenderUiState(pageWindow = window, paginationError = "pending"))
        viewModel.submitPageWindow(window)
        viewModel.requestRedraw()
        assertNull(viewModel.uiState.value.paginationError)
        assertEquals(1L, viewModel.uiState.value.redrawRevision)

        viewModel.onEntranceStateChanged(true)
        assertEquals("pending", viewModel.uiState.value.paginationError)
        assertEquals(1L, viewModel.uiState.value.redrawRevision)
        viewModel.submit(ReaderRenderUiState(pageWindow = window))
        assertNull(viewModel.uiState.value.paginationError)
        assertEquals(1L, viewModel.uiState.value.redrawRevision)
        assertSame(window, viewModel.uiState.value.pageWindow)
    }

    private fun page() = ReaderPage(
        id = ReaderPageId(chapterIndex = 1, pageIndex = 2),
        chapterTitle = "chapter", text = "content", widthPx = 100, heightPx = 200,
        contentTopPx = 0f, contentBottomPx = 200f, elements = emptyList(), revision = 1L,
    )

    @Test
    fun buffersRenderUpdatesUntilEntranceSettlesAndThenPublishesImmediately() {
        val viewModel = ReaderSessionViewModel()

        viewModel.submit(ReaderRenderUiState(paginationError = "prepared-before-enter"))
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

        viewModel.submit(ReaderRenderUiState(paginationError = "after-enter"))
        assertEquals("after-enter", viewModel.uiState.value.paginationError)

        viewModel.onEntranceStateChanged(false)
        viewModel.submit(ReaderRenderUiState(paginationError = "while-exiting"))
        assertEquals("after-enter", viewModel.uiState.value.paginationError)

        viewModel.onEntranceStateChanged(true)
        assertEquals("while-exiting", viewModel.uiState.value.paginationError)
    }
}
