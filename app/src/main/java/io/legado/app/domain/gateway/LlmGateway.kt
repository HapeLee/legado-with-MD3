package io.legado.app.domain.gateway

import io.legado.app.domain.model.DictPair
import io.legado.app.domain.model.RetryReason
import io.legado.app.domain.model.TranslationConstants

interface LlmGateway {
    suspend fun translate(
        text: String,
        targetLanguage: String,
        provider: String,
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String,
        temperature: Float = TranslationConstants.DEFAULT_TEMPERATURE,
        dictionaries: List<DictPair> = emptyList(),
        onUpdate: ((List<DictPair>) -> Unit)? = null,
        retryReason: RetryReason? = null
    ): Result<String>
}