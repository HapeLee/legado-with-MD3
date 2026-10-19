package io.legado.app.service

import android.app.Application
import android.support.v4.media.session.MediaSessionCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import io.legado.app.data.entities.HttpTTS
import io.legado.app.data.repository.HttpTtsRepository
import io.legado.app.data.repository.ReadAloudVoiceRepository
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.gateway.ReadAloudSettingsGateway
import io.legado.app.domain.gateway.ReadSettingsGateway
import io.legado.app.domain.model.settings.OtherSettings
import io.legado.app.domain.model.settings.ReadAloudSettings
import io.legado.app.domain.model.settings.ReadSettings
import io.legado.app.domain.usecase.SyncHttpTtsCatalogUseCase
import io.legado.app.domain.usecase.SyncReadAloudVoicesUseCase
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadAloudSessionStore
import io.legado.app.model.ReadBook
import io.legado.app.support.InMemoryAppDatabaseFixture
import java.io.File
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
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
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class HttpReadAloudParagraphResumeTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var fixture: InMemoryAppDatabaseFixture
    private lateinit var service: HttpReadAloudService
    private val items = mutableListOf<MediaItem>()
    private var prepareCount = 0
    private var playCount = 0
    private lateinit var cacheDirectory: File

    @Before
    fun setUp() {
        stopKoin()
        Dispatchers.setMain(dispatcher)
        val app = RuntimeEnvironment.getApplication()
        app.injectAsAppCtx()
        fixture = InMemoryAppDatabaseFixture(app)
        val db = fixture.database
        val voices = ReadAloudVoiceRepository(db.readAloudVoiceDao, db)
        startKoin { modules(module {
            single { ReadAloudSessionStore() }
            single { HttpTtsRepository(db.httpTTSDao, db,
                SyncHttpTtsCatalogUseCase(SyncReadAloudVoicesUseCase(voices))) }
            single<ReadAloudSettingsGateway> { object : ReadAloudSettingsGateway {
                override val settings = MutableStateFlow(ReadAloudSettings(
                    ignoreAudioFocus = true, readAloudWakeLock = false,
                    streamReadAloudAudio = false, ttsParagraphInterval = 100,
                ))
                override val currentSettings get() = settings.value
                override suspend fun update(transform: (ReadAloudSettings) -> ReadAloudSettings) {
                    settings.value = transform(settings.value)
                }
            } }
            single<ReadSettingsGateway> { object : ReadSettingsGateway {
                override val settings = MutableStateFlow(ReadSettings())
                override val currentSettings get() = settings.value
                override suspend fun update(transform: (ReadSettings) -> ReadSettings) {
                    settings.value = transform(settings.value)
                }
            } }
            single<OtherSettingsGateway> { object : OtherSettingsGateway {
                override val settings = MutableStateFlow(OtherSettings())
                override val currentSettings get() = settings.value
                override suspend fun update(transform: (OtherSettings) -> OtherSettings) {
                    settings.value = transform(settings.value)
                }
            } }
        }) }
        setStatic("isRun", false)
        ReadBook.clearCurrentBook()
        setStatic("stopRequested", false)
        setStatic("pause", false)
        ReadAloud.httpTTS = HttpTTS(id = 2443, name = "test", url = "http://localhost/unused")
        db.httpTTSDao.insert(requireNotNull(ReadAloud.httpTTS))
        service = Robolectric.buildService(HttpReadAloudService::class.java).get()
        service.contentList = listOf("下一段")
        val player = Proxy.newProxyInstance(ExoPlayer::class.java.classLoader,
            arrayOf(ExoPlayer::class.java)) { _, method, args ->
            when (method.name) {
                "getMediaItemCount" -> items.size
                "setMediaItem" -> { items.clear(); items.add(args!![0] as MediaItem); null }
                "clearMediaItems" -> { items.clear(); null }
                "prepare" -> { prepareCount++; null }
                "play" -> { playCount++; null }
                else -> when (method.returnType) {
                    java.lang.Boolean.TYPE -> false
                    java.lang.Integer.TYPE -> 0
                    java.lang.Long.TYPE -> 0L
                    java.lang.Float.TYPE -> 0f
                    else -> null
                }
            }
        } as ExoPlayer
        setField("exoPlayer\$delegate", lazyOf(player))
        cacheDirectory = File(app.cacheDir, "paragraph-resume-test").apply { mkdirs() }
        cacheDirectory.listFiles()?.forEach { it.delete() }
        setField("ttsFolderPath\$delegate", lazyOf(cacheDirectory.absolutePath + File.separator))
    }

    @After
    fun tearDown() {
        if (::service.isInitialized) {
            service.playStop()
            service.lifecycleScope.cancel()
            BaseReadAloudService::class.java.getDeclaredMethod("getMediaSessionCompat").apply {
                isAccessible = true
                (invoke(service) as MediaSessionCompat).release()
            }
        }
        ReadAloud.httpTTS = null
        BaseReadAloudService.requestStop()
        ReadBook.stopAutoSaveSession()
        fixture.close()
        stopKoin()
        Dispatchers.resetMain()
    }

    @Test
    fun pauseCancelsBothTheGapAndThePendingFileResume() {
        val interval = pendingJob()
        val resume = pendingJob()
        setField("paragraphIntervalJob", interval)
        setField("paragraphResumeJob", resume)

        service.pauseReadAloud(false)

        assertTrue(BaseReadAloudService.pause)
        assertTrue("Gap timer must not outlive pause", interval.isCancelled)
        assertTrue("File wait must not outlive pause", resume.isCancelled)
    }

    @Test
    fun resumeWithEmptyPlaylistInstallsTheCurrentCachedParagraphWithoutRestartingSynthesis() {
        cacheCurrentParagraph()
        val download = pendingJob()
        setField("downloadTask", download)
        setStatic("pause", true)

        service.resumeReadAloud()
        drainUntil { items.isNotEmpty() }

        assertFalse(BaseReadAloudService.pause)
        assertEquals(1, items.size)
        assertEquals(1, prepareCount)
        assertTrue("Current chapter synthesis must survive resume", download.isActive)
    }

    @Test
    fun pauseDuringFileWaitCannotBeUndoneWhenSynthesisStops() {
        val download = pendingJob()
        setField("downloadTask", download)
        invokeNextParagraph()
        dispatcher.scheduler.runCurrent()
        val resume = getField("paragraphResumeJob") as Coroutine<*>

        service.pauseReadAloud(false)
        download.cancel()
        drainUntil { resume.isCompleted }

        assertTrue("Failed wait must preserve explicit pause", BaseReadAloudService.pause)
        assertTrue(items.isEmpty())
        assertEquals(0, prepareCount)
    }

    @Test
    fun resumeWithExistingMediaKeepsItsPlaybackPosition() {
        items.add(MediaItem.fromUri("file:///already-playing.mp3"))
        setStatic("pause", true)

        service.resumeReadAloud()

        assertFalse(BaseReadAloudService.pause)
        assertEquals(1, playCount)
        assertEquals(0, prepareCount)
    }

    private fun pendingJob() = service.execute(context = dispatcher) { awaitCancellation() }

    private fun cacheCurrentParagraph() {
        val synthesis = HttpReadAloudService::class.java.getDeclaredMethod(
            "cueSynthesisFor", Int::class.javaPrimitiveType, HttpTTS::class.java,
            Boolean::class.javaPrimitiveType,
        ).apply { isAccessible = true }.invoke(service, 0, ReadAloud.httpTTS, false)!!
        val name = synthesis.javaClass.getDeclaredField("fileName").apply {
            isAccessible = true
        }.get(synthesis) as String
        File(cacheDirectory, "$name.mp3").writeBytes(byteArrayOf(1, 2, 3))
    }

    private fun invokeNextParagraph() {
        HttpReadAloudService::class.java.getDeclaredMethod("playNextParagraph").apply {
            isAccessible = true
        }.invoke(service)
    }

    private fun drainUntil(done: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!done() && System.nanoTime() < deadline) {
            dispatcher.scheduler.runCurrent()
            Thread.sleep(10)
        }
        dispatcher.scheduler.runCurrent()
        assertTrue("Async playback operation did not finish", done())
    }

    private fun setStatic(name: String, value: Any) {
        BaseReadAloudService::class.java.getDeclaredField(name).apply {
            isAccessible = true
            set(null, value)
        }
    }

    private fun setField(name: String, value: Any) {
        HttpReadAloudService::class.java.getDeclaredField(name).apply {
            isAccessible = true
            set(service, value)
        }
    }

    private fun getField(name: String): Any? =
        HttpReadAloudService::class.java.getDeclaredField(name).run {
            isAccessible = true
            get(service)
        }
}
