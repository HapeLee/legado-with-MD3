package io.legado.app.ui.book.video

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.media3.common.Player
import io.legado.app.constant.BookSourceType
import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.rule.BookInfoRule
import io.legado.app.data.entities.rule.ContentRule
import io.legado.app.data.entities.rule.TocRule
import io.legado.app.data.repository.BookRepository
import io.legado.app.data.repository.BookSourceRepository
import io.legado.app.data.repository.OtherSettingsRepository
import io.legado.app.data.repository.ReadSettingsRepository
import io.legado.app.data.repository.SettingsRepository
import io.legado.app.data.repository.VideoContentRepository
import io.legado.app.domain.gateway.DownloadCacheSettingsGateway
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.gateway.ReadSettingsGateway
import io.legado.app.domain.model.settings.DownloadCacheSettings
import io.legado.app.help.config.AppConfigStore
import io.legado.app.support.InMemoryAppDatabaseFixture
import java.lang.reflect.Proxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class VideoPlayerBehaviorTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val progressScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val contentRepository = VideoContentRepository()
    private lateinit var fixture: InMemoryAppDatabaseFixture
    private lateinit var books: BookRepository
    private val source = BookSource(
        bookSourceUrl = "https://video.test",
        bookSourceType = BookSourceType.video,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val application = RuntimeEnvironment.getApplication()
        application.injectAsAppCtx()
        AppConfigStore.init(application)
        fixture = InMemoryAppDatabaseFixture(application)
        books = BookRepository(fixture.database.bookDao, fixture.database.bookChapterDao, fixture.database)
        startKoin {
            modules(module {
                single<DownloadCacheSettingsGateway> {
                    object : DownloadCacheSettingsGateway {
                        override val currentSettings = DownloadCacheSettings()
                        override val settings = flowOf(currentSettings)
                        override suspend fun update(transform: (DownloadCacheSettings) -> DownloadCacheSettings) = Unit
                    }
                }
                single<OtherSettingsGateway> { OtherSettingsRepository() }
                single<ReadSettingsGateway> { ReadSettingsRepository(SettingsRepository()) }
            })
        }
    }

    @After
    fun tearDown() {
        store.clear()
        progressScope.cancel()
        fixture.close()
        stopKoin()
        Dispatchers.resetMain()
    }

    @Test
    fun bookInfoRulesRunOffTheMainThread() = runBlocking {
        assertTrue(Looper.getMainLooper().isCurrentThread)
        val book = videoBook().apply { name = ""; infoHtml = "<html></html>" }
        source.ruleBookInfo = BookInfoRule(name = "@js: $backgroundThreadCheck 'Video title'")

        contentRepository.loadBookInfo(source, book)

        assertEquals("Video title", book.name)
    }

    @Test
    fun chapterRulesRunOffTheMainThread() = runBlocking {
        val book = videoBook().apply { tocHtml = "<a href='episode.mp4'>Episode</a>" }
        source.ruleToc = TocRule(
            chapterList = "a",
            chapterName = "@js: $backgroundThreadCheck 'Episode'",
            chapterUrl = "href",
        )

        val chapters = contentRepository.loadChapters(source, book)

        assertEquals(listOf("Episode"), chapters.map { it.title })
    }

    @Test
    fun mediaUrlRulesRunOffTheMainThreadAndKeepHeaders() = runBlocking {
        source.header = """{"Referer":"https://video.test/watch"}"""
        val chapter = BookChapter(url = "@js: $backgroundThreadCheck 'https://video.test/episode.m3u8'")

        val item = requireNotNull(contentRepository.loadMedia(source, videoBook(), chapter))
        val uri = requireNotNull(item.localConfiguration).uri.toString()

        assertTrue(uri.startsWith("https://video.test/episode.m3u8"))
        assertTrue(uri.contains("https://video.test/watch"))
        assertEquals("application/x-mpegURL", item.localConfiguration?.mimeType)
    }

    @Test
    fun contentRulesRunOffTheMainThread() = runBlocking {
        val book = videoBook().apply { tocHtml = "<html></html>" }
        source.ruleContent = ContentRule(
            content = "@js: $backgroundThreadCheck 'https://video.test/content.mp4'",
        )
        val chapter = BookChapter(url = book.bookUrl, bookUrl = book.bookUrl, title = "Episode")

        val item = requireNotNull(contentRepository.loadMedia(source, book, chapter))

        assertTrue(item.localConfiguration!!.uri.toString().startsWith("https://video.test/content.mp4"))
    }

    @Test
    fun backgroundPauseSavesBeforeViewModelIsClearedAndKeepsConcurrentEdits() = runTest(dispatcher) {
        val player = TestPlayer()
        val original = insertVideoBook()
        val viewModel = createViewModel(player)
        viewModel.init(original.bookUrl)
        viewModel.uiState.first { it.chapterTitle == "Episode" }
        books.update(original.copy(customCoverUrl = "updated-cover"))
        player.position = 42_000L

        viewModel.onIntent(VideoPlayerIntent.Pause)
        awaitProgressWrites()

        val saved = requireNotNull(books.getBook(original.bookUrl))
        assertEquals(42_000, saved.durChapterPos)
        assertEquals(0, saved.durChapterIndex)
        assertEquals("Episode", saved.durChapterTitle)
        assertEquals("updated-cover", saved.customCoverUrl)
        assertEquals(1, player.pauseCalls)
        assertFalse(player.released)
    }

    @Test
    fun successivePauseSnapshotsArePersistedInOrder() = runTest(dispatcher) {
        val player = TestPlayer()
        val original = insertVideoBook()
        val viewModel = createViewModel(player)
        viewModel.init(original.bookUrl)
        viewModel.uiState.first { it.chapterTitle == "Episode" }

        player.position = 10_000L
        viewModel.onIntent(VideoPlayerIntent.Pause)
        player.position = 20_000L
        viewModel.onIntent(VideoPlayerIntent.Pause)
        awaitProgressWrites()

        assertEquals(20_000, books.getBook(original.bookUrl)?.durChapterPos)
    }

    @Test
    fun finalProgressWriteSurvivesViewModelClear() = runTest(dispatcher) {
        val player = TestPlayer()
        val original = insertVideoBook()
        val viewModel = createViewModel(player)
        viewModel.init(original.bookUrl)
        viewModel.uiState.first { it.chapterTitle == "Episode" }
        player.position = 55_000L

        store.clear()
        awaitProgressWrites()

        assertTrue(player.released)
        assertEquals(55_000, books.getBook(original.bookUrl)?.durChapterPos)
    }

    private fun videoBook() = Book(
        bookUrl = "https://video.test/book",
        tocUrl = "https://video.test/book",
        origin = source.bookSourceUrl,
        name = "Video",
        type = BookType.video,
        durChapterPos = 7_000,
    )

    private fun insertVideoBook(): Book = videoBook().also {
        fixture.database.bookDao.insert(it)
        fixture.database.bookSourceDao.insert(source)
        fixture.database.bookChapterDao.insert(
            BookChapter(url = "https://video.test/episode.mp4", bookUrl = it.bookUrl, title = "Episode"),
        )
    }

    private fun createViewModel(player: TestPlayer) = VideoPlayerViewModel(
        books,
        BookSourceRepository(fixture.database.bookSourceDao),
        contentRepository,
        playerFactory = { player.instance },
        progressScope = progressScope,
    ).also { store.put("video", it) }

    private suspend fun awaitProgressWrites() {
        progressScope.coroutineContext[Job]!!.children.toList().joinAll()
    }

    private class TestPlayer {
        var position = 0L
        var pauseCalls = 0
        var released = false
        private var mediaItemCount = 0

        val instance = Proxy.newProxyInstance(
            Player::class.java.classLoader, arrayOf(Player::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getCurrentPosition" -> position
                "getMediaItemCount" -> mediaItemCount
                "setMediaItem" -> { mediaItemCount = 1; position = args[1] as Long; null }
                "pause" -> { pauseCalls++; null }
                "release" -> { released = true; null }
                "addListener", "removeListener", "prepare", "play" -> null
                else -> error("Unexpected player call: ${method.name}")
            }
        } as Player
    }

    // 使用 JDK 类型检查真实执行线程，避免 Rhino 工作线程加载 Robolectric 的 Android 沙箱类型。
    private val backgroundThreadCheck: String
        get() = "if (Packages.java.lang.Thread.currentThread().getId() == " +
            "${Looper.getMainLooper().thread.threadId()}) throw new Error('main thread');"
}
