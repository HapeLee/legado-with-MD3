package io.legado.app.help.readaloud.playback

import io.legado.app.domain.model.readaloud.CloudTtsAudio
import io.legado.app.domain.model.readaloud.CloudTtsEngine
import io.legado.app.domain.model.readaloud.CloudTtsProviderType
import io.legado.app.domain.model.readaloud.CloudTtsSynthesisRequest
import io.legado.app.domain.model.readaloud.CloudTtsVoiceDescriptor
import io.legado.app.help.http.okHttpClient
import okhttp3.OkHttpClient
import okhttp3.Response
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

internal interface CloudTtsProvider {
    val type: CloudTtsProviderType
    fun fetchVoices(engine: CloudTtsEngine): List<CloudTtsVoiceDescriptor>
    fun synthesize(engine: CloudTtsEngine, request: CloudTtsSynthesisRequest): CloudTtsAudio
}

internal class CloudTtsProviderRegistry(
    providers: List<CloudTtsProvider> = listOf(
        OpenAiCloudTtsProvider(),
        GeminiCloudTtsProvider(),
        MimoCloudTtsProvider(),
        AzureSpeechCloudTtsProvider(),
        AlibabaCloudTtsProvider(),
        AwsPollyCloudTtsProvider(),
        VolcengineCloudTtsProvider(),
    ),
) {
    private val providersByType = providers.associateBy(CloudTtsProvider::type)

    fun get(type: CloudTtsProviderType): CloudTtsProvider =
        providersByType[type] ?: error("不支持的云 TTS 引擎：${type.storageValue}")
}

internal val cloudTtsHttpClient: OkHttpClient by lazy {
    okHttpClient.newBuilder()
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
}

internal fun Response.requireCloudTtsSuccessful(providerName: String): Response {
    if (isSuccessful) return this
    val detail = body.string().trim().take(2_000)
    val suffix = detail.takeIf(String::isNotBlank)?.let { "：$it" }.orEmpty()
    error("$providerName 请求失败：HTTP $code$suffix")
}

internal fun String.escapeXml(): String = buildString(length) {
    this@escapeXml.forEach { char ->
        append(when (char) {
            '&' -> "&amp;"
            '<' -> "&lt;"
            '>' -> "&gt;"
            '\"' -> "&quot;"
            '\'' -> "&apos;"
            else -> char
        })
    }
}

internal fun pcm16MonoToWav(pcm: ByteArray, sampleRate: Int): ByteArray {
    val output = ByteBuffer.allocate(WAV_HEADER_SIZE + pcm.size).order(ByteOrder.LITTLE_ENDIAN)
    output.put("RIFF".toByteArray()).putInt(36 + pcm.size).put("WAVE".toByteArray())
    output.put("fmt ".toByteArray()).putInt(16).putShort(1.toShort()).putShort(1.toShort())
    output.putInt(sampleRate).putInt(sampleRate * 2).putShort(2.toShort()).putShort(16.toShort())
    output.put("data".toByteArray()).putInt(pcm.size).put(pcm)
    return output.array()
}

private const val WAV_HEADER_SIZE = 44
