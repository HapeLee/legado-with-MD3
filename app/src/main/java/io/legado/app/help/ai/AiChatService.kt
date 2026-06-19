package io.legado.app.help.ai

import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.postJson
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import splitties.init.appCtx
import java.io.IOException
import java.net.SocketException
import java.util.concurrent.TimeUnit
import kotlin.math.max

object AiChatService {

    private const val MAX_TOOL_ROUNDS = 12
    private const val MAX_DEBUG_LOG_CHARS = 16_000
    private const val MAX_DEBUG_PAYLOAD_CHARS = 8_000
    private const val DEFAULT_TOOL_TIMEOUT_MILLIS = 120_000L
    private const val IMAGE_TOOL_TIMEOUT_MILLIS = 300_000L
    private const val MAX_CONTEXT_TOKENS = 32_000

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

    private data class AssistantTurn(
        val content: String,
        val toolCalls: List<ToolCall>,
        val rawMessage: JSONObject,
        val reasoningContent: String = ""
    )

    private var currentProvider: AiProviderConfig? = null
    private var currentModel: AiModelConfig? = null

    fun setProvider(provider: AiProviderConfig, model: AiModelConfig) {
        currentProvider = provider
        currentModel = model
    }

    fun getCurrentProvider(): AiProviderConfig? = currentProvider
    fun getCurrentModel(): AiModelConfig? = currentModel

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
        }
        response.use { rawResponse ->
            val payload = rawResponse.body?.string().orEmpty()
            if (!rawResponse.isSuccessful) {
                throw AiChatException(
                    message = extractError(payload).ifBlank {
                        "${rawResponse.code} ${rawResponse.message}"
                    }
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
        useAllTools: Boolean = false
    ): String {
        val provider = currentProvider ?: AiDefaultConfig.DEFAULT_PROVIDER
        val model = currentModel ?: AiDefaultConfig.DEFAULT_MODEL
        val baseUrl = provider.baseUrl.trim()
        val modelId = model.modelId.trim()
        val apiMode = normalizeApiMode(provider.apiMode)
        val chatUrl = resolveChatUrl(baseUrl, apiMode)

        require(baseUrl.isNotBlank()) { "Base URL is empty" }
        require(modelId.isNotBlank()) { "Model is empty" }

        val tools = runCatching {
            if (useAllTools) AiToolRegistry.resolveAllTools() else AiToolRegistry.resolveAvailableTools()
        }.getOrDefault(emptyList())

        val reserveTokens = estimateStaticRequestTokens(messages, tools)
        val preparedContext = prepareContext(messages, contextSummary, reserveTokens)
        if (preparedContext.compressed && preparedContext.summary != null) {
            onContextSummary(preparedContext.summary)
        }

        val conversation = buildConversation(preparedContext.messages, preparedContext.summary)

        val totalEstimate = reserveTokens + preparedContext.inputTokens
        if (totalEstimate > preparedContext.limitTokens) {
            throw AiChatException(
                message = "上下文超出限制，请减少消息或打开上下文压缩。"
            )
        }

        return executeToolLoop(
            chatUrl = chatUrl,
            apiMode = apiMode,
            model = modelId,
            providerApiKey = provider.apiKey,
            providerHeaders = provider.headers.orEmpty(),
            conversation = conversation,
            tools = tools,
            onPartial = onPartial,
            onThinking = onThinking,
            onStatus = onStatus,
            includeStructuredBlocks = includeStructuredBlocks,
            useAllTools = useAllTools
        )
    }

    private suspend fun executeToolLoop(
        chatUrl: String,
        apiMode: String,
        model: String,
        providerApiKey: String,
        providerHeaders: String,
        conversation: MutableList<JSONObject>,
        tools: List<AiResolvedTool>,
        onPartial: (String) -> Unit,
        onThinking: (String) -> Unit,
        onStatus: (JSONObject) -> Unit,
        includeStructuredBlocks: Boolean,
        useAllTools: Boolean
    ): String {
        val toolMap = tools.associateBy { it.name }
        repeat(MAX_TOOL_ROUNDS) { round ->
            val roundNo = round + 1
            val thinkingKey = "thinking_$roundNo"
            onStatus(
                JSONObject().apply {
                    put("key", thinkingKey)
                    put("kind", "thinking")
                    put("stage", "start")
                    put("round", roundNo)
                    put("label", "思考中")
                    put("success", true)
                }
            )

            val assistantTurn = requestCompletionStream(
                chatUrl = chatUrl,
                apiMode = apiMode,
                model = model,
                providerApiKey = providerApiKey,
                providerHeaders = providerHeaders,
                messages = conversation,
                tools = tools,
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
                        put("label", "思考完成")
                        put("content", assistantTurn.reasoningContent)
                        put("removeIfBlank", assistantTurn.reasoningContent.isBlank())
                        put("success", true)
                    }
                )
                val content = assistantTurn.content
                if (content.isBlank()) {
                    throw AiChatException(message = "Empty response")
                }
                return if (includeStructuredBlocks) {
                    appendStructuredBlocks(content, JSONArray())
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
                    put("label", "思考完成")
                    put("content", assistantTurn.reasoningContent)
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
                        put("label", "调用工具: ${toolCall.name}")
                        put("content", toolCall.arguments)
                        put("success", true)
                    }
                )

                val result = executeToolCall(toolCall, toolMap, useAllTools)
                val resultSuccess = parseToolResultSuccess(result)

                onStatus(
                    JSONObject().apply {
                        put("key", toolCall.id.ifBlank { toolCall.name })
                        put("kind", "tool")
                        put("name", toolCall.name)
                        put("stage", "result")
                        put(
                            "label",
                            if (resultSuccess) "工具完成" else "工具失败"
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
            put("content", "工具调用轮次已达上限，请直接基于现有信息总结回复。")
        }

        val finalTurn = requestCompletionStream(
            chatUrl = chatUrl,
            apiMode = apiMode,
            model = model,
            providerApiKey = providerApiKey,
            providerHeaders = providerHeaders,
            messages = conversation,
            tools = emptyList(),
            round = MAX_TOOL_ROUNDS + 1,
            onPartial = onPartial,
            onThinking = onThinking
        )

        if (finalTurn.content.isBlank()) {
            throw AiChatException(message = "Empty response")
        }
        return if (includeStructuredBlocks) {
            appendStructuredBlocks(finalTurn.content, JSONArray())
        } else {
            finalTurn.content
        }
    }

    private fun parseToolResultSuccess(result: String): Boolean {
        return runCatching {
            JSONObject(result).optBoolean("ok", true)
        }.getOrDefault(true)
    }

    private suspend fun executeToolCall(
        toolCall: ToolCall,
        toolMap: Map<String, AiResolvedTool>,
        @Suppress("UNUSED_PARAMETER") useAllTools: Boolean
    ): String {
        val enabled = AiToolRegistry.defaultEnabledTools
        if (toolCall.name !in enabled) {
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
            withTimeout(toolTimeoutMillis(toolCall.name)) {
                resolvedTool.execute(arguments)
            }
        }.getOrElse { throwable ->
            JSONObject().apply {
                put("ok", false)
                put(
                    "error",
                    if (throwable is TimeoutCancellationException) {
                        "Tool timed out"
                    } else {
                        throwable.message ?: throwable.javaClass.simpleName
                    }
                )
            }.toString()
        }
    }

    private fun toolTimeoutMillis(name: String): Long {
        return if (name in setOf("generate_image", "generate_book_character_avatar")) {
            IMAGE_TOOL_TIMEOUT_MILLIS
        } else {
            DEFAULT_TOOL_TIMEOUT_MILLIS
        }
    }

    private fun appendStructuredBlocks(content: String, cards: JSONArray): String {
        if (cards.length() == 0) return content
        return buildString {
            append(content.trimEnd())
            append("\n\n")
            for (i in 0 until cards.length()) {
                append(cards.optString(i)).append("\n")
            }
        }
    }

    private fun aiChatHttpClient() = okHttpClient.newBuilder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .callTimeout(300, TimeUnit.SECONDS)
        .build()

    private suspend fun requestCompletionStream(
        chatUrl: String,
        apiMode: String,
        model: String,
        providerApiKey: String,
        providerHeaders: String,
        messages: List<JSONObject>,
        tools: List<AiResolvedTool>,
        round: Int,
        onPartial: (String) -> Unit,
        onThinking: (String) -> Unit
    ): AssistantTurn {
        val requestBody = buildRequestBody(messages, model, tools, true, apiMode)

        val response = aiChatHttpClient().newCallResponse {
            url(chatUrl)
            addHeader("Accept", "text/event-stream, application/json")
            addHeader("Content-Type", "application/json")
            providerApiKey.trim().takeIf { it.isNotBlank() }?.let {
                addHeader("Authorization", "Bearer $it")
            }
            addCustomHeaders(providerHeaders)
            postJson(requestBody)
        }

        response.use { rawResponse ->
            val body = rawResponse.body ?: throw AiChatException(
                message = "Empty response body",
                debugLog = "round=$round\nbody=empty"
            )
            if (!rawResponse.isSuccessful) {
                val payload = body.string()
                throw AiChatException(
                    message = extractError(payload).ifBlank {
                        "${rawResponse.code} ${rawResponse.message}"
                    },
                    debugLog = "status=${rawResponse.code}\nresponse=${safeDebugPayload(payload)}"
                )
            }

            val rendered = StringBuilder()
            val reasoningRendered = StringBuilder()
            val toolCallBuilders = linkedMapOf<Int, ToolCallBuilder>()

            body.byteStream().bufferedReader().use { reader ->
                while (true) {
                    val rawLine = reader.readLine()?.trim() ?: break
                    if (rawLine.isEmpty()) continue
                    when {
                        rawLine.startsWith("data:") -> {
                            val payload = rawLine.removePrefix("data:").trim()
                            if (payload == "[DONE]") break
                            if (apiMode == AI_API_MODE_RESPONSES) {
                                consumeResponsesStreamPayload(payload, rendered, reasoningRendered, toolCallBuilders, onPartial, onThinking)
                            } else {
                                consumeStreamPayload(payload, rendered, reasoningRendered, toolCallBuilders, onPartial, onThinking)
                            }
                        }
                        rawLine.startsWith("{") -> {
                            if (apiMode == AI_API_MODE_RESPONSES) {
                                consumeResponsesStreamPayload(rawLine, rendered, reasoningRendered, toolCallBuilders, onPartial, onThinking)
                            } else {
                                consumeStreamPayload(rawLine, rendered, reasoningRendered, toolCallBuilders, onPartial, onThinking)
                            }
                        }
                    }
                }
            }

            val toolCalls = toolCallBuilders.map { (_, builder) ->
                ToolCall(
                    id = builder.id.ifBlank { "call_${System.currentTimeMillis()}" },
                    name = builder.name,
                    arguments = builder.arguments.toString().ifBlank { "{}" }
                )
            }.filter { it.name.isNotBlank() }

            if (rendered.isBlank() && toolCalls.isEmpty()) {
                throw AiChatException(message = "Empty response", debugLog = "round=$round")
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
        apiMode: String
    ): String {
        if (apiMode == AI_API_MODE_RESPONSES) {
            return buildResponsesRequestBody(messages, model, tools, stream)
        }
        return JSONObject().apply {
            put("model", model)
            put("stream", stream)
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
        stream: Boolean
    ): String {
        return JSONObject().apply {
            put("model", model)
            put("stream", stream)
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

    private fun extractToolArguments(args: Any?): String {
        return when (args) {
            is JSONObject -> args.toString()
            is String -> args
            null -> "{}"
            else -> args.toString()
        }
    }

    private fun consumeResponsesStreamPayload(
        payload: String,
        rendered: StringBuilder,
        reasoningRendered: StringBuilder,
        toolCallBuilders: MutableMap<Int, ToolCallBuilder>,
        onPartial: (String) -> Unit,
        onThinking: (String) -> Unit
    ) {
        extractError(payload).takeIf { it.isNotBlank() }?.let {
            throw IllegalStateException(it)
        }
        val root = runCatching { JSONObject(payload) }.getOrNull() ?: return
        val type = root.optString("type")
        when {
            type.contains("reasoning", ignoreCase = true) && type.endsWith(".delta") -> {
                appendReasoningDelta(extractContentText(root.opt("delta")), reasoningRendered, onThinking)
            }
            type == "response.output_text.delta" || type.endsWith(".output_text.delta") -> {
                appendVisibleDelta(extractContentText(root.opt("delta")), rendered, onPartial)
            }
            type == "response.function_call_arguments.delta" || type.endsWith(".function_call_arguments.delta") -> {
                appendResponsesToolDelta(root, toolCallBuilders)
            }
            type == "response.function_call_arguments.done" || type.endsWith(".function_call_arguments.done") -> {
                applyResponsesToolItem(root, toolCallBuilders)
            }
            type == "response.output_item.added" || type == "response.output_item.done" -> {
                root.optJSONObject("item")?.let { item ->
                    val innerText = extractResponsesText(item)
                    if (innerText.isNotBlank()) {
                        appendVisibleDelta(innerText, rendered, onPartial)
                    }
                }
            }
            type == "response.completed" -> {
                // done
            }
            type.isBlank() -> {
                val text = extractResponsesText(root)
                if (text.isNotBlank()) {
                    appendVisibleDelta(text, rendered, onPartial)
                }
            }
        }
    }

    private fun appendVisibleDelta(
        delta: String,
        rendered: StringBuilder,
        onPartial: (String) -> Unit
    ) {
        if (delta.isEmpty()) return
        rendered.append(delta)
        onPartial(rendered.toString())
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
        rendered: StringBuilder,
        reasoningRendered: StringBuilder,
        toolCallBuilders: MutableMap<Int, ToolCallBuilder>,
        onPartial: (String) -> Unit,
        onThinking: (String) -> Unit
    ) {
        extractError(payload).takeIf { it.isNotBlank() }?.let {
            throw IllegalStateException(it)
        }
        val root = runCatching { JSONObject(payload) }.getOrNull() ?: return
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
            rendered.append(deltaText)
            onPartial(rendered.toString())
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
            put("content", AiDefaultConfig.DEFAULT_AI_SYSTEM_PROMPT)
        }
        contextSummary?.summary?.takeIf { it.isNotBlank() }?.let { summary ->
            conversation += JSONObject().apply {
                put("role", "system")
                put("content", "Conversation summary from earlier context:\n$summary")
            }
        }
        if (requiresBookshelfTool(messages)) {
            conversation += JSONObject().apply {
                put("role", "system")
                put(
                    "content",
                    "本轮用户请求涉及本地书架或书籍。回复正文前请先调用合适的本地工具；不要只说明将要查询。"
                )
            }
        }
        val textMessages = messages.filter { (it.kind ?: AiChatMessage.Kind.TEXT) == AiChatMessage.Kind.TEXT }
        val requestMessages = textMessages.takeLast(8)
        requestMessages.forEach { message ->
            conversation += JSONObject().apply {
                put(
                    "role",
                    if (message.role == AiChatMessage.Role.USER) "user" else "assistant"
                )
                put("content", message.content)
            }
        }
        return conversation
    }

    private data class PreparedContext(
        val messages: List<AiChatMessage>,
        val summary: AiContextSummary?,
        val compressed: Boolean,
        val inputTokens: Int,
        val limitTokens: Int
    )

    private fun prepareContext(
        messages: List<AiChatMessage>,
        contextSummary: AiContextSummary?,
        reserveTokens: Int
    ): PreparedContext {
        val inputTokens = messages.sumOf { estimateTokens(it.content) }
        val limitTokens = MAX_CONTEXT_TOKENS
        val total = inputTokens + reserveTokens
        val shouldCompress = total > limitTokens
        return PreparedContext(
            messages = if (shouldCompress) messages.takeLast(4) else messages,
            summary = contextSummary,
            compressed = shouldCompress,
            inputTokens = if (shouldCompress) messages.takeLast(4).sumOf { estimateTokens(it.content) } else inputTokens,
            limitTokens = limitTokens
        )
    }

    fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0
        val chinese = text.count { it.code in 0x4E00..0x9FFF }
        val ascii = text.count { it.code < 128 && it.isLetterOrDigit() }
        val other = text.length - chinese - ascii
        return max(1, (chinese * 2 + ascii + other) / 4)
    }

    private fun estimateStaticRequestTokens(messages: List<AiChatMessage>, tools: List<AiResolvedTool>): Int {
        val systemTokens = estimateTokens(AiDefaultConfig.DEFAULT_AI_SYSTEM_PROMPT)
        val toolTokens = tools.sumOf { estimateTokens(it.definition.toString()) + 16 }
        val bookshelfHintTokens = if (requiresBookshelfTool(messages)) 180 else 0
        return systemTokens + toolTokens + bookshelfHintTokens + 256
    }

    private fun requiresBookshelfTool(messages: List<AiChatMessage>): Boolean {
        val keywords = listOf(
            "书架", "我最近在读", "在读", "阅读记录", "书源", "章节",
            "书库", "图书馆", "这本书", "我的书", "本地书", "分组", "标签"
        )
        return messages.any { msg ->
            keywords.any { kw -> kw in msg.content }
        }
    }

    private fun extractContentText(obj: Any?): String {
        return when (obj) {
            null -> ""
            is String -> obj
            is JSONObject -> obj.optString("text")
                .ifBlank { obj.optString("content") }
                .ifBlank { obj.optString("delta") }
            else -> obj.toString()
        }
    }

    private fun extractResponsesText(root: JSONObject): String {
        val content = root.opt("content")
        return when (content) {
            is String -> content
            is JSONArray -> {
                buildString {
                    for (i in 0 until content.length()) {
                        val item = content.optJSONObject(i) ?: continue
                        val text = item.optString("text")
                        if (text.isNotBlank()) append(text)
                    }
                }
            }
            else -> root.optString("text")
        }
    }

    private fun extractError(payload: String): String {
        return runCatching {
            val root = JSONObject(payload)
            val error = root.optJSONObject("error") ?: return@runCatching ""
            error.optString("message").ifBlank {
                error.optString("msg")
            }
        }.getOrDefault("")
    }

    private fun resolveModelsUrl(baseUrl: String): String {
        val trimmed = baseUrl.trimEnd('/')
        return "$trimmed/models"
    }

    private fun resolveChatUrl(baseUrl: String, apiMode: String): String {
        val trimmed = baseUrl.trimEnd('/')
        return if (apiMode == AI_API_MODE_RESPONSES) {
            "$trimmed/responses"
        } else {
            "$trimmed/chat/completions"
        }
    }

    private fun normalizeApiMode(mode: String?): String {
        return when {
            mode.isNullOrBlank() -> AI_API_MODE_CHAT_COMPLETIONS
            "response" in mode.lowercase() -> AI_API_MODE_RESPONSES
            else -> AI_API_MODE_CHAT_COMPLETIONS
        }
    }

    private fun okhttp3.Request.Builder.addCustomHeaders(headers: String) {
        if (headers.isBlank()) return
        runCatching {
            val json = JSONObject(headers)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                addHeader(key, json.optString(key))
            }
        }
    }

    private fun safeDebugPayload(text: String, maxChars: Int = MAX_DEBUG_PAYLOAD_CHARS): String {
        val sanitized = text
            .replace(Regex("Bearer\\s+[^\\s\"']+", RegexOption.IGNORE_CASE), "Bearer <redacted>")
            .replace(Regex("(\"(?:api[_-]?key|authorization|token|secret)\"\\s*:\\s*\")([^\"]+)(\")", RegexOption.IGNORE_CASE), "$1<redacted>$3")
        return if (sanitized.length <= maxChars) {
            sanitized
        } else {
            sanitized.take(maxChars / 2) + "\n...[truncated]...\n" + sanitized.takeLast(maxChars / 2)
        }
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
}
