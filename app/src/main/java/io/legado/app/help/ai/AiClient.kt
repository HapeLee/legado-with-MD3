package io.legado.app.help.ai

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.help.http.okHttpClient
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * AI API 统一客户端。支持多供应商 (OpenAI / Anthropic / DeepSeek / Qwen / Kimi / Ollama / Custom)。
 */
object AiClient {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // ========== 核心辅助 ==========
    private fun buildClient(config: AiProviderConfig) = okHttpClient.newBuilder()
        .callTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
        .connectTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
        .build()

    private fun ensureTrailingSlash(url: String): String {
        return if (url.endsWith("/")) url else "$url/"
    }

    /**
     * 根据供应商格式化请求体。大多数是标准 OpenAI 格式。
     */
    private fun authHeaders(config: AiProviderConfig, isStreaming: Boolean = false): Map<String, String> {
        return when (config.provider) {
            AiProvider.OPENAI, AiProvider.DEEPSEEK, AiProvider.QWEN, AiProvider.KIMI,
            AiProvider.OLLAMA, AiProvider.CUSTOM, AiProvider.WENXIN -> {
                mapOf(
                    "Authorization" to "Bearer ${config.apiKey}",
                    "Content-Type" to "application/json"
                )
            }
            AiProvider.ANTHROPIC -> mapOf(
                "x-api-key" to config.apiKey,
                "anthropic-version" to "2023-06-01",
                "Content-Type" to "application/json"
            )
            AiProvider.GOOGLE -> mapOf(
                "x-goog-api-key" to config.apiKey,
                "Content-Type" to "application/json"
            )
        }
    }

