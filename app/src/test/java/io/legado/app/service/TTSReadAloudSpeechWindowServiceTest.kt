package io.legado.app.service

import android.app.Application
import android.os.Bundle
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.lifecycle.lifecycleScope
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.gateway.ReadAloudSettingsGateway
import io.legado.app.domain.model.readaloud.ChapterSpeechSegment
import io.legado.app.domain.model.readaloud.ReadAloudPlaybackCursor
import io.legado.app.domain.model.readaloud.ReadAloudPlaybackQueue
import io.legado.app.domain.model.readaloud.SpeechPlanItem
import io.legado.app.domain.model.readaloud.SpeechResolutionSource
import io.legado.app.domain.model.readaloud.SpeechRoleType
import io.legado.app.domain.model.settings.OtherSettings
import io.legado.app.domain.model.settings.ReadAloudSettings
import io.legado.app.model.ReadAloudSessionStore
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class TTSReadAloudSpeechWindowServiceTest {

    private lateinit var service: TTSReadAloudService
    private lateinit var engine: TestEngine
    private val settings = MutableStateFlow(ReadAloudSettings(ignoreAudioFocus = true))

    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        startKoin {
            modules(module {
                single { ReadAloudSessionStore() }
                single<OtherSettingsGateway> {
                    object : OtherSettingsGateway {
                        override val settings = MutableStateFlow(OtherSettings())
                        override val currentSettings get() = settings.value

                        override suspend fun update(transform: (OtherSettings) -> OtherSettings) {
                            settings.value = transform(settings.value)
                        }
                    }
                }
                single<ReadAloudSettingsGateway> {
                    object : ReadAloudSettingsGateway {
                        override val settings = this@TTSReadAloudSpeechWindowServiceTest.settings
                        override val currentSettings get() = settings.value

                        override suspend fun update(transform: (ReadAloudSettings) -> ReadAloudSettings) {
                            settings.value = transform(settings.value)
                        }
                    }
                }
            })
        }
        BaseReadAloudService::class.java.getDeclaredField("stopRequested").apply {
            isAccessible = true
            setBoolean(null, false)
        }
        service = Robolectric.buildService(TTSReadAloudService::class.java).get()
        val listener = serviceField("ttsUtteranceListener").get(service) as UtteranceProgressListener
        engine = TestEngine(listener)
        serviceField("textToSpeech").set(service, engine)
        serviceField("ttsInitFinish").setBoolean(service, true)
    }

    @After
    fun tearDown() {
        try {
            service.clearTTS()
            service.lifecycleScope.cancel()
            BaseReadAloudService.requestStop()
        } finally {
            stopKoin()
        }
    }

    @Test
    fun submitsTheLastCueAfterTheParagraphInterval() {
        settings.value = settings.value.copy(ttsParagraphInterval = 1)
        play("A", "B")

        engine.listener.onDone(assertNextSpoken("A").id)
        shadowOf(Looper.getMainLooper()).idle()

        assertNextSpoken("B")
        assertEquals(1, service.nowSpeak)
    }

    @Test
    fun refillSkipsAnAheadCueThatFailedDuringSubmission() {
        engine.failWhileSubmitting = "B"
        play("A", "B", "C", "D")

        val first = assertNextSpoken("A")
        assertNextSpoken("B")
        assertNextSpoken("C")
        shadowOf(Looper.getMainLooper()).idle()
        assertNextSpoken("D")

        engine.listener.onDone(first.id)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2, service.nowSpeak)
        assertTrue(engine.submissions.isEmpty())
    }

    private fun play(vararg lines: String) {
        val plan = lines.mapIndexed { index, line ->
            SpeechPlanItem(
                segment = ChapterSpeechSegment(
                    id = index.toString(),
                    analysisId = "test",
                    bookUrl = "test",
                    chapterIndex = 0,
                    text = line,
                    chapterPosition = index,
                    paragraphIndex = index,
                    start = 0,
                    end = line.length,
                    roleType = SpeechRoleType.Narrator,
                    source = SpeechResolutionSource.Rule,
                ),
                voice = null,
                fallbackVoices = emptyList(),
            )
        }
        service.contentList = lines.toList()
        service.playbackQueue = ReadAloudPlaybackQueue.from(plan)
        service.playbackCursor = ReadAloudPlaybackCursor(0, 0)
        service.play()
    }

    private fun assertNextSpoken(text: String): Submission {
        shadowOf(Looper.getMainLooper()).idle()
        val submission = engine.submissions.poll(5, TimeUnit.SECONDS)
        assertNotNull("Expected TTS to submit $text", submission)
        assertEquals(text, submission!!.text)
        return submission
    }

    private fun serviceField(name: String) = TTSReadAloudService::class.java.getDeclaredField(name)
        .apply { isAccessible = true }

    private data class Submission(val text: String, val id: String)

    private class TestEngine(val listener: UtteranceProgressListener) :
        TextToSpeech(RuntimeEnvironment.getApplication(), null) {

        val submissions = LinkedBlockingQueue<Submission>()
        var failWhileSubmitting: String? = null

        override fun speak(text: CharSequence, queueMode: Int, params: Bundle?, utteranceId: String): Int {
            if (text.toString() == failWhileSubmitting) listener.onError(utteranceId, ERROR_SYNTHESIS)
            submissions.add(Submission(text.toString(), utteranceId))
            return SUCCESS
        }
    }
}
