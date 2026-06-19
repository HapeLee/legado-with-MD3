package io.legado.app.help.ai

import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.help.http.addHeaders
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.postJson
import io.legado.app.ui.main.ai.AiChatException
import io.legado.app.ui.main.ai.AiChatMessage
import io.legado.app.ui.main.ai.AiContextSummary
import io.legado.app.ui.main.ai.AI_API_MODE_CHAT_COMPLETIONS
import io.legado.app.ui.main.ai.AI_API_MODE_RESPONSES
import io.legado.app.ui.main.ai.AiProviderConfig
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import splitties.init.appCtx
import java.io.IOException
import java.net.SocketException
import java.util.concurrent.TimeUnit

object AiChatService {

    private const val MAX_TOOL_ROUNDS = 12
    private const val MAX_SEARCH_RESULT_CARDS = 8
    private const val DEFAULT_TOOL_TIMEOUT_MILLIS = 120_000L
    private const val IMAGE_TOOL_TIMEOUT_MILLIS = 300_000L
    private const val NETWORK_ABORT_RETRY_COUNT = 1
    private const val MAX_DEBUG_LOG_CHARS = 16_000
    private const val MAX_DEBUG_PAYLOAD_CHARS = 8_000
    private val imageToolNames = setOf(
        "generate_image",
        "generate_book_character_avatar"
    )
    private val retryableToolNames = setOf(
        "query_bookshelf",
        "get_bookshelf_book_info",
        "list_book_chapters",
        "read_book_chapter_content",
        "query_read_records",
        "list_book_sources",
        "search_book_source",
        "get_book_source",
        "fetch_source_html",
        "debug_book_source",
        "reading_ajax",
        "reading_webview",
        "capture_web_requests",
        "search_web_tavily",
        "generate_image",
        "generate_book_character_avatar",
        "list_book_characters",
        "list_book_character_relations",
        "get_app_settings"
    )

    private data class ToolCall(
        val id: String,
        val name: String,
        val arguments: String
    )

    private data class ToolCallBuilder(
        var id: String = "",
        var name: String = "",
        val arguments: StringBuilder = StringBuilder()
    )

    private data class ToolEvent(
        val name: String,
        val stage: String,
        val content: String,
        val success: Boolean = true
    )

    private data class AssistantTurn(
        val content: String,
        val toolCalls: List<ToolCall>,
        val rawMessage: JSONObject,
        val reasoningContent: String = ""
    )

    suspend fun chat(messages: List<AiChatMessage>): String {
        return chatStream(messages, onPartial = {})
    }

    suspend fun fetchModels(provider: AiProviderConfig): List<String> {
        val baseUrl = provider.baseUrl.trim()
        require(baseUrl.isNotBlank()) { "Base URL is empty" }
        val response = okHttpClient.newCallResponse {
            url(resolveModelsUrl(baseUrl))
            addHeader("Accept", "application/json")
            provider.apiKey.trim().takeIf { it.isNotBlank() }?.let {
                addHeader("Authorization", "Bearer $it")
            }
            addHeaders(parseCustomHeaders(provider.headers.orEmpty()))
        }
        response.use { rawResponse ->
            val payload = rawResponse.body?.string().orEmpty()
            if (!rawResponse.isSuccessful) {
                throw AiChatException(
                    message = extractError(payload).ifBlank {
                        "${rawResponse.code} ${rawResponse.message}"
                    },
                    debugLog = safeDebugLog("url=${resolveModelsUrl(baseUrl)}\nresponse=$payload\n")
                )
            }
            val root = JSONObject(payload)
            val data = root.optJSONArray("data") ?: return emptyList()
            return buildList {
                for (index in 0 until data.length()) {
                    val item = data.optJSONObject(index) ?: continue
                    item.optString("id").trim().takeIf { it.isNotBlank() }?.let(::add)
                }
            }.distinct()
        }
    }

    suspend fun chatStream(
        messages: List<AiChatMessage>,
        onPartial: (String) -> Unit,
        onThinking: (String) -> Unit = {},
        onStatus: (JSONObject) -> Unit = {},
        includeStructuredBlocks: Boolean = true,
        contextSummary: AiContextSummary? = null,
        onContextSummary: (AiContextSummary) -> Unit = {},
        onContextStats: (JSONObject) -> Unit = {},
        useAllTools: Boolean = false
    ): String {
        val provider = AppConfig.aiCurrentProvider
        val modelConfig = AppConfig.aiCurrentModelConfig
        val baseUrl = provider?.baseUrl?.trim().orEmpty()
        val model = modelConfig?.modelId?.trim().orEmpty()
        val apiMode = normalizeApiMode(provider?.apiMode)
        val chatUrl = resolveChatUrl(baseUrl, apiMode)
        val promptCacheKey = provider
            ?.takeIf { it.promptCache }
            ?.let { buildPromptCacheKey(it, model) }
        require(baseUrl.isNotBlank()) { "Base URL is empty" }
        require(model.isNotBlank()) { "Model is empty" }

        val tools = runCatching {
            if (useAllTools) AiToolRegistry.resolveAllTools() else AiToolRegistry.resolveAvailableTools()
        }.getOrDefault(emptyList())
        val requestLog = StringBuilder().apply {
            append("url=$chatUrl").append('\n')
            append("model=$model").append('\n')
            append("apiMode=$apiMode").append('\n')
            append("provider=${provider?.name.orEmpty()}").append('\n')
            append("tools=${tools.joinToString { it.name }}").append('\n')
        }
        val reserveTokens = estimateStaticRequestTokens(messages, tools)
        val preparedContext = AiContextManager.prepare(messages, contextSummary, reserveTokens)
        val estimatedTotalTokens = reserveTokens + preparedContext.inputTokens
        preparedContext.summary
            ?.takeIf { preparedContext.compressed && it.isValid }
            ?.let(onContextSummary)
        onContextStats(
            JSONObject().apply {
                put("compressed", preparedContext.compressed)
                put("inputTokens", preparedContext.inputTokens)
                put("limitTokens", preparedContext.limitTokens)
                put("reserveTokens", reserveTokens)
                put("totalTokens", estimatedTotalTokens)
            }
        )
        val conversation = buildConversation(preparedContext.messages, preparedContext.summary)
        if (estimatedTotalTokens > preparedContext.limitTokens) {
            throw AiChatException(
                message = "当前 AI 静态配置或本轮输入超过上下文限制，已自动压缩但仍无法放入，请减少系统提示词、Skill、工具或本次输入。",
                debugLog = requestLog.append("estimatedTotalTokens=$estimatedTotalTokens\n")
                    .append("limitTokens=${preparedContext.limitTokens}\n")
                    .toSafeDebugLog()
            )
        }

        return runCatching {
            executeToolLoop(
                baseUrl = baseUrl,
                chatUrl = chatUrl,
                apiMode = apiMode,
                model = model,
                providerApiKey = provider?.apiKey.orEmpty(),
                providerHeaders = provider?.headers.orEmpty(),
                conversation = conversation,
                tools = tools,
                requestLog = requestLog,
                onPartial = onPartial,
                onThinking = onThinking,
                onStatus = onStatus,
                includeStructuredBlocks = includeStructuredBlocks,
                promptCacheKey = promptCacheKey,
                useAllTools = useAllTools
            )
        }.getOrElse { throwable ->
            if (throwable is AiChatException) {
                throw throwable
            }
            throw AiChatException(
                message = throwable.message ?: throwable.javaClass.simpleName,
                debugLog = requestLog.toSafeDebugLog(),
                cause = throwable
            )
        }
    }

