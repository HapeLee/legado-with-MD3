package io.legado.app.data.repository.ai

import androidx.annotation.Keep
import com.google.gson.JsonObject
import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.model.AiAvailableModel
import io.legado.app.domain.model.AiCapability
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiGenerateResponse
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiProtocol
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.domain.model.AiToolCall
import io.legado.app.domain.model.AiToolDefinition
import io.legado.app.help.http.addHeaders
import io.legado.app.help.http.await
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.newCallStrResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.postJson
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AnthropicHandler : AiProtocolHandler {

    override val protocols = setOf(AiProtocol.ANTHROPIC_MESSAGES)

    override suspend fun generate(request: AiGenerateRequest): Result<AiGenerateResponse> =
        withContext(Dispatchers.IO) {
            runCatching { generateInternal(request) }
        }

    override suspend fun stream(
        request: AiGenerateRequest,
        emitEvent: suspend (AiStreamEvent) -> Unit
    ) {
        streamInternal(request, emitEvent)
    }

    override suspend fun fetchModels(provider: AiProviderConfig): Result<List<AiAvailableModel>> =
        withContext(Dispatchers.IO) {
            runCatching { fetchModelsInternal(provider) }
        }

    private suspend fun generateInternal(request: AiGenerateRequest): AiGenerateResponse {
        val provider = request.model.provider
        require(provider.baseUrl.isNotBlank() && provider.apiKey.isNotBlank() && request.model.modelId.isNotBlank()) {
            "Anthropic configuration incomplete: baseUrl, apiKey, and model are required"
        }
        val keyRotator = KeyRotator(provider.apiKey)
        val systemPrompt = request.messages
            .filter { it.role == AiMessageRole.SYSTEM }
            .joinToString("\n\n") { it.content }
            .takeIf { it.isNotBlank() }
        val messages = request.messages
            .filter { it.role != AiMessageRole.SYSTEM }
            .toAnthropicMessages()
        val body = mutableMapOf<String, Any?>(
            "model" to request.model.modelId,
            "messages" to messages,
            "max_tokens" to (request.params.maxOutputTokens ?: request.model.maxOutputTokens.takeIf { it > 0 } ?: 2048)
        )
        request.tools.takeIf { it.isNotEmpty() }?.let { body["tools"] = it.toAnthropicTools() }
        systemPrompt?.let { body["system"] = it }
        // Reasoning config — suppress temperature when thinking is enabled
        val reasoningLevel = request.params.reasoningLevel
        if (hasReasoningCapability(request.model.capabilities)) {
            val thinking = buildMap<String, Any> {
                put("type", if (reasoningLevel == AiReasoningLevel.OFF) "disabled" else "adaptive")
                if (reasoningLevel != AiReasoningLevel.OFF) {
                    put("display", "summarized")
                }
            }
            body["thinking"] = thinking
            if (reasoningLevel != AiReasoningLevel.OFF && reasoningLevel != AiReasoningLevel.AUTO) {
                body["output_config"] = mapOf("effort" to reasoningLevel.effort)
            }
        } else {
            request.params.temperature?.let { body["temperature"] = it }
        }
        request.params.topP?.let { body["top_p"] = it }

        return retryWithBackoff(maxAttempts = 3, keyRotator = keyRotator) {
            val response = aiOkHttpClient.newCallStrResponse {
                url(provider.baseUrl + provider.messagesPath)
                postJson(GSON.toJson(body))
                addHeaders(
                    provider.headers + provider.customHeaders + mapOf(
                        "x-api-key" to keyRotator.currentKey,
                        "anthropic-version" to "2023-06-01",
                        "Content-Type" to "application/json"
                    )
                )
            }
            if (!response.isSuccessful()) {
                throw Exception("HTTP ${response.code()}: ${response.message()}")
            }
            val json = GSON.fromJson(response.body, AnthropicMessageResponse::class.java)
            val text = json?.content
                ?.mapNotNull { it.text?.takeIf { text -> text.isNotBlank() } }
                ?.joinToString("")
            if (text.isNullOrBlank()) {
                throw Exception("Empty AI response")
            } else {
                AiGenerateResponse(text = text, rawBody = response.body)
            }
        }
    }

    private suspend fun streamInternal(
        request: AiGenerateRequest,
        emitEvent: suspend (AiStreamEvent) -> Unit
    ) {
        val provider = request.model.provider
        require(provider.baseUrl.isNotBlank() && provider.apiKey.isNotBlank() && request.model.modelId.isNotBlank()) {
            "Anthropic configuration incomplete: baseUrl, apiKey, and model are required"
        }
        val systemPrompt = request.messages
            .filter { it.role == AiMessageRole.SYSTEM }
            .joinToString("\n\n") { it.content }
            .takeIf { it.isNotBlank() }
        val messages = request.messages
            .filter { it.role != AiMessageRole.SYSTEM }
            .toAnthropicMessages()
        val body = mutableMapOf<String, Any?>(
            "model" to request.model.modelId,
            "messages" to messages,
            "max_tokens" to (request.params.maxOutputTokens ?: request.model.maxOutputTokens.takeIf { it > 0 } ?: 2048),
            "stream" to true
        )
        request.tools.takeIf { it.isNotEmpty() }?.let { body["tools"] = it.toAnthropicTools() }
        systemPrompt?.let { body["system"] = it }
        // Reasoning config — suppress temperature when thinking is enabled
        val reasoningLevel = request.params.reasoningLevel
        if (hasReasoningCapability(request.model.capabilities)) {
            val thinking = buildMap<String, Any> {
                put("type", if (reasoningLevel == AiReasoningLevel.OFF) "disabled" else "adaptive")
                if (reasoningLevel != AiReasoningLevel.OFF) {
                    put("display", "summarized")
                }
            }
            body["thinking"] = thinking
            if (reasoningLevel != AiReasoningLevel.OFF && reasoningLevel != AiReasoningLevel.AUTO) {
                body["output_config"] = mapOf("effort" to reasoningLevel.effort)
            }
        } else {
            request.params.temperature?.let { body["temperature"] = it }
        }
        request.params.topP?.let { body["top_p"] = it }

        val keyRotator = KeyRotator(provider.apiKey)
        val response = retryWithBackoff(maxAttempts = 3, keyRotator = keyRotator) {
            aiOkHttpClient.newCallResponse {
                url(provider.baseUrl + provider.messagesPath)
                postJson(GSON.toJson(body))
                addHeaders(
                    provider.headers + provider.customHeaders + mapOf(
                        "x-api-key" to keyRotator.currentKey,
                        "anthropic-version" to "2023-06-01",
                        "Content-Type" to "application/json"
                    )
                )
            }.also {
                if (!it.isSuccessful) {
                    throw Exception("HTTP ${it.code}: ${it.message}")
                }
            }
        }
        val toolBlocks = mutableMapOf<Int, AnthropicToolBlock>()
        try {
            response.readSseData { data ->
                val event = runCatching {
                    GSON.fromJson(data, AnthropicStreamEvent::class.java)
                }.getOrElse {
                    throw Exception("Invalid Anthropic stream event", it)
                } ?: return@readSseData
                when (event.type) {
                    "error" -> {
                        throw Exception(event.error?.message ?: event.error?.type ?: "Anthropic stream error")
                    }
                    "content_block_start" -> {
                        val index = event.index ?: return@readSseData
                        val block = event.content_block ?: return@readSseData
                        if (block.type == "tool_use") {
                            toolBlocks[index] = AnthropicToolBlock(
                                id = block.id ?: "anthropic_tool_$index",
                                name = block.name.orEmpty()
                            )
                            emitEvent(
                                AiStreamEvent.ToolCallDelta(
                                    id = block.id ?: "anthropic_tool_$index",
                                    index = index,
                                    name = block.name,
                                    argumentsDelta = null,
                                    rawType = "tool_use"
                                )
                            )
                        }
                    }
                    "content_block_delta" -> {
                        val delta = event.delta ?: return@readSseData
                        when (delta.type) {
                            "text_delta", null -> {
                                delta.text?.takeIf { it.isNotEmpty() }?.let {
                                    emitEvent(AiStreamEvent.Content(it))
                                }
                                delta.thinking?.takeIf { it.isNotEmpty() }?.let {
                                    emitEvent(AiStreamEvent.Reasoning(it))
                                }
                                delta.partial_json?.takeIf { it.isNotEmpty() }?.let {
                                    val block = event.index?.let { index -> toolBlocks[index] }
                                    emitEvent(
                                        AiStreamEvent.ToolCallDelta(
                                            id = block?.id ?: event.index?.let { index -> "anthropic_tool_$index" },
                                            index = event.index,
                                            name = block?.name,
                                            argumentsDelta = it,
                                            rawType = "input_json_delta"
                                        )
                                    )
                                }
                            }
                            "thinking_delta" -> {
                                delta.thinking?.takeIf { it.isNotEmpty() }?.let {
                                    emitEvent(AiStreamEvent.Reasoning(it))
                                }
                            }
                            "input_json_delta" -> {
                                delta.partial_json?.takeIf { it.isNotEmpty() }?.let {
                                    val block = event.index?.let { index -> toolBlocks[index] }
                                    emitEvent(
                                        AiStreamEvent.ToolCallDelta(
                                            id = block?.id ?: event.index?.let { index -> "anthropic_tool_$index" },
                                            index = event.index,
                                            name = block?.name,
                                            argumentsDelta = it,
                                            rawType = "input_json_delta"
                                        )
                                    )
                                }
                            }
                        }
                    }
                    "content_block_stop" -> {
                        event.index?.let { toolBlocks.remove(it) }
                    }
                }
            }
        } finally {
            response.close()
        }
    }

    private suspend fun fetchModelsInternal(provider: AiProviderConfig): List<AiAvailableModel> {
        require(provider.baseUrl.isNotBlank() && provider.apiKey.isNotBlank()) {
            "Anthropic configuration incomplete: baseUrl and apiKey are required"
        }
        val keyRotator = KeyRotator(provider.apiKey)
        val modelsUrl = provider.modelsPath?.let { provider.baseUrl + it }
            ?: provider.modelsUrl
            ?: (provider.baseUrl + "/v1/models")
        return retryWithBackoff(maxAttempts = 2, keyRotator = keyRotator) {
            val response = okHttpClient.newCallStrResponse {
                url(modelsUrl)
                addHeaders(
                    provider.headers + provider.customHeaders + mapOf(
                        "x-api-key" to keyRotator.currentKey,
                        "anthropic-version" to "2023-06-01",
                        "Content-Type" to "application/json"
                    )
                )
            }
            if (!response.isSuccessful()) {
                throw Exception("HTTP ${response.code()}: ${response.message()}")
            }
            val json = GSON.fromJson(response.body, AnthropicModelsResponse::class.java)
            json?.data.toAvailableModels()
        }
    }
}

