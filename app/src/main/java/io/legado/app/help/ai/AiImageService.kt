package io.legado.app.help.ai

import android.util.Base64
import com.script.buildScriptBindings
import com.script.rhino.RhinoScriptEngine
import io.legado.app.constant.AppLog
import io.legado.app.data.entities.AiGeneratedImage
import io.legado.app.data.entities.BaseSource
import io.legado.app.help.CacheManager
import io.legado.app.help.config.AppConfig
import io.legado.app.help.http.CookieStore
import io.legado.app.help.http.addHeaders
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.postJson
import io.legado.app.help.source.getShareScope
import io.legado.app.ui.main.ai.AiImageProviderConfig
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object AiImageService {

    private const val MAX_IMAGE_BYTES = 32 * 1024 * 1024

    private data class ImageGenerationResult(
        val source: String,
        val model: String
    )

    suspend fun generate(
        prompt: String,
        provider: AiImageProviderConfig? = null
    ): String {
        val target = resolveProvider(provider)
        return generateRaw(effectivePrompt(prompt, target), target).source
    }

    suspend fun generateAndStore(
        prompt: String,
        provider: AiImageProviderConfig? = null,
        metadata: AiImageGalleryManager.ImageMetadata = AiImageGalleryManager.ImageMetadata()
    ): AiGeneratedImage {
        val target = resolveProvider(provider)
        val image = generateRaw(effectivePrompt(prompt, target), target)
        return AiImageGalleryManager.saveGeneratedImage(image.source, prompt, target, image.model, metadata)
    }

    fun currentProviderOrNull(): AiImageProviderConfig? {
        return AppConfig.aiCurrentImageProvider
    }

    fun providerByIdOrNull(providerId: String?): AiImageProviderConfig? {
        return AppConfig.findEnabledImageProvider(providerId)
    }

    private fun resolveProvider(provider: AiImageProviderConfig?): AiImageProviderConfig {
        return provider ?: currentProviderOrNull()
            ?: error("请选择可用生图模型")
    }

    private fun effectivePrompt(prompt: String, provider: AiImageProviderConfig): String {
        val style = provider.stylePrompt.trim()
        val content = prompt.trim()
        return when {
            style.isBlank() -> content
            content.isBlank() -> style
            else -> "$style\n\n$content"
        }
    }

    private suspend fun generateRaw(prompt: String, target: AiImageProviderConfig): ImageGenerationResult {
        return when (target.type) {
            AiImageProviderConfig.TYPE_JS -> generateByJs(prompt, target)
            else -> generateByOpenAi(prompt, target)
        }
    }

    private suspend fun generateByOpenAi(prompt: String, provider: AiImageProviderConfig): ImageGenerationResult {
        val baseUrl = normalizeBaseUrl(provider.baseUrl)
        require(baseUrl.isNotBlank()) { "Base URL is empty" }
        val params = runCatching { JSONObject(provider.defaultParamsJson.ifBlank { "{}" }) }
            .getOrDefault(JSONObject())
        return if (params.optString("endpoint").equals("responses", true)) {
            generateByResponses(prompt, provider, baseUrl, params)
        } else {
            generateByImagesApi(prompt, provider, baseUrl, params)
        }
    }

    private suspend fun generateByImagesApi(
        prompt: String,
        provider: AiImageProviderConfig,
        baseUrl: String,
        params: JSONObject
    ): ImageGenerationResult {
        val effectiveModel = provider.model
            .ifBlank { params.optString("model").ifBlank { "gpt-image-1" } }
        val payload = JSONObject().apply {
            put("model", effectiveModel)
            put("prompt", prompt)
            put("n", 1)
            put("size", "1024x1024")
            mergeJson(params, ignored = setOf("endpoint", "model", "prompt"))
        }
        val requestUrl = "${baseUrl.trimEnd('/')}/images/generations"
        val startedAt = System.currentTimeMillis()
        var status = ""
        try {
            val response = provider.httpClient().newCallResponse {
                url(requestUrl)
                postJson(payload.toString())
                addHeader("Accept", "application/json")
                addHeader("Content-Type", "application/json")
                provider.apiKey.takeIf { it.isNotBlank() }?.let {
                    addHeader("Authorization", "Bearer $it")
                }
                addHeaders(AiChatService.parseCustomHeaders(provider.headers))
            }
            response.use {
                status = "${it.code} ${it.message}"
                val text = it.body.string()
                if (!it.isSuccessful) error(text.ifBlank { status })
                val root = JSONObject(text)
                imageFromOpenAiResponse(root)?.let { source ->
                    logRequest(provider, requestUrl, status, startedAt, true, effectiveModel)
                    return ImageGenerationResult(source, effectiveModel)
                }
                error("No image url or base64 field in response: ${jsonShape(root)}")
            }
        } catch (e: Throwable) {
            logRequest(provider, requestUrl, status.ifBlank { e.javaClass.simpleName }, startedAt, false, effectiveModel, e)
            throw e
        }
    }

    private suspend fun generateByResponses(
        prompt: String,
        provider: AiImageProviderConfig,
        baseUrl: String,
        params: JSONObject
    ): ImageGenerationResult {
        val effectiveModel = provider.model
            .ifBlank { params.optString("model").ifBlank { "gpt-5" } }
        val tool = JSONObject().apply {
            put("type", "image_generation")
            params.optJSONObject("tool")?.let { mergeJson(it) }
            mergeJson(
                params,
                ignored = setOf("endpoint", "model", "input", "tools", "stream", "response", "tool")
            )
        }
        val payload = JSONObject().apply {
            put("model", effectiveModel)
            put("input", prompt)
            put("tools", JSONArray().put(tool))
            params.optJSONObject("response")?.let { mergeJson(it) }
        }
        val requestUrl = "${baseUrl.trimEnd('/')}/responses"
        val startedAt = System.currentTimeMillis()
        var status = ""
        try {
            val response = provider.httpClient().newCallResponse {
                url(requestUrl)
                postJson(payload.toString())
                addHeader("Accept", "application/json")
                addHeader("Content-Type", "application/json")
                provider.apiKey.takeIf { it.isNotBlank() }?.let {
                    addHeader("Authorization", "Bearer $it")
                }
                addHeaders(AiChatService.parseCustomHeaders(provider.headers))
            }
            response.use {
                status = "${it.code} ${it.message}"
                val text = it.body.string()
                if (!it.isSuccessful) error(text.ifBlank { status })
                val output = JSONObject(text).optJSONArray("output") ?: error("Empty responses output")
                for (index in 0 until output.length()) {
                    val item = output.optJSONObject(index) ?: continue
                    if (item.optString("type") == "image_generation_call") {
                        item.optString("result").takeIf { b64 -> b64.isNotBlank() }?.let { b64 ->
                            ensureImageSourceWithinLimit(b64)
                            logRequest(provider, requestUrl, status, startedAt, true, effectiveModel)
                            return ImageGenerationResult(normalizeImageString(b64), effectiveModel)
                        }
                    }
                    imageFromOpenAiJson(item)?.let { source ->
                        logRequest(provider, requestUrl, status, startedAt, true, effectiveModel)
                        return ImageGenerationResult(source, effectiveModel)
                    }
                }
                error("No image_generation_call result in response")
            }
        } catch (e: Throwable) {
            logRequest(provider, requestUrl, status.ifBlank { e.javaClass.simpleName }, startedAt, false, effectiveModel, e)
            throw e
        }
    }

    private suspend fun generateByJs(prompt: String, provider: AiImageProviderConfig): ImageGenerationResult {
        val script = provider.script.trim()
        if (script.isBlank()) error("JS script is empty")
        val source = AiImageJsSource(provider)
        val coroutineContext = currentCoroutineContext()
        val result = withTimeout(provider.validTimeout()) {
            val bindings = buildScriptBindings { bindings ->
                bindings["java"] = source
                bindings["source"] = source
                bindings["cache"] = CacheManager
                bindings["cookie"] = CookieStore
                bindings["baseUrl"] = source.getKey()
                bindings["prompt"] = prompt
                bindings["result"] = prompt
                bindings["key"] = prompt
                bindings["provider"] = provider
            }
            val sharedScope = source.getShareScope(coroutineContext)
            val scope = if (sharedScope == null) {
                RhinoScriptEngine.getRuntimeScope(bindings)
            } else {
                bindings.apply { prototype = sharedScope }
            }
            RhinoScriptEngine.eval(
                buildString {
                    append(script).append('\n')
                    append(
                        """
                        ;(function(){
                            if (typeof generate === 'function') return generate(prompt, provider);
                            if (typeof run === 'function') return run(prompt, provider);
                            if (typeof result !== 'undefined') return result;
                            return null;
                        })();
                        """.trimIndent()
                    )
                },
                scope,
                coroutineContext
            )
        }
        return ImageGenerationResult(normalizeImageResult(result), provider.model.ifBlank { "JS" })
    }

    private class AiImageJsSource(
        private val provider: AiImageProviderConfig
    ) : BaseSource {
        override var concurrentRate: String? = null
        override var loginUrl: String? = provider.loginUrl
        override var loginUi: String? = provider.loginUi
        override var header: String? = provider.headers
        override var enabledCookieJar: Boolean? = provider.enabledCookieJar
        override var jsLib: String? = provider.jsLib

        override fun getTag(): String {
            return "AiImageRule:${provider.id}:${provider.displayName()}"
        }

        override fun getKey(): String {
            return "ai_image_rule_${provider.id}"
        }
    }

    private fun normalizeBaseUrl(raw: String): String {
        val normalized = raw.trim().trimEnd('/')
        return when {
            normalized.isBlank() -> ""
            normalized.endsWith("/v1") -> normalized
            normalized.endsWith("/images/generations") -> normalized.removeSuffix("/images/generations")
            normalized.endsWith("/responses") -> normalized.removeSuffix("/responses")
            else -> "$normalized/v1"
        }
    }

    private fun AiImageProviderConfig.httpClient(): OkHttpClient {
        val timeout = validTimeout()
        return okHttpClient.newBuilder()
            .connectTimeout(timeout, TimeUnit.MILLISECONDS)
            .writeTimeout(timeout, TimeUnit.MILLISECONDS)
            .readTimeout(timeout, TimeUnit.MILLISECONDS)
            .callTimeout(timeout, TimeUnit.MILLISECONDS)
            .build()
    }

    private fun logRequest(
        provider: AiImageProviderConfig,
        url: String,
        status: String,
        startedAt: Long,
        success: Boolean,
        model: String = provider.model,
        throwable: Throwable? = null
    ) {
        val elapsed = System.currentTimeMillis() - startedAt
        val message = buildString {
            append("AI 生图请求")
            append(if (success) "成功" else "失败")
            append("\nurl=").append(url)
            append("\nprovider=").append(provider.displayName())
            append("\nmodel=").append(model)
            append("\ntimeout=").append(provider.validTimeout())
            append("\nelapsed=").append(elapsed)
            append("\nstatus=").append(status)
        }
        AppLog.put(message, throwable)
    }

    private fun JSONObject.mergeJson(extra: JSONObject, ignored: Set<String> = emptySet()) {
        extra.keys().forEach { key ->
            if (key !in ignored) put(key, extra.opt(key))
        }
    }

    private fun normalizeImageResult(result: Any?): String {
        return when (result) {
            null -> error("Empty JS image result")
            is String -> normalizeImageString(result)
            is JSONObject -> imageFromJson(result)
            is JSONArray -> imageFromArray(result)
            is NativeObject -> imageFromJson(result.toJSONObject())
            is NativeArray -> imageFromArray(result.toJSONArray())
            else -> normalizeImageString(result.toString())
        }
    }

    private fun normalizeImageString(raw: String): String {
        val text = raw.trim()
        if (text.isBlank() || text == "null" || text == "undefined") error("Empty JS image result")
        if (text.startsWith("http", true) || text.startsWith("data:", true)) {
            ensureImageSourceWithinLimit(text)
            return text
        }
        if (text.startsWith("{")) return imageFromJson(JSONObject(text))
        if (text.startsWith("[")) return imageFromArray(JSONArray(text))
        if (looksLikeImageBase64(text)) return "data:image/png;base64,$text"
        error("Image provider needs a returned url, data url, base64, or JSON image result")
    }

    private fun imageFromJson(json: JSONObject): String {
        return imageFromOpenAiJson(json)
            ?: error("No image url or base64 field in image result: ${jsonShape(json)}")
    }

    private fun imageFromArray(array: JSONArray): String {
        return imageFromOpenAiArray(array)
            ?: error("No image item in image result")
    }

    private fun imageFromOpenAiResponse(json: JSONObject): String? {
        return imageFromOpenAiJson(json)
    }

    private fun imageFromOpenAiJson(json: JSONObject): String? {
        listOf("url", "imageUrl", "image_url", "image", "result", "src", "output").forEach { key ->
            when (val value = json.opt(key)) {
                is String -> runCatching { normalizeImageString(value) }.getOrNull()?.let { return it }
                is JSONObject -> imageFromOpenAiJson(value)?.let { return it }
                is JSONArray -> imageFromOpenAiArray(value)?.let { return it }
            }
        }
        listOf("b64_json", "base64", "b64").forEach { key ->
            json.optString(key).takeIf { it.isNotBlank() }?.let { value ->
                runCatching { normalizeImageString(value) }.getOrNull()?.let { return it }
            }
        }
        listOf("data", "images", "artifacts", "content").forEach { key ->
            json.optJSONArray(key)?.let { imageFromOpenAiArray(it)?.let { source -> return source } }
            json.optJSONObject(key)?.let { imageFromOpenAiJson(it)?.let { source -> return source } }
        }
        return null
    }

    private fun imageFromOpenAiArray(array: JSONArray): String? {
        for (index in 0 until array.length()) {
            runCatching {
                when (val item = array.opt(index)) {
                    is JSONObject -> imageFromOpenAiJson(item)
                    is String -> normalizeImageString(item)
                    else -> null
                }
            }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun NativeObject.toJSONObject(): JSONObject {
        return JSONObject().apply {
            ids.forEach { key ->
                val name = key.toString()
                val value = get(name, this@toJSONObject)
                put(name, nativeToJsonValue(value))
            }
        }
    }

    private fun NativeArray.toJSONArray(): JSONArray {
        return JSONArray().apply {
            ids.forEach { key ->
                val index = key.toString().toIntOrNull() ?: return@forEach
                put(index, nativeToJsonValue(get(index, this@toJSONArray)))
            }
        }
    }

    private fun nativeToJsonValue(value: Any?): Any {
        return when (value) {
            null -> JSONObject.NULL
            is NativeObject -> value.toJSONObject()
            is NativeArray -> value.toJSONArray()
            is Number, is Boolean, is String -> value
            else -> value.toString()
        }
    }

    private fun looksLikeImageBase64(text: String): Boolean {
        val payload = text.filterNot { it.isWhitespace() }
        if (payload.length < 100 || !payload.matches(Regex("[A-Za-z0-9+/=_-]+"))) return false
        if (estimateBase64Bytes(payload) > MAX_IMAGE_BYTES) return false
        return runCatching {
            val bytes = decodeBase64Sample(payload)
            bytes.size >= 8 && (
                bytes.copyOfRange(0, 4).toString(Charsets.ISO_8859_1) == "\u0089PNG" ||
                    (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) ||
                    bytes.copyOfRange(0, 3).toString(Charsets.ISO_8859_1) == "GIF" ||
                    bytes.copyOfRange(0, 4).toString(Charsets.ISO_8859_1) == "RIFF"
                )
        }.getOrDefault(false)
    }

    private fun ensureImageSourceWithinLimit(text: String) {
        val payload = when {
            text.startsWith("data:", true) -> text.substringAfter(',', "").filterNot { it.isWhitespace() }
            else -> text.filterNot { it.isWhitespace() }
        }
        if (payload.isBlank() || payload.startsWith("http", true)) return
        val estimatedBytes = estimateBase64Bytes(payload)
        if (estimatedBytes > MAX_IMAGE_BYTES) error("Image is too large: $estimatedBytes bytes")
    }

    private fun estimateBase64Bytes(payload: String): Long {
        return payload.length.toLong() * 3L / 4L
    }

    private fun decodeBase64Sample(payload: String): ByteArray {
        val sample = payload.take(64).padBase64()
        return runCatching {
            Base64.decode(sample, Base64.DEFAULT)
        }.getOrElse {
            Base64.decode(sample, Base64.URL_SAFE)
        }
    }

    private fun String.padBase64(): String {
        val remainder = length % 4
        return if (remainder == 0) this else this + "=".repeat(4 - remainder)
    }

    private fun jsonShape(json: JSONObject): String {
        return json.keys().asSequence().take(12).joinToString(prefix = "{", postfix = "}") { key ->
            val value = json.opt(key)
            val type = when (value) {
                is JSONObject -> "object"
                is JSONArray -> "array(${value.length()})"
                JSONObject.NULL, null -> "null"
                else -> value.javaClass.simpleName
            }
            "$key:$type"
        }
    }
}
