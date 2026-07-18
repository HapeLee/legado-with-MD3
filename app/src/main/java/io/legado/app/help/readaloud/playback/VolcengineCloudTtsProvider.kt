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
import org.json.JSONObject
import java.util.UUID

internal class VolcengineCloudTtsProvider : CloudTtsProvider {
    override val type = CloudTtsProviderType.Volcengine

    override fun fetchVoices(engine: CloudTtsEngine): List<CloudTtsVoiceDescriptor> = BUILT_IN_VOICES

    override fun synthesize(
        engine: CloudTtsEngine,
        request: CloudTtsSynthesisRequest,
    ): CloudTtsAudio {
        require(engine.appId.isNotBlank()) { "火山引擎 App ID 不能为空" }
        require(engine.apiKey.isNotBlank()) { "火山引擎 Access Token 不能为空" }
        require(request.speed.isFinite() && request.speed in 0.2f..3f) {
            "火山引擎语速必须在 0.2 到 3.0 之间"
        }
        require(request.pitch.isFinite() && request.pitch in 0.1f..3f) {
            "火山引擎音调必须在 0.1 到 3.0 之间"
        }
        require(request.volume.isFinite() && request.volume in 0.1f..3f) {
            "火山引擎音量必须在 0.1 到 3.0 之间"
        }
        val format = request.format.lowercase()
        require(format in FORMATS) { "火山引擎不支持音频格式：$format" }
        val options = runCatching { JSONObject(engine.optionsJson) }.getOrElse { JSONObject() }
        val body = JSONObject().apply {
            put("app", JSONObject()
                .put("appid", engine.appId)
                .put("token", engine.apiKey)
                .put("cluster", options.optString("cluster", "volcano_tts")))
            put("user", JSONObject().put("uid", options.optString("uid", "legado")))
            put("audio", JSONObject()
                .put("voice_type", request.voiceId)
                .put("encoding", format)
                .put("speed_ratio", request.speed)
                .put("volume_ratio", request.volume)
                .put("pitch_ratio", request.pitch)
                .apply { if (request.style.isNotBlank()) put("emotion", request.style) })
            put("request", JSONObject()
                .put("reqid", UUID.randomUUID().toString())
                .put("text", request.text)
                .put("text_type", "plain")
                .put("operation", "query"))
        }
        val httpRequest = Request.Builder()
            .url(engine.baseUrl.ifBlank { DEFAULT_ENDPOINT })
            .header("Authorization", "Bearer;${engine.apiKey}")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return cloudTtsHttpClient.newCall(httpRequest).execute().use { response ->
            val json = JSONObject(response.requireCloudTtsSuccessful("火山引擎 TTS").body.string())
            val code = json.optInt("code")
            require(code == 0 || code == 3000) {
                "火山引擎 TTS 请求失败（$code）：${json.optString("message")}"
            }
            val data = json.optString("data")
            require(data.isNotBlank()) { "火山引擎 TTS 没有返回音频数据" }
            CloudTtsAudio(Base64.decode(data, Base64.DEFAULT), format)
        }
    }

    private companion object {
        const val DEFAULT_ENDPOINT = "https://openspeech.bytedance.com/api/v1/tts"
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        val FORMATS = setOf("mp3", "ogg_opus", "wav", "pcm")
        val BUILT_IN_VOICES = listOf(
            CloudTtsVoiceDescriptor("BV001_streaming", "通用女声", "zh-CN", "Female"),
            CloudTtsVoiceDescriptor("BV002_streaming", "通用男声", "zh-CN", "Male"),
            CloudTtsVoiceDescriptor("BV700_streaming", "灿灿", "zh-CN", "Female"),
            CloudTtsVoiceDescriptor("BV701_streaming", "擎苍", "zh-CN", "Male"),
        )
    }
}
