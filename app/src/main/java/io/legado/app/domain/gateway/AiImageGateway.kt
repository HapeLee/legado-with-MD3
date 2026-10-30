package io.legado.app.domain.gateway

import io.legado.app.domain.model.AiImageRequest
import io.legado.app.domain.model.AiImageResult

interface AiImageGateway {
    /** Exactly one paid request, no automatic protocol fallback or generation retry. */
    suspend fun generate(request: AiImageRequest): AiImageResult
}
