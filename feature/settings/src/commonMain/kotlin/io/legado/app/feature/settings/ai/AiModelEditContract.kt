package io.legado.app.feature.settings.ai

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.domain.model.TranslationConstants
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

// M5-5b：从 `:app` 的 `io.legado.app.ui.config.ai` 迁来（**只改包名**，结构逐字一致）。
//
// 与 `AiConfigContract` 一样：`AiModelEditEffect.ShowMessage` 带**裸 `String`**，
// 因为迁移前 VM 里那 4 条提示就是硬编码英文（"Default AI model saved" /
// "Failed to save AI model" / "AI connection test succeeded" / "AI connection test failed"）
// ⇒ 原样保留。

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
    val reasoningLevel: AiReasoningLevel = AiReasoningLevel.MEDIUM,
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
    data class UpdateReasoningLevel(val value: AiReasoningLevel) : AiModelEditIntent
    data object Save : AiModelEditIntent
    data object TestConnection : AiModelEditIntent
}

sealed interface AiModelEditEffect {
    data class ShowMessage(val message: String) : AiModelEditEffect
    data object NavigateBack : AiModelEditEffect
}
