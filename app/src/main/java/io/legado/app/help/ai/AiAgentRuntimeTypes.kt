package io.legado.app.help.ai

import org.json.JSONObject

internal data class AiAgentToolCall(
    val id: String,
    val name: String,
    val arguments: String
)

internal data class AiAgentAssistantTurn(
    val content: String,
    val toolCalls: List<AiAgentToolCall>,
    val rawMessage: JSONObject,
    val reasoningContent: String = ""
)

internal data class AiToolExecutionOptions(
    val useAllTools: Boolean,
    val extraToolNames: Set<String> = emptySet()
)
