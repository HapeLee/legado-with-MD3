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
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal class AwsPollyCloudTtsProvider : CloudTtsProvider {
    override val type = CloudTtsProviderType.AwsPolly

    override fun fetchVoices(engine: CloudTtsEngine): List<CloudTtsVoiceDescriptor> {
        val engineName = engine.model.ifBlank { "neural" }
        val result = mutableListOf<CloudTtsVoiceDescriptor>()
        var nextToken: String? = null
        do {
            val query = buildList {
                add("Engine=${encode(engineName)}")
                add("IncludeAdditionalLanguageCodes=yes")
                nextToken?.takeIf(String::isNotBlank)?.let { add("NextToken=${encode(it)}") }
            }.joinToString("&")
            val url = "${endpoint(engine)}/v1/voices?$query"
            val request = signedRequest(engine, "GET", url, ByteArray(0)).get().build()
            val json = cloudTtsHttpClient.newCall(request).execute().use { response ->
                JSONObject(response.requireCloudTtsSuccessful("Amazon Polly").body.string())
            }
            val voices = json.getJSONArray("Voices")
            repeat(voices.length()) { index ->
                val voice = voices.getJSONObject(index)
                val models = voice.optJSONArray("SupportedEngines")?.let { array ->
                    List(array.length()) { array.getString(it) }
                }.orEmpty()
                result += CloudTtsVoiceDescriptor(
                    id = voice.getString("Id"),
                    displayName = voice.optString("Name", voice.getString("Id")),
                    locale = voice.optString("LanguageCode"),
                    gender = voice.optString("Gender"),
                    supportedModels = models,
                )
            }
            nextToken = json.optString("NextToken").takeIf(String::isNotBlank)
        } while (nextToken != null)
        return result.distinctBy(CloudTtsVoiceDescriptor::id)
    }

    override fun synthesize(
        engine: CloudTtsEngine,
        request: CloudTtsSynthesisRequest,
    ): CloudTtsAudio {
        require(request.text.isNotBlank()) { "朗读文本不能为空" }
        val format = request.format.lowercase().let { if (it == "wav") "pcm" else it }
        require(format in FORMATS) { "Amazon Polly 不支持音频格式：${request.format}" }
        val body = JSONObject().apply {
            put("Engine", engine.model.ifBlank { "neural" })
            put("OutputFormat", format)
            put("Text", request.text)
            put("TextType", "text")
            put("VoiceId", request.voiceId)
            if (request.locale.isNotBlank()) put("LanguageCode", request.locale)
            request.sampleRate?.let { put("SampleRate", it.toString()) }
        }.toString().toByteArray()
        val url = "${endpoint(engine)}/v1/speech"
        val httpRequest = signedRequest(engine, "POST", url, body)
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return cloudTtsHttpClient.newCall(httpRequest).execute().use { response ->
            val bytes = response.requireCloudTtsSuccessful("Amazon Polly").body.bytes()
            require(bytes.isNotEmpty()) { "Amazon Polly 没有返回音频数据" }
            if (format == "pcm") {
                val sampleRate = request.sampleRate ?: 24_000
                CloudTtsAudio(pcm16MonoToWav(bytes, sampleRate), "wav", sampleRate)
            } else CloudTtsAudio(bytes, format, request.sampleRate)
        }
    }

    private fun signedRequest(
        engine: CloudTtsEngine,
        method: String,
        url: String,
        body: ByteArray,
    ): Request.Builder {
        require(engine.apiKey.isNotBlank()) { "AWS Access Key ID 不能为空" }
        require(engine.secretKey.isNotBlank()) { "AWS Secret Access Key 不能为空" }
        require(engine.region.isNotBlank()) { "AWS 区域不能为空" }
        val now = Date()
        val amzDate = UTC_DATE_TIME.format(now)
        val date = UTC_DATE.format(now)
        val uri = URI(url)
        val payloadHash = body.sha256Hex()
        val canonicalQuery = uri.rawQuery.orEmpty().split('&').filter(String::isNotBlank)
            .sorted().joinToString("&")
        val canonicalHeaders = "host:${uri.host}\nx-amz-content-sha256:$payloadHash\nx-amz-date:$amzDate\n"
        val signedHeaders = "host;x-amz-content-sha256;x-amz-date"
        val canonicalRequest = listOf(
            method, uri.rawPath.ifBlank { "/" }, canonicalQuery,
            canonicalHeaders, signedHeaders, payloadHash,
        ).joinToString("\n")
        val scope = "$date/${engine.region}/polly/aws4_request"
        val stringToSign = "AWS4-HMAC-SHA256\n$amzDate\n$scope\n${canonicalRequest.toByteArray().sha256Hex()}"
        val signingKey = hmac(hmac(hmac(hmac(
            "AWS4${engine.secretKey}".toByteArray(), date), engine.region), "polly"), "aws4_request")
        val signature = hmac(signingKey, stringToSign).toHex()
        return Request.Builder()
            .url(url)
            .header("Host", uri.host)
            .header("X-Amz-Date", amzDate)
            .header("X-Amz-Content-Sha256", payloadHash)
            .header("Authorization", "AWS4-HMAC-SHA256 Credential=${engine.apiKey}/$scope, SignedHeaders=$signedHeaders, Signature=$signature")
    }

    private fun endpoint(engine: CloudTtsEngine): String = engine.baseUrl.trim().trimEnd('/')
        .ifBlank {
            require(engine.region.isNotBlank()) { "AWS 区域不能为空" }
            "https://polly.${engine.region}.amazonaws.com"
        }

    private fun hmac(key: ByteArray, value: String): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256"))
        doFinal(value.toByteArray())
    }
    private fun ByteArray.sha256Hex() = MessageDigest.getInstance("SHA-256").digest(this).toHex()
    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        val FORMATS = setOf("mp3", "ogg_vorbis", "pcm")
        val UTC_DATE_TIME = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val UTC_DATE = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}
