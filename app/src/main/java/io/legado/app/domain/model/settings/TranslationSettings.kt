package io.legado.app.domain.model.settings

import io.legado.app.domain.model.TranslationConstants

data class TranslationSettings(
    val provider: String = TranslationConstants.PROVIDER_GOOGLE,
    val targetLanguage: String = "zh",
    val maxCharsPerChunk: Int = 10000,
    val concurrentChunks: Int = 1,
    val retryCount: Int = 2,
)
