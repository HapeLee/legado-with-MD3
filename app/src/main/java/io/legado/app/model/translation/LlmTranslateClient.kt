package io.legado.app.model.translation

import io.legado.app.domain.gateway.LlmGateway
import io.legado.app.help.http.addHeaders
import io.legado.app.help.http.newCallStrResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.postJson
import io.legado.app.ui.config.translation.TranslationConfig
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LlmTranslateClient : LlmGateway {

    override suspend fun translate(
        text: String,
        targetLanguage: String,
        provider: String,
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            when (provider) {
                TranslationConfig.PROVIDER_GOOGLE -> translateWithGoogle(text, targetLanguage)
                TranslationConfig.PROVIDER_OPENAI -> translateWithOpenAI(text, targetLanguage, baseUrl, apiKey, model, prompt)
                else -> Result.failure(IllegalArgumentException("Unknown provider: $provider"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun translateWithGoogle(text: String, targetLanguage: String): Result<String> {
        val encodedText = java.net.URLEncoder.encode(text, "UTF-8")
        val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$targetLanguage&dj=1&dt=t&ie=UTF-8&q=$encodedText"
        val response = okHttpClient.newCallStrResponse {
            url(url)
        }
        return if (response.isSuccessful()) {
            try {
                val json = GSON.fromJson(response.body, GoogleTranslateResponse::class.java)
                val translatedText = json.sentences.mapNotNull { it.trans }.joinToString("")
                if (translatedText.isNotEmpty()) {
                    Result.success(translatedText)
                } else {
                    Result.failure(Exception("Empty translation result"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        } else {
            Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
        }
    }

    private suspend fun translateWithOpenAI(
        text: String,
        targetLanguage: String,
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String
    ): Result<String> {
        if (baseUrl.isBlank() || apiKey.isBlank() || model.isBlank()) {
            return Result.failure(IllegalArgumentException("OpenAI configuration incomplete: baseUrl, apiKey, and model are required"))
        }
        val systemPrompt = "$prompt \n Target language: $targetLanguage"
        val requestBody = mapOf(
            "model" to model,
            "messages" to listOf(
                mapOf("role" to "system", "content" to systemPrompt),
                mapOf("role" to "user", "content" to "Translate the following text:\n\n$text")
            ),
            "temperature" to 0.8
        )
        val jsonBody = GSON.toJson(requestBody)
        val fullUrl = baseUrl.trimEnd('/') + "/v1/chat/completions"
        val response = okHttpClient.newCallStrResponse {
            url(fullUrl)
            postJson(jsonBody)
            addHeaders(mapOf(
                "Authorization" to "Bearer $apiKey",
                "Content-Type" to "application/json"
            ))
        }
        return if (response.isSuccessful()) {
            try {
                val json = GSON.fromJson(response.body, OpenAIResponse::class.java)
                val translatedText = json.choices.firstOrNull()?.message?.content
                if (translatedText != null) {
                    Result.success(translatedText)
                } else {
                    Result.failure(Exception("Empty translation result"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        } else {
            val errorMessage = "HTTP ${response.code()}: ${response.message()}"
            Result.failure(Exception(errorMessage))
        }
    }
}

data class GoogleTranslateResponse(
    val sentences: List<GoogleSentence>,
    val src: String?,
    val spell: GoogleSpell?
)

data class GoogleSentence(
    val trans: String?,
    val orig: String?,
    val backend: Int?
)

data class GoogleSpell(
    val spell: String?
)

data class OpenAIResponse(
    val choices: List<OpenAIChoice>
)

data class OpenAIChoice(
    val message: OpenAIMessage
)

data class OpenAIMessage(
    val content: String
)