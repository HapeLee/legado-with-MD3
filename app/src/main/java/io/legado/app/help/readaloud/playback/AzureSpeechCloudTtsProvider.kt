package io.legado.app.help.readaloud.playback

import io.legado.app.domain.model.readaloud.CloudTtsAudio
import io.legado.app.domain.model.readaloud.CloudTtsEngine
import io.legado.app.domain.model.readaloud.CloudTtsProviderType
import io.legado.app.domain.model.readaloud.CloudTtsSynthesisRequest
import io.legado.app.domain.model.readaloud.CloudTtsVoiceDescriptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray

internal class AzureSpeechCloudTtsProvider : CloudTtsProvider {
    override val type = CloudTtsProviderType.AzureSpeech

    override fun fetchVoices(engine: CloudTtsEngine): List<CloudTtsVoiceDescriptor> {
        require(engine.apiKey.isNotBlank()) { "Azure Speech Key 不能为空" }
        val request = Request.Builder()
            .url("${endpoint(engine)}/cognitiveservices/voices/list")
            .header("Ocp-Apim-Subscription-Key", engine.apiKey)
            .get()
            .build()
        return cloudTtsHttpClient.newCall(request).execute().use { response ->
            val voices = JSONArray(response.requireCloudTtsSuccessful("Azure Speech").body.string())
            buildList {
                repeat(voices.length()) { index ->
                    val voice = voices.getJSONObject(index)
                    add(CloudTtsVoiceDescriptor(
                        id = voice.getString("ShortName"),
                        displayName = voice.optString("LocalName").ifBlank {
                            voice.optString("DisplayName", voice.getString("ShortName"))
                        },
                        locale = voice.optString("Locale"),
                        gender = voice.optString("Gender"),
                        styles = voice.optJSONArray("StyleList").toStringList(),
                        roles = voice.optJSONArray("RolePlayList").toStringList(),
                        sampleRate = voice.optString("SampleRateHertz").toIntOrNull(),
                    ))
                }
            }
        }
    }

    override fun synthesize(
        engine: CloudTtsEngine,
        request: CloudTtsSynthesisRequest,
    ): CloudTtsAudio {
        require(engine.apiKey.isNotBlank()) { "Azure Speech Key 不能为空" }
        require(request.voiceId.isNotBlank()) { "Azure 音色不能为空" }
        val locale = request.locale.ifBlank {
            request.voiceId.split('-').take(2).joinToString("-").ifBlank { "zh-CN" }
        }
        val prosody = "<prosody rate=\"${request.speed}\" pitch=\"${request.pitch.toAzurePitch()}\" volume=\"${request.volume.toAzureVolume()}\">${request.text.escapeXml()}</prosody>"
        val expressed = if (request.style.isNotBlank() || request.role.isNotBlank()) {
            val style = request.style.ifBlank { "general" }.escapeXml()
            val role = request.role.takeIf(String::isNotBlank)
                ?.let { " role=\"${it.escapeXml()}\"" }.orEmpty()
            "<mstts:express-as style=\"$style\"$role>$prosody</mstts:express-as>"
        } else prosody
        val ssml = "<speak version=\"1.0\" xmlns=\"http://www.w3.org/2001/10/synthesis\" xmlns:mstts=\"https://www.w3.org/2001/mstts\" xml:lang=\"${locale.escapeXml()}\"><voice name=\"${request.voiceId.escapeXml()}\">$expressed</voice></speak>"
        val outputFormat = when (request.format.lowercase()) {
            "wav" -> "riff-24khz-16bit-mono-pcm"
            "ogg", "opus" -> "ogg-24khz-16bit-mono-opus"
            else -> "audio-24khz-48kbitrate-mono-mp3"
        }
        val httpRequest = Request.Builder()
            .url("${endpoint(engine)}/cognitiveservices/v1")
            .header("Ocp-Apim-Subscription-Key", engine.apiKey)
            .header("X-Microsoft-OutputFormat", outputFormat)
            .header("User-Agent", "Legado")
            .post(ssml.toRequestBody(SSML_MEDIA_TYPE))
            .build()
        return cloudTtsHttpClient.newCall(httpRequest).execute().use { response ->
            val bytes = response.requireCloudTtsSuccessful("Azure Speech").body.bytes()
            require(bytes.isNotEmpty()) { "Azure Speech 没有返回音频数据" }
            CloudTtsAudio(bytes, request.format.lowercase())
        }
    }

    private fun endpoint(engine: CloudTtsEngine): String = engine.baseUrl.trim().trimEnd('/')
        .ifBlank {
            require(engine.region.isNotBlank()) { "Azure 区域不能为空" }
            "https://${engine.region}.tts.speech.microsoft.com"
        }

    private fun JSONArray?.toStringList(): List<String> = this?.let { array ->
        List(array.length()) { index -> array.getString(index) }
    }.orEmpty()

    private fun Float.toAzurePitch() = "${((this - 1f) * 100).toInt().coerceIn(-100, 100)}%"
    private fun Float.toAzureVolume() = "${((this - 1f) * 100).toInt().coerceIn(-100, 100)}%"

    private companion object {
        val SSML_MEDIA_TYPE = "application/ssml+xml".toMediaType()
    }
}
