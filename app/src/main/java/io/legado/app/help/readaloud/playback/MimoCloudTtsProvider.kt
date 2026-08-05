package io.legado.app.help.readaloud.playback

import android.util.Base64
import io.legado.app.domain.model.readaloud.CloudTtsAudio
import io.legado.app.domain.model.readaloud.CloudTtsEngine
import io.legado.app.domain.model.readaloud.CloudTtsProviderType
import io.legado.app.domain.model.readaloud.CloudTtsSynthesisRequest
import io.legado.app.domain.model.readaloud.CloudTtsVoiceDescriptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

internal class MimoCloudTtsProvider : CloudTtsProvider {
    override val type = CloudTtsProviderType.Mimo

    override fun fetchVoices(engine: CloudTtsEngine) = BUILT_IN_VOICES

    override fun synthesize(
        engine: CloudTtsEngine,
        request: CloudTtsSynthesisRequest,
    ): CloudTtsAudio {
        require(engine.apiKey.isNotBlank()) { "MiMo API Key 不能为空" }
        require(engine.model.isNotBlank()) { "MiMo 模型不能为空" }
        require(request.text.isNotBlank()) { "朗读文本不能为空" }
        val expression = request.style.takeIf(String::isNotBlank)?.let { "($it)" }.orEmpty()
        val messages = JSONArray().apply {
            request.instructions.takeIf(String::isNotBlank)?.let { instruction ->
                put(JSONObject().put("role", "user").put("content", instruction))
            }
            put(JSONObject()
                .put("role", "assistant")
                .put("content", "$expression${request.text}"))
        }
        val body = JSONObject().apply {
            put("model", engine.model)
            put("messages", messages)
            put("audio", JSONObject().put("format", "pcm16").put("voice", request.voiceId))
            put("stream", true)
        }
        val baseUrl = engine.baseUrl.ifBlank { DEFAULT_BASE_URL }.trimEnd('/')
        val httpRequest = Request.Builder()
            .url("$baseUrl/chat/completions")
            .header("api-key", engine.apiKey)
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return cloudTtsHttpClient.newCall(httpRequest).execute().use { response ->
            response.requireCloudTtsSuccessful("MiMo TTS")
            val pcm = ByteArrayOutputStream()
            response.body.source().use { source ->
                while (!source.exhausted()) {
                    val line = source.readUtf8Line()?.trim().orEmpty()
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload == "[DONE]") break
                    val audio = runCatching {
                        JSONObject(payload).optJSONArray("choices")?.optJSONObject(0)
                            ?.optJSONObject("delta")?.optJSONObject("audio")?.optString("data")
                    }.getOrNull()
                    if (!audio.isNullOrBlank()) pcm.write(Base64.decode(audio, Base64.DEFAULT))
                }
            }
            val bytes = pcm.toByteArray()
            require(bytes.isNotEmpty()) { "MiMo TTS 没有返回音频数据" }
            CloudTtsAudio(pcm16MonoToWav(bytes, SAMPLE_RATE), "wav", SAMPLE_RATE)
        }
    }

    private companion object {
        const val DEFAULT_BASE_URL = "https://api.xiaomimimo.com/v1"
        const val SAMPLE_RATE = 24_000
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        val STYLES = listOf("开心", "悲伤", "愤怒", "惊讶", "恐惧", "厌恶", "小声", "平静")
        val BUILT_IN_VOICES = listOf(
            CloudTtsVoiceDescriptor("mimo_default", "MiMo 默认", styles = STYLES, sampleRate = SAMPLE_RATE),
            CloudTtsVoiceDescriptor("冰糖", "冰糖", "zh-CN", "Female", STYLES, sampleRate = SAMPLE_RATE),
            CloudTtsVoiceDescriptor("茉莉", "茉莉", "zh-CN", "Female", STYLES, sampleRate = SAMPLE_RATE),
            CloudTtsVoiceDescriptor("苏打", "苏打", "zh-CN", "Male", STYLES, sampleRate = SAMPLE_RATE),
            CloudTtsVoiceDescriptor("白桦", "白桦", "zh-CN", "Male", STYLES, sampleRate = SAMPLE_RATE),
            CloudTtsVoiceDescriptor("Mia", "Mia", "en", "Female", STYLES, sampleRate = SAMPLE_RATE),
            CloudTtsVoiceDescriptor("Chloe", "Chloe", "en", "Female", STYLES, sampleRate = SAMPLE_RATE),
            CloudTtsVoiceDescriptor("Milo", "Milo", "en", "Male", STYLES, sampleRate = SAMPLE_RATE),
            CloudTtsVoiceDescriptor("Dean", "Dean", "en", "Male", STYLES, sampleRate = SAMPLE_RATE),
        )
    }
}
