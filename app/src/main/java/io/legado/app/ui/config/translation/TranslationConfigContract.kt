package io.legado.app.ui.config.translation

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.settings.TranslationSettings

@Stable
data class TranslationConfigUiState(
    val settings: TranslationSettings = TranslationSettings(),
)

sealed interface TranslationConfigIntent {
    data class SetProvider(val value: String) : TranslationConfigIntent
    data class SetTargetLanguage(val value: String) : TranslationConfigIntent
    data class SetMaxCharsPerChunk(val value: Int) : TranslationConfigIntent
}

sealed interface TranslationConfigEffect
