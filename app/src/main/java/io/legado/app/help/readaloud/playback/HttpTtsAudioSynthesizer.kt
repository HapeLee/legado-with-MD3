package io.legado.app.help.readaloud.playback

import io.legado.app.data.entities.HttpTTS
import io.legado.app.data.repository.HttpTtsRepository
import io.legado.app.domain.model.readaloud.ReadAloudVoice
import io.legado.app.exception.NoStackTraceException
import io.legado.app.model.analyzeRule.AnalyzeUrl
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Response

/** 试听、实时、后台和流式朗读共用源脚本、Cookie、登录检查与源级配额。 */
class HttpTtsAudioSynthesizer(private val repository: HttpTtsRepository) {
    suspend fun synthesize(voice: ReadAloudVoice, text: String, output: File): Boolean =
        withContext(Dispatchers.IO) {
            val source = voice.engineId.toLongOrNull()?.let { repository.findById(it) }
                ?: throw NoStackTraceException("HTTP TTS 引擎不存在：${voice.engineId}")
            output.parentFile?.mkdirs()
            try {
                openStream(source, text, voice, ownerJob = currentCoroutineContext()[Job]).use { input ->
                    output.outputStream().use { target ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            target.write(buffer, 0, count)
                        }
                    }
                }
                output.length() > 0
            } catch (error: Throwable) {
                output.delete()
                currentCoroutineContext().ensureActive()
                throw error
            }
        }

    suspend fun openStream(
        source: HttpTTS,
        text: String,
        voice: ReadAloudVoice? = null,
        emotion: String = "",
        ownerJob: Job? = null,
    ): InputStream {
        require(voice == null ||
            (voice.engineType == ReadAloudVoice.ENGINE_HTTP && voice.engineId == source.id.toString())) {
            "HTTP TTS 音色与源不匹配"
        }
        val quota = HttpTtsSynthesisLimiter.shared.acquire(source.id.toString(), source.synthesisThreadCount)
        val activeCall = AtomicReference<Call?>()
        // 取消 Call 会打断 socket；不能在读取线程之外关闭 Okio 的响应体。
        val cancellation = ownerJob?.let { owner ->
            CoroutineScope(owner + Dispatchers.Unconfined).launch(start = CoroutineStart.UNDISPATCHED) {
                try { awaitCancellation() } finally {
                    if (!owner.isActive) activeCall.get()?.cancel()
                    quota.close()
                }
            }
        }
        var response: Response? = null
        var ownedStream: InputStream? = null
        return try {
            currentCoroutineContext().ensureActive()
            val catalog = HttpTtsEmotionMapper.parseCatalog(voice?.emotionCatalogJson).ifEmpty {
                HttpTtsEmotionMapper.parseCatalog(repository.getEmotionCatalog(source.id.toString()))
            }
            val mapped = HttpTtsEmotionMapper.map(emotion, catalog)
            val tone = voice?.speakerId.orEmpty()
            val analyze = AnalyzeUrl(
                source.url,
                speakText = text,
                speakSpeed = (source.speed ?: 5) + 5,
                currentToneID = tone,
                currentSpeakerName = if (tone.isBlank()) "" else voice?.displayName.orEmpty(),
                currentEmotionName = mapped.emotionName,
                currentEmotionTag = mapped.emotionTag,
                source = source,
                readTimeout = 300 * 1000L,
                coroutineContext = currentCoroutineContext(),
            )
            var audioResponse = analyze.getResponseAwait { call ->
                activeCall.set(call)
                if (ownerJob?.isActive == false) call.cancel()
            }
            response = audioResponse
            source.loginCheckJs?.takeIf { it.isNotBlank() }?.let { script ->
                val checked = analyze.evalJS(script, audioResponse) as Response
                if (checked !== audioResponse && checked.body !== audioResponse.body) audioResponse.close()
                audioResponse = checked
                response = checked
            }
            currentCoroutineContext().ensureActive()
            audioResponse.headers["Content-Type"]?.substringBefore(';')?.let { contentType ->
                val expected = source.contentType
                if (contentType == "application/json" || contentType.startsWith("text/") ||
                    (!expected.isNullOrBlank() && !contentType.matches(expected.toRegex()))) {
                    throw NoStackTraceException("TTS服务器返回错误：${audioResponse.body.string()}")
                }
            }
            val input = audioResponse.body.byteStream()
            val stream = object : FilterInputStream(input) {
                private val closed = AtomicBoolean()
                override fun close() {
                    if (closed.compareAndSet(false, true)) try {
                        audioResponse.close()
                    } finally {
                        quota.close()
                        cancellation?.cancel()
                    }
                }
                override fun read(): Int = consume { input.read() }
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                    consume { input.read(buffer, offset, length) }

                private inline fun consume(read: () -> Int): Int = try {
                    read().also { if (it < 0) close() }
                } catch (error: Throwable) {
                    close()
                    throw error
                }
            }
            ownedStream = stream
            currentCoroutineContext().ensureActive()
            stream
        } catch (error: Throwable) {
            try { ownedStream?.close() ?: response?.close() } finally {
                cancellation?.cancel()
                quota.close()
            }
            currentCoroutineContext().ensureActive()
            throw error
        }
    }
}

internal fun httpTtsAudioCacheSourceKey(source: HttpTTS, voice: ReadAloudVoice, emotion: String): String {
    // 保留旧默认音色在默认合成语速下的离线缓存。
    if (voice.speakerId.isBlank() && emotion.isBlank() && (source.speed == null || source.speed == 5) &&
        HttpTtsEmotionMapper.parseCatalog(voice.emotionCatalogJson).isEmpty()) {
        return source.url
    }
    return "http:${voice.id}:${voice.revision}:${source.id}:${voice.speakerId}:$emotion:" +
        "${HttpTtsEmotionMapper.VERSION}:${voice.emotionCatalogJson}:${source.speed}:${source.lastUpdateTime}:" +
        "${source.header}:${source.jsLib}:${source.url}"
}
