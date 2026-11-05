package io.legado.app.data.repository.ai

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.domain.gateway.AiImageGateway
import io.legado.app.domain.model.*
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Native image transports. A failed paid request is never retried or sent to another protocol. */
class AiImageRepository(private val suppliedClient: OkHttpClient? = null) : AiImageGateway {
    private val client by lazy {
        (suppliedClient ?: OkHttpClient()).newBuilder().connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS).readTimeout(180, TimeUnit.SECONDS)
            .callTimeout(240, TimeUnit.SECONDS).retryOnConnectionFailure(false).build()
    }

    override suspend fun generate(request: AiImageRequest): AiImageResult = withContext(Dispatchers.IO) {
        val wire = imageWireRequest(request)
        val root = JsonParser.parseString(execute(wire).toString(Charsets.UTF_8)).asJsonObject
        val payload = imagePayload(root, checkNotNull(AiImageModels.editProtocol(request.model.provider.protocol)))
        val bytes = payload.base64?.let {
            require(it.length <= MAX_RESPONSE_BYTES) { "Generated image is too large" }
            Base64.getDecoder().decode(it)
        } ?: payload.url?.let { url ->
            require(url.startsWith("https://")) { "Image service returned an unsupported URL" }
            // Credentials and provider custom headers must never reach a generated-image host.
            execute(Request.Builder().url(url).build())
        } ?: error("Image service returned no image")
        check(bytes.isNotEmpty()) { "Image service returned an empty image" }
        AiImageResult(bytes)
    }

    private suspend fun execute(request: Request): ByteArray = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val bytes = response.use {
                        check(it.isSuccessful) { "Image service HTTP ${it.code}" }
                        val output = ByteArrayOutputStream()
                        it.body.byteStream().use { input ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                check(output.size() + n <= MAX_RESPONSE_BYTES) { "Image response is too large" }
                                output.write(buffer, 0, n)
                            }
                        }
                        output.toByteArray()
                    }
                    if (continuation.isActive) continuation.resume(bytes)
                } catch (error: Exception) { if (continuation.isActive) continuation.resumeWithException(error) }
            }
        })
    }
}

private const val MAX_RESPONSE_BYTES = 48 * 1024 * 1024

internal fun imageWireRequest(request: AiImageRequest): Request {
    val model = request.model
    val provider = model.provider
    val protocol = AiImageModels.editProtocol(provider.protocol) ?: error("Unsupported image protocol")
    val capability = if (request.image == null) AiCapability.IMAGE_GENERATION else AiCapability.IMAGE_EDITING
    require(capability in model.capabilities) { "The model does not have the required image output capability" }
    require(request.prompt.isNotBlank() && request.prompt.length <= 32_000) { "Invalid image prompt" }
    require(provider.baseUrl.isNotBlank() && provider.apiKey.split(',').any { it.isNotBlank() } && model.modelId.isNotBlank()) { "Image provider configuration is incomplete" }
    val data = request.image?.dataUrl
    val textPart = mapOf("text" to request.prompt)
    val (path, body) = when (protocol) {
        AiProtocol.OPENAI_IMAGES -> if (request.image != null) {
            "/images/edits" to MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("model", model.modelId).addFormDataPart("prompt", request.prompt)
                .addFormDataPart("size", "1024x1024")
                .addFormDataPart("image", "input.png", Base64.getDecoder().decode(request.image.base64Data)
                    .toRequestBody(request.image.mimeType.toMediaType())).build()
        } else "/images/generations" to jsonBody(mapOf("model" to model.modelId, "prompt" to request.prompt, "size" to "1024x1024", "n" to 1))
        AiProtocol.GEMINI_IMAGES -> "/interactions" to jsonBody(mapOf(
            "model" to model.modelId, "store" to false,
            "input" to buildList {
                add(mapOf("type" to "text", "text" to request.prompt))
                request.image?.let { add(mapOf("type" to "image", "mime_type" to it.mimeType, "data" to it.base64Data)) }
            }, "response_format" to mapOf("type" to "image", "mime_type" to "image/png", "aspect_ratio" to "1:1"),
        ))
        AiProtocol.GEMINI_IMAGES_LEGACY -> {
            require(model.modelId.removePrefix("models/").matches(Regex("[A-Za-z0-9._-]+"))) { "Invalid Gemini model ID" }
            "/models/${model.modelId.removePrefix("models/")}:generateContent" to jsonBody(mapOf(
                "contents" to listOf(mapOf("role" to "user", "parts" to buildList {
                    add(textPart)
                    request.image?.let { add(mapOf("inlineData" to mapOf("mimeType" to it.mimeType, "data" to it.base64Data))) }
                })), "generationConfig" to mapOf("responseModalities" to listOf("TEXT", "IMAGE"), "imageConfig" to mapOf("aspectRatio" to "1:1")),
            ))
        }
        AiProtocol.SEEDREAM_IMAGES -> "/images/generations" to jsonBody(buildMap {
            put("model", model.modelId); put("prompt", request.prompt); put("size", "2K")
            put("response_format", "b64_json"); put("stream", false)
            put("watermark", false)
            put("sequential_image_generation", "disabled")
            data?.let { put("image", it) }
        })
        AiProtocol.DASHSCOPE_IMAGES -> "/services/aigc/multimodal-generation/generation" to jsonBody(mapOf(
            "model" to model.modelId,
            "input" to mapOf("messages" to listOf(mapOf("role" to "user", "content" to buildList {
                data?.let { add(mapOf("image" to it)) }; add(textPart)
            }))), "parameters" to mapOf("n" to 1, "size" to "1024*1024", "prompt_extend" to false),
        ))
        AiProtocol.XAI_IMAGES -> (if (data == null) "/images/generations" else "/images/edits") to jsonBody(buildMap {
            put("model", model.modelId); put("prompt", request.prompt); put("n", 1); put("response_format", "b64_json")
            data?.let { put("image", mapOf("url" to it, "type" to "image_url")) }
        })
        AiProtocol.SILICONFLOW_IMAGES -> "/images/generations" to jsonBody(buildMap {
            put("model", model.modelId); put("prompt", request.prompt)
            if (data == null) put("image_size", "1024x1024") else put("image", data)
        })
        else -> error("Unsupported image protocol")
    }
    val endpoint = request.endpointPath?.takeIf { it.isNotBlank() } ?: path
    require(endpoint.startsWith('/') && !endpoint.startsWith("//") && !endpoint.contains('?') && !endpoint.contains('#') && !endpoint.contains("..")) { "Invalid image endpoint path" }
    val nativeHeaders = if (protocol == AiProtocol.GEMINI_IMAGES || protocol == AiProtocol.GEMINI_IMAGES_LEGACY)
        mapOf("x-goog-api-key" to KeyRotator(provider.apiKey).currentKey)
    else mapOf("Authorization" to "Bearer ${KeyRotator(provider.apiKey).currentKey}")
    return Request.Builder().url(provider.baseUrl.trimEnd('/') + endpoint).post(body).apply {
        provider.aiRequestHeaders(null, nativeHeaders).forEach { (name, value) -> header(name, value) }
    }.build()
}

