package io.legado.app.ui.book.manga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class MangaPageLoadStateTest {
    private val page = MangaReaderItemUi.Page(
        key = "page", imageUrl = "url", bookUrl = "book", chapterIndex = 0, chapterCount = 1,
        pageIndex = 0, pageCount = 1, chapterName = "chapter",
    )

    @Test
    fun `retry ignores late start success and failure of previous request`() {
        val retry = page.copy(retryRevision = 1)
        for (event in listOf(
            MangaPageLoadState.Loading(100), MangaPageLoadState.Ready,
            MangaPageLoadState.Failed("old error")
        )) {
            assertSame(retry, retry.reduceImageLoad(page.requestId, event, force = true))
        }
        assertEquals(
            MangaPageLoadState.Ready,
            retry.reduceImageLoad(retry.requestId, MangaPageLoadState.Ready).loadState
        )
    }

    @Test
    fun `book switch cannot accept another book callback with same page key`() {
        val switched = page.copy(bookUrl = "another-book")
        assertSame(switched, switched.reduceImageLoad(page.requestId, MangaPageLoadState.Ready))
    }

    @Test
    fun `cached Ready is preserved but missing original can force Loading without erasing progress`() {
        val ready = page.reduceImageLoad(page.requestId, MangaPageLoadState.Ready)
        assertSame(ready, ready.reduceImageLoad(page.requestId, MangaPageLoadState.Loading()))
        assertEquals(
            MangaPageLoadState.Failed("decode failed"),
            ready.reduceImageLoad(
                page.requestId,
                MangaPageLoadState.Failed("decode failed")
            ).loadState
        )
        val fetching =
            ready.reduceImageLoad(page.requestId, MangaPageLoadState.Loading(50), force = true)
        assertEquals(MangaPageLoadState.Loading(50), fetching.loadState)
        assertEquals(
            MangaPageLoadState.Loading(50),
            fetching.reduceImageLoad(page.requestId, MangaPageLoadState.Loading()).loadState
        )
        assertEquals(
            MangaPageLoadState.Failed("current failure"),
            fetching.reduceImageLoad(
                page.requestId,
                MangaPageLoadState.Failed("current failure")
            ).loadState
        )
    }
}
