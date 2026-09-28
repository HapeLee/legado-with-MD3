package io.legado.app.service

import android.app.Application
import android.app.PendingIntent
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.domain.gateway.ReadAloudSettingsGateway
import io.legado.app.domain.model.settings.ReadAloudSettings
import io.legado.app.feature.reader.core.readaloud.ReaderReadAloudChapter
import io.legado.app.model.ReadAloudSessionStore
import io.legado.app.model.ReadBook
import io.legado.app.support.InMemoryAppDatabaseFixture
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx
import java.util.concurrent.TimeUnit

/** Exercises the real service completion -> reader progress -> Room save path. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ReadAloudChapterStopProgressTest {

    private lateinit var service: CompletionService
    private lateinit var book: Book
    private lateinit var databaseFixture: InMemoryAppDatabaseFixture
    private val sessionStore = ReadAloudSessionStore()
    private var renderCount = 0

    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        databaseFixture = InMemoryAppDatabaseFixture(RuntimeEnvironment.getApplication())
        startKoin {
            modules(module {
                single { sessionStore }
                single<ReadAloudSettingsGateway> { TestReadAloudSettingsGateway() }
            })
        }
        setServiceFlag("isRun", false)
        setServiceFlag("stopRequested", false)
        setServiceFlag("pause", false)
        setReaderField("durChapterIndex", 5)
        setReaderField("lastProgressSaveAt", 0L)
        book = Book(
            bookUrl = "https://example.test/read-aloud-timer",
            name = "Timer progress",
            durChapterIndex = 5,
            durChapterPos = 20,
        )
        appDb.bookDao.insert(book)
        ReadBook.replaceCurrentBook(book)
        ReadBook.updateReadingPosition(20)
        ReadBook.clearReaderPagination()
        service = Robolectric.buildService(CompletionService::class.java).get()
        service.readerReadAloudChapter = ReaderReadAloudChapter.create(
            chapterIndex = 5,
            title = "Current chapter",
            semanticContent = "正文".repeat(100),
            pageStarts = listOf(0, 60, 120, 180),
        )
        // HTTP completion resets its next-paragraph cursor; it must not become the stop anchor.
        service.readAloudNumber = 0
        ReadBook.renderCallBack = object : ReadBook.ReaderRenderCallback {
            override fun upContent(
                relativePosition: Int,
                resetPageOffset: Boolean,
                success: (() -> Unit)?,
            ) {
                assertFalse("The redraw must not restart playback", BaseReadAloudService.isRun)
                renderCount++
            }

            override suspend fun upContentAwait(
                relativePosition: Int,
                resetPageOffset: Boolean,
                success: (() -> Unit)?,
            ) = Unit

            override fun pageChanged() = Unit
            override fun contentLoadFinish() = Unit
            override fun upPageAnim(upRecorder: Boolean) = Unit
            override fun cancelSelect() = Unit
        }
        setServiceFlag("isRun", true)
    }

    @After
    fun tearDown() {
        try {
            BaseReadAloudService.requestStop()
            awaitProgressSave()
        } finally {
            try {
                ReadBook.renderCallBack = null
                ReadBook.clearCurrentBook()
            } finally {
                try {
                    if (::databaseFixture.isInitialized) databaseFixture.close()
                } finally {
                    stopKoin()
                }
            }
        }
    }

    @Test
    fun minuteTimerFinishesAtChapterEndAndPersistsAfterStopRequest() {
        setTimerField("finishChapterAtIndex", 5)

        service.finishChapter()
        awaitProgressSave()

        assertStoppedAtChapterEnd()
    }

    @Test
    fun chapterQuotaFinishesAtChapterEndWithoutPaginationOrFinalProgressCallback() {
        setTimerField("chapterQuota", 1)

        service.finishChapter()
        awaitProgressSave()

        assertStoppedAtChapterEnd()
    }

    @Test
    fun detachedReaderKeepsItsManuallySelectedPositionWhenTimerStops() {
        sessionStore.detachReadAloudFollow()
        setTimerField("finishChapterAtIndex", 5)

        service.finishChapter()
        awaitProgressSave()

        assertTrue(Shadows.shadowOf(service).isStoppedBySelf)
        assertEquals(20, ReadBook.durChapterPos)
        assertEquals(20, appDb.bookDao.getBook(book.bookUrl)?.durChapterPos)
        assertEquals(0, renderCount)
    }

    @Test
    fun finishingAnOlderChapterDoesNotOverwriteTheNewReaderChapter() {
        service.readerReadAloudChapter = service.readerReadAloudChapter!!.copy(chapterIndex = 4)
        setTimerField("finishChapterAtIndex", 4)

        service.finishChapter()
        awaitProgressSave()

        assertTrue(Shadows.shadowOf(service).isStoppedBySelf)
        assertEquals(5, ReadBook.durChapterIndex)
        assertEquals(20, ReadBook.durChapterPos)
        assertEquals(5, appDb.bookDao.getBook(book.bookUrl)?.durChapterIndex)
        assertEquals(0, renderCount)
    }

    private fun assertStoppedAtChapterEnd() {
        assertTrue(Shadows.shadowOf(service).isStoppedBySelf)
        assertFalse(BaseReadAloudService.isRun)
        assertEquals(5, ReadBook.durChapterIndex)
        assertEquals(199, ReadBook.durChapterPos)
        assertEquals(199, appDb.bookDao.getBook(book.bookUrl)?.durChapterPos)
        assertEquals(1, renderCount)
    }

    private fun awaitProgressSave() {
        ReadBook.executor.submit {}.get(5, TimeUnit.SECONDS)
    }

    private fun setTimerField(name: String, value: Any) {
        BaseReadAloudService::class.java.getDeclaredField(name).apply {
            isAccessible = true
            set(service, value)
        }
    }

    private fun setServiceFlag(name: String, value: Boolean) {
        BaseReadAloudService::class.java.getDeclaredField(name).apply {
            isAccessible = true
            setBoolean(null, value)
        }
    }

    private fun setReaderField(name: String, value: Any) {
        ReadBook::class.java.getDeclaredField(name).apply {
            isAccessible = true
            set(null, value)
        }
    }

    class CompletionService : BaseReadAloudService() {
        fun finishChapter() = completeCurrentChapter()
        override fun playStop() = Unit
        override fun upSpeechRate(reset: Boolean) = Unit
        override fun aloudServicePendingIntent(actionStr: String): PendingIntent? = null
    }

    private class TestReadAloudSettingsGateway : ReadAloudSettingsGateway {
        override val settings = MutableStateFlow(ReadAloudSettings())
        override val currentSettings get() = settings.value
        override suspend fun update(transform: (ReadAloudSettings) -> ReadAloudSettings) {
            settings.value = transform(settings.value)
        }
    }
}
