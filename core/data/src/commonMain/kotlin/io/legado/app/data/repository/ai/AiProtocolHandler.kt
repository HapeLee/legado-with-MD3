package io.legado.app.data.repository.ai

import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.model.AiAvailableModel
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiGenerateResponse
import io.legado.app.domain.model.AiProviderConfig

/**
 * Platform-neutral protocol contract for AI text providers.
 *
 * HTTP and SSE implementations remain platform-side; this contract only models
 * their request, response, streaming, and model-discovery behavior.
 */
interface AiProtocolHandler {
    /** Protocol identifiers this implementation accepts. */
    val protocols: Set<String>

    suspend fun generate(request: AiGenerateRequest): Result<AiGenerateResponse>

    suspend fun stream(
        request: AiGenerateRequest,
        emitEvent: suspend (AiStreamEvent) -> Unit,
    )

    suspend fun fetchModels(provider: AiProviderConfig): Result<List<AiAvailableModel>>
}
