package io.legado.app.ui.book.manga

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import coil3.ImageLoader
import io.legado.app.data.AppDatabase
import io.legado.app.data.repository.manga.MangaReaderActionRepository
import io.legado.app.domain.gateway.BookCacheDownloadGateway
import io.legado.app.domain.gateway.MangaReaderSession
import io.legado.app.domain.gateway.MangaReaderSessionFactory
import io.legado.app.domain.gateway.MangaSettingsGateway
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.gateway.ReadSettingsGateway
import io.legado.app.domain.model.manga.MangaBookState
import io.legado.app.domain.model.manga.MangaChapterContent
import io.legado.app.domain.model.manga.MangaChapterState
import io.legado.app.domain.model.manga.MangaLoadToken
import io.legado.app.domain.model.manga.MangaPageContent
import io.legado.app.domain.model.manga.MangaSessionCommand
import io.legado.app.domain.model.manga.MangaSessionId
import io.legado.app.domain.model.manga.MangaSessionState
import io.legado.app.domain.model.settings.MangaSettings
import io.legado.app.domain.model.settings.OtherSettings
import io.legado.app.domain.model.settings.ReadSettings
import io.legado.app.domain.usecase.CacheBookChaptersUseCase
import io.legado.app.model.cache.CacheDownloadRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class MangaReaderViewModelNavigationTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private lateinit var database: AppDatabase
    private lateinit var imageLoader: ImageLoader
    private lateinit var session: FakeSession

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val application = RuntimeEnvironment.getApplication()
        application.injectAsAppCtx()
        database = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java).build()
        imageLoader = ImageLoader.Builder(application).build()
        session = FakeSession()
    }

    @After
    fun tearDown() {
        store.clear()
        imageLoader.shutdown()
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `pending seek still targets the same page after 300 pages are prepended`() =
        runTest(dispatcher) {
            val reader = createReader()
            runCurrent()
            completeScroll(reader)
            runCurrent()
            reader.onIntent(MangaReaderIntent.SeekToPage(5))
            val before = reader.uiState.value
            val request = requireNotNull(before.scrollRequest)
            val targetKey = before.pages[request.itemIndex].key

            session.state.value = session.state.value.copy(previousChapter = ready(0, 300))
            runCurrent()

            val after = reader.uiState.value
            val rebased = requireNotNull(after.scrollRequest)
            assertEquals(targetKey, after.pages[rebased.itemIndex].key)
            assertEquals(305, rebased.itemIndex)
            assertNotEquals(request.id, rebased.id)
            completeScroll(reader)
            runCurrent()
            assertEquals(1, reader.uiState.value.chapterIndex)
            assertEquals(5, reader.uiState.value.currentPage)
            assertEquals(null, reader.uiState.value.scrollRequest)
        }

    @Test
    fun `pending seek restarts on append even when its target index is unchanged`() =
        runTest(dispatcher) {
            val reader = createReader()
            runCurrent()
            completeScroll(reader)
            runCurrent()
            reader.onIntent(MangaReaderIntent.SeekToPage(5))
            val request = requireNotNull(reader.uiState.value.scrollRequest)
            session.state.value = session.state.value.copy(nextChapter = ready(2, 20))
            runCurrent()
            val updated = requireNotNull(reader.uiState.value.scrollRequest)
            assertEquals(5, updated.itemIndex)
            assertNotEquals(request.id, updated.id)
        }

    @Test
    fun `pending seek follows page identity when its image URL changes`() = runTest(dispatcher) {
        val reader = createReader()
        runCurrent()
        completeScroll(reader)
        runCurrent()
        reader.onIntent(MangaReaderIntent.SeekToPage(5))
        val request = requireNotNull(reader.uiState.value.scrollRequest)
        val current = session.state.value.currentChapter as MangaChapterState.Ready
        session.state.value = session.state.value.copy(
            currentChapter = current.copy(
                chapter = current.chapter.copy(pages = current.chapter.pages.map {
                    if (it.pageIndex == 5) it.copy(imageUrl = "new-image:1:5") else it
                }),
            )
        )
        runCurrent()
        val state = reader.uiState.value
        val updated = requireNotNull(state.scrollRequest)
        assertEquals(5, updated.itemIndex)
        assertEquals(
            "new-image:1:5",
            (state.pages[updated.itemIndex] as MangaReaderItemUi.Page).imageUrl
        )
        assertNotEquals(request.id, updated.id)
    }

    @Test
    fun `removed seek target falls back to current page instead of another chapter`() =
        runTest(dispatcher) {
            val reader = createReader()
            runCurrent()
            completeScroll(reader)
            runCurrent()
            reader.onIntent(MangaReaderIntent.SeekToPage(56))
            session.state.value =
                session.state.value.copy(currentChapter = ready(1, 2), nextChapter = ready(2, 100))
            runCurrent()
            val state = reader.uiState.value
            val target =
                state.pages[requireNotNull(state.scrollRequest).itemIndex] as MangaReaderItemUi.Page
            assertEquals(1, target.chapterIndex)
            assertEquals(0, target.pageIndex)
        }

    @Test
    fun `nonstructural session updates do not restart pending seeks`() = runTest(dispatcher) {
        val reader = createReader()
        runCurrent()
        completeScroll(reader)
        runCurrent()
        reader.onIntent(MangaReaderIntent.SeekToPage(5))
        val request = reader.uiState.value.scrollRequest
        session.state.value = session.state.value.copy(resumed = true)
        runCurrent()
        assertEquals(request, reader.uiState.value.scrollRequest)
    }

    @Test
    fun `backward and forward chapter promotion preserve the boundary page`() =
        runTest(dispatcher) {
            val reader = createReader()
            runCurrent()
            completeScroll(reader)
            runCurrent()
            session.state.value = session.state.value.copy(previousChapter = ready(0, 300))
            runCurrent()
            reader.onIntent(
                MangaReaderIntent.VisibleItemChanged(
                    299,
                    currentChapterVisible = false,
                    navigationId = reader.uiState.value.navigationId,
                )
            )
            runCurrent()
            assertEquals(0, reader.uiState.value.chapterIndex)
            assertEquals(299, reader.uiState.value.currentPage)
            assertEquals(300, reader.uiState.value.pageCount)
            reader.onIntent(
                MangaReaderIntent.VisibleItemChanged(
                    300,
                    currentChapterVisible = false,
                    navigationId = reader.uiState.value.navigationId,
                )
            )
            runCurrent()
            assertEquals(1, reader.uiState.value.chapterIndex)
            assertEquals(0, reader.uiState.value.currentPage)
            assertEquals(57, reader.uiState.value.pageCount)
        }

    @Test
    fun `adjacent completion waits for idle and old navigation callbacks cannot promote a chapter`() =
        runTest(dispatcher) {
            val reader = createReader()
            runCurrent()
            completeScroll(reader)
            runCurrent()
            val oldNavigation = reader.uiState.value.navigationId
            reader.onIntent(MangaReaderIntent.PagerScrollChanged(true))
            session.state.value = session.state.value.copy(previousChapter = ready(0, 300))
            runCurrent()
            assertEquals(57, reader.uiState.value.pages.size)
            reader.onIntent(MangaReaderIntent.PagerScrollChanged(false))
            runCurrent()
            assertEquals(357, reader.uiState.value.pages.size)
            assertEquals(300, reader.uiState.value.currentItemIndex)
            session.commands.clear()
            reader.onIntent(
                MangaReaderIntent.VisibleItemChanged(
                    0, currentChapterVisible = false, navigationId = oldNavigation,
                )
            )
            assertTrue(session.commands.isEmpty())
            assertEquals(1, reader.uiState.value.chapterIndex)
            assertEquals(0, reader.uiState.value.currentPage)
            assertFalse(reader.uiState.value.isChapterLoading)
        }

    @Test
    fun `open relative previous chapter lands on the last page of the chapter`() =
        runTest(dispatcher) {
            val reader = createReader()
            runCurrent()
            completeScroll(reader)
            runCurrent()
            session.state.value = session.state.value.copy(previousChapter = ready(0, 300))
            runCurrent()
            reader.onIntent(MangaReaderIntent.PreviousChapter)
            runCurrent()
            val openCmd =
                session.commands.filterIsInstance<MangaSessionCommand.OpenChapter>().lastOrNull()
            org.junit.Assert.assertNotNull(openCmd)
            assertEquals(0, openCmd?.chapterIndex)
            assertEquals(299, openCmd?.pageIndex)
        }

    private fun completeScroll(reader: MangaReaderViewModel) {
        val state = reader.uiState.value
        val index = requireNotNull(state.scrollRequest).itemIndex
        reader.onIntent(
            MangaReaderIntent.VisibleItemChanged(
                itemIndex = index, currentChapterVisible = true, navigationId = state.navigationId,
            )
        )
    }

    private fun createReader(): MangaReaderViewModel {
        val manga = object : MangaSettingsGateway {
            override val settings = MutableStateFlow(MangaSettings(hideTitle = true))
            override val currentSettings get() = settings.value
            override suspend fun update(transform: (MangaSettings) -> MangaSettings) {
                settings.value = transform(settings.value)
            }
        }
        val other = object : OtherSettingsGateway {
            override val settings = MutableStateFlow(OtherSettings())
            override val currentSettings get() = settings.value
            override suspend fun update(transform: (OtherSettings) -> OtherSettings) {
                settings.value = transform(settings.value)
            }
        }
        val read = object : ReadSettingsGateway {
            override val settings = MutableStateFlow(ReadSettings())
            override val currentSettings get() = settings.value
            override suspend fun update(transform: (ReadSettings) -> ReadSettings) {
                settings.value = transform(settings.value)
            }
        }
        val downloads = object : BookCacheDownloadGateway {
            override suspend fun start(request: CacheDownloadRequest): Unit =
                error("Unexpected download")

            override suspend fun start(requests: List<CacheDownloadRequest>): Unit =
                error("Unexpected download")

            override suspend fun start(bookUrl: String, chapterIndices: List<Int>): Unit =
                error("Unexpected download")

            override suspend fun start(bookUrl: String, startIndex: Int, endIndex: Int): Unit =
                error("Unexpected download")
        }
        return MangaReaderViewModel(
            manga, other, read,
            MangaReaderActionRepository(
                RuntimeEnvironment.getApplication(),
                database,
                imageLoader,
                other,
                read
            ),
            CacheBookChaptersUseCase(downloads), MangaReaderSessionFactory { session },
        ).also { store.put("reader", it) }
    }

    private class FakeSession : MangaReaderSession {
        override val state = MutableStateFlow(
            MangaSessionState(
                sessionId = MangaSessionId("test"),
                book = MangaBookState(
                    bookUrl = "book", name = "manga", author = "author", coverUrl = null,
                    customCoverUrl = null, sourceOrigin = null, sourceName = "", sourceType = null,
                    inBookshelf = true, scrollMode = null, sidePaddingDp = null, isLocal = true,
                    chapterTitles = listOf("previous", "current", "next"),
                ),
                chapterIndex = 1, chapterCount = 3, currentChapter = ready(1, 57),
            )
        )
        override val events = emptyFlow<io.legado.app.domain.model.manga.MangaSessionEvent>()
        val commands = mutableListOf<MangaSessionCommand>()
        override suspend fun execute(command: MangaSessionCommand) {
            commands += command
            if (command is MangaSessionCommand.PromoteVisibleChapter) {
                val old = state.value
                val forward = command.chapterIndex > old.chapterIndex
                val target = if (forward) old.nextChapter else old.previousChapter
                check(target is MangaChapterState.Ready)
                state.value = old.copy(
                    revision = old.revision + 1,
                    chapterIndex = command.chapterIndex, pageIndex = command.pageIndex,
                    currentChapter = target,
                    previousChapter = if (forward) old.currentChapter else MangaChapterState.Empty,
                    nextChapter = if (forward) MangaChapterState.Empty else old.currentChapter,
                )
            }
            if (command is MangaSessionCommand.VisiblePageChanged && command.chapterIndex == state.value.chapterIndex) {
                state.value = state.value.copy(pageIndex = command.pageIndex)
            }
        }

        override fun close() = Unit
    }

    private companion object {
        fun ready(index: Int, count: Int) = MangaChapterState.Ready(
            MangaLoadToken(MangaSessionId("test"), 0, "book", index),
            MangaChapterContent(
                chapterIndex = index, chapterTitle = "chapter $index", chapterUrl = null,
                pages = (0 until count).map { MangaPageContent("image:$index:$it", it, count) },
                isVolume = false, chapterSubject = "chapter-$index",
            ),
        )
    }
}
