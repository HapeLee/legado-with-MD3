package io.legado.app.ui.config.ai.prompt

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.AiPromptTemplate
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.TranslationConstants
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class AiPromptTaskItem(
    val taskType: String,
    val nameResId: Int,
    val descResId: Int,
    val defaultPrompt: String,
    val currentPrompt: String
)

@Stable
data class AiPromptConfigUiState(
    val loading: Boolean = true,
    val items: ImmutableList<AiPromptTaskItem> = persistentListOf(),
    val activeDialog: AiPromptConfigDialog? = null
)

@Stable
sealed interface AiPromptConfigDialog {
    data class EditPrompt(val taskType: String, val currentPrompt: String) : AiPromptConfigDialog
    data object RestoreAllConfirm : AiPromptConfigDialog
}

sealed interface AiPromptConfigIntent {
    data class OpenPromptDialog(val taskType: String, val currentPrompt: String) :
        AiPromptConfigIntent

    data class UpdateDialogPrompt(val prompt: String) : AiPromptConfigIntent
    data object CloseDialog : AiPromptConfigIntent
    data class SavePrompt(val taskType: String, val prompt: String) : AiPromptConfigIntent
    data class ResetPrompt(val taskType: String) : AiPromptConfigIntent
    data object OpenRestoreAllDialog : AiPromptConfigIntent
    data object RestoreAllDefaults : AiPromptConfigIntent
}

sealed interface AiPromptConfigEffect {
    data class ShowMessage(val message: String) : AiPromptConfigEffect
}
