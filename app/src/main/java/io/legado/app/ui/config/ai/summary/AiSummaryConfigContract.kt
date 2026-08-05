package io.legado.app.ui.config.ai.summary

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.AiPromptTemplate
import io.legado.app.domain.model.TranslationConstants

@Stable
sealed interface AiSummaryConfigDialog {
    @Stable
    data class EditPrompt(val currentPrompt: String) : AiSummaryConfigDialog
}

@Stable
data class AiSummaryConfigUiState(
    val loading: Boolean = true,
    val promptTemplate: String = AiPromptTemplate.DEFAULT_CHAPTER_SUMMARY,
    val temperature: Float = TranslationConstants.DEFAULT_TEMPERATURE,
    val maxOutputTokens: Int = 0,
    val defaultPrompt: String = AiPromptTemplate.DEFAULT_CHAPTER_SUMMARY,
    val initialized: Boolean = false,
    val activeDialog: AiSummaryConfigDialog? = null
)

sealed interface AiSummaryConfigIntent {
    data class UpdatePrompt(val prompt: String) : AiSummaryConfigIntent
    data class OpenPromptDialog(val currentPrompt: String) : AiSummaryConfigIntent
    data class UpdateDialogPrompt(val prompt: String) : AiSummaryConfigIntent
    data object CloseDialog : AiSummaryConfigIntent
    data class UpdateTemperature(val temperature: Float) : AiSummaryConfigIntent
    data class UpdateMaxOutputTokens(val tokens: Int) : AiSummaryConfigIntent
    data object ResetPrompt : AiSummaryConfigIntent
    data object Save : AiSummaryConfigIntent
}

sealed interface AiSummaryConfigEffect {
    data class ShowMessage(val message: String) : AiSummaryConfigEffect
    data object NavigateBack : AiSummaryConfigEffect
}