// ---- Message & tool format converters ----

internal fun List<AiMessage>.toAnthropicMessages(): List<Map<String, Any?>> {
    return map { message ->
        when {
            message.role == AiMessageRole.TOOL -> mapOf(
                "role" to "user",
                "content" to listOf(
                    mapOf(
                        "type" to "tool_result",
                        "tool_use_id" to message.toolCallId,
                        "content" to message.content
                    )
                )
            )
            message.toolCalls.isNotEmpty() -> {
                val content = buildList {
                    if (message.content.isNotBlank()) {
                        add(mapOf("type" to "text", "text" to message.content))
                    }
                    message.toolCalls.forEach {
                        add(
                            mapOf(
                                "type" to "tool_use",
                                "id" to it.id,
                                "name" to it.name,
                                "input" to (it.arguments.toJsonObject() ?: JsonObject())
                            )
                        )
                    }
                }
                mapOf("role" to "assistant", "content" to content)
            }
            else -> mapOf(
                "role" to if (message.role == AiMessageRole.ASSISTANT) "assistant" else "user",
                "content" to message.content
            )
        }
    }
}

internal fun List<AiToolDefinition>.toAnthropicTools(): List<Map<String, Any?>> {
    return map {
        mapOf(
            "name" to it.name,
            "description" to it.description,
            "input_schema" to it.inputSchema
        )
    }
}

