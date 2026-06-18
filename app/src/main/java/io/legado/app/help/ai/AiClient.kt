package io.legado.app.help.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * AI API 统一客户端
 * - 支持 OpenAI 兼容协议（OpenAI、DeepSeek、Qwen、硅基流动、Ollama、Groq、OpenRouter、智谱、讯飞）
 * - 支持 Anthropic Claude
 * - 支持 Google Gemini（文本对话）
 */
object AiClient {

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private fun httpClient(timeoutSeconds: Int): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    // ========== 聊天（非流式） ==========
    suspend fun chat(
        messages: List<AiMessage>,
        config: AiProviderConfig,
        model: String = config.chatModel,
        temperature: Float = config.temperature,
        systemPrompt: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val finalMessages = buildList {
                if (!systemPrompt.isNullOrEmpty()) {
                    add(AiMessage("system", systemPrompt))
                }
                addAll(messages)
            }
            when (config.provider) {
                AiProvider.ANTHROPIC -> chatAnthropic(finalMessages, config, model, temperature)
                AiProvider.GOOGLE -> chatGemini(finalMessages, config, model, temperature)
                else -> chatOpenAiCompatible(finalMessages, config, model, temperature)
            }
        }
    }

    private fun chatOpenAiCompatible(
        messages: List<AiMessage>,
        config: AiProviderConfig,
        model: String,
        temperature: Float
    ): String {
        val url = config.endpoint.trimEnd('/') + "/chat/completions"
        val bodyJson = buildString {
            append("{")
            append("\"model\":").append(jsonString(model)).append(",")
            append("\"temperature\":").append(temperature).append(",")
            append("\"messages\":[")
            messages.forEachIndexed { i, m ->
                if (i > 0) append(",")
                append("{\"role\":").append(jsonString(m.role)).append(",\"content\":").append(jsonString(m.content)).append("}")
            }
            append("]")
            append("}")
        }
        val req = Request.Builder()
            .url(url)
            .apply {
                if (config.provider == AiProvider.OLLAMA || config.apiKey.isEmpty()) {
                    // Ollama 不需要 key
                } else {
                    header("Authorization", "Bearer ${config.apiKey}")
                }
            }
            .post(bodyJson.toRequestBody(JSON_MEDIA))
            .build()
        httpClient(config.timeoutSeconds).newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw Exception("HTTP ${resp.code}: ${resp.body?.string()?.take(500)}")
            }
            val text = resp.body?.string().orEmpty()
            // 提取第一条 message.content
            val idx1 = text.indexOf("\"content\":")
            if (idx1 < 0) throw Exception("响应中无 content: $text")
            val start = text.indexOf("\"", idx1 + 10) + 1
            var end = start
            var inEscape = false
            while (end < text.length) {
                val c = text[end]
                if (inEscape) { inEscape = false; end++; continue }
                if (c == '\\') { inEscape = true; end++; continue }
                if (c == '"') break
                end++
            }
            val raw = text.substring(start, end)
            return unescapeJsonString(raw)
        }
    }

    private fun chatAnthropic(
        messages: List<AiMessage>,
        config: AiProviderConfig,
        model: String,
        temperature: Float
    ): String {
        val url = config.endpoint.trimEnd('/') + "/messages"
        val (sysPrompt, userMsgs) = separateSystemPrompt(messages)
        val messagesJson = userMsgs.joinToString(",") { m ->
            "{\"role\":${jsonString(if (m.role == "system") "user" else m.role)},\"content\":${jsonString(m.content)}}"
        }
        val bodyJson = buildString {
            append("{")
            append("\"model\":").append(jsonString(model)).append(",")
            append("\"max_tokens\":4096,")
            append("\"temperature\":").append(temperature).append(",")
            if (sysPrompt != null) {
                append("\"system\":").append(jsonString(sysPrompt)).append(",")
            }
            append("\"messages\":[").append(messagesJson).append("]")
            append("}")
        }
        val req = Request.Builder()
            .url(url)
            .header("x-api-key", config.apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .post(bodyJson.toRequestBody(JSON_MEDIA))
            .build()
        httpClient(config.timeoutSeconds).newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}: ${resp.body?.string()?.take(500)}")
            val text = resp.body?.string().orEmpty()
            // 查找第一个 "text":"... 形式
            val idx1 = text.indexOf("\"text\":")
            if (idx1 < 0) throw Exception("响应中无 text: $text")
            val start = text.indexOf("\"", idx1 + 8) + 1
            var end = start
            var inEscape = false
            while (end < text.length) {
                val c = text[end]
                if (inEscape) { inEscape = false; end++; continue }
                if (c == '\\') { inEscape = true; end++; continue }
                if (c == '"') break
                end++
            }
            return unescapeJsonString(text.substring(start, end))
        }
    }

    private fun chatGemini(
        messages: List<AiMessage>,
        config: AiProviderConfig,
        model: String,
        temperature: Float
    ): String {
        val url = "${config.endpoint.trimEnd('/')}/models/$model:generateContent?key=${config.apiKey}"
        val (sysPrompt, userMsgs) = separateSystemPrompt(messages)
        val contentsJson = userMsgs.joinToString(",") { m ->
            val role = if (m.role == "assistant") "model" else "user"
            "{\"role\":${jsonString(role)},\"parts\":[{\"text\":${jsonString(m.content)}}]}"
        }
        val bodyJson = buildString {
            append("{")
            if (sysPrompt != null) {
                append("\"systemInstruction\":{\"parts\":[{\"text\":${jsonString(sysPrompt)}}]},")
            }
            append("\"generationConfig\":{\"temperature\":").append(temperature).append("},")
            append("\"contents\":[").append(contentsJson).append("]")
            append("}")
        }
        val req = Request.Builder()
            .url(url)
            .header("content-type", "application/json")
            .post(bodyJson.toRequestBody(JSON_MEDIA))
            .build()
        httpClient(config.timeoutSeconds).newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}: ${resp.body?.string()?.take(500)}")
            val text = resp.body?.string().orEmpty()
            val idx1 = text.indexOf("\"text\":")
            if (idx1 < 0) throw Exception("响应中无 text: $text")
            val start = text.indexOf("\"", idx1 + 8) + 1
            var end = start
            var inEscape = false
            while (end < text.length) {
                val c = text[end]
                if (inEscape) { inEscape = false; end++; continue }
                if (c == '\\') { inEscape = true; end++; continue }
                if (c == '"') break
                end++
            }
            return unescapeJsonString(text.substring(start, end))
        }
    }

    private fun separateSystemPrompt(messages: List<AiMessage>): Pair<String?, List<AiMessage>> {
        val sys = messages.firstOrNull { it.role == "system" }?.content
        val rest = if (messages.firstOrNull()?.role == "system") messages.drop(1) else messages
        return sys to rest
    }

    // ========== 流式聊天 ==========
    fun chatStream(
        messages: List<AiMessage>,
        config: AiProviderConfig,
        model: String = config.chatModel,
        temperature: Float = config.temperature,
        systemPrompt: String? = null
    ): Flow<ChatStreamChunk> = flow {
        runCatching {
            val finalMessages = buildList {
                if (!systemPrompt.isNullOrEmpty()) {
                    add(AiMessage("system", systemPrompt))
                }
                addAll(messages)
            }
            when (config.provider) {
                AiProvider.ANTHROPIC -> streamAnthropic(finalMessages, config, model, temperature)
                AiProvider.GOOGLE -> streamGemini(finalMessages, config, model, temperature)
                else -> streamOpenAiCompatible(finalMessages, config, model, temperature)
            }
        }.onSuccess { flowInternal ->
            flowInternal.collect { emit(it) }
        }.onFailure { err ->
            emit(ChatStreamChunk(delta = null, error = err.message, done = true))
        }
    }.flowOn(Dispatchers.IO)

    private fun streamOpenAiCompatible(
        messages: List<AiMessage>,
        config: AiProviderConfig,
        model: String,
        temperature: Float
    ): Flow<ChatStreamChunk> = callbackFlow {
        val url = config.endpoint.trimEnd('/') + "/chat/completions"
        val bodyJson = buildString {
            append("{")
            append("\"model\":").append(jsonString(model)).append(",")
            append("\"temperature\":").append(temperature).append(",")
            append("\"stream\":true,")
            append("\"messages\":[")
            messages.forEachIndexed { i, m ->
                if (i > 0) append(",")
                append("{\"role\":").append(jsonString(m.role)).append(",\"content\":").append(jsonString(m.content)).append("}")
            }
            append("]")
            append("}")
        }
        val req = Request.Builder()
            .url(url)
            .apply {
                if (config.provider != AiProvider.OLLAMA && config.apiKey.isNotEmpty()) {
                    header("Authorization", "Bearer ${config.apiKey}")
                }
            }
            .post(bodyJson.toRequestBody(JSON_MEDIA))
            .build()
        val call = httpClient(config.timeoutSeconds).newCall(req)
        val resp: Response = call.execute()
        try {
            if (!resp.isSuccessful) {
                val err = "HTTP ${resp.code}: ${resp.body?.string()?.take(500)}"
                trySend(ChatStreamChunk(null, err, true))
                return@callbackFlow
            }
            val reader = BufferedReader(InputStreamReader(resp.body?.byteStream()))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line?.trim() ?: continue
                if (l.isEmpty()) continue
                if (!l.startsWith("data:")) continue
                val payload = l.substring(5).trim()
                if (payload == "[DONE]") {
                    trySend(ChatStreamChunk(null, null, true))
                    break
                }
                // 简单解析，查找 "delta":{"role":"xxx","content":"YYY"} 或 "delta":{"content":"YYY"}
                val cIdx = payload.indexOf("\"content\":")
                if (cIdx > 0) {
                    val start = payload.indexOf("\"", cIdx + 10) + 1
                    var end = start
                    var inEscape = false
                    while (end < payload.length) {
                        val c = payload[end]
                        if (inEscape) { inEscape = false; end++; continue }
                        if (c == '\\') { inEscape = true; end++; continue }
                        if (c == '"') break
                        end++
                    }
                    val delta = unescapeJsonString(payload.substring(start, end))
                    trySend(ChatStreamChunk(delta, null, false))
                }
            }
        } catch (e: Throwable) {
            trySend(ChatStreamChunk(null, e.message, true))
        } finally {
            resp.close()
        }
        awaitClose { call.cancel() }
    }

    private fun streamAnthropic(
        messages: List<AiMessage>,
        config: AiProviderConfig,
        model: String,
        temperature: Float
    ): Flow<ChatStreamChunk> = callbackFlow {
        val url = config.endpoint.trimEnd('/') + "/messages"
        val (sysPrompt, userMsgs) = separateSystemPrompt(messages)
        val messagesJson = userMsgs.joinToString(",") { m ->
            "{\"role\":${jsonString(if (m.role == "system") "user" else m.role)},\"content\":${jsonString(m.content)}}"
        }
        val bodyJson = buildString {
            append("{")
            append("\"model\":").append(jsonString(model)).append(",")
            append("\"max_tokens\":4096,")
            append("\"temperature\":").append(temperature).append(",")
            append("\"stream\":true,")
            if (sysPrompt != null) {
                append("\"system\":").append(jsonString(sysPrompt)).append(",")
            }
            append("\"messages\":[").append(messagesJson).append("]")
            append("}")
        }
        val req = Request.Builder()
            .url(url)
            .header("x-api-key", config.apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .post(bodyJson.toRequestBody(JSON_MEDIA))
            .build()
        val call = httpClient(config.timeoutSeconds).newCall(req)
        val resp = call.execute()
        try {
            if (!resp.isSuccessful) {
                trySend(ChatStreamChunk(null, "HTTP ${resp.code}: ${resp.body?.string()?.take(500)}", true))
                return@callbackFlow
            }
            val reader = BufferedReader(InputStreamReader(resp.body?.byteStream()))
            var line: String?
            var eventType = ""
            while (reader.readLine().also { line = it } != null) {
                val l = line?.trim() ?: continue
                if (l.startsWith("event:")) {
                    eventType = l.substring(6).trim()
                } else if (l.startsWith("data:")) {
                    val payload = l.substring(5).trim()
                    if (eventType == "content_block_delta") {
                        val cIdx = payload.indexOf("\"text\":")
                        if (cIdx > 0) {
                            val start = payload.indexOf("\"", cIdx + 8) + 1
                            var end = start
                            var inEscape = false
                            while (end < payload.length) {
                                val c = payload[end]
                                if (inEscape) { inEscape = false; end++; continue }
                                if (c == '\\') { inEscape = true; end++; continue }
                                if (c == '"') break
                                end++
                            }
                            trySend(ChatStreamChunk(unescapeJsonString(payload.substring(start, end)), null, false))
                        }
                    } else if (eventType == "message_stop") {
                        trySend(ChatStreamChunk(null, null, true))
                        break
                    }
                }
            }
        } catch (e: Throwable) {
            trySend(ChatStreamChunk(null, e.message, true))
        } finally {
            resp.close()
        }
        awaitClose { call.cancel() }
    }

    private fun streamGemini(
        messages: List<AiMessage>,
        config: AiProviderConfig,
        model: String,
        temperature: Float
    ): Flow<ChatStreamChunk> = callbackFlow {
        val url = "${config.endpoint.trimEnd('/')}/models/$model:streamGenerateContent?alt=sse&key=${config.apiKey}"
        val (sysPrompt, userMsgs) = separateSystemPrompt(messages)
        val contentsJson = userMsgs.joinToString(",") { m ->
            val role = if (m.role == "assistant") "model" else "user"
            "{\"role\":${jsonString(role)},\"parts\":[{\"text\":${jsonString(m.content)}}]}"
        }
        val bodyJson = buildString {
            append("{")
            if (sysPrompt != null) {
                append("\"systemInstruction\":{\"parts\":[{\"text\":${jsonString(sysPrompt)}}]},")
            }
            append("\"generationConfig\":{\"temperature\":").append(temperature).append("},")
            append("\"contents\":[").append(contentsJson).append("]")
            append("}")
        }
        val req = Request.Builder()
            .url(url)
            .header("content-type", "application/json")
            .post(bodyJson.toRequestBody(JSON_MEDIA))
            .build()
        val call = httpClient(config.timeoutSeconds).newCall(req)
        val resp = call.execute()
        try {
            if (!resp.isSuccessful) {
                trySend(ChatStreamChunk(null, "HTTP ${resp.code}: ${resp.body?.string()?.take(500)}", true))
                return@callbackFlow
            }
            val reader = BufferedReader(InputStreamReader(resp.body?.byteStream()))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line?.trim() ?: continue
                if (!l.startsWith("data:")) continue
                val payload = l.substring(5).trim()
                val cIdx = payload.indexOf("\"text\":")
                if (cIdx > 0) {
                    val start = payload.indexOf("\"", cIdx + 8) + 1
                    var end = start
                    var inEscape = false
                    while (end < payload.length) {
                        val c = payload[end]
                        if (inEscape) { inEscape = false; end++; continue }
                        if (c == '\\') { inEscape = true; end++; continue }
                        if (c == '"') break
                        end++
                    }
                    trySend(ChatStreamChunk(unescapeJsonString(payload.substring(start, end)), null, false))
                }
            }
            trySend(ChatStreamChunk(null, null, true))
        } catch (e: Throwable) {
            trySend(ChatStreamChunk(null, e.message, true))
        } finally {
            resp.close()
        }
        awaitClose { call.cancel() }
    }

    // ========== 图像生成 ==========
    suspend fun generateImages(
        prompt: String,
        config: AiProviderConfig,
        model: String = config.imageModel,
        size: String = "1024x1024",
        n: Int = 1,
        quality: String = "standard"
    ): Result<List<GeneratedImage>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = config.endpoint.trimEnd('/') + "/images/generations"
            val bodyJson = buildString {
                append("{")
                append("\"model\":").append(jsonString(model.ifEmpty { "dall-e-3" })).append(",")
                append("\"prompt\":").append(jsonString(prompt)).append(",")
                append("\"size\":").append(jsonString(size)).append(",")
                append("\"quality\":").append(jsonString(quality)).append(",")
                append("\"n\":").append(n).append(",")
                append("\"response_format\":\"b64_json\"")
                append("}")
            }
            val req = Request.Builder()
                .url(url)
                .apply {
                    if (config.provider != AiProvider.OLLAMA && config.apiKey.isNotEmpty()) {
                        header("Authorization", "Bearer ${config.apiKey}")
                    }
                }
                .post(bodyJson.toRequestBody(JSON_MEDIA))
                .build()
            httpClient(config.timeoutSeconds).newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}: ${resp.body?.string()?.take(500)}")
                val text = resp.body?.string().orEmpty()
                // 查找 b64_json 或 url 列表
                val result = mutableListOf<GeneratedImage>()
                // 简单提取 "url":"xxx" 和 "b64_json":"xxx"
                val regex = Regex("\"url\"\\s*:\\s*\"([^\"]+)\"")
                for (match in regex.findAll(text)) {
                    result.add(GeneratedImage(url = match.groupValues[1], prompt = prompt))
                }
                val b64Regex = Regex("\"b64_json\"\\s*:\\s*\"([^\"]+)\"")
                for (match in b64Regex.findAll(text)) {
                    result.add(GeneratedImage(url = null, prompt = prompt, base64 = match.groupValues[1]))
                }
                if (result.isEmpty()) {
                    throw Exception("未在响应中找到图像: ${text.take(500)}")
                }
                result
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
            // 尝试多个可能的视频生成 API 路径：
            // - /videos/generations (OpenAI 风格)
            // - /videos/generation (通用)
            // - /v1/videos/generation
            val candidatePaths = listOf("/videos/generations", "/videos/generation", "/v1/videos/generation")
            var lastErr: Throwable? = null
            for (path in candidatePaths) {
                try {
                    val url = config.endpoint.trimEnd('/') + path
                    val bodyJson = buildString {
                        append("{")
                        append("\"model\":").append(jsonString(model.ifEmpty { "sora" })).append(",")
                        append("\"prompt\":").append(jsonString(prompt)).append(",")
                        append("\"aspect_ratio\":").append(jsonString(aspectRatio)).append(",")
                        append("\"duration\":").append(durationSeconds)
                        append("}")
                    }
                    val req = Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer ${config.apiKey}")
                        .post(bodyJson.toRequestBody(JSON_MEDIA))
                        .build()
                    httpClient(config.timeoutSeconds).newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            lastErr = Exception("HTTP ${resp.code}: ${resp.body?.string()?.take(500)}")
                            return@use
                        }
                        val text = resp.body?.string().orEmpty()
                        // 简单查找 "url":"..."
                        val match = Regex("\"url\"\\s*:\\s*\"([^\"]+)\"").find(text)
                        if (match != null) {
                            return@runCatching match.groupValues[1]
                        }
                        // 可能需要轮询：查找 "id"
                        val idMatch = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(text)
                        if (idMatch != null) {
                            return@runCatching "生成请求已提交 ID: ${idMatch.groupValues[1]}, 请稍后使用该 ID 获取视频"
                        }
                        lastErr = Exception("未找到 URL: ${text.take(300)}")
                    }
                } catch (t: Throwable) {
                    lastErr = t
                }
            }
            throw lastErr ?: Exception("视频生成失败")
        }
    }

    // ========== 视觉分析 ==========
    suspend fun visionAnalyze(
        prompt: String,
        imageBase64: String,
        config: AiProviderConfig,
        model: String = config.visionModel
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = config.endpoint.trimEnd('/') + "/chat/completions"
            val bodyJson = buildString {
                append("{")
                append("\"model\":").append(jsonString(model.ifEmpty { "gpt-4o" })).append(",")
                append("\"messages\":[{\"role\":\"user\",\"content\":[")
                append("{\"type\":\"text\",\"text\":").append(jsonString(prompt)).append("},")
                append("{\"type\":\"image_url\",\"image_url\":{\"url\":\"data:image/jpeg;base64,").append(imageBase64).append("\"}}")
                append("]}]}")
                append("}")
            }
            val req = Request.Builder()
                .url(url)
                .apply {
                    if (config.provider != AiProvider.OLLAMA && config.apiKey.isNotEmpty()) {
                        header("Authorization", "Bearer ${config.apiKey}")
                    }
                }
                .post(bodyJson.toRequestBody(JSON_MEDIA))
                .build()
            httpClient(config.timeoutSeconds).newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}: ${resp.body?.string()?.take(500)}")
                val text = resp.body?.string().orEmpty()
                val idx1 = text.indexOf("\"content\":")
                if (idx1 < 0) throw Exception("响应中无 content: $text")
                val start = text.indexOf("\"", idx1 + 10) + 1
                var end = start
                var inEscape = false
                while (end < text.length) {
                    val c = text[end]
                    if (inEscape) { inEscape = false; end++; continue }
                    if (c == '\\') { inEscape = true; end++; continue }
                    if (c == '"') break
                    end++
                }
                return@runCatching unescapeJsonString(text.substring(start, end))
            }
        }
    }

    // ========== 文本工具（复用 chat） ==========
    suspend fun textTool(
        toolId: String,
        input: String,
        config: AiProviderConfig,
        model: String = config.chatModel
    ): Result<String> {
        val tool = TEXT_TOOLS.firstOrNull { it.id == toolId }
        return chat(
            messages = listOf(AiMessage("user", input)),
            config = config,
            model = model,
            systemPrompt = tool?.systemPrompt
        )
    }

    // ========== 工具函数 ==========
    private fun jsonString(value: String): String {
        val sb = StringBuilder()
        sb.append('"')
        for (c in value) {
            when {
                c == '\\' -> sb.append("\\\\")
                c == '"' -> sb.append("\\\"")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c.code < 0x20 -> sb.append(String.format("\\u%04x", c.code))
                else -> sb.append(c)
            }
        }
        sb.append('"')
        return sb.toString()
    }

    private fun unescapeJsonString(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val next = s[i + 1]) {
                    '"' -> { sb.append('"'); i += 2 }
                    '\\' -> { sb.append('\\'); i += 2 }
                    '/' -> { sb.append('/'); i += 2 }
                    'n' -> { sb.append('\n'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    'u' -> {
                        val hex = s.substring(i + 2, i + 6).toIntOrNull(16)
                        if (hex != null) {
                            sb.append(hex.toChar())
                            i += 6
                        } else {
                            sb.append(c); i++
                        }
                    }
                    else -> { sb.append(c); i++ }
                }
            } else {
                sb.append(c); i++
            }
        }
        return sb.toString()
    }
}
