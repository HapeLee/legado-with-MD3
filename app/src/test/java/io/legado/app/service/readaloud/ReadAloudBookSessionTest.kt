package io.legado.app.service.readaloud

import android.app.Application
import android.app.PendingIntent
import android.text.TextPaint
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.gateway.AppShellSettingsGateway
import io.legado.app.domain.gateway.ReadAloudSettingsGateway
import io.legado.app.domain.gateway.ReadSettingsGateway
import io.legado.app.domain.model.settings.OtherSettings
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.ReadAloudSettings
import io.legado.app.domain.model.settings.ReadSettings
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import io.legado.app.feature.reader.core.readaloud.ReaderReadAloudChapter
import io.legado.app.feature.reader.platform.ReaderAndroidPaginationStyle
import io.legado.app.help.book.BookHelp
import io.legado.app.help.config.AppConfigStore
import io.legado.app.help.globalExecutor
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadAloudSessionStore
import io.legado.app.model.ReadBook
import io.legado.app.model.ReaderPaginationEnvironment
import io.legado.app.service.BaseReadAloudService
import io.legado.app.support.InMemoryAppDatabaseFixture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx
import java.util.concurrent.TimeUnit

/** The reader and playback deliberately use the same chapter number in different books. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class ReadAloudBookSessionTest {
    private val mainDispatcher = StandardTestDispatcher()
    private lateinit var database: InMemoryAppDatabaseFixture
    private val sessions = ReadAloudSessionStore()
    private lateinit var a: Book
    private lateinit var b: Book
    private lateinit var service: PlaybackService

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        stopKoin()
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        AppConfigStore.init(RuntimeEnvironment.getApplication())
        database = InMemoryAppDatabaseFixture(RuntimeEnvironment.getApplication())
        startKoin { modules(module {
            single { sessions }
            single<ReadAloudSettingsGateway> { AloudSettings() }
            single<ReadSettingsGateway> { ReadingSettings() }
            single<OtherSettingsGateway> { OtherPreferences() }
            single<AppShellSettingsGateway> { ShellSettings() }
        }) }
        flag("isRun", false)
        flag("stopRequested", false)
        flag("pause", false)
        a = Book(bookUrl = "https://session.test/a", origin = "https://session.test/source",
            name = "A", durChapterIndex = 5, durChapterPos = 20)
        b = a.copy(bookUrl = "https://session.test/b", name = "B")
        appDb.bookDao.insert(a, b)
        val chapter = BookChapter(bookUrl = a.bookUrl, url = "a/6", index = 6, title = "A 的下一章")
        appDb.bookChapterDao.insert(chapter)
        BookHelp.saveText(a, chapter, "只有 A 的正文。\n继续听 A。")
        ReadAloud.bookSession.adopt(ReadAloudBookSession.Snapshot(
            book = a, chapterIndex = 5, environment = environment(), paragraphIndent = "　　",
        ))
        ReadBook.replaceCurrentBook(b)
        ReadBook::class.java.getDeclaredField("durChapterIndex").apply {
            isAccessible = true; setInt(null, 5)
        }
        ReadBook.isUiActive = true
        ReadBook.updateReadingPosition(20)
        service = Robolectric.buildService(PlaybackService::class.java).get()
        service.readerReadAloudChapter = ReaderReadAloudChapter.create(
            chapterIndex = 5, title = "A 的当前章", semanticContent = "正文".repeat(100),
            pageStarts = listOf(0, 60, 120, 180),
        )
        flag("isRun", true)
    }

    @After
    fun tearDown() {
        flag("isRun", false)
        ReadBook.isUiActive = false
        globalExecutor.submit {}.get(5, TimeUnit.SECONDS)
        ReadAloud.bookSession.adopt(ReadAloudBookSession.Snapshot())
        ReadBook.clearCurrentBook()
        database.close()
        stopKoin()
        Dispatchers.resetMain()
    }

    @Test
    fun openingAndTurningBDoesNotChangePlaybackOrFollowState() {
        assertFalse(ReadBook.isCurrentBookReadAloud)
        assertFalse(ReadBook.onComposeManualPageTurn())
        ReadBook.updateReadingPosition(100)
        assertEquals(a.bookUrl, ReadAloud.bookSession.book?.bookUrl)
        assertEquals(5, ReadAloud.bookSession.chapterIndex)
        assertTrue(sessions.state.value.followReadAloudPosition)
    }

    @Test
    fun playbackPageAdvanceDoesNotMoveBAndSavesProgressOnlyToA() {
        service.advancePage(65)
        ReadAloud.bookSession.updateProgress(65, forceSave = true)
        globalExecutor.submit {}.get(5, TimeUnit.SECONDS)
        assertEquals(1, service.pageIndex)
        assertEquals(20, ReadBook.durChapterPos)
        assertEquals(65, appDb.bookDao.getBook(a.bookUrl)?.durChapterPos)
        assertEquals(20, appDb.bookDao.getBook(b.bookUrl)?.durChapterPos)
        assertEquals(20, b.durChapterPos)
    }

    @Test
    fun nextChapterLoadsAndPaginatesAWhileReaderStaysOnB() = runBlocking {
        ReadAloud.bookSession.selectChapter(6)
        val prepared = ReadAloud.bookSession.prepareChapter()
        assertEquals(a.bookUrl, prepared.input?.book?.bookUrl)
        assertEquals("A 的下一章", prepared.input?.displayTitle)
        assertTrue(prepared.input!!.source.semanticContent.contains("只有 A 的正文"))
        assertTrue(prepared.input.source.semanticContent.startsWith("　　"))
        assertTrue(prepared.pagination!!.pageStarts.isNotEmpty())
        ReadAloud.bookSession.updateProgress(0, forceSave = true)
        globalExecutor.submit {}.get(5, TimeUnit.SECONDS)
        assertEquals("A 的下一章", appDb.bookDao.getBook(a.bookUrl)?.durChapterTitle)
        assertEquals(b.bookUrl, ReadBook.book?.bookUrl)
        assertEquals(5, ReadBook.durChapterIndex)
        assertEquals(20, ReadBook.durChapterPos)
    }

    @Test
    fun chapterTimerPersistsAEndWithoutTouchingB() {
        BaseReadAloudService::class.java.getDeclaredField("finishChapterAtIndex").apply {
            isAccessible = true; setInt(service, 5)
        }
        service.completeChapter()
        globalExecutor.submit {}.get(5, TimeUnit.SECONDS)
        assertFalse(BaseReadAloudService.isRun)
        assertEquals(199, appDb.bookDao.getBook(a.bookUrl)?.durChapterPos)
        assertEquals(20, appDb.bookDao.getBook(b.bookUrl)?.durChapterPos)
        assertEquals(20, ReadBook.durChapterPos)
    }

    @Test
    fun adoptingBRequiresAnExplicitPlaybackRequestAndDoesNotShareMutableBook() {
        val request = ReadAloudBookSession.Snapshot(book = b, chapterIndex = 5, chapterPosition = 20)
        assertTrue(ReadAloud.bookSession.owns(a.bookUrl))
        ReadAloud.bookSession.adopt(request)
        b.name = "B 的新标题"
        assertTrue(ReadAloud.bookSession.owns(b.bookUrl))
        assertEquals("B", ReadAloud.bookSession.book?.name)
        assertTrue(ReadBook.isCurrentBookReadAloud)
    }

    @Test
    fun lateProgressFromPreviousChapterCannotOverwriteTheNewChapter() {
        ReadAloud.bookSession.selectChapter(6)
        ReadAloud.bookSession.updateProgress(150, forceSave = true, expectedChapterIndex = 5)
        assertEquals(6, ReadAloud.bookSession.chapterIndex)
        assertEquals(0, ReadAloud.bookSession.state.value.chapterPosition)
    }

    @Test
    fun explicitSwitchWaitsForOldServiceTeardownBeforeAdoptingB() = runTest(mainDispatcher) {
        try {
            flag("isAlive", true)
            ReadAloud.playRequest(RuntimeEnvironment.getApplication(),
                ReadAloudBookSession.Snapshot(book = b, chapterIndex = 5), chapterPosition = 20)
            runCurrent()
            assertFalse(BaseReadAloudService.isRun)
            assertEquals(a.bookUrl, ReadAloud.bookSession.book?.bookUrl)
            advanceTimeBy(100)
            runCurrent()
            assertEquals(a.bookUrl, ReadAloud.bookSession.book?.bookUrl)
            flag("isAlive", false)
            advanceTimeBy(20)
            runCurrent()
            assertEquals(b.bookUrl, ReadAloud.bookSession.book?.bookUrl)
            val started = Shadows.shadowOf(RuntimeEnvironment.getApplication()).nextStartedService
            assertNotNull(started)
            assertEquals(20, started.getIntExtra("chapterPosition", -1))
        } finally {
            flag("isAlive", false)
        }
    }

    private fun environment(): ReaderPaginationEnvironment {
        val paint = TextPaint().apply { textSize = 20f }
        val style = ReaderTextStyle(0xff000000.toInt(), 20f)
        return ReaderPaginationEnvironment(400, 800, ReaderAndroidPaginationStyle(
            paint, TextPaint(paint), style, style, 10, 10, 10, 10,
            24f, 24f, 20f, 20f, 1f, 1f, 10,
        ))
    }

    private fun flag(name: String, value: Boolean) {
        BaseReadAloudService::class.java.getDeclaredField(name).apply {
            isAccessible = true; setBoolean(null, value)
        }
    }

    class PlaybackService : BaseReadAloudService() {
        fun advancePage(position: Int) = moveToReadAloudPage(position)
        fun completeChapter() = completeCurrentChapter()
        override fun playStop() = Unit
        override fun upSpeechRate(reset: Boolean) = Unit
        override fun aloudServicePendingIntent(actionStr: String): PendingIntent? = null
    }

    private class AloudSettings : ReadAloudSettingsGateway {
        override val settings = MutableStateFlow(ReadAloudSettings())
        override val currentSettings get() = settings.value
        override suspend fun update(transform: (ReadAloudSettings) -> ReadAloudSettings) {
            settings.value = transform(settings.value)
        }
    }
    private class ReadingSettings : ReadSettingsGateway {
        override val settings = MutableStateFlow(ReadSettings())
        override val currentSettings get() = settings.value
        override suspend fun update(transform: (ReadSettings) -> ReadSettings) {
            settings.value = transform(settings.value)
        }
    }
    private class OtherPreferences : OtherSettingsGateway {
        override val settings = MutableStateFlow(OtherSettings())
        override val currentSettings get() = settings.value
        override suspend fun update(transform: (OtherSettings) -> OtherSettings) {
            settings.value = transform(settings.value)
        }
    }
    private class ShellSettings : AppShellSettingsGateway {
        override val settings = MutableStateFlow(AppShellSettings(themeMode = "1"))
        override val currentSettings get() = settings.value
        override suspend fun update(transform: (AppShellSettings) -> AppShellSettings) {
            settings.value = transform(settings.value)
        }
    }
}
