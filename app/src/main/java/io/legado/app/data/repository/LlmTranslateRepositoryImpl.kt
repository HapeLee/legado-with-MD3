package io.legado.app.data.repository

import androidx.annotation.Keep
import io.legado.app.domain.gateway.LlmGateway
import io.legado.app.domain.model.DictPair
import io.legado.app.domain.model.RetryReason
import io.legado.app.domain.model.TranslationConstants
import io.legado.app.domain.model.TranslationConstants.OUTPUT_FORMAT
import io.legado.app.help.http.addHeaders
import io.legado.app.help.http.newCallStrResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.postJson
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LlmTranslateRepositoryImpl : LlmGateway {

    /**
     * Get display name for a language code, e.g., "zh" -> "简体中文"
     */
    private fun getLanguageDisplayName(code: String): String {
        return TranslationConstants.targetLanguages.find { it.first == code }?.second ?: code
    }

    override suspend fun translate(
        text: String,
        targetLanguage: String,
        provider: String,
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String,
        temperature: Float,
        dictionaries: List<DictPair>,
        onUpdate: ((List<DictPair>) -> Unit)?,
        retryReason: RetryReason?
    ): Result<String> = withContext(Dispatchers.IO) {
        // 如果目标语言是英语，且文本已经是英文（英文字符及标点占比超过80%），跳过翻译直接返回原文
        if (targetLanguage == "en" && isMostlyEnglish(text)) {
            return@withContext Result.success(text)
        }

        // 如果目标语言是其他语言（如中文），且文本已经是该语言，跳过翻译直接返回原文
        if (targetLanguage == "zh" && isMostlyChinese(text)) {
            return@withContext Result.success(text)
        }

        try {
            when (provider) {
                TranslationConstants.PROVIDER_GOOGLE -> translateWithGoogle(text, targetLanguage)
                TranslationConstants.PROVIDER_OPENAI -> translateWithOpenAI(
                    text,
                    targetLanguage,
                    baseUrl,
                    apiKey,
                    model,
                    prompt,
                    temperature,
                    dictionaries,
                    onUpdate,
                    retryReason
                )

                else -> Result.failure(IllegalArgumentException("Unknown provider: $provider"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 判断文本是否大部分为英文（英文字母及标点符号占比超过80%）
     */
    private fun isMostlyEnglish(text: String): Boolean {
        if (text.isEmpty()) return false
        val englishChars =
            text.count { it in 'A'..'Z' || it in 'a'..'z' || it in ".,!?;:'\"-()[]{}–—…" }
        return englishChars.toDouble() / text.length > 0.8
    }

    /**
     * 判断文本是否大部分为中文（中文及中文标点占比超过80%）
     */
    private fun isMostlyChinese(text: String): Boolean {
        if (text.isEmpty()) return false
        val chinesePunctuation = "。，！？；：“”‘’（）【】《》——…"
        val chineseChars = text.count {
            it in '一'..'鿿' || it in chinesePunctuation
        }
        return chineseChars.toDouble() / text.length > 0.8
    }

    /**
     * 如果翻译结果是中文，删除其中英文含量超过80%的段落（LLM可能生成中英对照内容）
     */
    private fun filterHighEnglishParagraphs(text: String): String {
        val paragraphs = text.split("\n")
        val filtered = paragraphs.filter { paragraph ->
            !isMostlyEnglish(paragraph)
        }
        return filtered.joinToString("\n")
    }

    private suspend fun translateWithGoogle(text: String, targetLanguage: String): Result<String> {
        val encodedText = java.net.URLEncoder.encode(text, "UTF-8")
        val url =
            "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$targetLanguage&dj=1&dt=t&ie=UTF-8&q=$encodedText"
        val response = okHttpClient.newCallStrResponse {
            url(url)
        }
        return if (response.isSuccessful()) {
            try {
                val json = GSON.fromJson(response.body, GoogleTranslateResponse::class.java)
                val translatedText = json?.sentences?.mapNotNull { it.trans }?.joinToString("") ?: ""
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
        prompt: String,
        temperature: Float,
        dictionaries: List<DictPair>,
        onUpdate: ((List<DictPair>) -> Unit)?,
        retryReason: RetryReason?
    ): Result<String> {
        if (baseUrl.isBlank() || apiKey.isBlank() || model.isBlank()) {
            return Result.failure(IllegalArgumentException("OpenAI configuration incomplete: baseUrl, apiKey, and model are required"))
        }

        // Build dictionary instruction for consistent terminology
        val dictionaryInstruction = buildDictionaryInstruction(dictionaries)

        val systemPrompt =
            buildSystemPrompt(prompt, targetLanguage, dictionaryInstruction, OUTPUT_FORMAT)
        val requestBody = mapOf(
            "model" to model,
            "messages" to listOf(
                mapOf("role" to "system", "content" to systemPrompt),
                mapOf("role" to "user", "content" to "Translate the following text:\n\n$text")
            ),
            "temperature" to temperature.coerceIn(
                TranslationConstants.MIN_TEMPERATURE,
                TranslationConstants.MAX_TEMPERATURE
            )
        )
        val jsonBody = GSON.toJson(requestBody)
        val fullUrl = baseUrl.trimEnd('/') + "/v1/chat/completions"
        val response = okHttpClient.newCallStrResponse {
            url(fullUrl)
            postJson(jsonBody)
            addHeaders(
                mapOf(
                    "Authorization" to "Bearer $apiKey",
                    "Content-Type" to "application/json"
                )
            )
        }
        return if (response.isSuccessful()) {
            try {
                val json = GSON.fromJson(response.body, OpenAIResponse::class.java)
                val rawContent = json?.choices?.firstOrNull()?.message?.content
                if (rawContent != null) {
                    // Parse the output with [dictionary] and [result] sections
                    val parseResult = parseLlmOutput(rawContent, dictionaries)

                    // Report extracted pairs for dictionary update (max 10)
                    if (parseResult.extractedPairs.isNotEmpty()) {
                        val limitedPairs = parseResult.extractedPairs.take(10)
                        onUpdate?.invoke(limitedPairs)
                    }

                    // Always return the result, even if dictionary parsing failed
                    val finalText = if (targetLanguage == "zh") {
                        filterHighEnglishParagraphs(parseResult.translatedText)
                    } else {
                        parseResult.translatedText
                    }
                    Result.success(finalText)
                } else {
                    Result.failure(Exception("Empty translation result"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        } else {
            val errorMsg = "HTTP ${response.code()}: ${response.message()}"
            Result.failure(Exception(errorMsg))
        }
    }

    /**
     * Result of parsing LLM output
     */
    data class ParseOutputResult(
        val translatedText: String,
        val extractedPairs: List<DictPair>
    )

    /**
     * Parse LLM output that should contain [dictionary] and [result] sections.
     * Fallback: if parsing fails or format is wrong, still return the result part.
     * Don't use regex for parsing.
     */
    private fun parseLlmOutput(
        rawOutput: String,
        existingDictionaries: List<DictPair>
    ): ParseOutputResult {
        val existingOriginals = existingDictionaries.map { it.original }.toSet()

        var dictionarySection: String? = null
        var resultSection: String? = null

        val lines = rawOutput.split('\n')
        var currentSection: String? = null

        for (line in lines) {
            val trimmedLine = line.trim()

            // Detect section header
            when {
                trimmedLine.startsWith("[dictionary]", ignoreCase = true) -> {
                    currentSection = "dictionary"
                }

                trimmedLine.startsWith("[result]", ignoreCase = true) -> {
                    currentSection = "result"
                }

                currentSection == "dictionary" -> {
                    dictionarySection = (dictionarySection ?: "") + line + "\n"
                }

                currentSection == "result" -> {
                    resultSection = (resultSection ?: "") + line + "\n"
                }
            }
        }

        // Extract pairs from dictionary section
        val extractedPairs = mutableListOf<DictPair>()
        if (dictionarySection != null) {
            extractedPairs.addAll(parseDictionarySection(dictionarySection, existingOriginals))
        }

        // Determine translated text
        val translatedText = when {
            resultSection != null -> resultSection.trim()
            else -> rawOutput.trim() // ultimate fallback: return raw output
        }

        return ParseOutputResult(translatedText, extractedPairs)
    }

    /**
     * Parse dictionary section line by line without regex.
     * Format: "Original -> Translation" or "Original: Translation"
     */
    private fun parseDictionarySection(
        section: String,
        existingOriginals: Set<String>
    ): List<DictPair> {
        val pairs = mutableListOf<DictPair>()
        val lines = section.split('\n')

        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) continue

            // Skip section headers
            if (trimmedLine.startsWith("[") || trimmedLine.startsWith(
                    "dictionary",
                    ignoreCase = true
                )
            ) {
                continue
            }

            // Try to find separator: "->" or ":"
            val separator = when {
                trimmedLine.contains(" -> ") -> " -> "
                trimmedLine.contains(" ->") -> " ->"
                trimmedLine.contains("-> ") -> "-> "
                trimmedLine.contains("->") -> "->"
                trimmedLine.contains(" : ") -> " : "
                trimmedLine.contains(": ") -> ": "
                trimmedLine.contains(" :") -> " :"
                trimmedLine.contains(":") -> ":"
                else -> null
            }

            if (separator != null) {
                val parts = trimmedLine.split(separator, limit = 2)
                if (parts.size == 2) {
                    val original = parts[0].trim()
                    val translation = parts[1].trim()

                    // Skip if already in existing dictionary
                    if (original !in existingOriginals && original.isNotEmpty() && translation.isNotEmpty()) {
                        pairs.add(DictPair(original, translation))
                        // Max 10 pairs
                        if (pairs.size >= 10) break
                    }
                }
            }
        }

        return pairs
    }

    /**
     * Build a dictionary instruction string for consistent terminology.
     */
    private fun buildDictionaryInstruction(dictionaries: List<DictPair>): String {
        if (dictionaries.isEmpty()) return ""

        val terms = dictionaries.joinToString("\n") { "${it.original} -> ${it.translation}" }
        return """

Terminology Dictionary (use these exact translations):
$terms
"""
    }

    /**
     * Build the full system prompt with dictionary instructions.
     */
    private fun buildSystemPrompt(
        prompt: String,
        targetLanguage: String,
        dictionaryInstruction: String,
        outputFormat: String,
    ): String {
        return buildString {
            append(prompt)
            append("\n Target language: ").append(getLanguageDisplayName(targetLanguage))
            if (dictionaryInstruction.isNotEmpty()) {
                append(dictionaryInstruction)
            }
            append("\n ").append(outputFormat)
        }
    }
}

@Keep
data class GoogleTranslateResponse(
    val sentences: List<GoogleSentence>,
    val src: String?,
    val spell: GoogleSpell?
)

@Keep
data class GoogleSentence(
    val trans: String?,
    val orig: String?,
    val backend: Int?
)

@Keep
data class GoogleSpell(
    val spell: String?
)

@Keep
data class OpenAIResponse(
    val choices: List<OpenAIChoice>
)

@Keep
data class OpenAIChoice(
    val message: OpenAIMessage
)

@Keep
data class OpenAIMessage(
    val content: String
)
