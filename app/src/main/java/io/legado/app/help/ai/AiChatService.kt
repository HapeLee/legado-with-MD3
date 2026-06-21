package io.legado.app.help.ai

import io.legado.app.data.entities.AiAgentTrace
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookCharacter
import io.legado.app.help.book.characterBookKey
import io.legado.app.help.character.BookCharacterProfileMeta
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
import io.legado.app.ui.main.ai.AiChatCompanionConfig
import io.legado.app.ui.main.ai.AiModelConfig
import io.legado.app.ui.main.ai.AiProviderConfig
import io.legado.app.ui.main.ai.AiSkillConfig
import io.legado.app.ui.main.ai.AiWorldBookEntry
import org.json.JSONArray
import org.json.JSONObject
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit

data class AiUsageStats(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val totalTokens: Int = 0,
    val cachedInputTokens: Int = 0
) {
    operator fun plus(other: AiUsageStats): AiUsageStats {
        return AiUsageStats(
            inputTokens = inputTokens + other.inputTokens,
            outputTokens = outputTokens + other.outputTokens,
            totalTokens = totalTokens + other.totalTokens,
            cachedInputTokens = cachedInputTokens + other.cachedInputTokens
        )
    }
}

data class AiSingleToolCallResult(
    val toolName: String = "",
    val arguments: String = "",
    val content: String = "",
    val modelConfig: AiModelConfig? = null
) {
    val hasToolCall: Boolean
        get() = toolName.isNotBlank() && arguments.isNotBlank()
}

object AiChatService {

    private const val NETWORK_ABORT_RETRY_COUNT = 1
    private const val MAX_DEBUG_LOG_CHARS = 16_000
    private const val MAX_DEBUG_PAYLOAD_CHARS = 8_000
    private const val TOOL_ONLY_SYSTEM_PROMPT =
        "You are a deterministic extraction worker. Read the user payload and call the provided tool with complete, valid JSON. Do not answer with prose unless the tool is impossible."

    private data class ToolCallBuilder(
        var id: String = "",
        var name: String = "",
        val arguments: StringBuilder = StringBuilder()
    )

    private data class CompletionEndpoint(
        val modelConfig: AiModelConfig?,
        val provider: AiProviderConfig?,
        val baseUrl: String,
        val model: String,
        val apiMode: String,
        val chatUrl: String,
        val promptCacheKey: String?
    )

    suspend fun chat(messages: List<AiChatMessage>): String {
        return chatStream(messages, onPartial = {})
    }