    private suspend fun executeToolLoop(
        baseUrl: String,
        chatUrl: String,
        apiMode: String,
        model: String,
        providerApiKey: String,
        providerHeaders: String,
        conversation: MutableList<JSONObject>,
        tools: List<AiResolvedTool>,
        requestLog: StringBuilder,
        onPartial: (String) -> Unit,
        onThinking: (String) -> Unit,
        onStatus: (JSONObject) -> Unit,
        includeStructuredBlocks: Boolean,
        promptCacheKey: String?,
        useAllTools: Boolean
    ): String {
        val toolMap = tools.associateBy { it.name }
        val searchResultCards = JSONArray()
        val toolEvents = JSONArray()
        repeat(MAX_TOOL_ROUNDS) { round ->
            val roundNo = round + 1
            val thinkingKey = "thinking_$roundNo"
            onStatus(
                JSONObject().apply {
                    put("key", thinkingKey)
                    put("kind", "thinking")
                    put("stage", "start")
                    put("round", roundNo)
                    put("label", appCtx.getString(R.string.ai_chat_thinking))
                    put("success", true)
                }
            )
            val assistantTurn = requestCompletionStreamWithRetry(
                chatUrl = chatUrl,
                apiMode = apiMode,
                model = model,
                providerApiKey = providerApiKey,
                providerHeaders = providerHeaders,
                messages = conversation,
                tools = tools,
                promptCacheKey = promptCacheKey,
                requestLog = requestLog,
                round = roundNo,
                onPartial = onPartial,
                onThinking = onThinking
            )
            conversation += assistantTurn.rawMessage
            if (assistantTurn.toolCalls.isEmpty()) {
                onStatus(
                    JSONObject().apply {
                        put("key", thinkingKey)
                        put("kind", "thinking")
                        put("stage", "finish")
                        put("round", roundNo)
                        put("label", appCtx.getString(R.string.ai_chat_thinking_done))
                        put("content", assistantTurn.reasoningContent)
                        put("removeIfBlank", assistantTurn.reasoningContent.isBlank())
                        put("success", true)
                    }
                )
                val content = assistantTurn.content
                if (content.isBlank()) {
                    throw AiChatException(
                        message = "Empty response",
                        debugLog = requestLog.toSafeDebugLog()
                    )
                }
                return if (includeStructuredBlocks) {
                    appendStructuredBlocks(content, searchResultCards, toolEvents)
                } else {
                    content
                }
            }
            onStatus(
                JSONObject().apply {
                    put("key", thinkingKey)
                    put("kind", "thinking")
                    put("stage", "finish")
                    put("round", roundNo)
                    put("label", appCtx.getString(R.string.ai_chat_thinking_done))
                    put("content", assistantTurn.reasoningContent)
                    put("fallback", appCtx.getString(R.string.ai_tool_status_calling))
                    put("success", true)
                }
            )
            assistantTurn.toolCalls.forEach { toolCall ->
                onStatus(
                    JSONObject().apply {
                        put("key", toolCall.id.ifBlank { toolCall.name })
                        put("kind", "tool")
                        put("name", toolCall.name)
                        put("stage", "call")
                        put("label", appCtx.getString(R.string.ai_tool_status_calling))
                        put("content", toolCall.arguments)
                        put("success", true)
                    }
                )
                toolEvents.put(
                    JSONObject().apply {
                        put("name", toolCall.name)
                        put("stage", "call")
                        put("content", toolCall.arguments)
                        put("success", true)
                    }
                )
                val result = executeToolCall(toolCall, toolMap, useAllTools)
                collectSearchResultCards(toolCall, result, searchResultCards)
                val resultSuccess = parseToolResultSuccess(result)
                toolEvents.put(
                    JSONObject().apply {
                        put("name", toolCall.name)
                        put("stage", "result")
                        put("content", result)
                        put("success", resultSuccess)
                    }
                )
                onStatus(
                    JSONObject().apply {
                        put("key", toolCall.id.ifBlank { toolCall.name })
                        put("kind", "tool")
                        put("name", toolCall.name)
                        put("stage", "result")
                        put(
                            "label",
                            appCtx.getString(
                                if (resultSuccess) R.string.ai_tool_status_done else R.string.ai_tool_status_failed
                            )
                        )
                        put("content", result)
                        put("success", resultSuccess)
                    }
                )
                conversation += JSONObject().apply {
                    if (apiMode == AI_API_MODE_RESPONSES) {
                        put("type", "function_call_output")
                        put("call_id", toolCall.id)
                        put("output", result)
                    } else {
                        put("role", "tool")
                        put("tool_call_id", toolCall.id)
                        put("content", result)
                    }
                }
            }
        }
        conversation += JSONObject().apply {
            put("role", "system")
            put(
                "content",
                appCtx.getString(R.string.ai_tool_round_limit_system_prompt)
            )
        }
        val finalTurn = requestCompletionStreamWithRetry(
            chatUrl = chatUrl,
            apiMode = apiMode,
            model = model,
            providerApiKey = providerApiKey,
            providerHeaders = providerHeaders,
            messages = conversation,
            tools = emptyList(),
            promptCacheKey = promptCacheKey,
            requestLog = requestLog,
            round = MAX_TOOL_ROUNDS + 1,
            onPartial = onPartial,
            onThinking = onThinking
        )
        if (finalTurn.content.isBlank()) {
            throw AiChatException(
                message = appCtx.getString(R.string.ai_tool_round_limit_summary),
                debugLog = requestLog.toSafeDebugLog()
            )
        }
        return if (includeStructuredBlocks) {
            appendStructuredBlocks(finalTurn.content, searchResultCards, toolEvents)
        } else {
            finalTurn.content
        }
    }

