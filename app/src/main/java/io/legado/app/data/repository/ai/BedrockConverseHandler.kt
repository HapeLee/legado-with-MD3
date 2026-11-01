package io.legado.app.data.repository.ai

import com.google.gson.JsonObject
import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.model.AiAvailableModel
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiGenerateResponse
import io.legado.app.domain.model.AiHttpException
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiProtocol
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiToolDefinition
import io.legado.app.help.http.addHeaders
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.newCallStrResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.postJson
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Response
import okio.Buffer
import okio.BufferedSource
import java.net.URLEncoder

/**
 * AWS Bedrock Converse API, authenticated with a Bedrock API key (Bearer token).
 *
 * Bedrock serves inference and model listing from different hosts, so both URLs are configured
 * separately on the provider:
 * - baseUrl: runtime endpoint, e.g. `https://bedrock-runtime.us-east-1.amazonaws.com`
 * - modelsUrl: control-plane list endpoint, e.g.
 *   `https://bedrock.us-east-1.amazonaws.com/inference-profiles` or `.../foundation-models`
 */
class BedrockConverseHandler : AiProtocolHandler {

    override val protocols = setOf(AiProtocol.AWS_BEDROCK_CONVERSE)

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
        requireConfigured(request)
        val keyRotator = KeyRotator(provider.apiKey)
        val body = request.toConverseBody()
        return retryWithBackoff(maxAttempts = 3, keyRotator = keyRotator) {
            val response = request.httpClient().newCallStrResponse {
                url(request.modelUrl("converse"))
                postJson(GSON.toJson(body))
                addHeaders(provider.bedrockHeaders(request.sessionId, keyRotator.currentKey))
            }
            if (!response.isSuccessful()) {
                throw AiHttpException(response.code(), response.httpErrorMessage())
            }
            val text = response.body?.toJsonObject()
                ?.getAsJsonObject("output")
                ?.getAsJsonObject("message")
                ?.get("content")
                ?.asJsonArrayOrNull()
                ?.mapNotNull { it.asJsonObjectOrNull()?.getString("text") }
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
        requireConfigured(request)
        val keyRotator = KeyRotator(provider.apiKey)
        val body = request.toConverseBody()
        val response = retryWithBackoff(maxAttempts = 3, keyRotator = keyRotator) {
            request.httpClient().newCallResponse {
                url(request.modelUrl("converse-stream"))
                postJson(GSON.toJson(body))
                addHeaders(
                    provider.bedrockHeaders(request.sessionId, keyRotator.currentKey) +
                        ("Accept" to "application/vnd.amazon.eventstream")
                )
            }.also {
                if (!it.isSuccessful) {
                    throw AiHttpException(it.code, it.httpErrorMessage())
                }
            }
        }
        val toolBlocks = mutableMapOf<Int, AnthropicToolBlock>()
        response.readEventStream { headers, payload ->
            val json = payload.toJsonObject() ?: JsonObject()
            when (headers[":message-type"]) {
                "exception" -> throw Exception(
                    "${headers[":exception-type"] ?: "Bedrock error"}: ${json.getString("message").orEmpty()}"
                )
                "error" -> throw Exception(
                    "${headers[":error-code"] ?: "Bedrock error"}: ${headers[":error-message"].orEmpty()}"
                )
            }
            val index = json.get("contentBlockIndex")?.takeIf { !it.isJsonNull }?.asInt
            when (headers[":event-type"]) {
                "contentBlockStart" -> {
                    val toolUse = json.getAsJsonObject("start")?.getAsJsonObject("toolUse")
                        ?: return@readEventStream
                    val id = toolUse.getString("toolUseId") ?: "bedrock_tool_$index"
                    val name = toolUse.getString("name").orEmpty()
                    index?.let { toolBlocks[it] = AnthropicToolBlock(id, name) }
                    emitEvent(AiStreamEvent.ToolCallDelta(id, index, name, null, "tool_use"))
                }
                "contentBlockDelta" -> {
                    val delta = json.getAsJsonObject("delta") ?: return@readEventStream
                    delta.getString("text")?.takeIf { it.isNotEmpty() }?.let {
                        emitEvent(AiStreamEvent.Content(it))
                    }
                    delta.getAsJsonObject("reasoningContent")?.getString("text")
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { emitEvent(AiStreamEvent.Reasoning(it)) }
                    delta.getAsJsonObject("toolUse")?.getString("input")
                        ?.takeIf { it.isNotEmpty() }
                        ?.let {
                            val block = index?.let { i -> toolBlocks[i] }
                            emitEvent(
                                AiStreamEvent.ToolCallDelta(
                                    id = block?.id ?: index?.let { i -> "bedrock_tool_$i" },
                                    index = index,
                                    name = block?.name,
                                    argumentsDelta = it,
                                    rawType = "input_json_delta"
                                )
                            )
                        }
                }
                "contentBlockStop" -> index?.let { toolBlocks.remove(it) }
            }
        }
    }