    suspend fun requestSingleToolCall(
        messages: List<AiChatMessage>,
        tool: AiResolvedTool,
        modelConfigOverride: AiModelConfig? = null,
        fallbackModelConfig: AiModelConfig? = null,
        promptCacheKeyOverride: String? = null,
        firstResponseTimeoutMillis: Long = 0L,
        activeSkills: List<AiSkillConfig> = emptyList(),
        includeChatContext: Boolean = true,
        onUsage: (AiUsageStats) -> Unit = {}
    ): AiSingleToolCallResult {
        val systemPrompt = if (includeChatContext) {
            AppConfig.aiSystemPrompt.ifBlank { AppConfig.DEFAULT_AI_SYSTEM_PROMPT }
        } else {
            TOOL_ONLY_SYSTEM_PROMPT
        }
        val personaPrompt = if (includeChatContext) AppConfig.aiCurrentPersona?.prompt.orEmpty() else ""
        val reserveTokens = estimateStaticRequestTokens(
            messages = messages,
            tools = listOf(tool),
            activeSkills = if (includeChatContext) activeSkills else emptyList(),
            systemPrompt = systemPrompt,
            personaPrompt = personaPrompt
        )
        val preparedContext = if (includeChatContext) {
            AiContextManager.prepare(messages, null, reserveTokens)
        } else {
            val clean = messages.filterNot { it.pending }.filter { it.content.isNotBlank() }
            AiContextManager.PreparedContext(
                messages = clean,
                summary = null,
                compressed = false,
                inputTokens = AiContextManager.estimateMessagesTokens(clean),
                limitTokens = AppConfig.aiContextWindowTokens
            )
        }
        val conversation = if (includeChatContext) {
            buildConversation(
                messages = preparedContext.messages,
                contextSummary = preparedContext.summary,
                activeSkills = activeSkills,
                systemPrompt = systemPrompt,
                personaPrompt = personaPrompt
            )
        } else {
            buildToolOnlyConversation(
                messages = preparedContext.messages,
                systemPrompt = systemPrompt
            )
        }
        val endpoints = buildList {
            add(resolveCompletionEndpoint(modelConfigOverride, promptCacheKeyOverride))
            val primaryId = first().modelConfig?.id.orEmpty()
            fallbackModelConfig
                ?.takeIf { it.id != primaryId }
                ?.let { add(resolveCompletionEndpoint(it, promptCacheKeyOverride)) }
        }
        var lastThrowable: Throwable? = null
        endpoints.forEachIndexed { endpointIndex, endpoint ->
            val requestLog = StringBuilder().apply {
                append("url=${endpoint.chatUrl}").append('\n')
                append("model=${endpoint.model}").append('\n')
                append("apiMode=${endpoint.apiMode}").append('\n')
                append("provider=${endpoint.provider?.name.orEmpty()}").append('\n')
                append("singleTool=${tool.name}").append('\n')
                if (endpointIndex > 0) append("fallbackAttempt=$endpointIndex").append('\n')
            }
            try {
                val assistantTurn = requestCompletionStreamWithRetry(
                    chatUrl = endpoint.chatUrl,
                    apiMode = endpoint.apiMode,
                    model = endpoint.model,
                    providerApiKey = endpoint.provider?.apiKey.orEmpty(),
                    providerHeaders = endpoint.provider?.headers.orEmpty(),
                    messages = conversation,
                    tools = listOf(tool),
                    promptCacheKey = endpoint.promptCacheKey,
                    requestLog = requestLog,
                    round = 1,
                    firstResponseTimeoutMillis = firstResponseTimeoutMillis,
                    onPartial = {},
                    onThinking = {},
                    onUsage = onUsage
                )
                val toolCall = assistantTurn.toolCalls.firstOrNull { it.name == tool.name }
                return AiSingleToolCallResult(
                    toolName = toolCall?.name.orEmpty(),
                    arguments = toolCall?.arguments.orEmpty(),
                    content = assistantTurn.content,
                    modelConfig = endpoint.modelConfig
                )
            } catch (throwable: Throwable) {
                lastThrowable = throwable
                val canTryFallback = endpointIndex < endpoints.lastIndex && throwable.isAiFastFallbackCandidate()
                if (!canTryFallback) {
                    if (throwable is AiChatException) throw throwable
                    throw AiChatException(
                        message = throwable.message ?: throwable.javaClass.simpleName,
                        debugLog = requestLog.toSafeDebugLog(),
                        cause = throwable
                    )
                }
            }
        }
        val throwable = lastThrowable ?: IllegalStateException("AI request failed")
        if (throwable is AiChatException) throw throwable
        throw AiChatException(
            message = throwable.message ?: throwable.javaClass.simpleName,
            debugLog = "",
            cause = throwable
        )
    }

