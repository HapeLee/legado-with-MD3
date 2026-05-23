package io.legado.app.ui.config.translation

import io.legado.app.constant.PreferKey
import io.legado.app.domain.model.TranslationConstants
import io.legado.app.ui.config.prefDelegate

object TranslationConfig {

    var llmTranslateEnabled by prefDelegate(
        PreferKey.llmTranslateEnabled,
        false
    )

    var llmProvider by prefDelegate(
        PreferKey.llmProvider,
        "google"
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
        TranslationConstants.DEFAULT_PROMPT
    )

    // Delegate constants to domain layer
    const val PROVIDER_OPENAI = TranslationConstants.PROVIDER_OPENAI
    const val PROVIDER_GOOGLE = TranslationConstants.PROVIDER_GOOGLE
    val providerDisplayNames get() = TranslationConstants.providerDisplayNames
    val providerValues get() = TranslationConstants.providerValues
    val targetLanguages get() = TranslationConstants.targetLanguages
    const val DEFAULT_PROMPT = TranslationConstants.DEFAULT_PROMPT
    const val OUTPUT_FORMAT = TranslationConstants.OUTPUT_FORMAT
}
