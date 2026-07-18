package io.legado.app.help.readaloud.playback

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume

/** Serializes Android TTS file synthesis and reuses the active engine between adjacent cues. */
class SystemTtsFileSynthesizer(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private var tts: TextToSpeech? = null
    private var activeEngine = ""

    suspend fun synthesize(
        engine: String,
        voiceName: String,
        text: String,
        output: File,
        speechRate: Float,
        pitch: Float = 1f,
    ): Boolean = mutex.withLock {
        val instance = ensureEngine(engine) ?: return@withLock false
        if (voiceName.isNotBlank()) {
            val voice = instance.voices.orEmpty().firstOrNull { it.name == voiceName }
                ?: return@withLock false
            if (instance.setVoice(voice) == TextToSpeech.ERROR) return@withLock false
        }
        output.parentFile?.mkdirs()
        if (output.exists()) output.delete()
        val utteranceId = "legado-file-${UUID.randomUUID()}"
        suspendCancellableCoroutine { continuation ->
            instance.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(completedId: String?) {
                    if (completedId == utteranceId && continuation.isActive) {
                        continuation.resume(output.exists() && output.length() > 0)
                    }
                }

                override fun onError(failedId: String?) {
                    if (failedId == utteranceId && continuation.isActive) continuation.resume(false)
                }

                override fun onError(failedId: String?, errorCode: Int) = onError(failedId)
            })
            if (instance.setSpeechRate(speechRate) == TextToSpeech.ERROR ||
                instance.setPitch(pitch) == TextToSpeech.ERROR
            ) {
                continuation.resume(false)
                return@suspendCancellableCoroutine
            }
            val result = instance.synthesizeToFile(text, null, output, utteranceId)
            if (result == TextToSpeech.ERROR && continuation.isActive) continuation.resume(false)
            continuation.invokeOnCancellation { instance.stop() }
        }
    }

    suspend fun close() = withContext(Dispatchers.Main.immediate) {
        tts?.stop()
        tts?.shutdown()
        tts = null
        activeEngine = ""
    }

    private suspend fun ensureEngine(engine: String): TextToSpeech? {
        if (tts != null && activeEngine == engine) return tts
        close()
        return withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                lateinit var instance: TextToSpeech
                val listener = TextToSpeech.OnInitListener { status ->
                    if (!continuation.isActive) return@OnInitListener
                    if (status == TextToSpeech.SUCCESS) {
                        tts = instance
                        activeEngine = engine
                        continuation.resume(instance)
                    } else {
                        instance.shutdown()
                        continuation.resume(null)
                    }
                }
                instance = if (engine.isBlank()) {
                    TextToSpeech(appContext, listener)
                } else {
                    TextToSpeech(appContext, listener, engine)
                }
                continuation.invokeOnCancellation { instance.shutdown() }
            }
        }
    }
}