    private fun resolveCompletionEndpoint(
        modelConfigOverride: AiModelConfig?,
        promptCacheKeyOverride: String?
    ): CompletionEndpoint {
        val modelConfig = modelConfigOverride ?: AppConfig.aiCurrentModelConfig
        val provider = modelConfigOverride?.let { AppConfig.aiProviderForModel(it) }
            ?: AppConfig.aiCurrentProvider
        val baseUrl = provider?.baseUrl?.trim().orEmpty()
        val model = modelConfig?.modelId?.trim().orEmpty()
        val apiMode = normalizeApiMode(provider?.apiMode)
        val chatUrl = resolveChatUrl(baseUrl, apiMode)
        val promptCacheKey = promptCacheKeyOverride
            ?.takeIf { provider?.promptCache == true }
            ?.let(::normalizePromptCacheKey)
            ?: provider
                ?.takeIf { it.promptCache }
                ?.let { buildPromptCacheKey(it, model) }
        require(baseUrl.isNotBlank()) { "Base URL is empty" }
        require(model.isNotBlank()) { "Model is empty" }
        return CompletionEndpoint(
            modelConfig = modelConfig,
            provider = provider,
            baseUrl = baseUrl,
            model = model,
            apiMode = apiMode,
            chatUrl = chatUrl,
            promptCacheKey = promptCacheKey
        )
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
        useAllTools: Boolean = false,
        toolOverride: List<AiResolvedTool>? = null,
        extraTools: List<AiResolvedTool> = emptyList(),
        modelConfigOverride: AiModelConfig? = null,
        fallbackModelConfigOverride: AiModelConfig? = null,
        promptCacheKeyOverride: String? = null,
        firstResponseTimeoutMillis: Long = 0L,
        activeSkills: List<AiSkillConfig> = emptyList(),
        onUsage: (AiUsageStats) -> Unit = {},
        agentRun: AiAgentStateStore.Run? = null,
        memoryContext: AiMemoryContext? = null
    ): String {
        val modelConfig = modelConfigOverride ?: AppConfig.aiCurrentModelConfig
        val provider = modelConfigOverride?.let { AppConfig.aiProviderForModel(it) }
            ?: AppConfig.aiCurrentProvider
        val baseUrl = provider?.baseUrl?.trim().orEmpty()
        val model = modelConfig?.modelId?.trim().orEmpty()
        val apiMode = normalizeApiMode(provider?.apiMode)
        val chatUrl = resolveChatUrl(baseUrl, apiMode)
        val chatCompanion = resolveChatCompanion(memoryContext)
        val systemPrompt = buildCompanionSystemPrompt(chatCompanion)
        val promptCacheKey = promptCacheKeyOverride
            ?.takeIf { provider?.promptCache == true }
            ?.let(::normalizePromptCacheKey)
            ?: provider
            ?.takeIf { it.promptCache }
            ?.let { buildPromptCacheKey(it, model) }
        require(baseUrl.isNotBlank()) { "Base URL is empty" }
        require(model.isNotBlank()) { "Model is empty" }

        val baseTools = toolOverride ?: runCatching {
            if (useAllTools) AiToolRegistry.resolveAllTools() else AiToolRegistry.resolveAvailableTools()
        }.getOrDefault(emptyList())
        val tools = baseTools
            .plus(extraTools)
            .distinctBy { it.name }
        val extraToolNames = extraTools.mapTo(hashSetOf()) { it.name }.apply {
            if (toolOverride != null) {
                addAll(toolOverride.map { it.name })
            }
        }
        val requestLog = StringBuilder().apply {
            append("url=$chatUrl").append('\n')
            append("model=$model").append('\n')
            append("apiMode=$apiMode").append('\n')
            append("provider=${provider?.name.orEmpty()}").append('\n')
            append("companion=${chatCompanion.name}:${chatCompanion.id}").append('\n')
            append("tools=${tools.joinToString { it.name }}").append('\n')
        }
        val reserveTokens = estimateStaticRequestTokens(
            messages = messages,
            tools = tools,
            activeSkills = activeSkills,
            systemPrompt = systemPrompt
        )
        val preparedContext = AiContextManager.prepare(messages, contextSummary, reserveTokens)
        val estimatedTotalTokens = reserveTokens + preparedContext.inputTokens
        preparedContext.summary
            ?.takeIf { preparedContext.compressed && it.isValid }
            ?.let(onContextSummary)
        val retrievedMemory = AiMemoryRetriever.retrieve(memoryContext, preparedContext.messages)
        if (retrievedMemory.isNotEmpty) {
            AiAgentStateStore.trace(
                run = agentRun,
                eventType = AiAgentTrace.EVENT_MEMORY_RETRIEVED,
                payload = JSONObject()
                    .put("items", retrievedMemory.items.size)
                    .put("fragments", retrievedMemory.fragments.size),
                success = true
            )
        }
        val worldBookContext = AiWorldBookManager.retrieve(memoryContext, preparedContext.messages)
        if (worldBookContext.isNotEmpty) {
            AiAgentStateStore.trace(
                run = agentRun,
                eventType = AiAgentTrace.EVENT_WORLD_BOOK_RETRIEVED,
                payload = worldBookContext.toTraceJson(),
                success = true
            )
        }
        val agentPlan = AiAgentPlanner.create(preparedContext.messages, tools)
        if (agentPlan.steps.isNotEmpty()) {
            AiAgentStateStore.trace(
                run = agentRun,
                eventType = AiAgentTrace.EVENT_PLAN_CREATED,
                payload = agentPlan.toTraceJson(),
                success = true
            )
        }
        val dynamicContextTokens = AiContextManager.estimateTokens(worldBookContext.toTokenText()) +
                AiContextManager.estimateTokens(agentPlan.toSystemPrompt())
        val totalTokensWithDynamicContext = estimatedTotalTokens + dynamicContextTokens
        onContextStats(
            JSONObject().apply {
                put("compressed", preparedContext.compressed)
                put("inputTokens", preparedContext.inputTokens)
                put("limitTokens", preparedContext.limitTokens)
                put("reserveTokens", reserveTokens)
                put("dynamicContextTokens", dynamicContextTokens)
                put("totalTokens", totalTokensWithDynamicContext)
            }
        )
        val conversation = buildConversation(
            messages = preparedContext.messages,
            contextSummary = preparedContext.summary,
            retrievedMemory = retrievedMemory,
            worldBookContext = worldBookContext,
            agentPlan = agentPlan,
            activeSkills = activeSkills,
            systemPrompt = systemPrompt
        )
        if (totalTokensWithDynamicContext > preparedContext.limitTokens) {
            throw AiChatException(
                message = "当前 AI 静态配置或本轮输入超过上下文限制，已自动压缩但仍无法放入，请减少系统提示词、Skill、工具或本次输入。",
                debugLog = requestLog.append("estimatedTotalTokens=$totalTokensWithDynamicContext\n")
                    .append("dynamicContextTokens=$dynamicContextTokens\n")
                    .append("limitTokens=${preparedContext.limitTokens}\n")
                    .toSafeDebugLog()
            )
        }

        var totalUsage = AiUsageStats()
        val tracedUsage: (AiUsageStats) -> Unit = { stats ->
            totalUsage += stats
            AiAgentStateStore.trace(
                run = agentRun,
                eventType = AiAgentTrace.EVENT_MODEL_RESPONSE,
                payload = JSONObject().put("stage", "usage"),
                success = true,
                usage = stats
            )
            onUsage(stats)
        }
        return runCatching {
            AiAgentStateStore.trace(
                run = agentRun,
                eventType = AiAgentTrace.EVENT_MODEL_REQUEST,
                payload = JSONObject()
                    .put("estimatedTotalTokens", totalTokensWithDynamicContext)
                    .put("dynamicContextTokens", dynamicContextTokens)
                    .put("tools", tools.map { it.name }.joinToString(","))
            )
            AiAgentRuntime.runToolLoop(
                conversation = conversation,
                tools = tools,
                requestLog = requestLog,
                onStatus = onStatus,
                includeStructuredBlocks = includeStructuredBlocks,
                apiMode = apiMode,
                useAllTools = useAllTools,
                extraToolNames = extraToolNames,
                agentRun = agentRun
            ) { roundNo, roundMessages, roundTools ->
                requestCompletionStreamWithFallback(
                    chatUrl = chatUrl,
                    apiMode = apiMode,
                    model = model,
                    provider = provider,
                    fallbackModelConfig = fallbackModelConfigOverride,
                    promptCacheKeyOverride = promptCacheKeyOverride,
                    messages = roundMessages,
                    tools = roundTools,
                    promptCacheKey = promptCacheKey,
                    requestLog = requestLog,
                    round = roundNo,
                    firstResponseTimeoutMillis = firstResponseTimeoutMillis,
                    onPartial = onPartial,
                    onThinking = onThinking,
                    onUsage = tracedUsage
                )
            }
        }.onSuccess { content ->
            AiMemoryExtractor.recordConversation(memoryContext, messages, content)
            AiAgentStateStore.trace(
                run = agentRun,
                eventType = AiAgentTrace.EVENT_STATUS,
                payload = JSONObject()
                    .put("stage", "chat_success")
                    .put("outputChars", content.length)
                    .put("totalTokens", totalUsage.totalTokens),
                success = true,
                usage = totalUsage
            )
        }.getOrElse { throwable ->
            AiAgentStateStore.trace(
                run = agentRun,
                eventType = AiAgentTrace.EVENT_ERROR,
                payload = JSONObject()
                    .put("message", throwable.localizedMessage ?: throwable.javaClass.simpleName),
                success = false
            )
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

    private fun aiChatHttpClient(firstResponseTimeoutMillis: Long = 0L) = okHttpClient.newBuilder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .readTimeout(
            if (firstResponseTimeoutMillis > 0L) firstResponseTimeoutMillis else 300_000L,
            TimeUnit.MILLISECONDS
        )
        .callTimeout(300, TimeUnit.SECONDS)
        .build()

    private suspend fun requestCompletionStreamWithFallback(
        chatUrl: String,
        apiMode: String,
        model: String,
        provider: AiProviderConfig?,
        fallbackModelConfig: AiModelConfig?,
        promptCacheKeyOverride: String?,
        messages: List<JSONObject>,
        tools: List<AiResolvedTool>,
        promptCacheKey: String?,
        requestLog: StringBuilder,
        round: Int,
        firstResponseTimeoutMillis: Long,
        onPartial: (String) -> Unit,
        onThinking: (String) -> Unit,
        onUsage: (AiUsageStats) -> Unit
    ): AiAgentAssistantTurn {
        try {
            return requestCompletionStreamWithRetry(
                chatUrl = chatUrl,
                apiMode = apiMode,
                model = model,
                providerApiKey = provider?.apiKey.orEmpty(),
                providerHeaders = provider?.headers.orEmpty(),
                messages = messages,
                tools = tools,
                promptCacheKey = promptCacheKey,
                requestLog = requestLog,
                round = round,
                firstResponseTimeoutMillis = firstResponseTimeoutMillis,
                onPartial = onPartial,
                onThinking = onThinking,
                onUsage = onUsage
            )
        } catch (throwable: Throwable) {
            val fallback = fallbackModelConfig
                ?.takeIf { throwable.isAiFastFallbackCandidate() }
                ?.let { resolveCompletionEndpoint(it, promptCacheKeyOverride) }
                ?.takeIf { it.chatUrl != chatUrl || it.model != model }
                ?: throw throwable
            requestLog.append("round=").append(round)
                .append(" fallbackModel=").append(fallback.model)
                .append(" reason=").append(throwable.message ?: throwable.javaClass.simpleName)
                .append('\n')
            onThinking("AI 请求超时，正在切换备用模型")
            return requestCompletionStreamWithRetry(
                chatUrl = fallback.chatUrl,
                apiMode = fallback.apiMode,
                model = fallback.model,
                providerApiKey = fallback.provider?.apiKey.orEmpty(),
                providerHeaders = fallback.provider?.headers.orEmpty(),
                messages = messages,
                tools = tools,
                promptCacheKey = fallback.promptCacheKey,
                requestLog = requestLog,
                round = round,
                firstResponseTimeoutMillis = firstResponseTimeoutMillis,
                onPartial = onPartial,
                onThinking = onThinking,
                onUsage = onUsage
            )
        }
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
        firstResponseTimeoutMillis: Long = 0L,
        onPartial: (String) -> Unit,
        onThinking: (String) -> Unit,
        onUsage: (AiUsageStats) -> Unit
    ): AiAgentAssistantTurn {
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
                    firstResponseTimeoutMillis = firstResponseTimeoutMillis,
                    onPartial = onPartial,
                    onThinking = onThinking,
                    onUsage = onUsage
                )
            } catch (throwable: Throwable) {
                lastError = throwable
                if (attempt >= NETWORK_ABORT_RETRY_COUNT || !throwable.isAiRetryableNetworkAbort()) {
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
        firstResponseTimeoutMillis: Long = 0L,
        onPartial: (String) -> Unit,
        onThinking: (String) -> Unit,
        onUsage: (AiUsageStats) -> Unit
    ): AiAgentAssistantTurn {
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
        val response = aiChatHttpClient(firstResponseTimeoutMillis).newCallResponse {
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
            val source = body.source()
            var firstPayloadReceived = false
            if (firstResponseTimeoutMillis > 0L) {
                source.timeout().timeout(firstResponseTimeoutMillis, TimeUnit.MILLISECONDS)
            }
            source.use {
                while (true) {
                    val rawLine = it.readUtf8Line()?.trim() ?: break
                    if (rawLine.isEmpty()) continue
                    if (!firstPayloadReceived) {
                        firstPayloadReceived = true
                        it.timeout().timeout(300, TimeUnit.SECONDS)
                    }
                    rawPayload.append(rawLine).append('\n')
                    if (rawLine.startsWith("data:")) {
                        val payload = rawLine.removePrefix("data:").trim()
                        if (payload == "[DONE]") break
                        if (apiMode == AI_API_MODE_RESPONSES) {
                            consumeResponsesStreamPayload(payload, rawRendered, rendered, reasoningRendered, toolCallBuilders, onPartial, onThinking, onUsage)
                        } else {
                            consumeStreamPayload(payload, rawRendered, rendered, reasoningRendered, toolCallBuilders, onPartial, onThinking, onUsage)
                        }
                    } else if (rawLine.startsWith("{")) {
                        if (apiMode == AI_API_MODE_RESPONSES) {
                            consumeResponsesStreamPayload(rawLine, rawRendered, rendered, reasoningRendered, toolCallBuilders, onPartial, onThinking, onUsage)
                        } else {
                            consumeStreamPayload(rawLine, rawRendered, rendered, reasoningRendered, toolCallBuilders, onPartial, onThinking, onUsage)
                        }
                    }
                }
            }
            requestLog.append("response=").append(safeDebugPayload(rawPayload.toString())).append('\n')
            val toolCalls = toolCallBuilders.map { (index, builder) ->
                AiAgentToolCall(
                    id = builder.id.ifBlank { "call_$index" },
                    name = builder.name,
                    arguments = builder.arguments.toString().ifBlank { "{}" }
                )
            }.filter { it.name.isValidJsonString() }
            if (rendered.isBlank() && toolCalls.isEmpty()) {
                val fallback = runCatching { extractContent(rawPayload.toString()) }.getOrDefault("")
                if (fallback.isNotBlank()) {
                    val visibleFallback = stripInlineThinking(fallback, onThinking)
                    onPartial(visibleFallback)
                    return AiAgentAssistantTurn(
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
            return AiAgentAssistantTurn(
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
                put("call_id", toolCall.optJsonString("id").ifBlank { "call_$index" })
                put("name", function.optJsonString("name"))
                put("arguments", extractToolArguments(function.opt("arguments")))
            })
        }
    }

    private fun responsesToolDefinition(definition: JSONObject): JSONObject? {
        val function = definition.optJSONObject("function") ?: definition
        val name = function.optJsonString("name").takeIf { it.isNotBlank() } ?: return null
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
        onThinking: (String) -> Unit,
        onUsage: (AiUsageStats) -> Unit
    ) {
        extractError(payload).takeIf { it.isNotBlank() }?.let {
            throw IllegalStateException(it)
        }
        val root = JSONObject(payload)
        extractUsage(root)?.let(onUsage)
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
            callId = root.optJsonString("call_id").ifBlank { root.optJsonString("item_id") },
            outputIndex = root.optInt("output_index", -1)
        )
        root.optJsonString("call_id").takeIf { it.isNotBlank() }?.let { builder.id = it }
        root.optJsonString("name").takeIf { it.isNotBlank() }?.let { builder.name = it }
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
            callId = item.optJsonString("call_id").ifBlank { item.optJsonString("id") },
            outputIndex = item.optInt("output_index", -1)
        )
        item.optJsonString("call_id").ifBlank { item.optJsonString("id") }
            .takeIf { it.isNotBlank() }
            ?.let { builder.id = it }
        item.optJsonString("name").takeIf { it.isNotBlank() }?.let { builder.name = it }
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
        onThinking: (String) -> Unit,
        onUsage: (AiUsageStats) -> Unit
    ) {
        extractError(payload).takeIf { it.isNotBlank() }?.let {
            throw IllegalStateException(it)
        }
        val root = JSONObject(payload)
        extractUsage(root)?.let(onUsage)
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
            toolCall.optJsonString("id").takeIf { it.isNotBlank() }?.let { builder.id = it }
            val function = toolCall.optJSONObject("function") ?: continue
            function.optJsonString("name").takeIf { it.isNotBlank() }?.let { builder.name = it }
            val args = function.opt("arguments")
            when (args) {
                is String -> builder.arguments.append(args)
                is JSONObject, is JSONArray -> builder.arguments.append(args.toString())
            }
        }
    }

    private fun extractUsage(root: JSONObject): AiUsageStats? {
        val usage = root.optJSONObject("usage")
            ?: root.optJSONObject("response")?.optJSONObject("usage")
            ?: return null
        val inputTokens = usage.firstInt("prompt_tokens", "input_tokens")
        val outputTokens = usage.firstInt("completion_tokens", "output_tokens")
        val totalTokens = usage.firstInt("total_tokens")
            .takeIf { it > 0 }
            ?: (inputTokens + outputTokens).takeIf { it > 0 }
            ?: 0
        val cachedInputTokens = usage.firstInt("cached_tokens", "cache_read_input_tokens") +
                usage.firstInt("prompt_tokens_details.cached_tokens", "input_tokens_details.cached_tokens")
        return AiUsageStats(
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            totalTokens = totalTokens,
            cachedInputTokens = cachedInputTokens
        )
    }

    private fun JSONObject.firstInt(vararg keys: String): Int {
        keys.forEach { key ->
            val parts = key.split('.')
            val value = when (parts.size) {
                1 -> optInt(parts[0], -1)
                2 -> optJSONObject(parts[0])?.optInt(parts[1], -1) ?: -1
                else -> -1
            }
            if (value >= 0) return value
        }
        return 0
    }

    private fun buildAssistantRawMessage(
        content: String,
        toolCalls: List<AiAgentToolCall>,
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
        toolCalls: List<AiAgentToolCall>
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
        contextSummary: AiContextSummary? = null,
        retrievedMemory: AiRetrievedMemory = AiRetrievedMemory(),
        worldBookContext: AiWorldBookContext = AiWorldBookContext(),
        agentPlan: AiAgentPlan = AiAgentPlan("", emptyList()),
        activeSkills: List<AiSkillConfig> = emptyList(),
        systemPrompt: String = AppConfig.aiSystemPrompt.ifBlank { AppConfig.DEFAULT_AI_SYSTEM_PROMPT },
        personaPrompt: String = AppConfig.aiCurrentPersona?.prompt.orEmpty()
    ): MutableList<JSONObject> {
        val conversation = mutableListOf<JSONObject>()
        conversation += JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt.ifBlank { AppConfig.DEFAULT_AI_SYSTEM_PROMPT })
        }
        appendWorldBookInjections(
            conversation = conversation,
            worldBookContext = worldBookContext,
            position = AiWorldBookEntry.POSITION_AFTER_SYSTEM_PROMPT
        )
        personaPrompt.takeIf { it.isNotBlank() }?.let { prompt ->
            conversation += JSONObject().apply {
                put("role", "system")
                put("content", prompt)
            }
        }
        contextSummary?.summary?.takeIf { it.isNotBlank() }?.let { summary ->
            conversation += JSONObject().apply {
                put("role", "system")
                put("content", "Conversation summary from earlier context:\n$summary")
            }
        }
        retrievedMemory.toSystemPrompt().takeIf { it.isNotBlank() }?.let { memoryPrompt ->
            conversation += JSONObject().apply {
                put("role", "system")
                put("content", memoryPrompt)
            }
        }
        agentPlan.toSystemPrompt().takeIf { it.isNotBlank() }?.let { planPrompt ->
            conversation += JSONObject().apply {
                put("role", "system")
                put("content", planPrompt)
            }
        }
        activeSkills.forEach { skill ->
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
        appendWorldBookInjections(
            conversation = conversation,
            worldBookContext = worldBookContext,
            position = AiWorldBookEntry.POSITION_BEFORE_PROMPT
        )
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
        insertWorldBookBeforeLastUser(conversation, worldBookContext)
        insertWorldBookByDepth(conversation, worldBookContext)
        return conversation
    }

