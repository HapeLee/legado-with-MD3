package io.legado.app.domain.usecase

import com.google.gson.JsonObject
import io.legado.app.domain.gateway.AiChatGateway
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.gateway.AiMemoryGateway
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.gateway.AiToolGateway
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessagePart
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.AiToolApprovalState
import io.legado.app.domain.model.AiToolCall
import io.legado.app.domain.model.toolParts
import io.legado.app.ui.ai.chat.AiChatBookResultUi
import io.legado.app.ui.ai.chat.AiChatMessageUi
import io.legado.app.utils.GSON

/**
 * Encapsulates chat generation logic: request building, streaming, tool execution loop.
 * ViewModel only handles UI state updates by collecting [GenerationEvent]s.
 */
class AiChatGenerationUseCase(
    private val aiTextGateway: AiTextGateway,
    private val aiToolGateway: AiToolGateway,
    private val aiProfileGateway: AiProfileGateway,
    private val aiChatGateway: AiChatGateway,
    private val aiMemoryGateway: AiMemoryGateway
) {

    suspend fun buildRequest(
        userContent: String,
        history: List<AiChatMessageUi>,
        reasoningLevel: AiReasoningLevel,
        conversationId: String? = null
    ): AiGenerateRequest {
        val preset = aiProfileGateway.getTaskPreset(AiTaskType.CHAT)
            ?: aiProfileGateway.getTaskPreset(AiTaskType.TRANSLATE_CHAPTER)
            ?: error("Please configure a default AI model first")
        return AiGenerateRequest(
            model = preset.model,
            messages = buildRequestMessages(userContent, history, conversationId),
            params = preset.params.copy(reasoningLevel = reasoningLevel),
            tools = aiToolGateway.availableTools()
        )
    }

    suspend fun collectStream(
        request: AiGenerateRequest,
        toolTrace: ToolTraceBuilder,
        onContent: suspend (String) -> Unit,
        onReasoning: suspend (String) -> Unit,
        onToolTraceUpdate: suspend () -> Unit
    ) {
        toolTrace.beginResponse()
        aiTextGateway.generateStream(request).collect { event ->
            when (event) {
                is AiStreamEvent.Content -> onContent(event.text)
                is AiStreamEvent.Reasoning -> onReasoning(event.text)
                is AiStreamEvent.ToolCallDelta -> {
                    toolTrace.append(event)
                    onToolTraceUpdate()
                }
            }
        }
    }

    suspend fun executeToolCalls(
        request: AiGenerateRequest,
        assistantContent: String,
        toolTrace: ToolTraceBuilder,
        toolCalls: List<AiToolCall>,
        onToolTraceUpdate: suspend () -> Unit
    ): AiGenerateRequest {
        val toolResultMessages = toolCalls.map { toolCall ->
            val result = aiToolGateway.execute(toolCall)
            val truncated = result.content.truncateToolOutput()
            toolTrace.appendResult(result.callId, truncated)
            onToolTraceUpdate()
            AiMessage(
                role = AiMessageRole.TOOL,
                content = truncated,
                toolCallId = result.callId,
                name = result.name
            )
        }
        return request.copy(
            messages = request.messages +
                AiMessage(
                    role = AiMessageRole.ASSISTANT,
                    content = assistantContent,
                    toolCalls = toolCalls
                ) +
                toolResultMessages
        )
    }

    fun requiresConfirmation(toolName: String): Boolean {
        return aiToolGateway.requiresConfirmation(toolName)
    }

    fun buildAssistantParts(
        text: String,
        reasoning: String,
        toolTrace: ToolTraceBuilder
    ): List<AiMessagePart> {
        return buildList {
            reasoning.takeIf { it.isNotBlank() }?.let { add(AiMessagePart.Reasoning(it)) }
            text.takeIf { it.isNotBlank() }?.let { add(AiMessagePart.Text(it)) }
            addAll(toolTrace.toParts())
            addAll(toolTrace.bookResults())
        }
    }

    suspend fun generateTitle(
        userContent: String,
        assistantContent: String,
        reasoningLevel: AiReasoningLevel
    ): String {
        val preset = aiProfileGateway.getTaskPreset(AiTaskType.CHAT)
            ?: error("No AI model configured")
        val prompt = """
            请根据以下对话内容，生成一个简短的中文标题（不超过20个字）。
            只输出标题，不要任何解释或前缀。

            用户：${userContent.take(500)}
            助手：${assistantContent.take(500)}
        """.trimIndent()
        val request = AiGenerateRequest(
            model = preset.model,
            messages = listOf(AiMessage(AiMessageRole.USER, prompt)),
            params = preset.params.copy(reasoningLevel = reasoningLevel)
        )
        val result = aiTextGateway.generate(request)
        return result.getOrNull()?.text?.trim()?.take(30) ?: userContent.take(20)
    }

    private suspend fun buildRequestMessages(
        newContent: String,
        history: List<AiChatMessageUi>,
        conversationId: String? = null
    ): List<AiMessage> {
        val system = buildSystemPrompt(conversationId)
        val trimmedHistory = history.trimForRequest(MAX_HISTORY_MESSAGES)
        val messages = trimmedHistory.flatMap {
            when (it.role) {
                AiMessageRole.USER -> listOf(AiMessage(AiMessageRole.USER, it.content))
                AiMessageRole.ASSISTANT -> it.toRequestMessages()
                else -> null
            }.orEmpty()
        }
        return listOf(AiMessage(AiMessageRole.SYSTEM, system)) +
            messages +
            AiMessage(AiMessageRole.USER, newContent)
    }

    /**
     * Tool-aware history trimming. Ensures tool_call and tool_result pairs
     * are never split — if a tool_result would be kept without its tool_call,
     * the message is dropped.
     */
    private fun List<AiChatMessageUi>.trimForRequest(maxMessages: Int): List<AiChatMessageUi> {
        if (size <= maxMessages) return this
        val trimmed = takeLast(maxMessages)
        // Find the first message where all Tool parts with output have their
        // corresponding ToolCall present in the trimmed set
        val firstSafe = trimmed.indexOfFirst { msg ->
            msg.parts.toolParts().filter { it.output.isNotBlank() }.all { tool ->
                trimmed.any { other ->
                    other.parts.toolParts().any { it.toolCallId == tool.toolCallId && it.output.isBlank() }
                }
            }
        }
        return if (firstSafe > 0) trimmed.drop(firstSafe) else trimmed
    }

    private fun AiChatMessageUi.toRequestMessages(): List<AiMessage> {
        val tools = parts.toolParts()
        val toolCalls = tools.filter {
            it.output.isNotBlank() || it.approvalState == AiToolApprovalState.AUTO
        }.map {
            AiToolCall(id = it.toolCallId, name = it.toolName, arguments = it.input)
        }
        val toolResults = tools.filter { it.output.isNotBlank() }.map {
            AiMessage(
                role = AiMessageRole.TOOL,
                content = it.output,
                toolCallId = it.toolCallId,
                name = it.toolName
            )
        }
        return listOf(
            AiMessage(role = AiMessageRole.ASSISTANT, content = content, toolCalls = toolCalls)
        ) + toolResults
    }

    private suspend fun buildSystemPrompt(conversationId: String? = null): String {
        val base = """
            You are a helpful AI assistant inside a reading app.
            Render answers in complete Markdown when structure helps.
            Use local reading tools when the user asks about bookshelf books, current reading progress, chapters, bookmarks, reading statistics, or existing AI notes.
            For requests like summarizing, explaining, or continuing from the current chapter, use the local book and chapter tools before answering.
            If a tool says content is missing or unavailable, state that limitation clearly and do not invent book content.
            Save notes or summaries only when the user explicitly asks to save them.
            Do not reveal hidden chain-of-thought. If reasoning is useful, provide a brief reasoning summary.
        """.trimIndent()

        if (conversationId == null) return base

        val memories = aiMemoryGateway.getForPrompt(conversationId)
        if (memories.isEmpty()) return base

        val memoryBlock = memories.joinToString("\n") { "- ${it.key}: ${it.value}" }
        return "$base\n\n## User Memory\nThe following facts about the user have been remembered from prior conversations:\n$memoryBlock"
    }

    companion object {
        const val MAX_HISTORY_MESSAGES = 12
        const val MAX_TOOL_OUTPUT_CHARS = 8_000
    }
}

