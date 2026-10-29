package io.legado.app.ui.book.video

import android.app.Application
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.legado.app.constant.BookSourceType
import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.RssArticle
import io.legado.app.data.entities.rule.BookInfoRule
import io.legado.app.data.entities.rule.ContentRule
import io.legado.app.data.entities.rule.TocRule
import io.legado.app.data.repository.BookRepository
import io.legado.app.data.repository.VideoContentRepository
import io.legado.app.data.repository.VideoMedia
import io.legado.app.help.CacheManager
import io.legado.app.help.config.AppConfigStore
import io.legado.app.model.VideoPlay
import io.legado.app.model.VideoPlayState
import io.legado.app.support.InMemoryAppDatabaseFixture
import java.lang.reflect.Proxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
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

/**
 * 视频播放会话的行为基线：规则执行线程、目录补齐、进度落库与卷/集推进。
 *
 * 会话模型 [VideoPlay] 是进程单例，这里用假播放器 + 内存库驱动它，
 * 断言的是「用户能不能看到上一集看到的位置」这类可观察结果。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class VideoPlaySessionTest {

    private val dispatcher = StandardTestDispatcher()
    private val realContentRepository = VideoContentRepository()
    private lateinit var fixture: InMemoryAppDatabaseFixture
    private lateinit var books: BookRepository
    private lateinit var player: TestPlayer

    private val source = BookSource(
        bookSourceUrl = "https://video.test",
        bookSourceType = BookSourceType.video,
    )

    private val fakeContentRepository = object : VideoContentRepository() {
        override suspend fun loadMedia(
            source: BookSource,
            book: Book,
            chapter: BookChapter,
        ): VideoMedia? = VideoMedia(
            mediaItem = MediaItem.fromUri(chapter.url),
            playUrl = chapter.url,
        )

        override suspend fun loadChapters(
            source: BookSource,
            book: Book,
        ): List<BookChapter> = listOf(
            BookChapter(
                url = "${book.bookUrl}/1", bookUrl = book.bookUrl, title = "Episode 1", index = 0
            ),
            BookChapter(
                url = "${book.bookUrl}/2", bookUrl = book.bookUrl, title = "Episode 2", index = 1
            ),
        )
    }

    @Before
    fun setUp() {
        // 会话模型用 MainScope 起协程，这里让它们直接在测试线程跑，避免测试调度器的未完成协程判定
        Dispatchers.setMain(Dispatchers.Unconfined)
        val application = RuntimeEnvironment.getApplication()
        application.injectAsAppCtx()
        AppConfigStore.init(application)
        fixture = InMemoryAppDatabaseFixture(application)
        books = BookRepository(
            fixture.database.bookDao, fixture.database.bookChapterDao, fixture.database
        )
        player = TestPlayer()
        // 其它测试类可能已经起过 Koin，这里只保留视频会话需要的绑定
        runCatching { stopKoin() }
        startKoin {
            modules(module {
                // WebBook 解析链上会用到这些网关（与原播放页测试保持一致）
                single<io.legado.app.domain.gateway.DownloadCacheSettingsGateway> {
                    object : io.legado.app.domain.gateway.DownloadCacheSettingsGateway {
                        override val currentSettings =
                            io.legado.app.domain.model.settings.DownloadCacheSettings()

                        override val settings = kotlinx.coroutines.flow.flowOf(currentSettings)
                        override suspend fun update(
                            transform: (io.legado.app.domain.model.settings.DownloadCacheSettings)
                            -> io.legado.app.domain.model.settings.DownloadCacheSettings
                        ) = Unit
                    }
                }
                single<io.legado.app.domain.gateway.OtherSettingsGateway> {
                    io.legado.app.data.repository.OtherSettingsRepository()
                }
                single<io.legado.app.domain.gateway.ReadSettingsGateway> {
                    io.legado.app.data.repository.ReadSettingsRepository(
                        io.legado.app.data.repository.SettingsRepository()
                    )
                }
                single { books }
                single<VideoContentRepository> { fakeContentRepository }
            })
        }
    }

    @After
    fun tearDown() {
        // Koin 关掉之前必须让会话的后台协程跑完，否则它们会在下一个测试里抛未捕获异常
        runBlocking { VideoPlay.stop(); awaitWrites() }
        VideoPlay.release()
        fixture.close()
        stopKoin()
        Dispatchers.resetMain()
    }

    // ——————————————————— 规则执行线程（真实解析器） ———————————————————

    @Test
    fun bookInfoRulesRunOffTheMainThread() = runBlocking {
        assertTrue(Looper.getMainLooper().isCurrentThread)
        val book = videoBook().apply { name = ""; infoHtml = "<html></html>" }
        source.ruleBookInfo = BookInfoRule(name = "@js: $backgroundThreadCheck 'Video title'")

        realContentRepository.loadBookInfo(source, book)

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

        val chapters = realContentRepository.loadChapters(source, book)

        assertEquals(listOf("Episode"), chapters.map { it.title })
    }

    @Test
    fun mediaUrlRulesRunOffTheMainThreadAndKeepHeaders() = runBlocking {
        source.header = """{"Referer":"https://video.test/watch"}"""
        val chapter = BookChapter(
            url = "@js: $backgroundThreadCheck 'https://video.test/episode.m3u8'"
        )

        val media = requireNotNull(realContentRepository.loadMedia(source, videoBook(), chapter))
        val uri = requireNotNull(media.mediaItem.localConfiguration).uri.toString()

        assertTrue(uri.startsWith("https://video.test/episode.m3u8"))
        assertTrue(uri.contains("https://video.test/watch"))
        assertEquals("application/x-mpegURL", media.mediaItem.localConfiguration?.mimeType)
    }

    @Test
    fun contentRulesRunOffTheMainThread() = runBlocking {
        val book = videoBook().apply { tocHtml = "<html></html>" }
        source.ruleContent = ContentRule(
            content = "@js: $backgroundThreadCheck 'https://video.test/content.mp4'",
        )
        val chapter = BookChapter(url = book.bookUrl, bookUrl = book.bookUrl, title = "Episode")

        val media = requireNotNull(realContentRepository.loadMedia(source, book, chapter))

        assertTrue(
            media.mediaItem.localConfiguration!!.uri.toString()
                .startsWith("https://video.test/content.mp4")
        )
    }

    // ——————————————————— 会话与进度 ———————————————————

    @Test
    fun openBookFillsMissingChaptersBeforePlaying() = runBlocking {
        val book = insertVideoBook(withChapters = false)
        VideoPlay.attachPlayer(player.instance)

        VideoPlay.openBook(book.bookUrl)

                awaitState("Episode 1") { it.chapterTitle == "Episode 1" }
        assertEquals(
            2, fixture.database.bookChapterDao.getChapterList(book.bookUrl).size
        )
        awaitWrites()
    }

    @Test
    fun pauseSavesProgressAndKeepsConcurrentEdits() = runBlocking {
        val book = insertVideoBook()
        VideoPlay.attachPlayer(player.instance)
        VideoPlay.openBook(book.bookUrl, autoPlay = true)
                awaitState("Episode 1") { it.chapterTitle == "Episode 1" }

        books.update(book.copy(customCoverUrl = "updated-cover"))
        player.position = 42_000L
        VideoPlay.pause()
        awaitWrites()

        val saved = requireNotNull(books.getBook(book.bookUrl))
        assertEquals(42_000, saved.durChapterPos)
        assertEquals(0, saved.durChapterIndex)
        assertEquals("Episode 1", saved.durChapterTitle)
        assertEquals("updated-cover", saved.customCoverUrl)
        assertEquals(1, player.pauseCalls)
        assertFalse(player.released)
    }

    @Test
    fun successivePauseSnapshotsArePersistedInOrder() = runBlocking {
        val book = insertVideoBook()
        VideoPlay.attachPlayer(player.instance)
        VideoPlay.openBook(book.bookUrl)
                awaitState("Episode 1") { it.chapterTitle == "Episode 1" }

        player.position = 10_000L
        VideoPlay.pause()
        player.position = 20_000L
        VideoPlay.pause()
        awaitWrites()

        assertEquals(20_000, books.getBook(book.bookUrl)?.durChapterPos)
    }

    @Test
    fun stopSavesFinalProgressAndResetsSession() = runBlocking {
        val book = insertVideoBook()
        VideoPlay.attachPlayer(player.instance)
        VideoPlay.openBook(book.bookUrl)
                awaitState("Episode 1") { it.chapterTitle == "Episode 1" }

        player.position = 55_000L
        VideoPlay.stop()
        awaitWrites()

        assertEquals(55_000, books.getBook(book.bookUrl)?.durChapterPos)
        assertEquals(0, books.getBook(book.bookUrl)?.lastCheckCount)
        assertEquals("", VideoPlay.state.value.chapterTitle)
    }

    @Test
    fun singleUrlProgressIsRememberedByUrl() = runBlocking {
        VideoPlay.attachPlayer(player.instance)

        VideoPlay.openUrl("https://video.test/once.mp4", "Once")
        player.position = 12_000L
        VideoPlay.saveRead()
        awaitWrites()

        assertEquals(12_000, CacheManager.getInt("video_pos_https://video.test/once.mp4"))
    }

    // ——————————————————— RSS 视频 ———————————————————

    @Test
    fun rssArticlePlaysContentAndSavesStarProgress() = runBlocking {
        val article = RssArticle(
            origin = "https://rss.test",
            sort = "sort",
            title = "RSS Video",
            link = "https://rss.test/article",
            content = "https://rss.test/article.mp4",
            type = 2,
        )
        fixture.database.rssStarDao.insert(article.toStar())
        VideoPlay.attachPlayer(player.instance)

        VideoPlay.openRssArticle(article)
        awaitState("RSS Video") { it.bookName == "RSS Video" }

        assertEquals("https://rss.test/article.mp4", VideoPlay.playUrl)
        player.position = 9_000L
        VideoPlay.saveRead()
        awaitWrites()

        assertEquals(
            9_000,
            fixture.database.rssStarDao.get(article.origin, article.link)?.durPos,
        )
        assertTrue(VideoPlay.singleUrl)
    }

    @Test
    fun rssArticleWithoutStarFallsBackToCachedProgress() = runBlocking {
        val article = RssArticle(
            origin = "https://rss.test",
            sort = "sort",
            title = "RSS Video",
            link = "https://rss.test/uncached",
            content = "https://rss.test/uncached.mp4",
            type = 2,
        )
        VideoPlay.attachPlayer(player.instance)

        VideoPlay.openRssArticle(article)
        awaitState("RSS Video") { it.bookName == "RSS Video" }
        player.position = 4_500L
        VideoPlay.saveRead()
        awaitWrites()

        assertEquals(
            4_500,
            CacheManager.getInt("video_pos_https://rss.test/uncached.mp4"),
        )
    }

    // ——————————————————— 卷与集 ———————————————————

    @Test
    fun volumesSplitEpisodesAndNextCanCrossVolume() = runBlocking {
        val book = insertVideoBook(withVolumes = true)
        VideoPlay.attachPlayer(player.instance)
        VideoPlay.openBook(book.bookUrl)
                awaitState("Episode 1") { it.chapterTitle == "Episode 1" }

        val state = VideoPlay.state.value
        assertEquals(listOf("Vol 1", "Vol 2"), state.volumes)
        assertEquals(listOf("Episode 1", "Episode 2"), state.episodes)

        VideoPlay.upDurIndex(1)
        awaitState("Episode 2") { it.chapterTitle == "Episode 2" }
        VideoPlay.upDurIndex(1)
        awaitState("volume 1") { it.volumeIndex == 1 }
        assertEquals("Episode 3", VideoPlay.state.value.chapterTitle)

        // 最后一集再往后不越界
        VideoPlay.upDurIndex(1)
        assertEquals("Episode 3", VideoPlay.state.value.chapterTitle)
        awaitWrites()
    }

    @Test
    fun previousAtFirstEpisodeDoesNotWrap() = runBlocking {
        val book = insertVideoBook()
        VideoPlay.attachPlayer(player.instance)
        VideoPlay.openBook(book.bookUrl)
                awaitState("Episode 1") { it.chapterTitle == "Episode 1" }

        VideoPlay.upDurIndex(-1)

        assertEquals("Episode 1", VideoPlay.state.value.chapterTitle)
        assertEquals(0, VideoPlay.state.value.episodeIndex)
        awaitWrites()
    }

    @Test
    fun selectEpisodeSwitchesVolumeAndEpisode() = runBlocking {
        val book = insertVideoBook(withVolumes = true)
        VideoPlay.attachPlayer(player.instance)
        VideoPlay.openBook(book.bookUrl)
                awaitState("Episode 1") { it.chapterTitle == "Episode 1" }

        VideoPlay.selectEpisode(volumeIndex = 1, episodeIndex = 0)

        awaitState("Episode 3") { it.chapterTitle == "Episode 3" }
        assertEquals(1, VideoPlay.state.value.volumeIndex)
        assertEquals(0, VideoPlay.state.value.episodeIndex)
        awaitWrites()
    }

    // ——————————————————— 播放页菜单能力 ———————————————————

    @Test
    fun uiStateCarriesSourceCapabilitiesForMenu() = runBlocking {
        source.loginUrl = "https://video.test/login"
        source.customButton = true
        val book = insertVideoBook()
        VideoPlay.attachPlayer(player.instance)
        val viewModel = VideoPlayerViewModel()

        viewModel.init(book.bookUrl)
        val state = awaitUiState(viewModel) { it.chapterTitle == "Episode 1" }

        assertEquals(source.bookSourceUrl, state.bookSourceUrl)
        assertTrue(state.hasSource)
        assertTrue(state.hasSourceLogin)
        assertTrue(state.hasCustomButton)
        awaitWrites()
    }

    @Test
    fun uiStateHidesSourceCapabilitiesTheSourceDoesNotOffer() = runBlocking {
        // 默认书源既没有登录地址，也没有声明自定义按钮
        val book = insertVideoBook()
        VideoPlay.attachPlayer(player.instance)
        val viewModel = VideoPlayerViewModel()

        viewModel.init(book.bookUrl)
        val state = awaitUiState(viewModel) { it.chapterTitle == "Episode 1" }

        assertTrue(state.hasSource)
        assertFalse(state.hasSourceLogin)
        assertFalse(state.hasCustomButton)
        awaitWrites()
    }

    @Test
    fun uiStateHasNoSourceCapabilitiesForRssArticle() = runBlocking {
        val article = RssArticle(
            origin = "https://rss.test",
            sort = "sort",
            title = "RSS Video",
            link = "https://rss.test/menu",
            content = "https://rss.test/menu.mp4",
            type = 2,
        )
        VideoPlay.attachPlayer(player.instance)
        val viewModel = VideoPlayerViewModel()

        viewModel.initRss(article)
        val state = awaitUiState(viewModel) { it.bookName == "RSS Video" }

        assertFalse(state.hasSource)
        assertFalse(state.hasSourceLogin)
        assertFalse(state.hasCustomButton)
        awaitWrites()
    }

    // ——————————————————— helpers ———————————————————

    private fun videoBook() = Book(
        bookUrl = "https://video.test/book",
        tocUrl = "https://video.test/book",
        origin = source.bookSourceUrl,
        name = "Video",
        type = BookType.video,
        durChapterPos = 7_000,
    )

    private fun insertVideoBook(
        withChapters: Boolean = true,
        withVolumes: Boolean = false,
    ): Book = videoBook().also { book ->
        fixture.database.bookDao.insert(book)
        fixture.database.bookSourceDao.insert(source)
        if (withVolumes) {
            fixture.database.bookChapterDao.insert(
                *volumeChapters(book).toTypedArray()
            )
        } else if (withChapters) {
            fixture.database.bookChapterDao.insert(
                BookChapter(
                    url = "${book.bookUrl}/1", bookUrl = book.bookUrl,
                    title = "Episode 1", index = 0,
                ),
                BookChapter(
                    url = "${book.bookUrl}/2", bookUrl = book.bookUrl,
                    title = "Episode 2", index = 1,
                ),
            )
        }
    }

    /** 卷是目录里 isVolume 的条目，集是它后面的条目，与上游一致。 */
    private fun volumeChapters(book: Book): List<BookChapter> = listOf(
        chapter(book, "Vol 1", isVolume = true),
        chapter(book, "Episode 1"),
        chapter(book, "Episode 2"),
        chapter(book, "Vol 2", isVolume = true),
        chapter(book, "Episode 3"),
    ).mapIndexed { index, chapter -> chapter.apply { this.index = index } }

    private fun chapter(
        book: Book,
        title: String,
        isVolume: Boolean = false,
    ) = BookChapter(
        url = "${book.bookUrl}/$title",
        bookUrl = book.bookUrl,
        title = title,
        isVolume = isVolume,
    )

    /** 等播放页 UiState 出现期望的快照。 */
    private suspend fun awaitUiState(
        viewModel: VideoPlayerViewModel,
        predicate: (VideoPlayerUiState) -> Boolean,
    ): VideoPlayerUiState = withTimeout(5_000) { viewModel.uiState.first(predicate) }

    /** 等会话里所有后台写库/预解析协程跑完，避免 runTest 报未完成协程。 */
    /** 等会话进入期望状态；超时直接带着当前 error 失败，避免测试无限挂起。 */
    private suspend fun awaitState(
        expected: String,
        predicate: (VideoPlayState) -> Boolean,
    ) {
        try {
            withTimeout(5_000) { VideoPlay.state.first(predicate) }
        } catch (e: TimeoutCancellationException) {
            val current = VideoPlay.state.value
            throw AssertionError(
                "等待「$expected」超时，当前状态: book=${current.bookName}, " +
                    "chapter=${current.chapterTitle}, error=${current.error}"
            )
        }
    }

    @Suppress("SuspendFunctionOnCoroutineScope")
    private suspend fun awaitWrites() {
        // 预解析会在上一次写库之后才起新协程，多轮 join 才能收干净
        repeat(3) {
            (VideoPlay as CoroutineScope).coroutineContext[Job]!!.children.toList().joinAll()
        }
    }

    private class TestPlayer {
        var position = 0L
        var pauseCalls = 0
        var released = false
        private var playing = false
        private var mediaItemCount = 0

        val instance = Proxy.newProxyInstance(
            ExoPlayer::class.java.classLoader, arrayOf(ExoPlayer::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getCurrentPosition" -> position
                "getMediaItemCount" -> mediaItemCount
                "setMediaItem" -> {
                    mediaItemCount = 1
                    if (args != null && args.size > 1) position = args[1] as Long
                    null
                }

                "isPlaying" -> playing
                "play" -> {
                    playing = true
                    null
                }

                "pause" -> {
                    pauseCalls++
                    playing = false
                    null
                }

                "release" -> {
                    released = true
                    null
                }

                "getPlaybackParameters" -> PlaybackParameters.DEFAULT
                else -> defaultValue(method.returnType)
            }
        } as ExoPlayer

        /** 代理未覆盖的方法按返回类型给默认值，避免新增调用就让测试挂掉。 */
        private fun defaultValue(returnType: Class<*>): Any? = when {
            returnType == Void.TYPE -> null
            returnType == java.lang.Boolean.TYPE -> false
            returnType == java.lang.Long.TYPE -> 0L
            returnType == java.lang.Integer.TYPE -> 0
            returnType == java.lang.Float.TYPE -> 1f
            returnType == java.lang.Double.TYPE -> 0.0
            else -> null
        }
    }

    // 使用 JDK 类型检查真实执行线程，避免 Rhino 工作线程加载 Robolectric 的 Android 沙箱类型。
    private val backgroundThreadCheck: String
        get() = "if (Packages.java.lang.Thread.currentThread().getId() == " +
            "${Looper.getMainLooper().thread.threadId()}) throw new Error('main thread');"
}
