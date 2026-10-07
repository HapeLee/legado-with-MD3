package io.legado.app.help.readaloud.playback

import android.content.Context
import android.speech.tts.TextToSpeech
import io.legado.app.constant.AppLog
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
            tts.voices
                .orEmpty()
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
                // 有的引擎（小米系统语音引擎）让多个 locale 复用同一个 Voice.name，
                // 而我们只用 name 去 setVoice：重复项既没意义又会让列表 key 撞车
                .distinctBy { it.id }
        } ?: emptyList()

    private suspend fun <T> withTts(
        enginePackage: String,
        block: suspend (TextToSpeech) -> T,
    ): T? {
        // 实例必须在有 Looper 的线程上建，读音色却会同步等引擎回包，所以读放在 IO 上
        val tts = withContext(Dispatchers.Main.immediate) { createTts(enginePackage) }
            ?: return null
        return try {
            withContext(Dispatchers.IO) { block(tts) }
        } finally {
            withContext(Dispatchers.Main.immediate) {
                tts.stop()
                tts.shutdown()
            }
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
                    AppLog.putDebug("系统 TTS 初始化失败: $enginePackage status=$status")
                    initialized?.shutdown()
                    continuation.resume(null)
                }
            }
            instance = if (enginePackage.isBlank()) {
                TextToSpeech(appContext, listener)
            } else {
                TextToSpeech(appContext, listener, enginePackage)
            }
            continuation.invokeOnCancellation { instance.shutdown() }
        }

    companion object {
        fun engineId(sourceId: String): String = "system:$sourceId"
    }
}
