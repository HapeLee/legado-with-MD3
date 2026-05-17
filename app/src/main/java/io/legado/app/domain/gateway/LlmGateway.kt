package io.legado.app.domain.gateway

import io.legado.app.model.translation.DictPair
import io.legado.app.model.translation.RetryReason

interface LlmGateway {
    suspend fun translate(
        text: String,
        targetLanguage: String,
        provider: String,
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String,
        dictionaries: List<DictPair> = emptyList(),
        onUpdate: ((List<DictPair>) -> Unit)? = null,
        retryReason: RetryReason? = null
    ): Result<String>
}