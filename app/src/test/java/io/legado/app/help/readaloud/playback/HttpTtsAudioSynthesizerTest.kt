package io.legado.app.help.readaloud.playback

import android.app.Application
import com.sun.net.httpserver.HttpServer
import io.legado.app.data.repository.HttpTtsRepository
import io.legado.app.domain.gateway.DownloadCacheSettingsGateway
import io.legado.app.domain.model.readaloud.HttpTtsEmotionDraft
import io.legado.app.domain.model.readaloud.ReadAloudVoice
import io.legado.app.domain.model.settings.DownloadCacheSettings
import io.legado.app.domain.usecase.SyncHttpTtsCatalogUseCase
import io.legado.app.domain.usecase.SyncReadAloudVoicesUseCase
import io.legado.app.data.repository.ReadAloudVoiceRepository
import io.legado.app.data.entities.HttpTTS
import io.legado.app.help.readaloud.cast.VoiceAudition
import io.legado.app.domain.gateway.ReadAloudVoiceGateway
import io.legado.app.support.InMemoryAppDatabaseFixture
import io.legado.app.utils.GSON
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import org.junit.After
import org.junit.Assert.*
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

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class HttpTtsAudioSynthesizerTest {
    private lateinit var fixture: InMemoryAppDatabaseFixture
    private lateinit var repository: HttpTtsRepository
    private lateinit var synthesizer: HttpTtsAudioSynthesizer
    private lateinit var server: HttpServer
    private val requests = LinkedBlockingQueue<Pair<String, String?>>()

    @Before fun setUp() {
        stopKoin()
        val app = RuntimeEnvironment.getApplication()
        app.injectAsAppCtx()
        fixture = InMemoryAppDatabaseFixture(app)
        val db = fixture.database
        val voiceGateway = ReadAloudVoiceRepository(db.readAloudVoiceDao, db)
        repository = HttpTtsRepository(db.httpTTSDao, db, SyncHttpTtsCatalogUseCase(SyncReadAloudVoicesUseCase(voiceGateway)))
        synthesizer = HttpTtsAudioSynthesizer(repository)
        startKoin { modules(module {
            single<DownloadCacheSettingsGateway> { object : DownloadCacheSettingsGateway {
                override val settings = MutableStateFlow(DownloadCacheSettings(cronetEnabled = false))
                override val currentSettings get() = settings.value
                override suspend fun update(transform: (DownloadCacheSettings) -> DownloadCacheSettings) {
                    settings.value = transform(settings.value)
                }
            } }
            single<ReadAloudVoiceGateway> { voiceGateway }
            single { repository }
        }) }
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/tts") { exchange ->
            requests.add(exchange.requestURI.rawQuery.orEmpty() to exchange.requestHeaders.getFirst("X-Source"))
            exchange.responseHeaders.add("Content-Type", "audio/mpeg")
            exchange.sendResponseHeaders(200, 3)
            exchange.responseBody.use { it.write(byteArrayOf(1, 2, 3)) }
        }
        server.start()
    }

    @After fun tearDown() {
        server.stop(0)
        fixture.close()
        stopKoin()
    }

    @Test fun `HTTP synthesis and shared audition run source script headers login check and variables`() = runBlocking {
        val source = source().apply {
            jsLib = "function tone(value) { return value + '-source'; }"
            url += "?voice={{tone(currentToneID)}}&speaker={{currentSpeakerName}}&emotion={{currentEmotionTag}}&name={{currentEmotionName}}&speed={{speakSpeed}}"
            loginCheckJs = "if (result.code() != 200) throw 'bad status'; result;"
        }
        repository.insert(source)
        val voice = voice(source)
        fixture.database.readAloudVoiceDao.upsertVoice(io.legado.app.data.entities.ReadAloudVoiceEntity(
            voice.id, voice.engineType, voice.engineId, voice.speakerId, voice.displayName,
            emotionCatalogJson = voice.emotionCatalogJson,
            managedBy = ReadAloudVoice.MANAGED_BY_HTTP_SOURCE_CATALOG,
        ))
        val output = java.io.File(RuntimeEnvironment.getApplication().cacheDir, "http-test.audio")
        assertTrue(synthesizer.synthesize(voice, "hello", output))
        assertArrayEquals(byteArrayOf(1, 2, 3), output.readBytes())
        val preview = requests.poll(5, TimeUnit.SECONDS)!!
        val query = URLDecoder.decode(preview.first, "UTF-8")
        assertTrue(query.contains("voice=tone-source"))
        assertTrue(query.contains("speaker=晓晓"))
        assertTrue(query.contains("speed=15"))
        assertEquals("header-value", preview.second)
        synthesizer.openStream(source, "hello", voice, "cheerful").use { it.readBytes() }
        val emotional = URLDecoder.decode(requests.poll(5, TimeUnit.SECONDS)!!.first, "UTF-8")
        assertTrue(emotional.contains("emotion=happy"))
        assertTrue(emotional.contains("name=开心"))
        synthesizer.openStream(
            source, "engine default", voice.copy(speakerId = "", emotionCatalogJson = "[]"), "cheerful"
        ).use { it.readBytes() }
        val defaultEmotion = URLDecoder.decode(requests.poll(5, TimeUnit.SECONDS)!!.first, "UTF-8")
        assertTrue(defaultEmotion.contains("emotion=happy"))
        assertTrue(defaultEmotion.contains("name=开心"))
        assertNotNull(VoiceAudition.synthesize(RuntimeEnvironment.getApplication(), voice.id, "shared audition"))
    }

    @Test fun `quota covers response consumption and releases on close and cancelled wait`() = runBlocking {
        val source = source()
        val input = synthesizer.openStream(source, "playing", voice(source))
        val waiting = async(Dispatchers.IO) { synthesizer.openStream(source, "background", voice(source)) }
        delay(100)
        assertFalse(waiting.isCompleted)
        waiting.cancel()
        assertTrue(runCatching { waiting.await() }.exceptionOrNull() is CancellationException)
        input.close()
        withTimeout(5000) {
            synthesizer.openStream(source, "next", voice(source)).use { assertEquals(3, it.readBytes().size) }
        }
    }

    @Test fun `failed login check releases quota for next attempt`() = runBlocking {
        val source = source().apply { loginCheckJs = "throw 'login required'" }
        assertTrue(runCatching { synthesizer.openStream(source, "fail", voice(source)) }.isFailure)
        source.loginCheckJs = null
        withTimeout(5000) { synthesizer.openStream(source, "retry", voice(source)).close() }
    }

    @Test fun `cancelling stream owner closes response and releases its source quota`() = runBlocking {
        val owner = kotlinx.coroutines.Job()
        val source = source()
        val input = synthesizer.openStream(source, "playing", voice(source), ownerJob = owner)
        owner.cancel()
        withTimeout(5000) {
            owner.join()
            synthesizer.openStream(source, "next", voice(source)).use { it.readBytes() }
        }
        input.close()
    }

    @Test fun `missing source never sends its tone to another source`() = runBlocking {
        val source = source()
        repository.insert(source)
        val output = java.io.File(RuntimeEnvironment.getApplication().cacheDir, "missing.audio")
        val error = runCatching {
            synthesizer.synthesize(voice(source).copy(engineId = "404"), "missing", output)
        }.exceptionOrNull()
        assertTrue(error?.message.orEmpty().contains("HTTP TTS 引擎不存在"))
        assertTrue(requests.isEmpty())
    }

    @Test fun `cancel during body read stays cancellation and removes partial audition file`() = runBlocking {
        val started = java.util.concurrent.CountDownLatch(1)
        val finish = java.util.concurrent.CountDownLatch(1)
        server.createContext("/slow") { exchange ->
            exchange.responseHeaders.add("Content-Type", "audio/mpeg")
            exchange.sendResponseHeaders(200, 1024)
            try {
                exchange.responseBody.write(byteArrayOf(1, 2, 3))
                exchange.responseBody.flush()
                started.countDown()
                finish.await(10, TimeUnit.SECONDS)
            } finally { runCatching { exchange.close() } }
        }
        val source = source().apply { url = "http://127.0.0.1:${server.address.port}/slow" }
        repository.insert(source)
        val output = java.io.File(RuntimeEnvironment.getApplication().cacheDir, "cancelled.audio")
        val task = async(Dispatchers.IO) { synthesizer.synthesize(voice(source), "cancel", output) }
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS))
            withTimeout(5000) {
                while (output.length() == 0L) delay(10)
                task.cancel()
                task.join()
            }
            assertTrue(task.isCancelled)
            assertFalse(output.exists())
        } finally {
            task.cancel()
            finish.countDown()
        }
    }

    private fun source() = HttpTTS(
        id = 987654321, name = "local test", url = "http://127.0.0.1:${server.address.port}/tts",
        header = """{"X-Source":"header-value"}""", speed = 10, synthesisThreadCount = 1,
    )

    private fun voice(source: HttpTTS) = ReadAloudVoice(
        id = "http-test", engineType = "http", engineId = source.id.toString(), speakerId = "tone", displayName = "晓晓",
        emotionCatalogJson = GSON.toJson(listOf(HttpTtsEmotionDraft("默认", "neutral"), HttpTtsEmotionDraft("开心", "happy"))),
    )
}
