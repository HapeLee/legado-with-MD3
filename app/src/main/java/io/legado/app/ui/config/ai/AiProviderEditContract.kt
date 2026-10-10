package io.legado.app.ui.config.ai

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.AiProtocol
import io.legado.app.domain.model.AiReasoningLevel
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
    val customHeaders: ImmutableList<AiProviderHeaderUi> = persistentListOf(),
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
data class AiProviderHeaderUi(
    val name: String = "",
    val value: String = ""
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
    data class UpdateCustomHeaders(val headers: ImmutableList<AiProviderHeaderUi>) : AiProviderEditIntent
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

    /** 本地网络权限授予后由界面触发，重跑因缺权限被打断的动作，避免再次进入申请分支。 */
    data object RetryAfterLocalNetworkPermission : AiProviderEditIntent
}

sealed interface AiProviderEditEffect {
    data class ShowMessage(val message: String) : AiProviderEditEffect
    data object NavigateBack : AiProviderEditEffect
    data object NavigateBackAfterDelete : AiProviderEditEffect

    /**
     * 供应商地址在局域网内，但系统尚未授予本地网络权限（Android 17+）。
     * 宿主申请权限，授权后回发 [AiProviderEditIntent.RetryAfterLocalNetworkPermission]；这不是失败，不提示。
     */
    data object RequestLocalNetworkPermission : AiProviderEditEffect
}
