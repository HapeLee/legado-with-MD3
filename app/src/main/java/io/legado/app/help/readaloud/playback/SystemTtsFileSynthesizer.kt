package io.legado.app.help.readaloud.playback

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import io.legado.app.constant.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.uuid.Uuid
import kotlin.coroutines.resume

/** 单次整句合成文件的等待上限：引擎不按约定回调时用它兜底。 */
private const val SYNTHESIZE_TIMEOUT_MS = 45_000L

/** Serializes Android TTS file synthesis and reuses the active engine between adjacent cues. */
class SystemTtsFileSynthesizer(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private var tts: TextToSpeech? = null
    private var activeEngine = ""

    /**
     * 引擎音色表：一个引擎只枚举一次。
     *
     * `tts.voices` 是同步 binder 全量枚举，逐句合成时每一句都付一次就是句间停顿；
     * 引擎换了（[ensureEngine] 重建）才作废。空表不缓存，避免引擎未就绪时锁死在默认音色。
     */
    private var voiceCatalog: Set<Voice>? = null

    suspend fun synthesize(
        engine: String,
        voiceName: String,
        text: String,
        output: File,
        speechRate: Float,
        pitch: Float = 1f,
    ): Boolean = mutex.withLock {
        val instance = ensureEngine(engine)
            ?: return@withLock fail(engine, voiceName, "引擎起不来（初始化失败）")
        if (voiceName.isNotBlank()) {
            val voices = catalog(instance)
            val voice = voices.firstOrNull { it.name == voiceName } ?: return@withLock fail(
                engine, voiceName, "这个音色不在引擎的音色表里（枚举到 ${voices.size} 个）",
            )
            if (instance.setVoice(voice) == TextToSpeech.ERROR) {
                return@withLock fail(engine, voiceName, "引擎拒绝切到这个音色")
            }
        }
        output.parentFile?.mkdirs()
        if (output.exists()) output.delete()
        val utteranceId = "legado-file-${Uuid.random()}"
        // 引擎不按约定回调时（合成请求发出去就再无 onStart/onDone/onError）这一句会一直占着
        // 串行锁，整段朗读停在这儿不动。给一次合成设上限，到点按合成失败处理：这一句换成
        // 无声音频，下一句照旧。
        withTimeoutOrNull(SYNTHESIZE_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                instance.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit

                    override fun onDone(completedId: String?) {
                        if (completedId == utteranceId && continuation.isActive) {
                            val written = output.exists() && output.length() > 0
                            if (!written) {
                                fail(engine, voiceName, "引擎说合成完了，但没写出文件")
                            }
                            continuation.resume(written)
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(failedId: String?) {
                        if (failedId == utteranceId && continuation.isActive) {
                            fail(engine, voiceName, "引擎回调 onError（未带错误码）")
                            continuation.resume(false)
                        }
                    }

                    @Suppress("DEPRECATION")
                    override fun onError(failedId: String?, errorCode: Int) {
                        if (failedId == utteranceId) {
                            fail(engine, voiceName, "引擎报错 errorCode=$errorCode")
                            if (continuation.isActive) continuation.resume(false)
                        }
                    }
                })
                if (instance.setSpeechRate(speechRate) == TextToSpeech.ERROR ||
                    instance.setPitch(pitch) == TextToSpeech.ERROR
                ) {
                    fail(engine, voiceName, "引擎拒绝设置语速/音高")
                    continuation.resume(false)
                    return@suspendCancellableCoroutine
                }
                val result = instance.synthesizeToFile(text, null, output, utteranceId)
                if (result == TextToSpeech.ERROR && continuation.isActive) {
                    fail(engine, voiceName, "synthesizeToFile 直接返回 ERROR")
                    continuation.resume(false)
                }
                continuation.invokeOnCancellation { instance.stop() }
            }
        } ?: fail(engine, voiceName, "引擎 ${SYNTHESIZE_TIMEOUT_MS / 1000} 秒没有回调")
    }

    /**
     * 合成失败要说得出为什么失败。
     *
     * 失败的那一句会被换成无声占位音频，用户听到的只是「这一句没读」，除此之外没有任何
     * 线索；不写清原因就只能靠猜。日志不挑引擎：猜错引擎比猜错代码更浪费时间。
     */
    private fun fail(engine: String, voiceName: String, reason: String): Boolean {
        AppLog.put(
            "系统引擎文件合成失败：$reason\n引擎：$engine 音色：${voiceName.ifBlank { "(默认)" }}"
        )
        return false
    }

    suspend fun close() = withContext(Dispatchers.Main.immediate) {
        tts?.stop()
        tts?.shutdown()
        tts = null
        activeEngine = ""
        voiceCatalog = null
    }

    /** 当前引擎的音色表，只在换引擎后重新枚举。 */
    private fun catalog(instance: TextToSpeech): Set<Voice> {
        voiceCatalog?.let { return it }
        val loaded = runCatching { instance.voices }.getOrNull().orEmpty()
        if (loaded.isEmpty()) return emptySet()
        voiceCatalog = loaded
        return loaded
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
                        voiceCatalog = null
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
