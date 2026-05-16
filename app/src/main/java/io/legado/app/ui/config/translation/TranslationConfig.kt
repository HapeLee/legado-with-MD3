package io.legado.app.ui.config.translation

import io.legado.app.constant.PreferKey
import io.legado.app.ui.config.prefDelegate

object TranslationConfig {

    var llmTranslateEnabled by prefDelegate(
        PreferKey.llmTranslateEnabled,
        false
    )

    var llmProvider by prefDelegate(
        PreferKey.llmProvider,
        "openai"
    )

    var llmBaseUrl by prefDelegate(
        PreferKey.llmBaseUrl,
        ""
    )

    var llmApiKey by prefDelegate(
        PreferKey.llmApiKey,
        ""
    )

    var llmModel by prefDelegate(
        PreferKey.llmModel,
        ""
    )

    var llmTargetLanguage by prefDelegate(
        PreferKey.llmTargetLanguage,
        "zh"
    )

    var llmMaxCharsPerChunk by prefDelegate(
        PreferKey.llmMaxCharsPerChunk,
        10000
    )

    var llmConcurrentChunks by prefDelegate(
        PreferKey.llmConcurrentChunks,
        1
    )

    var llmRetryCount by prefDelegate(
        PreferKey.llmRetryCount,
        2
    )

    var llmPrompt by prefDelegate(
        PreferKey.llmPrompt,
        DEFAULT_PROMPT
    )

    
    const val PROVIDER_OPENAI = "openai"
    const val PROVIDER_GOOGLE = "google"

    val providerDisplayNames = listOf("OpenAI", "Google Translate")
    val providerValues = listOf(PROVIDER_OPENAI, PROVIDER_GOOGLE)

    val targetLanguages = listOf(
        "zh" to "中文",
        "en" to "English",
        "ja" to "日本語",
        "ko" to "한국어",
        "fr" to "Français",
        "de" to "Deutsch",
        "es" to "Español",
        "ru" to "Русский",
        "ar" to "العربية"
    )

    const val DEFAULT_PROMPT = """You are a professional literary translator. Translate the following text accurately while:
1. Preserving the original paragraph count and order
2. Keeping all honorifics, numbers, and punctuation
3. Maintaining the literary style and tone
4. Not summarizing, condensing, or omitting any content
5. Translating only, no commentary or explanations

Original text:"""

}