package io.legado.app.domain.gateway

import io.legado.app.domain.model.AiToolCall
import io.legado.app.domain.model.AiToolDefinition
import io.legado.app.domain.model.AiToolResult

interface AiToolGateway {
    fun availableTools(): List<AiToolDefinition>
    fun requiresConfirmation(toolName: String): Boolean
    suspend fun execute(call: AiToolCall): AiToolResult
}
