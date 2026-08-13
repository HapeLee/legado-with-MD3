package io.legado.app.data.repository.manga

import io.legado.app.domain.gateway.MangaReaderDataGateway
import io.legado.app.domain.model.manga.MangaBookState
import io.legado.app.domain.model.manga.MangaBookPresentation
import io.legado.app.domain.model.manga.MangaChapterContent
import io.legado.app.domain.model.manga.MangaChapterState
import io.legado.app.domain.model.manga.MangaPageContent
import io.legado.app.domain.model.manga.MangaProgressState
import io.legado.app.domain.model.manga.MangaSessionCommand
import io.legado.app.domain.model.manga.OpenedMangaBook
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultMangaReaderSessionTest {

    @Test
    fun `opening a new book cancels and rejects old chapter results`() = runTest {
        val gateway = FakeGateway()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val session = DefaultMangaReaderSession(gateway, dispatcher, dispatcher)

        session.execute(MangaSessionCommand.Open("book-a", true, false))
        advanceUntilIdle()
        session.execute(MangaSessionCommand.Open("book-b", true, false))
        advanceUntilIdle()

        gateway.complete("book-a", 0)
        advanceUntilIdle()
        assertEquals("book-b", session.state.value.book?.bookUrl)
        assertTrue(session.state.value.currentChapter is MangaChapterState.Loading)

        gateway.complete("book-b", 0)
        advanceUntilIdle()
        val chapter = session.state.value.currentChapter as MangaChapterState.Ready
        assertEquals("book-b/0", chapter.chapter.pages.single().imageUrl)
        session.close()
    }

    @Test
    fun `failed chapter can be retried without a leaked loading marker`() = runTest {
        val gateway = FakeGateway()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val session = DefaultMangaReaderSession(gateway, dispatcher, dispatcher)

        session.execute(MangaSessionCommand.Open("book-a", true, false))
        advanceUntilIdle()
        gateway.fail("book-a", 0)
        advanceUntilIdle()
        assertTrue(session.state.value.currentChapter is MangaChapterState.Failed)

        session.execute(MangaSessionCommand.RetryChapter(0))
        advanceUntilIdle()
        assertTrue(session.state.value.currentChapter is MangaChapterState.Loading)
        gateway.complete("book-a", 0)
        advanceUntilIdle()
        assertTrue(session.state.value.currentChapter is MangaChapterState.Ready)
        session.close()
    }

    @Test
    fun `closing session persists progress and ends reading lifecycle`() = runTest {
        val gateway = FakeGateway()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val session = DefaultMangaReaderSession(gateway, dispatcher, dispatcher)

        session.execute(MangaSessionCommand.Open("book-a", true, false))
        advanceUntilIdle()
        session.execute(MangaSessionCommand.Resume)
        session.execute(MangaSessionCommand.VisiblePageChanged(0, 7))
        advanceUntilIdle()
        session.close()
        advanceUntilIdle()

        assertEquals(Triple("book-a", 0, 7), gateway.persisted.single())
        assertEquals(listOf("book-a"), gateway.paused)
    }

    @Test
    fun `prefetch is bounded to the active session window`() = runTest {
        val gateway = FakeGateway()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val session = DefaultMangaReaderSession(gateway, dispatcher, dispatcher)

        session.execute(MangaSessionCommand.PrefetchCountChanged(2))
        session.execute(MangaSessionCommand.Open("book-a", true, false))
        advanceUntilIdle()

        assertEquals(listOf("book-a" to 2, "book-a" to 3), gateway.prefetched)
        session.close()
    }

    @Test
    fun `book presentation changes survive later chapter state updates`() = runTest {
        val gateway = FakeGateway()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val session = DefaultMangaReaderSession(gateway, dispatcher, dispatcher)

        session.execute(MangaSessionCommand.Open("book-a", true, false))
        advanceUntilIdle()
        gateway.presentation.value = MangaBookPresentation(scrollMode = 2, sidePaddingDp = null)
        gateway.complete("book-a", 0)
        advanceUntilIdle()

        assertEquals(2, session.state.value.book?.scrollMode)
        session.close()
    }

    @Test
    fun `promoting to a visible next chapter keeps current chapter data without reload`() = runTest {
        val gateway = FakeGateway()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val session = DefaultMangaReaderSession(gateway, dispatcher, dispatcher)

        session.execute(MangaSessionCommand.Open("book-a", true, false))
        advanceUntilIdle()
        gateway.complete("book-a", 0)
        advanceUntilIdle()
        session.execute(MangaSessionCommand.VisiblePageChanged(0, 0))
        advanceUntilIdle()
        gateway.complete("book-a", 1)
        advanceUntilIdle()
        assertEquals(0, session.state.value.chapterIndex)
        assertTrue(session.state.value.currentChapter is MangaChapterState.Ready)
        assertTrue(session.state.value.nextChapter is MangaChapterState.Ready)

        session.execute(MangaSessionCommand.PromoteVisibleChapter(1, 0))
        advanceUntilIdle()

        val state = session.state.value
        assertEquals(1, state.chapterIndex)
        assertEquals(0, state.pageIndex)
        // 已加载的章节原位保留，不清空重载
        assertEquals(0, (state.previousChapter as MangaChapterState.Ready).chapter.chapterIndex)
        assertEquals(1, (state.currentChapter as MangaChapterState.Ready).chapter.chapterIndex)
        // 只加载新的远端章节（chapter 2）
        assertEquals("book-a" to 2, gateway.loadedRequests.lastOrNull())
        session.close()
    }

    @Test
    fun `promoting to a visible previous chapter keeps current chapter data without reload`() = runTest {
        val gateway = FakeGateway()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val session = DefaultMangaReaderSession(gateway, dispatcher, dispatcher)

        session.execute(MangaSessionCommand.Open("book-a", true, false))
        advanceUntilIdle()
        session.execute(MangaSessionCommand.OpenChapter(2, 0))
        advanceUntilIdle()
        gateway.complete("book-a", 2)
        advanceUntilIdle()
        session.execute(MangaSessionCommand.VisiblePageChanged(2, 0))
        advanceUntilIdle()
        gateway.complete("book-a", 1)
        advanceUntilIdle()
        assertEquals(2, session.state.value.chapterIndex)
        assertTrue(session.state.value.previousChapter is MangaChapterState.Ready)

        session.execute(MangaSessionCommand.PromoteVisibleChapter(1, 0))
        advanceUntilIdle()

        val state = session.state.value
        assertEquals(1, state.chapterIndex)
        assertEquals(0, state.pageIndex)
        assertEquals(1, (state.currentChapter as MangaChapterState.Ready).chapter.chapterIndex)
        assertEquals(2, (state.nextChapter as MangaChapterState.Ready).chapter.chapterIndex)
        assertEquals("book-a" to 0, gateway.loadedRequests.lastOrNull())
        session.close()
    }

    @Test
    fun `promoting outside the chapter range is ignored`() = runTest {
        val gateway = FakeGateway()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val session = DefaultMangaReaderSession(gateway, dispatcher, dispatcher)

        session.execute(MangaSessionCommand.Open("book-a", true, false))
        advanceUntilIdle()
        val revision = session.state.value.revision

        session.execute(MangaSessionCommand.PromoteVisibleChapter(99, 0))
        advanceUntilIdle()
        assertEquals(revision, session.state.value.revision)
        session.close()
    }

    private class FakeGateway : MangaReaderDataGateway {
        val presentation = MutableStateFlow(MangaBookPresentation(null, null))
        private val requests = mutableMapOf<Pair<String, Int>, CompletableDeferred<MangaChapterContent>>()
        val persisted = mutableListOf<Triple<String, Int, Int>>()
        val paused = mutableListOf<String>()
        val prefetched = mutableListOf<Pair<String, Int>>()
        val loadedRequests = mutableListOf<Pair<String, Int>>()

        override fun observeBookPresentation(bookUrl: String) = presentation

        override suspend fun openBook(
            bookUrl: String?,
            inBookshelf: Boolean,
            chapterChanged: Boolean,
        ) = OpenedMangaBook(
            book = MangaBookState(
                bookUrl = requireNotNull(bookUrl),
                name = bookUrl,
                author = "author",
                coverUrl = null,
                customCoverUrl = null,
                sourceOrigin = "source",
                sourceName = "source",
                sourceType = 0,
                inBookshelf = inBookshelf,
                scrollMode = null,
                sidePaddingDp = null,
                chapterTitles = List(5) { "Chapter ${it + 1}" },
            ),
            chapterIndex = 0,
            pageIndex = 0,
            chapterCount = 5,
        )

        override suspend fun loadChapter(bookUrl: String, chapterIndex: Int): MangaChapterContent {
            loadedRequests += bookUrl to chapterIndex
            return requests.getOrPut(bookUrl to chapterIndex) { CompletableDeferred() }.await()
        }

        override suspend fun prefetchChapter(bookUrl: String, chapterIndex: Int) {
            prefetched += bookUrl to chapterIndex
        }

        fun complete(bookUrl: String, chapterIndex: Int) {
            deferred(bookUrl, chapterIndex).complete(chapter(bookUrl, chapterIndex))
        }

        fun fail(bookUrl: String, chapterIndex: Int) {
            deferred(bookUrl, chapterIndex).completeExceptionally(IllegalStateException("failed"))
            requests.remove(bookUrl to chapterIndex)
        }

        private fun deferred(bookUrl: String, chapterIndex: Int) =
            requests.getOrPut(bookUrl to chapterIndex) { CompletableDeferred() }

        private fun chapter(bookUrl: String, chapterIndex: Int) = MangaChapterContent(
            chapterIndex = chapterIndex,
            chapterTitle = "chapter $chapterIndex",
            chapterUrl = null,
            pages = listOf(MangaPageContent("$bookUrl/$chapterIndex", 0, 1)),
            isVolume = false,
        )

        override suspend fun persistProgress(bookUrl: String, chapterIndex: Int, pageIndex: Int) {
            persisted += Triple(bookUrl, chapterIndex, pageIndex)
        }

        override suspend fun applyProgress(bookUrl: String, progress: MangaProgressState) = Unit

        override suspend fun resume(bookUrl: String) = Unit

        override suspend fun pause(bookUrl: String, inBookshelf: Boolean) {
            paused += bookUrl
        }

        override suspend fun syncProgress(bookUrl: String): MangaProgressState? = null
    }
}