    // ========== 聊天 (非流式) ==========
    suspend fun chat(
        messages: List<AiMessage>,
        config: AiProviderConfig,
        model: String = config.chatModel,
        temperature: Float = config.temperature,
        systemPrompt: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
            runCatching {
            val endpoint = ensureTrailingSlash(config.endpoint)
            val url = when (config.provider) {
                AiProvider.OPENAI, AiProvider.DEEPSEEK, AiProvider.QWEN, AiProvider.KIMI,
                AiProvider.OLLAMA, AiProvider.CUSTOM -> "${endpoint}chat/completions"
                AiProvider.ANTHROPIC -> "${endpoint}messages"
                AiProvider.GOOGLE -> "${endpoint}models/$model:generateContent"
                AiProvider.WENXIN -> "${endpoint}chat/completions"
            }

            val requestBody = buildChatRequestBody(config, messages, model, temperature, systemPrompt, stream = false)
            val request = Request.Builder().url(url)
                .apply { authHeaders(config).forEach { (k, v) -> addHeader(k, v) } }
                .post(requestBody.toString().toRequestBody(jsonMediaType))
                .build()

            buildClient(config).newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string() ?: ""
                    throw Exception("HTTP ${resp.code}: $err")
                }
                val body = resp.body?.string() ?: throw Exception("Empty response")
                parseChatResponse(body, config.provider)
            }
        }
    }

    // ========== 聊天 (流式 SSE) ==========
    fun chatStream(
        messages: List<AiMessage>,
        config: AiProviderConfig,
        model: String = config.chatModel,
        temperature: Float = config.temperature,
        systemPrompt: String? = null
    ): Flow<ChatStreamChunk> = callbackFlow {
        val endpoint = ensureTrailingSlash(config.endpoint)
        val url = when (config.provider) {
            AiProvider.OPENAI, AiProvider.DEEPSEEK, AiProvider.QWEN, AiProvider.KIMI,
            AiProvider.OLLAMA, AiProvider.CUSTOM, AiProvider.WENXIN -> "${endpoint}chat/completions"
            AiProvider.ANTHROPIC -> "${endpoint}messages"
            AiProvider.GOOGLE -> "${endpoint}models/$model:streamGenerateContent?alt=sse"
        }
        val requestBody = buildChatRequestBody(config, messages, model, temperature, systemPrompt, stream = true)
        val request = Request.Builder().url(url)
            .apply { authHeaders(config, isStreaming = true).forEach { (k, v) -> addHeader(k, v) } }
            .post(requestBody.toString().toRequestBody(jsonMediaType))
            .build()

        val call = buildClient(config).newCall(request)
        try {
            val resp = call.execute()
            if (!resp.isSuccessful) {
                val err = resp.body?.string() ?: ""
                trySend(ChatStreamChunk(null, true, "HTTP ${resp.code}: $err))
                channel.close()
                return@callbackFlow
            }
            val reader = BufferedReader(InputStreamReader(resp.body?.byteStream()))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: continue
                when {
                    l.startsWith("data: [DONE]") -> {
                        trySend(ChatStreamChunk(null, true, null))
                        break
                    }
                    l.startsWith("data:") -> {
                        val jsonStr = l.substring(5).trim()
                        if (jsonStr.isNotEmpty()) {
                            val delta = parseStreamDelta(jsonStr, config.provider)
                            if (delta != null) trySend(ChatStreamChunk(delta, false, null))
                        }
                    }
                    l == "[DONE]" -> trySend(ChatStreamChunk(null, true, null))
                }
            }
            reader.close()
        } catch (e: Exception) {
                trySend(ChatStreamChunk(null, true, e.message))
        } finally {
            channel.close()
        }
        awaitClose { call.cancel()
    }.flowOn(Dispatchers.IO)

    // ========== 图像生成 ==========
    suspend fun generateImages(
        prompt: String,
        config: AiProviderConfig,
        model: String = config.imageModel,
        size: String = "1024x1024",
        n: Int = 1,
        quality: String? = null
    ): Result<List<GeneratedImage>> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = ensureTrailingSlash(config.endpoint)
            val url = "${endpoint}images/generations"
            val json = JsonObject().apply {
                addProperty("model", model)
                addProperty("prompt", prompt)
                addProperty("n", n)
                addProperty("size", size)
                if (quality?.isNotEmpty() == true) addProperty("quality", quality)
            }
            val request = Request.Builder().url(url)
                .apply { authHeaders(config).forEach { (k, v) -> addHeader(k, v) } }
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()

            buildClient(config).newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string() ?: ""
                    throw Exception("HTTP ${resp.code}: $err")
                }
                val body = resp.body?.string() ?: throw Exception("Empty response")
                val respObj = GSON.fromJsonObject<ImageGenerationResponse>(body).getOrThrow()
                if (respObj.error?.message?.let { throw Exception(it) }
                respObj.data?.mapIndexed { idx, item ->
                    GeneratedImage(
                        id = "${System.currentTimeMillis()}_$idx",
                        prompt = prompt,
                        url = item.url,
                        b64 = item.b64_json,
                        revisedPrompt = item.revised_prompt,
                        model = model,
                        createdAt = System.currentTimeMillis()
                    )
                } ?: throw Exception("No images returned")
            }
        }
    }

    // ========== 视频生成 ==========
    suspend fun generateVideo(
        prompt: String,
        config: AiProviderConfig,
        model: String = config.videoModel,
        aspectRatio: String = "16:9",
        durationSeconds: Int = 10
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = ensureTrailingSlash(config.endpoint)
            // 优先尝试 images/generations 风格端点 (部分供应商有不同的视频 API
            val candidates = listOfNotNull(
                "${endpoint}videos/generations",
                "${endpoint}videos:create",
                "${endpoint}media/generations/video"
            )
            var lastErr: Exception? = null
            for (url in candidates) {
                try {
                    val json = JsonObject().apply {
                    addProperty("model", model)
                    addProperty("prompt", prompt)
                    addProperty("aspect_ratio", aspectRatio)
                    addProperty("duration_seconds", durationSeconds)
                }
                val request = Request.Builder().url(url)
                    .apply { authHeaders(config).forEach { (k, v) -> addHeader(k, v) } }
                    .post(json.toString().toRequestBody(jsonMediaType))
                    .build()
                buildClient(config).newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) { lastErr = Exception("HTTP ${resp.code}: ${resp.body?.string()}")
                        return@use
                    }
                    val body = resp.body?.string() ?: return@use
                    val parsed = GSON.fromJsonObject<VideoGenerationResponse>(body).getOrThrow()
                    if (parsed.error?.message?.let { throw Exception(it) }
                    parsed.data?.url ?: throw Exception("No video returned")
                }
                lastErr = null
                break
            }
            if (lastErr != null) throw lastErr!!
            ""
        }
    }

    // ========== TTS ==========
    suspend fun textToSpeech(
        text: String,
        config: AiProviderConfig,
        model: String = config.ttsModel,
        voice: String = "alloy",
        speed: Float = 1.0f
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = ensureTrailingSlash(config.endpoint)
            val url = "${endpoint}audio/speech"
            val json = JsonObject().apply {
                addProperty("model", model)
                addProperty("input", text)
                addProperty("voice", voice)
                addProperty("response_format", "mp3")
                addProperty("speed", speed.toDouble())
            }
            val request = Request.Builder().url(url)
                .apply { authHeaders(config).forEach { (k, v) -> addHeader(k, v) } }
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()
            buildClient(config).newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string() ?: ""
                    throw Exception("HTTP ${resp.code}: $err")
                }
                resp.body?.bytes() ?: throw Exception("Empty audio response")
            }
        }
    }

    // ========== 视觉分析 (Vision) ==========
    suspend fun visionAnalyze(
        prompt: String,
        imageBase64: String,
        config: AiProviderConfig,
        model: String = config.visionModel,
        detail: String = "auto"
    ): Result<String> = withContext(Dispatchers.IO) {
            runCatching {
                val endpoint = ensureTrailingSlash(config.endpoint)
                val url = "${endpoint}chat/completions"
                val json = JsonObject().apply {
                    addProperty("model", model)
                    val messagesArr = com.google.gson.JsonArray()
                    val msgObj = JsonObject()
                    msgObj.addProperty("role", "user")
                    val contentArr = com.google.gson.JsonArray()
                    val textObj = JsonObject()
                    textObj.addProperty("type", "text")
                    textObj.addProperty("text", prompt)
                    contentArr.add(textObj)
                    val imgObj = JsonObject()
                    imgObj.addProperty("type", "image_url")
                    val urlObj = JsonObject()
                    urlObj.addProperty("url", "data:image/jpeg;base64,$imageBase64")
                    urlObj.addProperty("detail", detail)
                    imgObj.add("image_url", urlObj)
                    contentArr.add(imgObj)
                    msgObj.add("content", contentArr)
                    messagesArr.add(msgObj)
                    add("messages", messagesArr)
                    addProperty("temperature", config.temperature.toDouble())
                }
                val request = Request.Builder().url(url)
                    .apply { authHeaders(config).forEach { (k, v) -> addHeader(k, v) } }
                    .post(json.toString().toRequestBody(jsonMediaType))
                    .build()
                buildClient(config).newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        val err = resp.body?.string() ?: ""
                        throw Exception("HTTP ${resp.code}: $err")
                    }
                    val body = resp.body?.string() ?: throw Exception("Empty response")
                    parseChatResponse(body, config.provider)
                }
            }
        }

    // ========== 文本工具箱：单步函数（翻译/摘要/润色 基于 chat API ==========
    suspend fun textTool(
        tool: String,
        input: String,
        config: AiProviderConfig,
        model: String = config.chatModel
    ): Result<String> {
        val prompt = when (tool) {
            "translate_en" -> listOf(AiMessage("user", "请将以下文本翻译为英文：\n$input"))
            "translate_zh" -> listOf(AiMessage("user", "请将以下文本翻译为中文：\n$input"))
            "summarize" -> listOf(AiMessage("user", "请用中文总结以下内容的要点，用项目符号列出：\n$input"))
            "polish" -> listOf(AiMessage("user", "请润色以下文本，使其更流畅、专业，并给出修改说明：\n$input"))
            "expand" -> listOf(AiMessage("user", "请扩写以下内容，使其更丰富详细：\n$input"))
            "rewrite_formal" -> listOf(AiMessage("user", "请将以下文本改写为正式风格：\n$input"))
            "rewrite_casual" -> listOf(AiMessage("user", "请将以下文本改写为轻松口语风格：\n$input"))
            "explain_code" -> listOf(AiMessage("user", "请详细解释以下代码的作用、思路和可能的改进：\n$input"))
            "brainstorm" -> listOf(AiMessage("user", "围绕以下主题进行头脑风暴，给出至少 5 个方向：\n$input"))
            "translate_ja" -> listOf(AiMessage("user", "请将以下文本翻译为日文：\n$input"))
            else -> listOf(AiMessage("user", input))
        }
        return chat(prompt, config, model = model)
    }

    // ========== 请求体构造 ==========
    private fun buildChatRequestBody(
        config: AiProviderConfig,
        messages: List<AiMessage>,
        model: String,
        temperature: Float,
        systemPrompt: String?,
        stream: Boolean
    ): JsonObject {
        return when (config.provider) {
            AiProvider.OPENAI, AiProvider.DEEPSEEK, AiProvider.QWEN, AiProvider.KIMI,
            AiProvider.OLLAMA, AiProvider.CUSTOM, AiProvider.WENXIN -> {
                JsonObject().apply {
                    addProperty("model", model)
                    addProperty("temperature", temperature.toDouble())
                    addProperty("stream", stream)
                    val msgs = com.google.gson.JsonArray()
                    if (!systemPrompt.isNullOrEmpty()) {
                        val sys = JsonObject()
                        sys.addProperty("role", "system")
                        sys.addProperty("content", systemPrompt)
                        msgs.add(sys)
                    }
                    messages.forEach { m ->
                        val obj = JsonObject()
                        obj.addProperty("role", m.role)
                        obj.addProperty("content", m.content)
                        msgs.add(obj)
                    }
                    add("messages", msgs)
                }
            }
            AiProvider.ANTHROPIC -> {
                JsonObject().apply {
                    addProperty("model", model)
                    addProperty("max_tokens", 4096)
                    addProperty("stream", stream)
                    addProperty("temperature", temperature.toDouble())
                    systemPrompt?.let { addProperty("system", it) }
                    val msgs = com.google.gson.JsonArray()
                    messages.forEach { m ->
                        val obj = JsonObject()
                        obj.addProperty("role", m.role)
                        obj.addProperty("content", m.content)
                        msgs.add(obj)
                    }
                    add("messages", msgs)
                }
            }
            AiProvider.GOOGLE -> {
                JsonObject().apply {
                    val contents = com.google.gson.JsonArray()
                    messages.forEach { m ->
                        val c = JsonObject()
                        // Gemini 使用 parts 数组
                        val role = when (m.role)
                        c.addProperty("role", if (role == "user" || role == "assistant") role else "user")
                        val parts = com.google.gson.JsonArray()
                        val part = JsonObject()
                        part.addProperty("text", m.content)
                        parts.add(part)
                        c.add("parts", parts)
                        contents.add(c)
                    }
                    add("contents", contents)
                    if (!systemPrompt.isNullOrEmpty()) {
                        val sysIns = JsonObject()
                        val parts = com.google.gson.JsonArray()
                        val part = JsonObject()
                        part.addProperty("text", systemPrompt)
                        parts.add(part)
                        sysIns.add("parts", parts)
                        add("systemInstruction", sysIns)
                    }
                    val genConfig = JsonObject()
                    genConfig.addProperty("temperature", temperature.toDouble())
                    add("generationConfig", genConfig)
                }
            }
        }
    }

    // ========== 响应解析 ==========
    private fun parseChatResponse(body: String, provider: AiProvider): String {
        return when (provider) {
            AiProvider.OPENAI, AiProvider.DEEPSEEK, AiProvider.QWEN, AiProvider.KIMI,
            AiProvider.OLLAMA, AiProvider.CUSTOM, AiProvider.WENXIN -> {
                val resp = GSON.fromJsonObject<ChatCompletionResponse>(body).getOrThrow()
                resp.error?.message?.let { throw Exception(it) }
                resp.choices?.firstOrNull()?.message?.content ?: ""
            }
            AiProvider.ANTHROPIC -> {
                val json = JsonParser.parseString(body).asJsonObject
                val contents = json.getAsJsonArray("content")
                val err = json.get("error")?.asJsonObject?.get("message")?.asString
                if (!err.isNullOrEmpty()) throw Exception(err)
                contents?.mapNotNull { c ->
                    c.asJsonObject.get("text")?.asString }?.joinToString("") ?: ""
                } ?: ""
            }
            AiProvider.GOOGLE -> {
                val json = JsonParser.parseString(body).asJsonObject
                val candidates = json.getAsJsonArray("candidates")
                candidates?.firstOrNull()?.asJsonObject?.getAsJsonObject("content")?.getAsJsonArray("parts")
                    ?.map { it.asJsonObject.get("text")?.asString }?.joinToString("")
                ?: ""
            }
        }
    }

    private fun parseStreamDelta(jsonStr: String, provider: AiProvider): String? {
        return runCatching {
            val json = JsonParser.parseString(jsonStr).asJsonObject
            when (provider) {
                AiProvider.OPENAI, AiProvider.DEEPSEEK, AiProvider.QWEN, AiProvider.KIMI,
                AiProvider.OLLAMA, AiProvider.CUSTOM, AiProvider.WENXIN -> {
                    json.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject
                        ?.getAsJsonObject("delta")?.get("content")?.asString
                }
                AiProvider.ANTHROPIC -> {
                    val type = json.get("type")?.asString
                    if (type == "content_block_delta") {
                        json.getAsJsonObject("delta")?.get("text")?.asString
                    } else null
                }
                AiProvider.GOOGLE -> {
                    json.getAsJsonArray("candidates")?.firstOrNull()?.asJsonObject
                        ?.getAsJsonObject("content")?.getAsJsonArray("parts")
                        ?.firstOrNull()?.asJsonObject?.get("text")?.asString
                }
            }
        }.getOrNull()
    }
}