private fun jsonBody(value: Any): RequestBody = GSON.toJson(value).toRequestBody("application/json".toMediaType())

internal data class ImagePayload(val base64: String? = null, val url: String? = null)
private fun JsonObject.string(key: String): String? = get(key)?.takeUnless { it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
private fun JsonObject.objects(key: String): List<JsonObject> = getAsJsonArray(key)?.filter { it.isJsonObject }?.map(JsonElement::getAsJsonObject).orEmpty()

internal fun imagePayload(root: JsonObject, protocol: String): ImagePayload = when (protocol) {
    AiProtocol.GEMINI_IMAGES -> {
        check(root.string("status") in setOf(null, "completed")) { "Image interaction did not complete" }
        val image = root.objects("steps").filter { it.string("type") == "model_output" }
            .flatMap { it.objects("content") }.firstOrNull { it.string("type") == "image" }
            ?: error("Gemini returned no output image (possibly blocked or text-only)")
        require(image.string("mime_type") in setOf("image/png", "image/jpeg", "image/webp")) { "Unsupported output image format" }
        ImagePayload(base64 = image.string("data"), url = image.string("uri"))
    }
    AiProtocol.GEMINI_IMAGES_LEGACY -> {
        val image = root.objects("candidates").flatMap { it.getAsJsonObject("content")?.objects("parts").orEmpty() }
            .filter { it.get("thought")?.asBoolean != true }.firstNotNullOfOrNull { it.getAsJsonObject("inlineData") ?: it.getAsJsonObject("inline_data") }
            ?: error("Gemini returned no output image (possibly blocked or text-only)")
        require((image.string("mimeType") ?: image.string("mime_type")) in setOf("image/png", "image/jpeg", "image/webp")) { "Unsupported output image format" }
        ImagePayload(base64 = image.string("data"))
    }
    AiProtocol.DASHSCOPE_IMAGES -> {
        val image = root.getAsJsonObject("output")?.objects("choices")?.flatMap { it.getAsJsonObject("message")?.objects("content").orEmpty() }
            ?.firstNotNullOfOrNull { it.string("image") } ?: error("DashScope returned no image")
        if (image.startsWith("data:image/")) ImagePayload(base64 = image.substringAfter(";base64,")) else ImagePayload(url = image)
    }
    AiProtocol.SILICONFLOW_IMAGES -> root.objects("images").firstOrNull()?.let { ImagePayload(url = it.string("url")) }
        ?: error("SiliconFlow returned no image")
    else -> root.objects("data").firstOrNull()?.let { ImagePayload(it.string("b64_json"), it.string("url")) }
        ?: error("Image service returned no image")
}
