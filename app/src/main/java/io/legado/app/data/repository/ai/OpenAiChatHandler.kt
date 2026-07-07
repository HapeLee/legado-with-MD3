package io.legado.app.data.repository.ai

import androidx.annotation.Keep
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
import okhttp3.Response

class OpenAiChatHandler : AiProtocolHandler {

    override val protocols = setOf(AiProtocol.OPENAI_CHAT_COMPLETIONS)

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
            "OpenAI-compatible configuration incomplete: baseUrl, apiKey, and model are required"
        }
        val keyRotator = KeyRotator(provider.apiKey)
        val params = request.params
        val body = mutableMapOf<String, Any?>(
            "model" to request.model.modelId,
            "messages" to request.messages.toOpenAiChatMessages()
        )
        request.tools.takeIf { it.isNotEmpty() }?.let { body["tools"] = it.toOpenAiChatTools() }
        params.temperature?.let { body["temperature"] = it }
        params.maxOutputTokens?.let { body["max_tokens"] = it }
        params.topP?.let { body["top_p"] = it }
        if (hasReasoningCapability(request.model.capabilities) && params.reasoningLevel != AiReasoningLevel.AUTO) {
            body["reasoning_effort"] = params.reasoningLevel.toOpenAiEffort()
        }

        return retryWithBackoff(maxAttempts = 3, keyRotator = keyRotator) {
            val response = aiOkHttpClient.newCallStrResponse {
                url(provider.baseUrl + provider.chatPath)
                postJson(GSON.toJson(body))
                addHeaders(
                    provider.headers + provider.customHeaders + mapOf(
                        "Authorization" to "Bearer ${keyRotator.currentKey}",
                        "Content-Type" to "application/json"
                    )
                )
            }
            if (!response.isSuccessful()) {
                throw Exception("HTTP ${response.code()}: ${response.message()}")
            }
            val json = GSON.fromJson(response.body, OpenAiChatResponse::class.java)
            val text = json?.choices?.firstOrNull()?.message?.content
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
            "OpenAI-compatible configuration incomplete: baseUrl, apiKey, and model are required"
        }
        val keyRotator = KeyRotator(provider.apiKey)
        val params = request.params
        val body = mutableMapOf<String, Any?>(
            "model" to request.model.modelId,
            "messages" to request.messages.toOpenAiChatMessages(),
            "stream" to true
        )
        request.tools.takeIf { it.isNotEmpty() }?.let { body["tools"] = it.toOpenAiChatTools() }
        params.temperature?.let { body["temperature"] = it }
        params.maxOutputTokens?.let { body["max_tokens"] = it }
        params.topP?.let { body["top_p"] = it }
        if (hasReasoningCapability(request.model.capabilities) && params.reasoningLevel != AiReasoningLevel.AUTO) {
            body["reasoning_effort"] = params.reasoningLevel.toOpenAiEffort()
        }

        // For streaming, we retry before establishing the SSE connection.
        // Once streaming starts, errors are not retried (partial output would be confusing).
        val response = retryWithBackoff(maxAttempts = 3, keyRotator = keyRotator) {
            aiOkHttpClient.newCallResponse {
                url(provider.baseUrl + provider.chatPath)
                postJson(GSON.toJson(body))
                addHeaders(
                    provider.headers + provider.customHeaders + mapOf(
                        "Authorization" to "Bearer ${keyRotator.currentKey}",
                        "Content-Type" to "application/json"
                    )
                )
            }.also {
                if (!it.isSuccessful) {
                    throw Exception("HTTP ${it.code}: ${it.message}")
                }
            }
        }
        try {
            response.readSseData { data ->
                val root = data.toJsonObject()
                root?.extractApiErrorMessage()?.let { throw Exception(it) }
                runCatching {
                    val chunk = GSON.fromJson(data, OpenAiChatStreamChunk::class.java)
                    val delta = chunk?.choices?.firstOrNull()?.delta
                    val reasoning = delta?.reasoning_content ?: delta?.reasoning
                    if (!reasoning.isNullOrEmpty()) {
                        emitEvent(AiStreamEvent.Reasoning(reasoning))
                    }
                    val content = delta?.content
                    if (!content.isNullOrEmpty()) {
                        emitEvent(AiStreamEvent.Content(content))
                    }
                    delta?.tool_calls?.forEach { toolCall ->
                        emitEvent(
                            AiStreamEvent.ToolCallDelta(
                                id = toolCall.id,
                                index = toolCall.index,
                                name = toolCall.function?.name,
                                argumentsDelta = toolCall.function?.arguments,
                                rawType = toolCall.type ?: "tool_call"
                            )
                        )
                    }
                }.getOrElse {
                    throw Exception("Invalid OpenAI chat stream chunk", it)
                }
            }
        } finally {
            response.close()
        }
    }

    private suspend fun fetchModelsInternal(provider: AiProviderConfig): List<AiAvailableModel> {
        require(provider.baseUrl.isNotBlank() && provider.apiKey.isNotBlank()) {
            "AI provider configuration incomplete: baseUrl and apiKey are required"
        }
        val keyRotator = KeyRotator(provider.apiKey)
        val modelsUrl = provider.modelsPath?.let { provider.baseUrl + it }
            ?: provider.modelsUrl
            ?: (provider.baseUrl + "/models")
        return retryWithBackoff(maxAttempts = 2, keyRotator = keyRotator) {
            val response = okHttpClient.newCallStrResponse {
                url(modelsUrl)
                addHeaders(
                    provider.headers + provider.customHeaders + mapOf(
                        "Authorization" to "Bearer ${keyRotator.currentKey}",
                        "Content-Type" to "application/json"
                    )
                )
            }
            if (!response.isSuccessful()) {
                throw Exception("HTTP ${response.code()}: ${response.message()}")
            }
            val json = GSON.fromJson(response.body, OpenAiModelsResponse::class.java)
            json?.data.toAvailableModels()
        }
    }
}

