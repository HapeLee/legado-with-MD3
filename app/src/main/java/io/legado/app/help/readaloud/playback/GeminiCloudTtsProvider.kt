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

internal class GeminiCloudTtsProvider : CloudTtsProvider {
    override val type = CloudTtsProviderType.GeminiTts

    override fun fetchVoices(engine: CloudTtsEngine): List<CloudTtsVoiceDescriptor> =
        VOICES.map { CloudTtsVoiceDescriptor(it, it) }

    override fun synthesize(
        engine: CloudTtsEngine,
        request: CloudTtsSynthesisRequest,
    ): CloudTtsAudio {
        require(engine.apiKey.isNotBlank()) { "Gemini API Key 不能为空" }
        require(engine.model.isNotBlank()) { "Gemini TTS 模型不能为空" }
        val directions = listOfNotNull(
            request.style.takeIf(String::isNotBlank),
            request.instructions.takeIf(String::isNotBlank),
        ).joinToString("\n")
        val prompt = buildString {
            appendLine("Synthesize the following transcript as speech.")
            if (directions.isNotBlank()) {
                appendLine("### DIRECTOR'S NOTES")
                appendLine(directions)
            }
            appendLine("### TRANSCRIPT")
            append(request.text)
        }
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().put(
                "parts",
                JSONArray().put(JSONObject().put("text", prompt)),
            )))
            put("generationConfig", JSONObject()
                .put("responseModalities", JSONArray().put("AUDIO"))
                .put("speechConfig", JSONObject().put(
                    "voiceConfig",
                    JSONObject().put(
                        "prebuiltVoiceConfig",
                        JSONObject().put("voiceName", request.voiceId),
                    ),
                )))
        }
        val baseUrl = engine.baseUrl.ifBlank { DEFAULT_BASE_URL }.trimEnd('/').removeSuffix("/openai")
        val model = engine.model.removePrefix("models/")
        val httpRequest = Request.Builder()
            .url("$baseUrl/models/$model:generateContent")
            .header("x-goog-api-key", engine.apiKey)
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return cloudTtsHttpClient.newCall(httpRequest).execute().use { response ->
            val json = JSONObject(response.requireCloudTtsSuccessful("Gemini TTS").body.string())
            val inlineData = json.optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)
                ?.optJSONObject("inlineData") ?: error("Gemini TTS 没有返回音频数据")
            val bytes = Base64.decode(inlineData.getString("data"), Base64.DEFAULT)
            val mimeType = inlineData.optString("mimeType").lowercase()
            if (mimeType in ENCODED_AUDIO_TYPES) {
                CloudTtsAudio(bytes, mimeType.substringAfter('/'))
            } else {
                CloudTtsAudio(pcm16MonoToWav(bytes, SAMPLE_RATE), "wav", SAMPLE_RATE)
            }
        }
    }

    private companion object {
        const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        const val SAMPLE_RATE = 24_000
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        val ENCODED_AUDIO_TYPES = setOf(
            "audio/wav", "audio/wave", "audio/x-wav", "audio/mpeg", "audio/mp3",
            "audio/ogg", "audio/opus", "audio/flac",
        )
        val VOICES = listOf(
            "Kore", "Puck", "Charon", "Fenrir", "Leda", "Orus", "Aoede", "Callirrhoe",
            "Autonoe", "Enceladus", "Iapetus", "Umbriel", "Algieba", "Despina", "Erinome",
            "Algenib", "Rasalgethi", "Laomedeia", "Achernar", "Alnilam", "Schedar", "Gacrux",
            "Pulcherrima", "Achird", "Zubenelgenubi", "Vindemiatrix", "Sadachbia",
            "Sadaltager", "Sulafat",
        )
    }
}
