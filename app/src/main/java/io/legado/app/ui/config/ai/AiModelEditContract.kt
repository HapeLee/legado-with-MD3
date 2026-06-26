package io.legado.app.ui.config.ai

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.TranslationConstants
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class AiModelEditUiState(
    val providers: ImmutableList<AiModelProviderOptionUi> = persistentListOf(),
    val providerId: String? = null,
    val modelProfileId: String? = null,
    val modelName: String = "",
    val modelId: String = "",
    val contextWindow: Int = 0,
    val maxOutputTokens: Int = 0,
    val temperature: Float = TranslationConstants.DEFAULT_TEMPERATURE,
    val isSaving: Boolean = false,
    val isTesting: Boolean = false,
    val initialized: Boolean = false
)

@Stable
data class AiModelProviderOptionUi(
    val id: String,
    val name: String,
    val protocol: String,
    val baseUrl: String
)

sealed interface AiModelEditIntent {
    data class SelectProvider(val providerId: String) : AiModelEditIntent
    data class UpdateModelName(val value: String) : AiModelEditIntent
    data class UpdateModelId(val value: String) : AiModelEditIntent
    data class UpdateContextWindow(val value: Int) : AiModelEditIntent
    data class UpdateMaxOutputTokens(val value: Int) : AiModelEditIntent
    data class UpdateTemperature(val value: Float) : AiModelEditIntent
    data object Save : AiModelEditIntent
    data object TestConnection : AiModelEditIntent
}

sealed interface AiModelEditEffect {
    data class ShowMessage(val message: String) : AiModelEditEffect
    data object NavigateBack : AiModelEditEffect
}