// ---- Message & tool format converters ----

internal fun List<AiMessage>.toOpenAiChatMessages(): List<Map<String, Any?>> {
    return mapNotNull { message ->
        when {
            message.role == AiMessageRole.TOOL -> mapOf(
                "role" to "tool",
                "tool_call_id" to message.toolCallId,
                "content" to message.content
            )
            message.toolCalls.isNotEmpty() -> {
                buildMap {
                    put("role", "assistant")
                    put("content", message.content.takeIf { it.isNotBlank() })
                    put(
                        "tool_calls",
                        message.toolCalls.map {
                            mapOf(
                                "id" to it.id,
                                "type" to "function",
                                "function" to mapOf(
                                    "name" to it.name,
                                    "arguments" to it.arguments
                                )
                            )
                        }
                    )
                }
            }
            else -> mapOf("role" to message.role, "content" to message.content)
        }
    }
}

internal fun List<AiToolDefinition>.toOpenAiChatTools(): List<Map<String, Any?>> {
    return map {
        mapOf(
            "type" to "function",
            "function" to mapOf(
                "name" to it.name,
                "description" to it.description,
                "parameters" to it.inputSchema
            )
        )
    }
}

private fun List<OpenAiModelItem>?.toAvailableModels(): List<AiAvailableModel> {
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
internal data class OpenAiChatResponse(
    val choices: List<OpenAiChatChoice>?
)

@Keep
internal data class OpenAiChatChoice(
    val message: OpenAiChatMessage?
)

@Keep
internal data class OpenAiChatMessage(
    val content: String?
)

@Keep
internal data class OpenAiModelsResponse(
    val data: List<OpenAiModelItem>?
)

@Keep
internal data class OpenAiModelItem(
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
internal data class OpenAiChatStreamChunk(
    val choices: List<OpenAiChatStreamChoice>?
)

@Keep
internal data class OpenAiChatStreamChoice(
    val delta: OpenAiChatStreamDelta?
)

@Keep
internal data class OpenAiChatStreamDelta(
    val content: String?,
    val reasoning_content: String?,
    val reasoning: String?,
    val tool_calls: List<OpenAiChatToolCall>?
)

@Keep
internal data class OpenAiChatToolCall(
    val index: Int?,
    val id: String?,
    val type: String?,
    val function: OpenAiChatToolCallFunction?
)

@Keep
internal data class OpenAiChatToolCallFunction(
    val name: String?,
    val arguments: String?
)
