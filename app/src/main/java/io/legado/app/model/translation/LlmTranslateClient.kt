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

    override suspend fun translateShortBatch(
        texts: List<String>,
        targetLanguage: String,
        provider: String,
        baseUrl: String,
        apiKey: String,
        model: String
    ): Result<Map<Int, String>> = withContext(Dispatchers.IO) {
        try {
            when (provider) {
                TranslationConfig.PROVIDER_GOOGLE -> translateShortBatchWithGoogle(texts, targetLanguage)
                TranslationConfig.PROVIDER_OPENAI -> translateShortBatchWithOpenAI(texts, targetLanguage, baseUrl, apiKey, model)
                else -> Result.failure(IllegalArgumentException("Unknown provider: $provider"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun translateShortBatchWithGoogle(texts: List<String>, targetLanguage: String): Result<Map<Int, String>> {
        if (texts.isEmpty()) return Result.success(emptyMap())

        val combinedText = texts.joinToString("\n")
        val encodedText = java.net.URLEncoder.encode(combinedText, "UTF-8")
        val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$targetLanguage&dj=1&dt=t&ie=UTF-8&q=$encodedText"

        val response = okHttpClient.newCallStrResponse {
            url(url)
        }

        if (!response.isSuccessful()) {
            return Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
        }

        return try {
            val json = GSON.fromJson(response.body, GoogleTranslateResponse::class.java)
            val translatedText = json.sentences.filter { it.trans != null }.joinToString("") { it.trans ?: "" }

            if (translatedText.isEmpty()) {
                return Result.failure(Exception("Empty translation result"))
            }

            // Google returns translations with newlines preserved between original texts
            val lines = translatedText.split("\n")
            val results = mutableMapOf<Int, String>()

            for (i in texts.indices) {
                if (i < lines.size) {
                    results[i] = lines[i]
                } else {
                    results[i] = texts[i]
                }
            }

            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun translateShortBatchWithOpenAI(
        texts: List<String>,
        targetLanguage: String,
        baseUrl: String,
        apiKey: String,
        model: String
    ): Result<Map<Int, String>> {
        if (baseUrl.isBlank() || apiKey.isBlank() || model.isBlank()) {
            return Result.failure(IllegalArgumentException("OpenAI configuration incomplete"))
        }

        // Build a batch prompt with indices
        val batchItems = texts.mapIndexed { index, text ->
            "[$index] $text"
        }.joinToString("\n")

        val systemPrompt = "You are a professional translator. Translate each text to $targetLanguage. " +
                "Return only translations in the same format: [index] translated_text. " +
                "Do not include any explanation or commentary."

        val requestBody = mapOf(
            "model" to model,
            "messages" to listOf(
                mapOf("role" to "system", "content" to systemPrompt),
                mapOf("role" to "user", "content" to "Translate these texts:\n$batchItems")
            ),
            "temperature" to 0.3
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
                val content = json.choices.firstOrNull()?.message?.content ?: ""
                val parsed = parseShortBatchResponse(content, texts.size)
                if (parsed.isNotEmpty()) {
                    Result.success(parsed)
                } else {
                    Result.failure(Exception("Failed to parse short batch response"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        } else {
            Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
        }
    }

    private fun parseShortBatchResponse(content: String, textCount: Int): Map<Int, String> {
        val result = mutableMapOf<Int, String>()
        // Parse lines like "[0] Translated text" or "[0]Translated text"
        val regex = Regex("\\[(\\d+)\\]\\s*(.+)")
        for (line in content.lines()) {
            val match = regex.find(line)
            if (match != null) {
                val index = match.groupValues[1].toIntOrNull()
                val translation = match.groupValues[2].trim()
                if (index != null && index in 0 until textCount && translation.isNotEmpty()) {
                    result[index] = translation
                }
            }
        }
        return result
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