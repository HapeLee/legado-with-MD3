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

    /**
     * Translate a batch of short texts (for metadata like book names, authors).
     * Returns a map of index to translated text.
     */
    suspend fun translateShortBatch(
        texts: List<String>,
        targetLanguage: String,
        provider: String,
        baseUrl: String,
        apiKey: String,
        model: String
    ): Result<Map<Int, String>>
}