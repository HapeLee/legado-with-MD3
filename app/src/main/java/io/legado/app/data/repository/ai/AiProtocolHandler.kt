package io.legado.app.data.repository.ai

import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.model.AiAvailableModel
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiGenerateResponse
import io.legado.app.domain.model.AiProviderConfig

/**
 * Protocol-specific handler for AI text generation.
 * Each implementation handles one or more protocol types (e.g. OpenAI Chat, Anthropic Messages).
 */
interface AiProtocolHandler {

    /** Protocol identifiers this handler supports (e.g. "openai_chat_completions"). */
    val protocols: Set<String>

    /** Single-shot text generation. */
    suspend fun generate(request: AiGenerateRequest): Result<AiGenerateResponse>

    /** Streaming text generation via SSE. */
    suspend fun stream(
        request: AiGenerateRequest,
        emitEvent: suspend (AiStreamEvent) -> Unit
    )

    /** Fetch available models from the provider. */
    suspend fun fetchModels(provider: AiProviderConfig): Result<List<AiAvailableModel>>
}
