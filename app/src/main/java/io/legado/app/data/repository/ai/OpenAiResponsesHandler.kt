package io.legado.app.data.repository.ai

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

class OpenAiResponsesHandler : AiProtocolHandler {

    override val protocols = setOf(AiProtocol.OPENAI_RESPONSES)

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
            runCatching { fetchOpenAiCompatibleModels(provider) }
        }

    private suspend fun generateInternal(request: AiGenerateRequest): AiGenerateResponse {
        val provider = request.model.provider
        require(provider.baseUrl.isNotBlank() && provider.apiKey.isNotBlank() && request.model.modelId.isNotBlank()) {
            "OpenAI Responses configuration incomplete: baseUrl, apiKey, and model are required"
        }
        val keyRotator = KeyRotator(provider.apiKey)
        val params = request.params
        val body = mutableMapOf<String, Any?>(
            "model" to request.model.modelId,
            "input" to request.messages.toOpenAiResponsesInput()
        )
        request.tools.takeIf { it.isNotEmpty() }?.let { body["tools"] = it.toOpenAiResponsesTools() }
        params.temperature?.let { body["temperature"] = it }
        params.maxOutputTokens?.let { body["max_output_tokens"] = it }
        params.topP?.let { body["top_p"] = it }
        if (hasReasoningCapability(request.model.capabilities)) {
            body["reasoning"] = buildMap<String, Any> {
                put("summary", "auto")
                if (params.reasoningLevel != AiReasoningLevel.AUTO) {
                    put("effort", params.reasoningLevel.effort)
                }
            }
        }

        return retryWithBackoff(maxAttempts = 3, keyRotator = keyRotator) {
            val response = aiOkHttpClient.newCallStrResponse {
                url(provider.baseUrl + provider.responsesPath)
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
            val root = GSON.fromJson(response.body, JsonObject::class.java)
            val text = root?.getString("output_text") ?: root.extractResponsesOutputText()
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
            "OpenAI Responses configuration incomplete: baseUrl, apiKey, and model are required"
        }
        val keyRotator = KeyRotator(provider.apiKey)
        val params = request.params
        val body = mutableMapOf<String, Any?>(
            "model" to request.model.modelId,
            "input" to request.messages.toOpenAiResponsesInput(),
            "stream" to true
        )
        request.tools.takeIf { it.isNotEmpty() }?.let { body["tools"] = it.toOpenAiResponsesTools() }
        params.temperature?.let { body["temperature"] = it }
        params.maxOutputTokens?.let { body["max_output_tokens"] = it }
        params.topP?.let { body["top_p"] = it }
        if (hasReasoningCapability(request.model.capabilities)) {
            body["reasoning"] = buildMap<String, Any> {
                put("summary", "auto")
                if (params.reasoningLevel != AiReasoningLevel.AUTO) {
                    put("effort", params.reasoningLevel.effort)
                }
            }
        }

        val response = retryWithBackoff(maxAttempts = 3, keyRotator = keyRotator) {
            aiOkHttpClient.newCallResponse {
                url(provider.baseUrl + provider.responsesPath)
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
                val root = data.toJsonObject() ?: throw Exception("Invalid OpenAI Responses stream chunk")
                root.extractApiErrorMessage()?.let { throw Exception(it) }
                when (root.getString("type")) {
                    "response.output_text.delta",
                    "response.refusal.delta" -> {
                        root.getString("delta")?.takeIf { it.isNotEmpty() }?.let {
                            emitEvent(AiStreamEvent.Content(it))
                        }
                    }
                    "response.reasoning_summary_text.delta",
                    "response.reasoning_text.delta" -> {
                        root.getString("delta")?.takeIf { it.isNotEmpty() }?.let {
                            emitEvent(AiStreamEvent.Reasoning(it))
                        }
                    }
                    "response.output_item.added",
                    "response.output_item.done" -> {
                        root.get("item")?.asJsonObjectOrNull()?.let { item ->
                            if (item.getString("type")?.contains("call") == true) {
                                emitEvent(
                                    AiStreamEvent.ToolCallDelta(
                                        id = item.getString("call_id") ?: item.getString("id"),
                                        index = root.getString("output_index")?.toIntOrNull(),
                                        name = item.getString("name") ?: item.getString("server_label"),
                                        argumentsDelta = item.getString("arguments"),
                                        rawType = item.getString("type") ?: root.getString("type").orEmpty()
                                    )
                                )
                            }
                        }
                    }
                    "response.function_call_arguments.delta",
                    "response.mcp_call_arguments.delta",
                    "response.code_interpreter_call_code.delta",
                    "response.custom_tool_call_input.delta" -> {
                        emitEvent(
                            AiStreamEvent.ToolCallDelta(
                                id = root.getString("call_id"),
                                index = root.getString("output_index")?.toIntOrNull(),
                                name = null,
                                argumentsDelta = root.getString("delta"),
                                rawType = root.getString("type").orEmpty()
                            )
                        )
                    }
                    "response.function_call_arguments.done",
                    "response.mcp_call_arguments.done",
                    "response.code_interpreter_call_code.done",
                    "response.custom_tool_call_input.done",
                    "response.file_search_call.in_progress",
                    "response.file_search_call.searching",
                    "response.file_search_call.completed",
                    "response.web_search_call.in_progress",
                    "response.web_search_call.searching",
                    "response.web_search_call.completed",
                    "response.mcp_call.in_progress",
                    "response.mcp_call.completed",
                    "response.mcp_call.failed",
                    "response.mcp_list_tools.in_progress",
                    "response.mcp_list_tools.completed",
                    "response.mcp_list_tools.failed",
                    "response.code_interpreter_call.in_progress",
                    "response.code_interpreter_call.interpreting",
                    "response.code_interpreter_call.completed" -> {
                        emitEvent(
                            AiStreamEvent.ToolCallDelta(
                                id = root.getString("call_id"),
                                index = root.getString("output_index")?.toIntOrNull(),
                                name = null,
                                argumentsDelta = root.getString("arguments")
                                    ?: root.getString("code")
                                    ?: root.getString("input"),
                                rawType = root.getString("type").orEmpty()
                            )
                        )
                    }
                    "response.failed" -> {
                        throw Exception(root.extractResponseFailureMessage() ?: "OpenAI response failed")
                    }
                    "response.incomplete" -> {
                        throw Exception(root.extractResponseIncompleteMessage() ?: "OpenAI response incomplete")
                    }
                }
            }
        } finally {
            response.close()
        }
    }

    private suspend fun fetchOpenAiCompatibleModels(provider: AiProviderConfig): List<AiAvailableModel> {
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

internal fun List<AiMessage>.toOpenAiResponsesInput(): List<Map<String, Any?>> {
    return flatMap { message ->
        when {
            message.role == AiMessageRole.TOOL -> listOf(
                mapOf(
                    "type" to "function_call_output",
                    "call_id" to message.toolCallId,
                    "output" to message.content
                )
            )
            message.toolCalls.isNotEmpty() -> {
                val textMessage = message.content.takeIf { it.isNotBlank() }?.let {
                    mapOf("role" to "assistant", "content" to it)
                }
                val toolCalls = message.toolCalls.map {
                    mapOf(
                        "type" to "function_call",
                        "call_id" to it.id,
                        "name" to it.name,
                        "arguments" to it.arguments
                    )
                }
                listOfNotNull(textMessage) + toolCalls
            }
            else -> listOf(mapOf("role" to message.role, "content" to message.content))
        }
    }
}

internal fun List<AiToolDefinition>.toOpenAiResponsesTools(): List<Map<String, Any?>> {
    return map {
        mapOf(
            "type" to "function",
            "name" to it.name,
            "description" to it.description,
            "parameters" to it.inputSchema
        )
    }
}

private fun JsonObject.extractResponseFailureMessage(): String? {
    val response = get("response")?.asJsonObjectOrNull() ?: return null
    val error = response.get("error")?.asJsonObjectOrNull()
    return error?.getString("message") ?: response.getString("status")
}

private fun JsonObject.extractResponseIncompleteMessage(): String? {
    val response = get("response")?.asJsonObjectOrNull() ?: return null
    val reason = response.get("incomplete_details")
        ?.asJsonObjectOrNull()
        ?.getString("reason")
    return reason?.let { "OpenAI response incomplete: $it" } ?: response.getString("status")
}

private fun JsonObject.extractResponsesOutputText(): String? {
    return get("output")
        ?.asJsonArrayOrNull()
        ?.flatMap { output ->
            output.asJsonObjectOrNull()
                ?.get("content")
                ?.asJsonArrayOrNull()
                ?.mapNotNull { content ->
                    content.asJsonObjectOrNull()
                        ?.takeIf { it.getString("type") == "output_text" }
                        ?.getString("text")
                }
                .orEmpty()
        }
        ?.joinToString("")
        ?.takeIf { it.isNotBlank() }
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
