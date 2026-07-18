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
import java.io.ByteArrayOutputStream

internal class AlibabaCloudTtsProvider : CloudTtsProvider {
    override val type = CloudTtsProviderType.AlibabaCloud

    override fun fetchVoices(engine: CloudTtsEngine): List<CloudTtsVoiceDescriptor> = BUILT_IN_VOICES

    override fun synthesize(
        engine: CloudTtsEngine,
        request: CloudTtsSynthesisRequest,
    ): CloudTtsAudio {
        require(engine.apiKey.isNotBlank()) { "阿里云百炼 API Key 不能为空" }
        require(engine.model.isNotBlank()) { "阿里云 TTS 模型不能为空" }
        require(request.text.isNotBlank()) { "朗读文本不能为空" }
        val body = JSONObject().apply {
            put("model", engine.model)
            put("input", JSONObject()
                .put("text", request.text)
                .put("voice", request.voiceId)
                .put("language_type", request.locale.ifBlank { "Auto" }))
            val emotionInstruction = request.style.takeIf(String::isNotBlank)
            val instructions = listOfNotNull(
                emotionInstruction,
                request.instructions.takeIf(String::isNotBlank),
            ).joinToString("；")
            if (instructions.isNotBlank() && engine.model.contains("instruct", true)) {
                getJSONObject("input").put("instructions", instructions)
            }
        }
        val endpoint = engine.baseUrl.ifBlank { DEFAULT_ENDPOINT }.trimEnd('/').let { base ->
            if (base.endsWith("/generation")) base
            else "$base/services/aigc/multimodal-generation/generation"
        }
        val httpRequest = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer ${engine.apiKey}")
            .header("X-DashScope-SSE", "enable")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return cloudTtsHttpClient.newCall(httpRequest).execute().use { response ->
            response.requireCloudTtsSuccessful("阿里云百炼 TTS")
            val pcm = ByteArrayOutputStream()
            var audioUrl = ""
            response.body.source().use { source ->
                while (!source.exhausted()) {
                    val line = source.readUtf8Line()?.trim().orEmpty()
                    if (!line.startsWith("data:")) continue
                    val json = runCatching { JSONObject(line.removePrefix("data:").trim()) }.getOrNull()
                        ?: continue
                    val audio = json.optJSONObject("output")?.optJSONObject("audio")
                    val data = audio?.optString("data").orEmpty()
                    if (data.isNotBlank()) pcm.write(Base64.decode(data, Base64.DEFAULT))
                    audioUrl = audio?.optString("url").orEmpty().ifBlank { audioUrl }
                }
            }
            val bytes = pcm.toByteArray().takeIf(ByteArray::isNotEmpty)
                ?: audioUrl.takeIf(String::isNotBlank)?.let(::downloadAudio)
                ?: error("阿里云百炼 TTS 没有返回音频数据")
            val audio = if (pcm.size() > 0) pcm16MonoToWav(bytes, SAMPLE_RATE) else bytes
            CloudTtsAudio(audio, if (pcm.size() > 0) "wav" else request.format, SAMPLE_RATE)
        }
    }

    private fun downloadAudio(url: String): ByteArray {
        val request = Request.Builder().url(url).get().build()
        return cloudTtsHttpClient.newCall(request).execute().use { response ->
            response.requireCloudTtsSuccessful("阿里云百炼音频下载").body.bytes()
        }
    }

    private companion object {
        const val DEFAULT_ENDPOINT = "https://dashscope.aliyuncs.com/api/v1"
        const val SAMPLE_RATE = 24_000
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        val BUILT_IN_VOICES = listOf(
            CloudTtsVoiceDescriptor("Cherry", "Cherry", "Chinese", "Female", sampleRate = SAMPLE_RATE),
            CloudTtsVoiceDescriptor("Serena", "Serena", "Chinese", "Female", sampleRate = SAMPLE_RATE),
            CloudTtsVoiceDescriptor("Ethan", "Ethan", "Chinese", "Male", sampleRate = SAMPLE_RATE),
            CloudTtsVoiceDescriptor("Chelsie", "Chelsie", "English", "Female", sampleRate = SAMPLE_RATE),
        )
    }
}
