package io.legado.app.feature.settings.ai.summary

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.AiPromptTemplate
import io.legado.app.domain.model.TranslationConstants

// M5-4b：从 `:app` 的 `io.legado.app.ui.config.ai.summary` 迁来。
//
// ⚠️ **唯一的结构性改动在 `AiSummaryConfigEffect`**：迁移前 VM 用
// `appCtx.getString(R.string.x)`就地取好文案，`ShowMessage` 直接携带成品 `String`；
// 共享层没有 `Context` ⇒ 照 M5-1c 的 about 那样改成「**枚举 + UI 侧查表**」
// （表在 `AiSummaryMessages.kt`，只有那个文件 import `Res`）。
//
// 因此 Effect 分成两个：
//   - `ShowMessage(AiSummaryMessage.*)`：文案来自本地化资源；
//   - `ShowRawMessage(text)`：**运行期**产生的文本（异常 message、以及迁移前就硬编码的
//     那句「加载章节摘要配置失败: …」）——它们本来就不在资源表里，保持原样直传。
//
// 这么分是为了与迁移前**逐字等价**：`save()` 失败时原文是
// `error.message ?: getString(ai_config_save_failed)`，即「有异常文案就用它，没有才回落到
// 资源文案」——两种来源必须同时存在，合成一个枚举参数会丢失这个 fallback 语义。

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

/** VM 会展示的、来自本地化资源的提示。查表见 [AiSummaryMessage.localizedText]。 */
enum class AiSummaryMessage {
    ResetPromptSuccess,
    SaveSuccess,
    SaveFailed,
}

sealed interface AiSummaryConfigEffect {
    data class ShowMessage(val message: AiSummaryMessage) : AiSummaryConfigEffect

    /** 运行期文本（异常信息 / 迁移前就硬编码的句子），不走资源表。 */
    data class ShowRawMessage(val text: String) : AiSummaryConfigEffect

    data object NavigateBack : AiSummaryConfigEffect
}