private fun List<AnthropicModelItem>?.toAvailableModels(): List<AiAvailableModel> {
    return orEmpty()
        .mapNotNull { item ->
            val id = item.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            AiAvailableModel(
                id = id,
                name = item.display_name?.takeIf { it.isNotBlank() }
                    ?: item.displayName?.takeIf { it.isNotBlank() }
                    ?: item.name?.takeIf { it.isNotBlank() }
                    ?: id,
                contextWindow = item.context_window ?: item.contextWindow ?: 0,
                maxOutputTokens = item.max_tokens
                    ?: item.maxTokens
                    ?: item.max_output_tokens
                    ?: item.maxOutputTokens
                    ?: 0
            )
        }
        .distinctBy { it.id }
        .sortedBy { it.name.lowercase() }
}

// ---- Data classes ----

@Keep
internal data class AnthropicMessageResponse(
    val content: List<AnthropicMessageContent>?
)

@Keep
internal data class AnthropicMessageContent(
    val type: String?,
    val text: String?
)

@Keep
internal data class AnthropicModelsResponse(
    val data: List<AnthropicModelItem>?
)

@Keep
internal data class AnthropicModelItem(
    val id: String?,
    val name: String?,
    val display_name: String?,
    val displayName: String?,
    val context_window: Int?,
    val contextWindow: Int?,
    val max_tokens: Int?,
    val maxTokens: Int?,
    val max_output_tokens: Int?,
    val maxOutputTokens: Int?
)

@Keep
internal data class AnthropicStreamEvent(
    val type: String?,
    val index: Int?,
    val content_block: AnthropicContentBlock?,
    val error: AnthropicStreamError?,
    val delta: AnthropicStreamDelta?
)

@Keep
internal data class AnthropicStreamDelta(
    val type: String?,
    val text: String?,
    val thinking: String?,
    val partial_json: String?,
    val signature: String?
)

@Keep
internal data class AnthropicContentBlock(
    val type: String?,
    val id: String?,
    val name: String?,
    val text: String?
)

@Keep
internal data class AnthropicStreamError(
    val type: String?,
    val message: String?
)

internal data class AnthropicToolBlock(
    val id: String,
    val name: String
)