    private fun collectSearchResultCards(
        toolCall: ToolCall,
        result: String,
        cards: JSONArray
    ) {
        if (toolCall.name != "search_book_source") return
        runCatching {
            val results = JSONObject(result).optJSONArray("results") ?: return
            for (index in 0 until results.length()) {
                if (cards.length() >= MAX_SEARCH_RESULT_CARDS) break
                val item = results.optJSONObject(index) ?: continue
                if (item.optString("bookUrl").isBlank() || item.optString("origin").isBlank()) continue
                cards.put(JSONObject().apply {
                    put("name", item.optString("name").take(80))
                    put("author", item.optString("author").take(60))
                    put("originName", item.optString("originName").take(60))
                    put("kind", item.optString("kind").take(80))
                    put("intro", item.optString("intro").replace(Regex("\\s+"), " ").trim().take(160))
                    put("latestChapterTitle", item.optString("latestChapterTitle").take(80))
                    put("coverUrl", item.optString("coverUrl"))
                    put("bookUrl", item.optString("bookUrl"))
                    put("origin", item.optString("origin"))
                    put("target", item.optString("target"))
                })
            }
        }
    }

    private fun appendStructuredBlocks(content: String, cards: JSONArray, toolEvents: JSONArray): String {
        if (cards.length() == 0) return content
        val payload = JSONObject().apply {
            put("type", "search_book_results")
            put("results", cards)
        }
        return buildString {
            append(content.trimEnd())
            if (cards.length() > 0) {
                append("\n\n```legado-search-results\n")
                append(payload)
                append("\n```")
            }
        }
    }

    private fun parseToolResultSuccess(result: String): Boolean {
        return runCatching {
            JSONObject(result).optBoolean("ok", true)
        }.getOrDefault(true)
    }

    private fun aiChatHttpClient() = okHttpClient.newBuilder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .callTimeout(300, TimeUnit.SECONDS)
        .build()

    private suspend fun executeToolCall(
        toolCall: ToolCall,
        toolMap: Map<String, AiResolvedTool>,
        useAllTools: Boolean
    ): String {
        val enabled = AppConfig.aiEnabledToolNames.ifEmpty { AiToolRegistry.defaultEnabledTools }
        if (!useAllTools && toolCall.name !in enabled) {
            return JSONObject().apply {
                put("ok", false)
                put("error", "Tool is disabled: ${toolCall.name}")
            }.toString()
        }
        val resolvedTool = toolMap[toolCall.name]
        if (resolvedTool == null) {
            return JSONObject().apply {
                put("ok", false)
                put("error", "Unknown tool: ${toolCall.name}")
            }.toString()
        }
        val arguments = runCatching {
            toolCall.arguments.trim().takeIf { it.isNotBlank() }?.let(::JSONObject)
        }.getOrElse { throwable ->
            return JSONObject().apply {
                put("ok", false)
                put("error", throwable.message ?: throwable.javaClass.simpleName)
            }.toString()
        }
        return runCatching {
            var lastError: Throwable? = null
            repeat(NETWORK_ABORT_RETRY_COUNT + 1) { attempt ->
                try {
                    return@runCatching withTimeout(toolTimeoutMillis(toolCall.name)) {
                        resolvedTool.execute(arguments)
                    }
                } catch (throwable: Throwable) {
                    lastError = throwable
                    if (attempt >= NETWORK_ABORT_RETRY_COUNT ||
                        toolCall.name !in retryableToolNames ||
                        !throwable.isRetryableNetworkAbort()
                    ) {
                        throw throwable
                    }
                }
            }
            throw lastError ?: IllegalStateException("Tool failed")
        }.getOrElse { throwable ->
            JSONObject().apply {
                put("ok", false)
                put(
                    "error",
                    if (throwable is TimeoutCancellationException) {
                        "Tool timed out after ${toolTimeoutMillis(toolCall.name)} ms"
                    } else {
                        throwable.message ?: throwable.javaClass.simpleName
                    }
                )
            }.toString()
        }
    }

    private fun toolTimeoutMillis(name: String): Long {
        return if (name in imageToolNames) IMAGE_TOOL_TIMEOUT_MILLIS else DEFAULT_TOOL_TIMEOUT_MILLIS
    }

