package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.gateway.AiToolGateway
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiToolContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class AiToolAwareGenerationUseCase(
    private val aiTextGateway: AiTextGateway,
    private val aiToolGateway: AiToolGateway,
) {

    suspend fun generate(request: AiGenerateRequest): String {
        val output = StringBuilder()
        generateStream(request).collect { event ->
            if (event is AiStreamEvent.Content) {
                output.append(event.text)
            }
        }
        return output.toString().trim()
            .ifEmpty { error("AI returned empty text") }
    }

    fun generateStream(request: AiGenerateRequest): Flow<AiStreamEvent> = flow {
        var currentRequest = request.withReadOnlyTools()
        while (true) {
            val toolTrace = ToolTraceBuilder()
            val roundContent = StringBuilder()
            toolTrace.beginResponse()

            aiTextGateway.generateStream(currentRequest).collect { event ->
                when (event) {
                    is AiStreamEvent.Content -> {
                        roundContent.append(event.text)
                        emit(event)
                    }

                    is AiStreamEvent.Reasoning -> emit(event)
                    is AiStreamEvent.ToolCallDelta -> {
                        toolTrace.append(event)
                        emit(event)
                    }
                }
            }

            val toolCalls = toolTrace.pendingToolCalls()
            if (toolCalls.isEmpty()) {
                return@flow
            }

            val toolResultMessages = toolCalls.map { toolCall ->
                val result = aiToolGateway.execute(toolCall)
                AiMessage(
                    role = AiMessageRole.TOOL,
                    content = result.content.truncateToolOutput(),
                    toolCallId = result.callId,
                    name = result.name,
                )
            }
            currentRequest = currentRequest.copy(
                messages = currentRequest.messages +
                    AiMessage(
                        role = AiMessageRole.ASSISTANT,
                        content = roundContent.toString(),
                        toolCalls = toolCalls,
                    ) +
                    toolResultMessages,
            )
        }
    }

    private fun AiGenerateRequest.withReadOnlyTools(): AiGenerateRequest {
        val readOnlyTools = aiToolGateway.availableTools()
            .filterNot { aiToolGateway.requiresConfirmation(it.name) }
        if (readOnlyTools.isEmpty()) return this
        return copy(
            messages = buildList {
                add(TOOL_CONTEXT_MESSAGE)
                toolContext?.toMessage()?.let { add(it) }
                addAll(messages)
            },
            tools = readOnlyTools,
        )
    }

    private fun AiToolContext.toMessage(): AiMessage? {
        val lines = buildList {
            bookUrl?.takeIf { it.isNotBlank() }?.let { add("bookUrl: $it") }
            bookName?.takeIf { it.isNotBlank() }?.let { add("bookName: $it") }
            chapterIndex?.let { add("chapterIndex: $it") }
            chapterTitle?.takeIf { it.isNotBlank() }?.let { add("chapterTitle: $it") }
        }
        if (lines.isEmpty()) return null
        return AiMessage(
            role = AiMessageRole.SYSTEM,
            content = "Current local book context for read-only tools:\n" +
                lines.joinToString("\n") +
                "\nWhen calling book tools, prefer these exact identifiers unless the user explicitly asks for another book.",
        )
    }

    companion object {
        const val CACHE_PROMPT_VERSION = "tool-aware-generation-v1"

        private val TOOL_CONTEXT_MESSAGE = AiMessage(
            role = AiMessageRole.SYSTEM,
            content = "Read-only local book tools are available. Use them when needed to inspect the current book, list chapters, search cached chapter text by character or plot keyword, read cached neighboring chapters for continuity, or look up saved character profiles, relationships, world-book entries, and outlines. Use tools silently; the final response must still follow the original task output format.",
        )
    }
}
