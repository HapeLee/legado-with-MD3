package io.legado.app.help.readaloud.playback

import android.content.Context
import android.speech.tts.TextToSpeech
import io.legado.app.domain.model.readaloud.TtsEngineDescriptor
import io.legado.app.domain.model.readaloud.TtsEngineKind
import io.legado.app.domain.model.readaloud.TtsNativeVoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class SystemTtsVoiceCatalog(context: Context) {

    private val appContext = context.applicationContext

    suspend fun getEngines(): List<TtsEngineDescriptor> = withTts("") { tts ->
        tts.engines
            .distinctBy { it.name }
            .map { engine ->
                TtsEngineDescriptor(
                    id = engineId(engine.name),
                    kind = TtsEngineKind.System,
                    sourceId = engine.name,
                    displayName = engine.label.ifBlank { engine.name },
                    providerName = engine.name,
                    supportsVoiceDiscovery = true,
                )
            }
            .sortedBy { it.displayName.lowercase() }
    } ?: emptyList()

    suspend fun getVoices(enginePackage: String): List<TtsNativeVoice> =
        withTts(enginePackage) { tts ->
            tts.voices.orEmpty()
                .map { voice ->
                    TtsNativeVoice(
                        id = voice.name,
                        engineId = engineId(enginePackage),
                        displayName = voice.name,
                        locale = voice.locale.toLanguageTag(),
                        quality = voice.quality,
                        latency = voice.latency,
                        requiresNetwork = voice.isNetworkConnectionRequired,
                    )
                }
                .sortedWith(compareBy<TtsNativeVoice> { it.locale }.thenBy { it.displayName })
        } ?: emptyList()

    private suspend fun <T> withTts(
        enginePackage: String,
        block: (TextToSpeech) -> T,
    ): T? = withContext(Dispatchers.Main.immediate) {
        val tts = createTts(enginePackage) ?: return@withContext null
        try {
            block(tts)
        } finally {
            tts.stop()
            tts.shutdown()
        }
    }

    private suspend fun createTts(enginePackage: String): TextToSpeech? =
        suspendCancellableCoroutine { continuation ->
            var instance: TextToSpeech? = null
            val listener = TextToSpeech.OnInitListener { status ->
                val initialized = instance
                if (!continuation.isActive) {
                    initialized?.shutdown()
                } else if (status == TextToSpeech.SUCCESS) {
                    continuation.resume(initialized)
                } else {
                    initialized?.shutdown()
                    continuation.resume(null)
                }
            }
            instance = if (enginePackage.isBlank()) {
                TextToSpeech(appContext, listener)
            } else {
                TextToSpeech(appContext, listener, enginePackage)
            }
            continuation.invokeOnCancellation { instance?.shutdown() }
        }

    companion object {
        fun engineId(sourceId: String): String = "system:$sourceId"
    }
}