    private fun buildToolOnlyConversation(
        messages: List<AiChatMessage>,
        systemPrompt: String
    ): MutableList<JSONObject> {
        val conversation = mutableListOf<JSONObject>()
        systemPrompt.takeIf { it.isNotBlank() }?.let { prompt ->
            conversation += JSONObject().apply {
                put("role", "system")
                put("content", prompt)
            }
        }
        messages
            .filter { (it.kind ?: AiChatMessage.Kind.TEXT) == AiChatMessage.Kind.TEXT }
            .forEach { message ->
                conversation += JSONObject().apply {
                    put(
                        "role",
                        if (message.role == AiChatMessage.Role.USER) "user" else "assistant"
                    )
                    put("content", stripSearchResultBlocks(message.content))
                }
            }
        return conversation
    }

    private fun appendWorldBookInjections(
        conversation: MutableList<JSONObject>,
        worldBookContext: AiWorldBookContext,
        position: String
    ) {
        worldBookContext.injections
            .filter {
                if (position == AiWorldBookEntry.POSITION_AFTER_SYSTEM_PROMPT) {
                    it.position == position || it.position.isBlank()
                } else {
                    it.position == position
                }
            }
            .forEach { injection ->
                conversation += worldBookInjectionMessage(injection)
            }
    }