/**
 * Accumulates streaming tool call deltas into complete [AiMessagePart.Tool] parts.
 * Thread-safe for use within a single coroutine context.
 */
class ToolTraceBuilder {
    private val calls = linkedMapOf<String, ToolCallTrace>()
    private val indexKeys = mutableMapOf<Int, String>()

    fun beginResponse() {
        indexKeys.clear()
    }

    fun append(event: AiStreamEvent.ToolCallDelta): String {
        val eventId = event.id?.takeIf { it.isNotBlank() }
        if (eventId != null && event.index != null) {
            indexKeys[event.index] = eventId
        }
        val baseId = eventId
            ?: event.index?.let { indexKeys[it] ?: "tool_index_$it" }
            ?: "tool_${calls.size + 1}"
        val id = if (eventId == null && calls[baseId]?.result != null) {
            "${baseId}_${calls.size + 1}"
        } else {
            baseId
        }
        val call = calls.getOrPut(id) { ToolCallTrace(id = id, rawType = event.rawType) }
        event.name?.takeIf { it.isNotBlank() }?.let { call.name = it }
        event.argumentsDelta?.takeIf { it.isNotEmpty() }?.let { call.arguments.append(it) }
        if (call.rawType.isBlank()) call.rawType = event.rawType
        return toString()
    }

