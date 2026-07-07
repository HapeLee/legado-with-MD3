package io.legado.app.ui.config.ai

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.AiProtocol
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class AiProviderEditUiState(
    val providerPresets: ImmutableList<AiProviderPresetUi> = persistentListOf(),
    val fetchedModels: ImmutableList<AiFetchedModelUi> = persistentListOf(),
    val selectedProviderPresetId: String = "",
    val providerId: String? = null,
    val providerName: String = "OpenAI Compatible",
    val protocol: String = AiProtocol.OPENAI_CHAT_COMPLETIONS,
    val baseUrl: String = "",
    val modelsUrl: String = "",
    val apiKey: String = "",
    val providerModels: ImmutableList<AiProviderModelUi> = persistentListOf(),
    val editingModel: AiProviderModelEditorUi? = null,
    val isTesting: Boolean = false,
    val isSaving: Boolean = false,
    val isFetchingModels: Boolean = false,
    val initialized: Boolean = false
)

@Stable
data class AiProviderPresetUi(
    val id: String,
    val name: String,
    val protocol: String,
    val baseUrl: String,
    val modelsUrl: String,
    val modelName: String,
    val modelId: String
)

@Stable
data class AiFetchedModelUi(
    val id: String,
    val name: String,
    val contextWindow: Int = 0,
    val maxOutputTokens: Int = 0
)

@Stable
data class AiProviderModelUi(
    val modelProfileId: String,
    val providerId: String,
    val modelName: String,
    val modelId: String,
    val contextWindow: Int = 0,
    val maxOutputTokens: Int = 0,
    val temperature: Float = 0.3f
)

@Stable
data class AiProviderModelEditorUi(
    val modelProfileId: String? = null,
    val modelName: String = "",
    val modelId: String = "",
    val contextWindow: String = "",
    val maxOutputTokens: String = "",
    val temperature: String = "0.3"
)

sealed interface AiProviderEditIntent {
    data class ApplyProviderPreset(val id: String) : AiProviderEditIntent
    data class UpdateProviderName(val value: String) : AiProviderEditIntent
    data class UpdateProtocol(val value: String) : AiProviderEditIntent
    data class UpdateBaseUrl(val value: String) : AiProviderEditIntent
    data class UpdateModelsUrl(val value: String) : AiProviderEditIntent
    data class UpdateApiKey(val value: String) : AiProviderEditIntent
    data object AddModel : AiProviderEditIntent
    data class EditModel(val modelProfileId: String) : AiProviderEditIntent
    data object DismissModelEditor : AiProviderEditIntent
    data class UpdateEditingModelName(val value: String) : AiProviderEditIntent
    data class UpdateEditingModelId(val value: String) : AiProviderEditIntent
    data class UpdateEditingContextWindow(val value: String) : AiProviderEditIntent
    data class UpdateEditingMaxOutputTokens(val value: String) : AiProviderEditIntent
    data class UpdateEditingTemperature(val value: String) : AiProviderEditIntent
    data object SaveEditingModel : AiProviderEditIntent
    data object TestConnection : AiProviderEditIntent
    data object SaveProvider : AiProviderEditIntent
    data object SyncModels : AiProviderEditIntent
    data object DeleteProvider : AiProviderEditIntent
    data class DeleteModel(val modelProfileId: String) : AiProviderEditIntent
}

sealed interface AiProviderEditEffect {
    data class ShowMessage(val message: String) : AiProviderEditEffect
    data object NavigateBack : AiProviderEditEffect
    data object NavigateBackAfterDelete : AiProviderEditEffect
}