    private fun insertWorldBookBeforeLastUser(
        conversation: MutableList<JSONObject>,
        worldBookContext: AiWorldBookContext
    ) {
        val messages = worldBookContext.injections
            .filter { it.position == AiWorldBookEntry.POSITION_BEFORE_LAST_USER }
            .map(::worldBookInjectionMessage)
        if (messages.isEmpty()) return
        val index = conversation.indexOfLast { it.optString("role") == "user" }
            .takeIf { it >= 0 }
            ?: conversation.size
        conversation.addAll(index, messages)
    }

    private fun insertWorldBookByDepth(
        conversation: MutableList<JSONObject>,
        worldBookContext: AiWorldBookContext
    ) {
        worldBookContext.injections
            .filter { it.position == AiWorldBookEntry.POSITION_INJECT_DEPTH }
            .sortedByDescending { it.injectDepth }
            .forEach { injection ->
                val index = (conversation.size - injection.injectDepth).coerceIn(1, conversation.size)
                conversation.add(index, worldBookInjectionMessage(injection))
            }
    }

    private fun worldBookInjectionMessage(injection: AiWorldBookInjection): JSONObject {
        return JSONObject().apply {
            put("role", injection.role)
            put("content", injection.content)
        }
    }

    private fun resolveChatCompanion(memoryContext: AiMemoryContext?): AiChatCompanionConfig {
        val companionId = memoryContext?.companionId
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: AppConfig.aiCurrentChatCompanionId
        return AppConfig.aiChatCompanionList.firstOrNull { config ->
            config.id == companionId && config.enabled
        } ?: AppConfig.aiCurrentChatCompanion
    }