    fun appendResult(id: String, result: String): String {
        calls[id]?.result = result
        return toString()
    }

    fun pendingToolCalls(): List<AiToolCall> {
        return calls.values.filter { it.result == null }.mapNotNull { call ->
            val name = call.name.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            AiToolCall(
                id = call.id,
                name = name,
                arguments = call.arguments.toString().ifBlank { "{}" }
            )
        }
    }

    fun toParts(): List<AiMessagePart> {
        val parts = mutableListOf<AiMessagePart>()
        calls.values.forEach { call ->
            val name = call.name.takeIf { it.isNotBlank() } ?: return@forEach
            parts += AiMessagePart.Tool(
                toolCallId = call.id,
                toolName = name,
                input = call.arguments.toString().ifBlank { "{}" },
                output = call.result ?: "",
                rawType = call.rawType.ifBlank { "tool_call" },
                approvalState = if (call.result == null) {
                    AiToolApprovalState.PENDING
                } else {
                    AiToolApprovalState.AUTO
                }
            )
        }
        return parts
    }

    fun bookResults(): List<AiMessagePart.BookResult> {
        val books = linkedMapOf<String, AiMessagePart.BookResult>()
        calls.values.mapNotNull { it.result }.forEach { result ->
            val root = runCatching {
                GSON.fromJson(result, JsonObject::class.java)
            }.getOrNull() ?: return@forEach
            root.getAsJsonArrayOrNull("books")?.forEach { element ->
                element.asJsonObjectOrNull()?.toBookResultPart()?.let {
                    books.putIfAbsent(it.bookUrl, it)
                }
            }
            root.getAsJsonObjectOrNull("book")?.toBookResultPart()?.let {
                books.putIfAbsent(it.bookUrl, it)
            }
        }
        return books.values.toList()
    }

    override fun toString(): String {
        return calls.values.joinToString("\n\n") { call ->
            buildString {
                append("Tool: ")
                append(call.name.ifBlank { call.rawType.ifBlank { call.id } })
                append('\n')
                append("ID: ")
                append(call.id)
                if (call.arguments.isNotBlank()) {
                    append('\n')
                    append(call.arguments)
                }
                call.result?.takeIf { it.isNotBlank() }?.let {
                    append('\n')
                    append("Result: ")
                    append(it.take(2000))
                }
            }
        }
    }
}

internal data class ToolCallTrace(
    val id: String,
    var rawType: String,
    var name: String = "",
    val arguments: StringBuilder = StringBuilder(),
    var result: String? = null
)

data class PendingToolRun(
    val conversationId: String?,
    val request: AiGenerateRequest,
    val fullText: String,
    val fullReasoning: String,
    val toolTrace: ToolTraceBuilder,
    val toolCalls: List<AiToolCall>,
    val assistantTextStart: Int,
    val round: Int,
    val parentMessageId: String? = null
)

// ---- Book result extraction helpers ----

internal fun JsonObject.toBookResultPart(): AiMessagePart.BookResult? {
    val bookUrl = string("bookUrl")?.takeIf { it.isNotBlank() } ?: return null
    return AiMessagePart.BookResult(
        bookUrl = bookUrl,
        name = string("name").orEmpty(),
        author = string("author").orEmpty(),
        origin = string("origin") ?: string("originName"),
        coverPath = string("coverPath") ?: string("coverUrl"),
        latestChapterTitle = string("latestChapterTitle"),
        currentChapterTitle = string("currentChapterTitle"),
        intro = string("intro")
    )
}

internal fun JsonObject.string(name: String): String? {
    return get(name)?.takeIf { !it.isJsonNull }?.asString
}

internal fun JsonObject.getAsJsonObjectOrNull(name: String): JsonObject? {
    return get(name)?.let { if (it.isJsonObject) it.asJsonObject else null }
}

internal fun JsonObject.getAsJsonArrayOrNull(name: String) = runCatching {
    get(name)?.takeIf { !it.isJsonNull && it.isJsonArray }?.asJsonArray
}.getOrNull()

internal fun com.google.gson.JsonElement.asJsonObjectOrNull(): JsonObject? {
    return takeIf { !it.isJsonNull && it.isJsonObject }?.asJsonObject
}

/** Truncate tool output to [AiChatGenerationUseCase.MAX_TOOL_OUTPUT_CHARS] to avoid overflowing context. */
internal fun String.truncateToolOutput(): String {
    if (length <= AiChatGenerationUseCase.MAX_TOOL_OUTPUT_CHARS) return this
    return take(AiChatGenerationUseCase.MAX_TOOL_OUTPUT_CHARS) +
        "\n\n[...truncated from ${length} chars]"
}
