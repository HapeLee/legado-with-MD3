package io.legado.app.feature.settings.ai

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.AiProtocol
import io.legado.app.domain.model.AiReasoningLevel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

// M5-5c：从 `:app` 的 `io.legado.app.ui.config.ai` 迁来（**只改包名**，结构逐字一致）。
//
// 与 `AiConfigContract` / `AiModelEditContract` 一样：`AiProviderEditEffect.ShowMessage` 带
// **裸 `String`**，因为迁移前 VM 里那些提示多数是硬编码英文（"AI model saved" /
// "AI provider saved" / "Failed to save AI provider" …）。本片唯一改动的是其中**三条**
// 走 `appCtx.getString` 的（测试连接结果）⇒ 见 `AiProviderStringSource`。

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
    val temperature: Float = 0.3f,
    val reasoningLevel: AiReasoningLevel = AiReasoningLevel.MEDIUM
)

@Stable
data class AiProviderModelEditorUi(
    val modelProfileId: String? = null,
    val modelName: String = "",
    val modelId: String = "",
    val contextWindow: String = "",
    val maxOutputTokens: String = "",
    val temperature: String = "0.3",
    val reasoningLevel: AiReasoningLevel = AiReasoningLevel.MEDIUM
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
    data class UpdateEditingReasoningLevel(val value: AiReasoningLevel) : AiProviderEditIntent
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