    private fun buildCompanionSystemPrompt(companion: AiChatCompanionConfig): String {
        if (companion.type != AiChatCompanionConfig.TYPE_CHARACTER) {
            return companion.prompt.ifBlank { AppConfig.DEFAULT_AI_SYSTEM_PROMPT }
        }
        val characterId = companion.characterId.toLongOrNull()
            ?: return companion.prompt.ifBlank { AppConfig.DEFAULT_AI_SYSTEM_PROMPT }
        val character = runCatching {
            appDb.bookCharacterDao.getCharacter(characterId)
        }.getOrNull()
            ?: return companion.prompt.ifBlank { AppConfig.DEFAULT_AI_SYSTEM_PROMPT }
        if (companion.bookKey.isNotBlank() && character.bookUrl != companion.bookKey) {
            return companion.prompt.ifBlank { AppConfig.DEFAULT_AI_SYSTEM_PROMPT }
        }
        return buildCharacterSystemPrompt(
            character = character,
            sourceLabel = resolveCharacterSourceLabel(character.bookUrl)
        )
    }

    private fun buildCharacterSystemPrompt(
        character: BookCharacter,
        sourceLabel: String
    ): String {
        val age = BookCharacterProfileMeta.ageOf(character)
        val attributes = BookCharacterProfileMeta.attributesWithoutAge(character.attributes)
        val profileLines = listOfNotNull(
            "来源作品：$sourceLabel".takeIf { sourceLabel.isNotBlank() },
            "角色名：${character.displayName()}",
            "角色定位：${character.roleLabel()}",
            "性别：${character.genderLabel()}".takeIf { character.genderLabel() != "未知" },
            "年纪：$age".takeIf { age.isNotBlank() },
            "身份：${character.identity}".takeIf { character.identity.isNotBlank() },
            "外貌：${character.appearance}".takeIf { character.appearance.isNotBlank() },
            "性格：${character.personality}".takeIf { character.personality.isNotBlank() },
            "能力/技能：${character.skills}".takeIf { character.skills.isNotBlank() },
            "经历/背景：${character.biography}".takeIf { character.biography.isNotBlank() },
            "属性：$attributes".takeIf { attributes.isNotBlank() }
        )
        return buildString {
            append("你正在扮演小说角色「")
            append(character.displayName())
            append("」与用户对话。")
            append("角色卡是本次对话的唯一人格来源；保持角色身份、语气、经历、认知边界和关系视角。")
            append("不要自称 AI，不要跳出角色解释系统规则。")
            append("如果用户询问角色卡没有覆盖的细节，可以按角色性格自然回避、反问或给出不确定表达，不要编造与角色设定冲突的内容。")
            append("\n\n角色卡：\n")
            append(profileLines.joinToString("\n"))
        }
    }

