package io.legado.app.ui.config.ai

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class AiConfigUiState(
    val providers: ImmutableList<AiProviderListItemUi> = persistentListOf(),
    val models: ImmutableList<AiModelListItemUi> = persistentListOf(),
    val currentModelProfileId: String? = null,
    val currentModelName: String = "",
    val providerCount: Int = 0,
    val modelCount: Int = 0,
    val presetCount: Int = 0
)

@Stable
data class AiProviderListItemUi(
    val providerId: String,
    val providerName: String,
    val protocol: String,
    val baseUrl: String,
    val modelCount: Int,
    val enabled: Boolean,
    val models: ImmutableList<AiModelListItemUi> = persistentListOf()
)

@Stable
data class AiModelListItemUi(
    val providerId: String,
    val modelProfileId: String,
    val providerName: String,
    val protocol: String,
    val baseUrl: String,
    val modelName: String,
    val modelId: String,
    val contextWindow: Int,
    val maxOutputTokens: Int,
    val enabled: Boolean,
    val isCurrent: Boolean
)

sealed interface AiConfigIntent
{
    data class SetDefaultModel(val modelProfileId: String) : AiConfigIntent
}

sealed interface AiConfigEffect {
    data class ShowMessage(val message: String) : AiConfigEffect
}
