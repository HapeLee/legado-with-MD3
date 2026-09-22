package io.legado.app.feature.settings.ai

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

// M5-5a：从 `:app` 的 `io.legado.app.ui.config.ai` 迁来（**只改包名**，结构逐字一致）。
//
// ⚠️ `AiConfigEffect.ShowMessage` 携带的是**裸 `String`**，不是枚举 —— 与
// ai/summary、ai/prompt 都不同。原因是迁移前 VM 里那两条文案就是**硬编码英文**
// （`"Default AI model saved"` / `"Failed to save default AI model"`），根本不在资源表里。
// 本片保持原样：**硬编码是既有行为，不是本片引入的**，也不在本次改动范围内
// （改成资源是另一个决策，会让 4 个语言下的文案发生变化）。

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

sealed interface AiConfigIntent {
    data class SetDefaultModel(val modelProfileId: String) : AiConfigIntent
}

sealed interface AiConfigEffect {
    data class ShowMessage(val message: String) : AiConfigEffect
}
