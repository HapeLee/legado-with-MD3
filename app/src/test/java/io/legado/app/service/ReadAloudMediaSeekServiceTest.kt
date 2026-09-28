package io.legado.app.service

import android.app.Application
import android.app.PendingIntent
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.lifecycle.lifecycleScope
import io.legado.app.domain.gateway.ReadAloudSettingsGateway
import io.legado.app.domain.model.readaloud.ReadAloudSessionStatus
import io.legado.app.domain.model.settings.ReadAloudSettings
import io.legado.app.feature.reader.core.readaloud.ReaderReadAloudChapter
import io.legado.app.model.ReadAloudSessionStore
import io.legado.app.model.ReadBook
import kotlinx.coroutines.cancel
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
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ReadAloudMediaSeekServiceTest {

    private lateinit var service: SeekService
    private val sessionStore = ReadAloudSessionStore()

    @Before
    fun setUp() {
        stopKoin()
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        startKoin {
            modules(module {
                single { sessionStore }
                single<ReadAloudSettingsGateway> { TestReadAloudSettingsGateway() }
            })
        }
        setStaticField("isRun", false)
        ReadBook.clearCurrentBook()
        ReadBook::class.java.getDeclaredField("durChapterIndex").apply {
            isAccessible = true
            setInt(null, 8)
        }
        ReadBook.updateReadingPosition(20)
        setStaticField("isRun", true)
        setStaticField("stopRequested", false)
        setStaticField("pause", false)
        setStaticField("timeMinute", 17)
        service = Robolectric.buildService(SeekService::class.java).get()
        service.readerReadAloudChapter = ReaderReadAloudChapter.create(
            chapterIndex = 5,
            title = "Reading chapter",
            semanticContent = "abcdef\nghij",
            pageStarts = listOf(0, 4, 7),
        )
        service.contentList = listOf("Reading chapter", "abcdef", "ghij")
        setServiceField("contentChapterPositions", listOf(null, 0, 7))
        setServiceField("finishChapterAtIndex", 5)
        setServiceField("chapterQuota", 3)
        setServiceField("androidMediaControlEnabled", true)
        sessionStore.updateTimer(17)
    }

    @After
    fun tearDown() {
        try {
            if (::service.isInitialized) {
                service.lifecycleScope.cancel()
                mediaSession().release()
            }
            BaseReadAloudService.requestStop()
            setStaticField("timeMinute", 0)
            ReadBook.stopAutoSaveSession()
            ReadBook.clearCurrentBook()
        } finally {
            stopKoin()
        }
    }

    @Test
    fun playingSeekUsesServiceChapterAndPreservesBothTimerModes() {
        service.seekToMediaPosition(2_000)

        assertEquals(1, service.stopCount)
        assertEquals(1, service.playCount)
        assertEquals("hij", service.lastPlayedText)
        assertEquals(2, service.nowSpeak)
        assertEquals(7, service.readAloudNumber)
        assertEquals(1, service.paragraphStartPos)
        assertEquals(8, BaseReadAloudService.currentProgress)
        assertEquals(5, BaseReadAloudService.currentChapterIndex)
        assertTrue(
            requireNotNull(mediaSession().controller.playbackState).actions and
                PlaybackStateCompat.ACTION_SEEK_TO != 0L
        )
        // The visible reader is browsing a different chapter; a media seek must not move it.
        assertEquals(8, ReadBook.durChapterIndex)
        assertEquals(20, ReadBook.durChapterPos)
        assertTrue(sessionStore.state.value.followReadAloudPosition)
        assertTimersUnchanged()
    }

    @Test
    fun pausedSeekDoesNotPlayAndResumeUsesTheNewBodyOffset() {
        setStaticField("pause", true)
        sessionStore.setStatus(ReadAloudSessionStatus.Paused)
        sessionStore.detachReadAloudFollow()

        service.seekToMediaPosition(500)

        assertEquals(1, service.stopCount)
        assertEquals(0, service.playCount)
        assertTrue(BaseReadAloudService.pause)
        assertTrue(service.pageChanged)
        assertFalse(sessionStore.state.value.followReadAloudPosition)
        assertEquals(2, BaseReadAloudService.currentProgress)
        assertEquals(500L, getServiceField("lastMediaSessionPositionMs"))
        assertEquals(ReadAloudSessionStatus.Paused, sessionStore.state.value.status)
        assertTimersUnchanged()

        service.resumeReadAloud()

        assertFalse(BaseReadAloudService.pause)
        assertEquals(1, service.playCount)
        assertEquals("cdef", service.lastPlayedText)
        assertEquals(8, ReadBook.durChapterIndex)
        assertEquals(20, ReadBook.durChapterPos)
        assertTimersUnchanged()
    }

    private fun assertTimersUnchanged() {
        assertEquals(17, BaseReadAloudService.timeMinute)
        assertEquals(17, sessionStore.state.value.timerMinutes)
        assertEquals(5, getServiceField("finishChapterAtIndex"))
        assertEquals(3, getServiceField("chapterQuota"))
    }

    private fun setStaticField(name: String, value: Any) {
        BaseReadAloudService::class.java.getDeclaredField(name).apply {
            isAccessible = true
            set(null, value)
        }
    }

    private fun setServiceField(name: String, value: Any) {
        BaseReadAloudService::class.java.getDeclaredField(name).apply {
            isAccessible = true
            set(service, value)
        }
    }

    private fun getServiceField(name: String): Any? =
        BaseReadAloudService::class.java.getDeclaredField(name).run {
            isAccessible = true
            get(service)
        }

    private fun mediaSession(): MediaSessionCompat =
        BaseReadAloudService::class.java.getDeclaredMethod("getMediaSessionCompat").run {
            isAccessible = true
            invoke(service) as MediaSessionCompat
        }

    class SeekService : BaseReadAloudService() {
        var stopCount = 0
        var playCount = 0
        var lastPlayedText = ""

        override fun playStop() {
            stopCount++
        }

        override fun play() {
            playCount++
            lastPlayedText = contentList[nowSpeak].substring(paragraphStartPos)
        }

        override fun resumeReadAloud() {
            super.resumeReadAloud()
            play()
        }

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