    private suspend fun fetchModelsInternal(provider: AiProviderConfig): List<AiAvailableModel> {
        require(provider.apiKey.isNotBlank()) { "Bedrock configuration incomplete: API key is required" }
        val modelsUrl = provider.modelsUrl?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException(
                "Bedrock models URL is required, e.g. https://bedrock.us-east-1.amazonaws.com/inference-profiles"
            )
        val keyRotator = KeyRotator(provider.apiKey)
        return retryWithBackoff(maxAttempts = 2, keyRotator = keyRotator) {
            val response = okHttpClient.newCallStrResponse {
                url(modelsUrl)
                addHeaders(provider.bedrockHeaders(null, keyRotator.currentKey))
            }
            if (!response.isSuccessful()) {
                throw AiHttpException(response.code(), response.httpErrorMessage())
            }
            response.body?.toJsonObject().toBedrockModels()
        }
    }

    private fun requireConfigured(request: AiGenerateRequest) {
        val provider = request.model.provider
        require(provider.baseUrl.isNotBlank() && provider.apiKey.isNotBlank() && request.model.modelId.isNotBlank()) {
            "Bedrock configuration incomplete: baseUrl, apiKey, and model are required"
        }
    }
}

private fun AiProviderConfig.bedrockHeaders(sessionId: String?, apiKey: String): Map<String, String> =
    aiRequestHeaders(
        sessionId = sessionId,
        protocolHeaders = mapOf(
            "Authorization" to "Bearer $apiKey",
            "Content-Type" to "application/json"
        )
    )

/** Model ids may be ARNs containing `/` and `:`, so the id is sent as one encoded path segment. */
private fun AiGenerateRequest.modelUrl(action: String): String {
    val modelId = URLEncoder.encode(model.modelId, "UTF-8").replace("+", "%20")
    return "${model.provider.baseUrl.trimEnd('/')}/model/$modelId/$action"
}

private fun AiGenerateRequest.toConverseBody(): Map<String, Any?> {
    val body = mutableMapOf<String, Any?>("messages" to messages.toBedrockMessages())
    messages.filter { it.role == AiMessageRole.SYSTEM && it.content.isNotBlank() }
        .takeIf { it.isNotEmpty() }
        ?.let { system -> body["system"] = system.map { mapOf("text" to it.content) } }
    val inferenceConfig = buildMap<String, Any> {
        (params.maxOutputTokens ?: model.maxOutputTokens.takeIf { it > 0 })?.let { put("maxTokens", it) }
        params.temperature?.let { put("temperature", it) }
        params.topP?.let { put("topP", it) }
    }
    if (inferenceConfig.isNotEmpty()) body["inferenceConfig"] = inferenceConfig
    tools.takeIf { it.isNotEmpty() }?.let { body["toolConfig"] = mapOf("tools" to it.toBedrockTools()) }
    return body
}

/** Converse requires alternating roles, so consecutive same-role messages (e.g. tool results) merge. */
internal fun List<AiMessage>.toBedrockMessages(): List<Map<String, Any?>> {
    val result = mutableListOf<Pair<String, MutableList<Map<String, Any?>>>>()
    filter { it.role != AiMessageRole.SYSTEM }.forEach { message ->
        val role = if (message.role == AiMessageRole.ASSISTANT) "assistant" else "user"
        val blocks = message.toBedrockContent().takeIf { it.isNotEmpty() } ?: return@forEach
        val last = result.lastOrNull()
        if (last?.first == role) last.second += blocks else result += role to blocks.toMutableList()
    }
    return result.map { (role, content) -> mapOf("role" to role, "content" to content) }
}

private fun AiMessage.toBedrockContent(): List<Map<String, Any?>> = buildList {
    if (role == AiMessageRole.TOOL) {
        add(
            mapOf(
                "toolResult" to mapOf(
                    "toolUseId" to toolCallId,
                    "content" to listOf(mapOf("text" to content.ifBlank { " " }))
                )
            )
        )
        return@buildList
    }
    // Bedrock rejects empty text blocks.
    if (content.isNotBlank()) add(mapOf("text" to content))
    images.forEach { image ->
        add(
            mapOf(
                "image" to mapOf(
                    "format" to image.mimeType.substringAfter('/'),
                    "source" to mapOf("bytes" to image.base64Data)
                )
            )
        )
    }
    toolCalls.forEach {
        add(
            mapOf(
                "toolUse" to mapOf(
                    "toolUseId" to it.id,
                    "name" to it.name,
                    "input" to (it.arguments.toJsonObject() ?: JsonObject())
                )
            )
        )
    }
}

