package io.legado.app.help.readaloud.playback

import io.legado.app.domain.model.readaloud.CloudTtsAudio
import io.legado.app.domain.model.readaloud.CloudTtsEngine
import io.legado.app.domain.model.readaloud.CloudTtsProviderType
import io.legado.app.domain.model.readaloud.CloudTtsSynthesisRequest
import io.legado.app.domain.model.readaloud.CloudTtsVoiceDescriptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

internal class OpenAiCloudTtsProvider : CloudTtsProvider {
    override val type = CloudTtsProviderType.OpenAiSpeech

    override fun fetchVoices(engine: CloudTtsEngine): List<CloudTtsVoiceDescriptor> =
        VOICES.map { CloudTtsVoiceDescriptor(it, it.replaceFirstChar(Char::uppercase)) }

    override fun synthesize(
        engine: CloudTtsEngine,
        request: CloudTtsSynthesisRequest,
    ): CloudTtsAudio {
        require(engine.apiKey.isNotBlank()) { "OpenAI API Key 不能为空" }
        require(engine.model.isNotBlank()) { "OpenAI TTS 模型不能为空" }
        require(request.voiceId.isNotBlank()) { "OpenAI 音色不能为空" }
        require(request.speed.isFinite() && request.speed in 0.25f..4f) {
            "OpenAI TTS 速度必须在 0.25 到 4.0 之间"
        }
        val format = request.format.lowercase()
        require(format in FORMATS) { "OpenAI TTS 不支持音频格式：$format" }
        val instructions = listOfNotNull(
            request.style.takeIf(String::isNotBlank),
            request.instructions.takeIf(String::isNotBlank),
        ).joinToString(" ")
        val body = JSONObject().apply {
            put("model", engine.model)
            put("input", request.text)
            if (request.voiceId.startsWith("voice_")) {
                put("voice", JSONObject().put("id", request.voiceId))
            } else {
                put("voice", request.voiceId)
            }
            put("response_format", format)
            put("speed", request.speed)
            if (instructions.isNotBlank() && !engine.model.startsWith("tts-1")) {
                put("instructions", instructions)
            }
        }
        val baseUrl = engine.baseUrl.ifBlank { DEFAULT_BASE_URL }.trimEnd('/')
        val endpoint = if (baseUrl.endsWith("/audio/speech")) baseUrl else "$baseUrl/audio/speech"
        val httpRequest = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer ${engine.apiKey}")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return cloudTtsHttpClient.newCall(httpRequest).execute().use { response ->
            val bytes = response.requireCloudTtsSuccessful("OpenAI TTS").body.bytes()
            require(bytes.isNotEmpty()) { "OpenAI TTS 没有返回音频数据" }
            CloudTtsAudio(bytes, format)
        }
    }

    private companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        val FORMATS = setOf("mp3", "opus", "aac", "flac", "wav", "pcm")
        val VOICES = listOf(
            "alloy", "ash", "ballad", "coral", "echo", "fable", "nova", "onyx",
            "sage", "shimmer", "verse", "marin", "cedar",
        )
    }
}