    private fun Throwable.isRetryableNetworkAbort(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            val message = current.message.orEmpty().lowercase()
            if (current is SocketException) return true
            if (current is IOException && (
                    "software caused connection abort" in message ||
                            "connection reset" in message ||
                            "unexpected end of stream" in message ||
                            "stream was reset" in message ||
                            "closed" in message && "connection" in message
                    )
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private suspend fun requestCompletionStreamWithRetry(
        chatUrl: String,
        apiMode: String,
        model: String,
        providerApiKey: String,
        providerHeaders: String,
        messages: List<JSONObject>,
        tools: List<AiResolvedTool>,
        promptCacheKey: String?,
        requestLog: StringBuilder,
        round: Int,
        onPartial: (String) -> Unit,
        onThinking: (String) -> Unit
    ): AssistantTurn {
        var lastError: Throwable? = null
        repeat(NETWORK_ABORT_RETRY_COUNT + 1) { attempt ->
            try {
                if (attempt > 0) {
                    requestLog.append("round=").append(round)
                        .append(" retry=").append(attempt)
                        .append(" reason=").append(lastError?.message ?: lastError?.javaClass?.simpleName)
                        .append('\n')
                    onThinking("连接中断，正在重试一次")
                }
                return requestCompletionStream(
                    chatUrl = chatUrl,
                    apiMode = apiMode,
                    model = model,
                    providerApiKey = providerApiKey,
                    providerHeaders = providerHeaders,
                    messages = messages,
                    tools = tools,
                    promptCacheKey = promptCacheKey,
                    requestLog = requestLog,
                    round = round,
                    onPartial = onPartial,
                    onThinking = onThinking
                )
            } catch (throwable: Throwable) {
                lastError = throwable
                if (attempt >= NETWORK_ABORT_RETRY_COUNT || !throwable.isRetryableNetworkAbort()) {
                    throw throwable
                }
            }
        }
        throw lastError ?: IllegalStateException("AI request failed")
    }

    private suspend fun requestCompletionStream(
        chatUrl: String,
        apiMode: String,
        model: String,
        providerApiKey: String,
        providerHeaders: String,
        messages: List<JSONObject>,
        tools: List<AiResolvedTool>,
        promptCacheKey: String?,
        requestLog: StringBuilder,
        round: Int,
        onPartial: (String) -> Unit,
        onThinking: (String) -> Unit
    ): AssistantTurn {
        val requestBody = buildRequestBody(
            messages = messages,
            model = model,
            tools = tools,
            stream = true,
            apiMode = apiMode,
            promptCacheKey = promptCacheKey
        )
        requestLog.append("round=").append(round).append('\n')
            .append("request=").append(safeDebugPayload(requestBody)).append('\n')
        val response = aiChatHttpClient().newCallResponse {
            url(chatUrl)
            addHeader("Accept", "text/event-stream, application/json")
            addHeader("Content-Type", "application/json")
            providerApiKey.trim().takeIf { it.isNotBlank() }?.let {
                addHeader("Authorization", "Bearer $it")
            }
            addHeaders(parseCustomHeaders(providerHeaders))
            postJson(requestBody)
        }
        response.use { rawResponse ->
            val body = rawResponse.body ?: throw AiChatException(
                message = "Empty response body",
                debugLog = requestLog.append("response=<empty body>\n").toSafeDebugLog()
            )
            if (!rawResponse.isSuccessful) {
                val payload = body.string()
                throw AiChatException(
                    message = extractError(payload).ifBlank {
                        "${rawResponse.code} ${rawResponse.message}"
                    },
                    debugLog = buildString {
                        append(requestLog)
                        append("status=${rawResponse.code} ${rawResponse.message}").append('\n')
                        append("response=").append(safeDebugPayload(payload)).append('\n')
                    }.let(::safeDebugLog)
                )
            }
            val rendered = StringBuilder()
            val rawRendered = StringBuilder()
            val reasoningRendered = StringBuilder()
            val rawPayload = StringBuilder()
            val toolCallBuilders = linkedMapOf<Int, ToolCallBuilder>()
            body.byteStream().bufferedReader().use { reader ->
                while (true) {
                    val rawLine = reader.readLine()?.trim() ?: break
                    if (rawLine.isEmpty()) continue
                    rawPayload.append(rawLine).append('\n')
                    if (rawLine.startsWith("data:")) {
                        val payload = rawLine.removePrefix("data:").trim()
                        if (payload == "[DONE]") break
                        if (apiMode == AI_API_MODE_RESPONSES) {
                            consumeResponsesStreamPayload(payload, rawRendered, rendered, reasoningRendered, toolCallBuilders, onPartial, onThinking)
                        } else {
                            consumeStreamPayload(payload, rawRendered, rendered, reasoningRendered, toolCallBuilders, onPartial, onThinking)
                        }
                    } else if (rawLine.startsWith("{")) {
                        if (apiMode == AI_API_MODE_RESPONSES) {
                            consumeResponsesStreamPayload(rawLine, rawRendered, rendered, reasoningRendered, toolCallBuilders, onPartial, onThinking)
                        } else {
                            consumeStreamPayload(rawLine, rawRendered, rendered, reasoningRendered, toolCallBuilders, onPartial, onThinking)
                        }
                    }
                }
            }
            requestLog.append("response=").append(safeDebugPayload(rawPayload.toString())).append('\n')
            val toolCalls = toolCallBuilders.map { (index, builder) ->
                ToolCall(
                    id = builder.id.ifBlank { "call_$index" },
                    name = builder.name,
                    arguments = builder.arguments.toString().ifBlank { "{}" }
                )
            }.filter { it.name.isNotBlank() }
            if (rendered.isBlank() && toolCalls.isEmpty()) {
                val fallback = runCatching { extractContent(rawPayload.toString()) }.getOrDefault("")
                if (fallback.isNotBlank()) {
                    val visibleFallback = stripInlineThinking(fallback, onThinking)
                    onPartial(visibleFallback)
                    return AssistantTurn(
                        visibleFallback,
                        emptyList(),
                        if (apiMode == AI_API_MODE_RESPONSES) {
                            buildResponsesRawMessage(visibleFallback, emptyList())
                        } else {
                            buildAssistantRawMessage(visibleFallback, emptyList(), reasoningRendered.toString())
                        },
                        reasoningRendered.toString()
                    )
                }
            }
            return AssistantTurn(
                content = rendered.toString(),
                toolCalls = toolCalls,
                rawMessage = if (apiMode == AI_API_MODE_RESPONSES) {
                    buildResponsesRawMessage(rendered.toString(), toolCalls)
                } else {
                    buildAssistantRawMessage(rendered.toString(), toolCalls, reasoningRendered.toString())
                },
                reasoningContent = reasoningRendered.toString()
            )
        }
    }

    private fun buildRequestBody(
        messages: List<JSONObject>,
        model: String,
        tools: List<AiResolvedTool>,
        stream: Boolean,
        apiMode: String,
        promptCacheKey: String?
    ): String {
        if (apiMode == AI_API_MODE_RESPONSES) {
            return buildResponsesRequestBody(messages, model, tools, stream, promptCacheKey)
        }
        return JSONObject().apply {
            put("model", model)
            put("stream", stream)
            promptCacheKey?.let { put("prompt_cache_key", it) }
            put("messages", JSONArray().apply {
                messages.forEach { put(it) }
            })
            if (tools.isNotEmpty()) {
                put("tools", JSONArray().apply {
                    tools.forEach { put(it.definition) }
                })
                put("tool_choice", "auto")
            }
        }.toString()
    }

    private fun buildResponsesRequestBody(
        messages: List<JSONObject>,
        model: String,
        tools: List<AiResolvedTool>,
        stream: Boolean,
        promptCacheKey: String?
    ): String {
        return JSONObject().apply {
            put("model", model)
            put("stream", stream)
            promptCacheKey?.let { put("prompt_cache_key", it) }
            put("input", buildResponsesInput(messages))
            if (tools.isNotEmpty()) {
                put("tools", JSONArray().apply {
                    tools.forEach { tool ->
                        responsesToolDefinition(tool.definition)?.let(::put)
                    }
                })
                put("tool_choice", "auto")
            }
        }.toString()
    }

    private fun buildResponsesInput(messages: List<JSONObject>): JSONArray {
        val input = JSONArray()
        messages.forEach { message ->
            when (message.optString("type")) {
                "responses_output" -> {
                    val items = message.optJSONArray("items") ?: JSONArray()
                    for (index in 0 until items.length()) {
                        items.optJSONObject(index)?.let(input::put)
                    }
                }
                "function_call", "function_call_output" -> input.put(message)
                else -> appendResponsesMessage(input, message)
            }
        }
        return input
    }

    private fun appendResponsesMessage(input: JSONArray, message: JSONObject) {
        val role = message.optString("role")
        if (role == "tool") {
            input.put(JSONObject().apply {
                put("type", "function_call_output")
                put("call_id", message.optString("tool_call_id"))
                put("output", message.optString("content"))
            })
            return
        }
        val content = message.optString("content")
        if (content.isNotBlank() && content != "null") {
            input.put(JSONObject().apply {
                put("role", role.ifBlank { "user" })
                put("content", content)
            })
        }
        val toolCalls = message.optJSONArray("tool_calls") ?: return
        for (index in 0 until toolCalls.length()) {
            val toolCall = toolCalls.optJSONObject(index) ?: continue
            val function = toolCall.optJSONObject("function") ?: continue
            input.put(JSONObject().apply {
                put("type", "function_call")
                put("call_id", toolCall.optString("id").ifBlank { "call_$index" })
                put("name", function.optString("name"))
                put("arguments", extractToolArguments(function.opt("arguments")))
            })
        }
    }

    private fun responsesToolDefinition(definition: JSONObject): JSONObject? {
        val function = definition.optJSONObject("function") ?: definition
        val name = function.optString("name").takeIf { it.isNotBlank() } ?: return null
        return JSONObject().apply {
            put("type", "function")
            put("name", name)
            put("description", function.optString("description"))
            put("parameters", function.optJSONObject("parameters") ?: JSONObject().put("type", "object"))
        }
    }

    private fun consumeResponsesStreamPayload(
        payload: String,
        rawRendered: StringBuilder,
        rendered: StringBuilder,
        reasoningRendered: StringBuilder,
        toolCallBuilders: MutableMap<Int, ToolCallBuilder>,
        onPartial: (String) -> Unit,
        onThinking: (String) -> Unit
    ) {
        extractError(payload).takeIf { it.isNotBlank() }?.let {
            throw IllegalStateException(it)
        }
        val root = JSONObject(payload)
        val type = root.optString("type")
        when {
            type.contains("reasoning", ignoreCase = true) && type.endsWith(".delta") -> {
                appendReasoningDelta(extractContentText(root.opt("delta")), reasoningRendered, onThinking)
            }
            type == "response.output_text.delta" || type.endsWith(".output_text.delta") -> {
                appendVisibleDelta(extractContentText(root.opt("delta")), rawRendered, rendered, onPartial, onThinking)
            }
            type == "response.function_call_arguments.delta" || type.endsWith(".function_call_arguments.delta") -> {
                appendResponsesToolDelta(root, toolCallBuilders)
            }
            type == "response.function_call_arguments.done" || type.endsWith(".function_call_arguments.done") -> {
                applyResponsesToolItem(root, toolCallBuilders)
            }
            type == "response.output_item.added" || type == "response.output_item.done" -> {
                root.optJSONObject("item")?.let { item ->
                    applyResponsesOutputItem(item, rawRendered, rendered, reasoningRendered, toolCallBuilders, onPartial, onThinking)
                }
            }
            type == "response.completed" -> {
                root.optJSONObject("response")
                    ?.optJSONArray("output")
                    ?.let { output ->
                        applyResponsesOutputArray(output, rawRendered, rendered, reasoningRendered, toolCallBuilders, onPartial, onThinking)
                    }
            }
            type == "response.failed" || type == "response.incomplete" -> {
                val message = root.optJSONObject("response")
                    ?.optJSONObject("error")
                    ?.optString("message")
                    .orEmpty()
                    .ifBlank { root.optJSONObject("error")?.optString("message").orEmpty() }
                    .ifBlank { type }
                throw IllegalStateException(message)
            }
            type.isBlank() -> {
                root.optJSONArray("output")?.let { output ->
                    applyResponsesOutputArray(output, rawRendered, rendered, reasoningRendered, toolCallBuilders, onPartial, onThinking)
                } ?: run {
                    val text = extractResponsesText(root)
                    if (text.isNotBlank()) {
                        appendVisibleDelta(text, rawRendered, rendered, onPartial, onThinking)
                    }
                }
            }
        }
    }

    private fun appendVisibleDelta(
        delta: String,
        rawRendered: StringBuilder,
        rendered: StringBuilder,
        onPartial: (String) -> Unit,
        onThinking: (String) -> Unit
    ) {
        if (delta.isEmpty()) return
        rawRendered.append(delta)
        val visibleText = stripInlineThinking(rawRendered.toString(), onThinking)
        if (visibleText != rendered.toString()) {
            rendered.clear()
            rendered.append(visibleText)
            onPartial(visibleText)
        }
    }

    private fun appendReasoningDelta(
        delta: String,
        reasoningRendered: StringBuilder,
        onThinking: (String) -> Unit
    ) {
        if (delta.isBlank()) return
        reasoningRendered.append(delta)
        onThinking(delta)
    }

    private fun appendResponsesToolDelta(
        root: JSONObject,
        toolCallBuilders: MutableMap<Int, ToolCallBuilder>
    ) {
        val builder = responsesToolBuilder(
            toolCallBuilders = toolCallBuilders,
            callId = root.optString("call_id").ifBlank { root.optString("item_id") },
            outputIndex = root.optInt("output_index", -1)
        )
        root.optString("call_id").takeIf { it.isNotBlank() }?.let { builder.id = it }
        root.optString("name").takeIf { it.isNotBlank() }?.let { builder.name = it }
        extractContentText(root.opt("delta")).takeIf { it.isNotEmpty() }?.let {
            builder.arguments.append(it)
        }
    }

    private fun applyResponsesToolItem(
        item: JSONObject,
        toolCallBuilders: MutableMap<Int, ToolCallBuilder>
    ) {
        val builder = responsesToolBuilder(
            toolCallBuilders = toolCallBuilders,
            callId = item.optString("call_id").ifBlank { item.optString("id") },
            outputIndex = item.optInt("output_index", -1)
        )
        item.optString("call_id").ifBlank { item.optString("id") }
            .takeIf { it.isNotBlank() }
            ?.let { builder.id = it }
        item.optString("name").takeIf { it.isNotBlank() }?.let { builder.name = it }
        val arguments = extractToolArguments(item.opt("arguments"))
        if (arguments != "{}" && builder.arguments.isBlank()) {
            builder.arguments.append(arguments)
        }
    }

    private fun applyResponsesOutputArray(
        output: JSONArray,
        rawRendered: StringBuilder,
        rendered: StringBuilder,
        reasoningRendered: StringBuilder,
        toolCallBuilders: MutableMap<Int, ToolCallBuilder>,
        onPartial: (String) -> Unit,
        onThinking: (String) -> Unit
    ) {
        for (index in 0 until output.length()) {
            output.optJSONObject(index)?.let { item ->
                if (!item.has("output_index")) item.put("output_index", index)
                applyResponsesOutputItem(item, rawRendered, rendered, reasoningRendered, toolCallBuilders, onPartial, onThinking)
            }
        }
    }

    private fun applyResponsesOutputItem(
        item: JSONObject,
        rawRendered: StringBuilder,
        rendered: StringBuilder,
        reasoningRendered: StringBuilder,
        toolCallBuilders: MutableMap<Int, ToolCallBuilder>,
        onPartial: (String) -> Unit,
        onThinking: (String) -> Unit
    ) {
        when (item.optString("type")) {
            "function_call" -> applyResponsesToolItem(item, toolCallBuilders)
            "message" -> {
                if (rendered.isBlank()) {
                    extractResponsesText(item).takeIf { it.isNotBlank() }?.let {
                        appendVisibleDelta(it, rawRendered, rendered, onPartial, onThinking)
                    }
                }
            }
            "reasoning" -> {
                extractResponsesReasoning(item).takeIf { it.isNotBlank() }?.let {
                    appendReasoningDelta(it, reasoningRendered, onThinking)
                }
            }
        }
    }

    private fun responsesToolBuilder(
        toolCallBuilders: MutableMap<Int, ToolCallBuilder>,
        callId: String,
        outputIndex: Int
    ): ToolCallBuilder {
        if (callId.isNotBlank()) {
            toolCallBuilders.entries.firstOrNull { it.value.id == callId }?.let {
                return it.value
            }
        }
        val key = if (outputIndex >= 0) {
            outputIndex
        } else {
            (toolCallBuilders.keys.maxOrNull() ?: -1) + 1
        }
        return toolCallBuilders.getOrPut(key) { ToolCallBuilder(id = callId) }
    }

    private fun consumeStreamPayload(
        payload: String,
        rawRendered: StringBuilder,
        rendered: StringBuilder,
        reasoningRendered: StringBuilder,
        toolCallBuilders: MutableMap<Int, ToolCallBuilder>,
        onPartial: (String) -> Unit,
        onThinking: (String) -> Unit
    ) {
        extractError(payload).takeIf { it.isNotBlank() }?.let {
            throw IllegalStateException(it)
        }
        val root = JSONObject(payload)
        val choice = root.optJSONArray("choices")?.optJSONObject(0) ?: return
        val delta = choice.optJSONObject("delta") ?: choice.optJSONObject("message") ?: return
        val reasoningText = extractContentText(delta.opt("reasoning_content"))
            .ifBlank { extractContentText(delta.opt("reasoning")) }
            .ifBlank { extractContentText(delta.opt("thinking")) }
        if (reasoningText.isNotBlank()) {
            reasoningRendered.append(reasoningText)
            onThinking(reasoningText)
        }
        val deltaText = extractContentText(delta.opt("content"))
        if (deltaText.isNotEmpty()) {
            rawRendered.append(deltaText)
            val visibleText = stripInlineThinking(rawRendered.toString(), onThinking)
            if (visibleText != rendered.toString()) {
                rendered.clear()
                rendered.append(visibleText)
                onPartial(visibleText)
            }
        }
        val toolCalls = delta.optJSONArray("tool_calls") ?: return
        for (i in 0 until toolCalls.length()) {
            val toolCall = toolCalls.optJSONObject(i) ?: continue
            val index = toolCall.optInt("index", i)
            val builder = toolCallBuilders.getOrPut(index) { ToolCallBuilder() }
            toolCall.optString("id").takeIf { it.isNotBlank() }?.let { builder.id = it }
            val function = toolCall.optJSONObject("function") ?: continue
            function.optString("name").takeIf { it.isNotBlank() }?.let { builder.name = it }
            val args = function.opt("arguments")
            when (args) {
                is String -> builder.arguments.append(args)
                is JSONObject, is JSONArray -> builder.arguments.append(args.toString())
            }
        }
    }

    private fun buildAssistantRawMessage(
        content: String,
        toolCalls: List<ToolCall>,
        reasoningContent: String = ""
    ): JSONObject {
        return JSONObject().apply {
            put("role", "assistant")
            put("content", if (content.isBlank()) JSONObject.NULL else content)
            if (reasoningContent.isNotBlank()) {
                put("reasoning_content", reasoningContent)
            }
            if (toolCalls.isNotEmpty()) {
                put(
                    "tool_calls",
                    JSONArray().apply {
                        toolCalls.forEach { toolCall ->
                            put(
                                JSONObject().apply {
                                    put("id", toolCall.id)
                                    put("type", "function")
                                    put(
                                        "function",
                                        JSONObject().apply {
                                            put("name", toolCall.name)
                                            put("arguments", toolCall.arguments)
                                        }
                                    )
                                }
                            )
                        }
                    }
                )
            }
        }
    }

    private fun buildResponsesRawMessage(
        content: String,
        toolCalls: List<ToolCall>
    ): JSONObject {
        return JSONObject().apply {
            put("type", "responses_output")
            put(
                "items",
                JSONArray().apply {
                    if (content.isNotBlank()) {
                        put(
                            JSONObject().apply {
                                put("type", "message")
                                put("role", "assistant")
                                put(
                                    "content",
                                    JSONArray().put(
                                        JSONObject().apply {
                                            put("type", "output_text")
                                            put("text", content)
                                        }
                                    )
                                )
                            }
                        )
                    }
                    toolCalls.forEach { toolCall ->
                        put(
                            JSONObject().apply {
                                put("type", "function_call")
                                put("call_id", toolCall.id)
                                put("name", toolCall.name)
                                put("arguments", toolCall.arguments)
                            }
                        )
                    }
                }
            )
        }
    }

    private fun buildConversation(
        messages: List<AiChatMessage>,
        contextSummary: AiContextSummary? = null
    ): MutableList<JSONObject> {
        val conversation = mutableListOf<JSONObject>()
        conversation += JSONObject().apply {
            put("role", "system")
            put("content", AppConfig.aiSystemPrompt.ifBlank { AppConfig.DEFAULT_AI_SYSTEM_PROMPT })
        }
        AppConfig.aiCurrentPersona?.prompt?.takeIf { it.isNotBlank() }?.let { personaPrompt ->
            conversation += JSONObject().apply {
                put("role", "system")
                put("content", personaPrompt)
            }
        }
        contextSummary?.summary?.takeIf { it.isNotBlank() }?.let { summary ->
            conversation += JSONObject().apply {
                put("role", "system")
                put("content", "Conversation summary from earlier context:\n$summary")
            }
        }
        AppConfig.aiEnabledSkills.forEach { skill ->
            conversation += JSONObject().apply {
                put("role", "system")
                put(
                    "content",
                    buildString {
                        append("以下是用户启用的真实 SKILL.md，请把它作为当前 agent 的能力规范执行。")
                        append("Skill 名称：")
                        append(skill.name)
                        if (skill.description.isNotBlank()) {
                            append("\nSkill 描述：")
                            append(skill.description)
                        }
                        if (skill.sourceUrl.isNotBlank()) {
                            append("\nSkill 来源：")
                            append(skill.sourceUrl)
                        }
                        append("\n\n")
                        append(skill.content)
                    }
                )
            }
        }
        if (requiresBookshelfTool(messages)) {
            conversation += JSONObject().apply {
                put("role", "system")
                put(
                    "content",
                    "本轮用户请求涉及本地书架、书籍详情、阅读记录、分组、标签或书源搜索。回复正文前必须先调用合适的本地工具；不要只说明将要查询。需要选择书源时先调用 list_book_sources。search_book_source 的结果会由客户端自动渲染成可点击卡片，回复里不要生成链接、不要输出内部 URL、不要手写 Markdown 打开链接，只需要用自然语言简短说明搜索结果。"
                )
            }
        }
        val textMessages = messages.filter { (it.kind ?: AiChatMessage.Kind.TEXT) == AiChatMessage.Kind.TEXT }
        val requestMessages = if (AppConfig.aiContextCompressionEnabled) textMessages else textMessages.takeLast(12)
        requestMessages.forEach { message ->
            conversation += JSONObject().apply {
                put(
                    "role",
                    if (message.role == AiChatMessage.Role.USER) "user" else "assistant"
                )
                if (message.role == AiChatMessage.Role.ASSISTANT) {
                    val (visibleContent, reasoningContent) = splitInlineThinking(
                        stripSearchResultBlocks(message.content)
                    )
                    put("content", visibleContent)
                    if (reasoningContent.isNotBlank()) {
                        put("reasoning_content", reasoningContent)
                    }
                } else {
                    put("content", stripSearchResultBlocks(message.content))
                }
            }
        }
        return conversation
    }

    private fun estimateStaticRequestTokens(messages: List<AiChatMessage>, tools: List<AiResolvedTool>): Int {
        val systemTokens = AiContextManager.estimateTokens(AppConfig.aiSystemPrompt)
        val personaTokens = AiContextManager.estimateTokens(AppConfig.aiCurrentPersona?.prompt.orEmpty())
        val skillTokens = AppConfig.aiEnabledSkills.sumOf {
            AiContextManager.estimateTokens(it.name) +
                AiContextManager.estimateTokens(it.description) +
                AiContextManager.estimateTokens(it.sourceUrl) +
                AiContextManager.estimateTokens(it.content)
        }
        val bookshelfHintTokens = if (requiresBookshelfTool(messages)) 180 else 0
        val toolTokens = tools.sumOf { AiContextManager.estimateTokens(it.definition.toString()) + 16 }
        return systemTokens + personaTokens + skillTokens + bookshelfHintTokens + toolTokens + 256
    }

    private fun stripSearchResultBlocks(content: String): String {
        return searchResultBlockRegex.replace(content, "").trim()
    }

    private fun StringBuilder.toSafeDebugLog(): String {
        return safeDebugLog(toString())
    }

    private fun safeDebugLog(text: String): String {
        return safeDebugPayload(text, MAX_DEBUG_LOG_CHARS)
    }

    private fun safeDebugPayload(text: String, maxChars: Int = MAX_DEBUG_PAYLOAD_CHARS): String {
        val sanitized = text
            .replace(Regex("Bearer\\s+[^\\s\"']+", RegexOption.IGNORE_CASE), "Bearer <redacted>")
            .replace(Regex("(\"(?:api[_-]?key|authorization|token|secret)\"\\s*:\\s*\")([^\"]+)(\")", RegexOption.IGNORE_CASE), "$1<redacted>$3")
            .replace(Regex("data:image/[^\\s\"')]+"), "data:image/<redacted>")
        return if (sanitized.length <= maxChars) {
            sanitized
        } else {
            sanitized.take(maxChars) + "\n...<truncated ${sanitized.length - maxChars} chars>"
        }
    }

    private fun requiresBookshelfTool(messages: List<AiChatMessage>): Boolean {
        val content = messages.lastOrNull { it.role == AiChatMessage.Role.USER }
            ?.content
            ?.lowercase()
            .orEmpty()
        if (content.isBlank()) return false
        return listOf(
            "书架",
            "书籍",
            "书名",
            "作者",
            "阅读记录",
            "最近读",
            "在读",
            "简介",
            "书源",
            "分组",
            "标签",
            "分类",
            "整理",
            "批量"
        ).any { content.contains(it) }
    }

    private fun parseAssistantTurn(response: JSONObject): AssistantTurn {
        val message = response.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?: JSONObject()
        val content = extractContentText(message.opt("content"))
        val reasoningContent = extractContentText(message.opt("reasoning_content"))
            .ifBlank { extractContentText(message.opt("reasoning")) }
            .ifBlank { extractContentText(message.opt("thinking")) }
        val toolCalls = buildList {
            val array = message.optJSONArray("tool_calls") ?: JSONArray()
            for (index in 0 until array.length()) {
                val toolCall = array.optJSONObject(index) ?: continue
                val function = toolCall.optJSONObject("function") ?: continue
                add(
                    ToolCall(
                        id = toolCall.optString("id").ifBlank { "call_$index" },
                        name = function.optString("name"),
                        arguments = extractToolArguments(function.opt("arguments"))
                    )
                )
            }
        }
        return AssistantTurn(
            content = content,
            toolCalls = toolCalls,
            rawMessage = JSONObject().apply {
                put("role", "assistant")
                put("content", if (content.isBlank()) JSONObject.NULL else content)
                if (reasoningContent.isNotBlank()) {
                    put("reasoning_content", reasoningContent)
                }
                if (toolCalls.isNotEmpty()) {
                    put(
                        "tool_calls",
                        JSONArray().apply {
                            toolCalls.forEach { toolCall ->
                                put(
                                    JSONObject().apply {
                                        put("id", toolCall.id)
                                        put("type", "function")
                                        put(
                                            "function",
                                            JSONObject().apply {
                                                put("name", toolCall.name)
                                                put("arguments", toolCall.arguments)
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    )
                }
            },
            reasoningContent = reasoningContent
        )
    }

    fun parseCustomHeaders(rawHeaders: String): Map<String, String> {
        val text = rawHeaders.trim()
        if (text.isBlank()) return emptyMap()
        runCatching {
            val json = JSONObject(text)
            return buildMap {
                json.keys().forEach { key ->
                    val value = json.optString(key)
                    if (key.isNotBlank() && value.isNotBlank()) put(key, value)
                }
            }
        }
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { line ->
                val separator = line.indexOf(':').takeIf { it > 0 } ?: line.indexOf('=').takeIf { it > 0 }
                separator?.let {
                    line.substring(0, it).trim() to line.substring(it + 1).trim()
                }
            }
            .filter { it.first.isNotBlank() && it.second.isNotBlank() }
            .toMap()
    }

    private fun normalizeApiMode(apiMode: String?): String {
        return if (apiMode == AI_API_MODE_RESPONSES) {
            AI_API_MODE_RESPONSES
        } else {
            AI_API_MODE_CHAT_COMPLETIONS
        }
    }

    private fun buildPromptCacheKey(provider: AiProviderConfig, model: String): String {
        val raw = "${provider.id}:${model}".lowercase()
        return raw.replace(Regex("[^a-z0-9._:-]"), "_")
            .take(128)
            .ifBlank { provider.id.take(64) }
    }

    private fun resolveChatUrl(baseUrl: String, apiMode: String): String {
        val normalized = baseUrl.trim().trimEnd('/')
        if (apiMode == AI_API_MODE_RESPONSES) {
            return when {
                normalized.endsWith("/responses") -> normalized
                normalized.endsWith("/chat/completions") -> normalized.removeSuffix("/chat/completions") + "/responses"
                normalized.endsWith("/v1") -> "$normalized/responses"
                else -> "$normalized/v1/responses"
            }
        }
        return when {
            normalized.endsWith("/chat/completions") -> normalized
            normalized.endsWith("/responses") -> normalized.removeSuffix("/responses") + "/chat/completions"
            normalized.endsWith("/v1") -> "$normalized/chat/completions"
            else -> "$normalized/v1/chat/completions"
        }
    }

    private fun resolveModelsUrl(baseUrl: String): String {
        val normalized = baseUrl.trim().trimEnd('/')
        return when {
            normalized.endsWith("/models") -> normalized
            normalized.endsWith("/chat/completions") -> normalized.removeSuffix("/chat/completions") + "/models"
            normalized.endsWith("/responses") -> normalized.removeSuffix("/responses") + "/models"
            normalized.endsWith("/v1") -> "$normalized/models"
            else -> "$normalized/v1/models"
        }
    }

    private fun extractError(body: String): String {
        if (body.isBlank()) return ""
        return runCatching {
            val root = JSONObject(body)
            root.optJSONObject("error")?.optString("message")
                ?: root.optString("message")
        }.getOrNull().orEmpty()
    }

    private fun extractContent(body: String): String {
        val root = JSONObject(body)
        root.optJSONArray("output")?.let { output ->
            return buildString {
                for (index in 0 until output.length()) {
                    val item = output.optJSONObject(index) ?: continue
                    append(extractResponsesText(item))
                }
            }
        }
        val choices = root.optJSONArray("choices") ?: return root.optString("response")
        val first = choices.optJSONObject(0) ?: return ""
        val message = first.optJSONObject("message")
        return extractContentText(message?.opt("content"))
            .ifBlank { first.optString("text") }
    }

    private fun extractResponsesText(item: JSONObject): String {
        item.optString("output_text").takeIf { it.isNotBlank() }?.let { return it }
        item.optString("text").takeIf { it.isNotBlank() }?.let { return it }
        val content = item.optJSONArray("content") ?: return ""
        return buildString {
            for (index in 0 until content.length()) {
                val part = content.optJSONObject(index) ?: continue
                if (part.optString("type") == "output_text" || part.has("text")) {
                    append(part.optString("text"))
                }
            }
        }
    }

    private fun extractResponsesReasoning(item: JSONObject): String {
        item.optString("summary_text").takeIf { it.isNotBlank() }?.let { return it }
        val summary = item.optJSONArray("summary") ?: return ""
        return buildString {
            for (index in 0 until summary.length()) {
                val part = summary.optJSONObject(index) ?: continue
                append(part.optString("text"))
            }
        }
    }

    private fun extractContentText(content: Any?): String {
        return when (content) {
            is String -> content
            is JSONArray -> contentArrayToText(content)
            is JSONObject -> content.optString("text")
            else -> ""
        }
    }

    private fun stripInlineThinking(
        text: String,
        onThinking: (String) -> Unit
    ): String {
        val (visible, reasoning) = splitInlineThinking(text)
        reasoning.takeIf { it.isNotBlank() }?.let(onThinking)
        return visible.trimStart()
    }

    private fun splitInlineThinking(text: String): Pair<String, String> {
        var visible = text
        val reasoningParts = mutableListOf<String>()
        val closedThinkRegex = Regex("<think>([\\s\\S]*?)</think>", RegexOption.IGNORE_CASE)
        closedThinkRegex.findAll(text).forEach { match ->
            match.groups[1]?.value
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let(reasoningParts::add)
        }
        visible = closedThinkRegex.replace(visible, "")
        val openMatch = Regex("<think>", RegexOption.IGNORE_CASE).find(visible)
        if (openMatch != null) {
            val thinking = visible.substring(openMatch.range.last + 1)
                .replace(Regex("</think>", RegexOption.IGNORE_CASE), "")
                .trim()
            if (thinking.isNotBlank()) {
                reasoningParts += thinking
            }
            visible = visible.substring(0, openMatch.range.first)
        }
        return visible.trimStart() to reasoningParts.joinToString("\n\n")
    }

    private fun extractToolArguments(arguments: Any?): String {
        return when (arguments) {
            is String -> arguments.ifBlank { "{}" }
            is JSONObject -> arguments.toString()
            is JSONArray -> arguments.toString()
            else -> "{}"
        }
    }

    private fun contentArrayToText(content: JSONArray): String {
        return buildString {
            for (index in 0 until content.length()) {
                val part = content.opt(index)
                if (part is JSONObject) {
                    append(part.optString("text"))
                } else if (part is String) {
                    append(part)
                }
            }
        }
    }

    private val searchResultBlockRegex = Regex(
        "```legado-search-results\\s*\\n([\\s\\S]*?)\\n```",
        setOf(RegexOption.MULTILINE)
    )
}
