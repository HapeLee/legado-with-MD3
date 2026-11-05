package io.legado.app.data.repository

import io.legado.app.data.repository.ai.AiProviderRegistry
import io.legado.app.data.repository.ai.AnthropicHandler
import io.legado.app.data.repository.ai.OpenAiChatHandler
import io.legado.app.data.repository.ai.OpenAiResponsesHandler
import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.model.AiAvailableModel
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiGenerateResponse
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiImageModels
import io.legado.app.domain.model.AiProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class AiTextRepositoryImpl : AiTextGateway {

    private val registry = AiProviderRegistry(
        handlers = listOf(
            OpenAiChatHandler(),
            OpenAiResponsesHandler(),
            AnthropicHandler()
        )
    )

    override suspend fun generate(request: AiGenerateRequest): Result<AiGenerateResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(request.model.provider.protocol !in AiProtocol.imageProtocols && request.model.capabilities.none { it in AiImageModels.outputCapabilities }) { "Use the image service for image output models" }
                registry.handlerFor(request.model.provider.protocol).generate(request)
            }.mapCatching { it.getOrThrow() }
        }

    override fun generateStream(request: AiGenerateRequest): Flow<AiStreamEvent> = flow {
        require(request.model.provider.protocol !in AiProtocol.imageProtocols && request.model.capabilities.none { it in AiImageModels.outputCapabilities }) { "Use the image service for image output models" }
        registry.handlerFor(request.model.provider.protocol).stream(request) { emit(it) }
    }.flowOn(Dispatchers.IO)

    override suspend fun fetchModels(provider: AiProviderConfig): Result<List<AiAvailableModel>> =
        withContext(Dispatchers.IO) {
            runCatching {
                registry.handlerFor(provider.protocol).fetchModels(provider).getOrThrow()
            }
        }
}