private fun List<AiToolDefinition>.toBedrockTools(): List<Map<String, Any?>> = map {
    mapOf(
        "toolSpec" to mapOf(
            "name" to it.name,
            "description" to it.description,
            "inputSchema" to mapOf("json" to it.inputSchema)
        )
    )
}

/** Accepts both ListFoundationModels and ListInferenceProfiles responses. */
internal fun JsonObject?.toBedrockModels(): List<AiAvailableModel> {
    this ?: return emptyList()
    val foundationModels = get("modelSummaries")?.asJsonArrayOrNull()?.toList().orEmpty()
        .mapNotNull { it.asJsonObjectOrNull() }
        .filter { summary ->
            val outputs = summary.get("outputModalities")?.asJsonArrayOrNull()
                ?: return@filter true
            outputs.any { !it.isJsonNull && it.asString == "TEXT" }
        }
        .mapNotNull { summary ->
            val id = summary.getString("modelId")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = summary.getString("modelName")?.takeIf { it.isNotBlank() } ?: id
            val providerName = summary.getString("providerName")?.takeIf { it.isNotBlank() }
            AiAvailableModel(id = id, name = providerName?.let { "$it $name" } ?: name)
        }
    val inferenceProfiles = get("inferenceProfileSummaries")?.asJsonArrayOrNull()?.toList().orEmpty()
        .mapNotNull { it.asJsonObjectOrNull() }
        .mapNotNull { summary ->
            val id = summary.getString("inferenceProfileId")?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            AiAvailableModel(
                id = id,
                name = summary.getString("inferenceProfileName")?.takeIf { it.isNotBlank() } ?: id
            )
        }
    return (foundationModels + inferenceProfiles)
        .distinctBy { it.id }
        .sortedBy { it.name.lowercase() }
}

internal sealed interface EventStreamItem {
    data class Message(val headers: Map<String, String>, val payload: String) : EventStreamItem
    data class Failure(val error: Throwable) : EventStreamItem
    data object End : EventStreamItem
}

/**
 * Reads an `application/vnd.amazon.eventstream` body frame by frame. Same threading model as
 * [readSseData]: the blocking read runs in a child coroutine and the response is closed on exit,
 * so cancelling the caller also ends a socket read that is still waiting.
 */
private suspend fun Response.readEventStream(onMessage: suspend (Map<String, String>, String) -> Unit) {
    val response = this
    val items = Channel<EventStreamItem>(Channel.UNLIMITED)
    coroutineScope {
        launch {
            try {
                pumpEventStream(body.source(), items)
                items.trySend(EventStreamItem.End)
            } catch (e: Throwable) {
                items.trySend(EventStreamItem.Failure(e))
            }
        }
        try {
            while (true) {
                when (val item = items.receive()) {
                    is EventStreamItem.Message -> onMessage(item.headers, item.payload)
                    is EventStreamItem.Failure -> throw item.error
                    EventStreamItem.End -> break
                }
            }
        } finally {
            items.close()
            runCatching { response.close() }
        }
    }
}

/** Frame: total length, headers length, prelude CRC, headers, payload, message CRC (TLS covers integrity). */
internal fun pumpEventStream(source: BufferedSource, items: Channel<EventStreamItem>) {
    source.use {
        while (!source.exhausted()) {
            val totalLength = source.readInt().toLong()
            val headersLength = source.readInt().toLong()
            source.readInt()
            val headers = parseEventStreamHeaders(Buffer().write(source.readByteArray(headersLength)))
            val payload = source.readUtf8(totalLength - headersLength - 16)
            source.readInt()
            items.trySend(EventStreamItem.Message(headers, payload))
        }
    }
}

/** Keeps string-typed headers (`:event-type`, `:message-type`, …) and skips other value types. */
private fun parseEventStreamHeaders(buffer: Buffer): Map<String, String> {
    val headers = mutableMapOf<String, String>()
    while (!buffer.exhausted()) {
        val name = buffer.readUtf8((buffer.readByte().toInt() and 0xff).toLong())
        when (val type = buffer.readByte().toInt()) {
            0, 1 -> Unit
            2 -> buffer.skip(1)
            3 -> buffer.skip(2)
            4 -> buffer.skip(4)
            5, 8 -> buffer.skip(8)
            6 -> buffer.skip((buffer.readShort().toInt() and 0xffff).toLong())
            7 -> headers[name] = buffer.readUtf8((buffer.readShort().toInt() and 0xffff).toLong())
            9 -> buffer.skip(16)
            else -> throw IllegalStateException("Unknown event stream header type $type")
        }
    }
    return headers
}
