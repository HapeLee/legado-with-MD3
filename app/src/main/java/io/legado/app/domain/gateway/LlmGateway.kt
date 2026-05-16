package io.legado.app.domain.gateway

interface LlmGateway {
    suspend fun translate(
        text: String,
        targetLanguage: String,
        provider: String,
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String
    ): Result<String>
}