    private fun resolveCharacterSourceLabel(bookKey: String): String {
        return runCatching {
            appDb.bookDao.all.firstOrNull { book -> book.characterBookKey() == bookKey }
                ?.let { book ->
                    listOf(book.name, book.author.ifBlank { "未知作者" })
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                }
        }.getOrNull()
            ?: displayBookKeyLabel(bookKey)
    }

    private fun displayBookKeyLabel(bookKey: String): String {
        val value = bookKey.trim()
        if (!value.startsWith("work:")) return value.ifBlank { "未绑定作品" }
        val body = value.removePrefix("work:")
        val parts = body.split('/', limit = 2)
        return when {
            parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank() -> "${parts[1]} · ${parts[0]}"
            body.isNotBlank() -> body
            else -> "未绑定作品"
        }
    }

    private fun estimateStaticRequestTokens(
        messages: List<AiChatMessage>,
        tools: List<AiResolvedTool>,
        activeSkills: List<AiSkillConfig> = emptyList(),
        systemPrompt: String = AppConfig.aiSystemPrompt.ifBlank { AppConfig.DEFAULT_AI_SYSTEM_PROMPT },
        personaPrompt: String = AppConfig.aiCurrentPersona?.prompt.orEmpty()
    ): Int {
        val systemTokens = AiContextManager.estimateTokens(systemPrompt)
        val personaTokens = AiContextManager.estimateTokens(personaPrompt)
        val skillTokens = activeSkills.sumOf {
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

    internal fun safeDebugLog(text: String): String {
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

    private fun parseAssistantTurn(response: JSONObject): AiAgentAssistantTurn {
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
                    AiAgentToolCall(
                        id = toolCall.optJsonString("id").ifBlank { "call_$index" },
                        name = function.optJsonString("name"),
                        arguments = extractToolArguments(function.opt("arguments"))
                    )
                )
            }
        }
        return AiAgentAssistantTurn(
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
        return normalizePromptCacheKey(raw).ifBlank { provider.id.take(64) }
    }

    private fun normalizePromptCacheKey(raw: String): String {
        return raw.lowercase()
            .replace(Regex("[^a-z0-9._:-]"), "_")
            .take(128)
    }

    private fun Throwable.isAiFastFallbackCandidate(): Boolean {
        if (isAiRetryableNetworkAbort()) return true
        var current: Throwable? = this
        while (current != null) {
            val message = current.message.orEmpty().lowercase()
            if (current is InterruptedIOException) return true
            if (
                "timeout" in message ||
                "timed out" in message ||
                "429" in message ||
                "rate limit" in message ||
                "too many requests" in message
            ) {
                return true
            }
            current = current.cause
        }
        return false
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

    private fun JSONObject.optJsonString(key: String): String {
        val text = when (val value = opt(key)) {
            null, JSONObject.NULL -> ""
            is String -> value
            else -> value.toString()
        }.trim()
        return if (text.equals("null", ignoreCase = true)) "" else text
    }

    private fun String.isValidJsonString(): Boolean {
        return isNotBlank() && !equals("null", ignoreCase = true)
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
