package io.legado.app.feature.settings.ai.prompt

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.AiTaskType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

// M5-4c：从 `:app` 的 `io.legado.app.ui.config.ai.prompt` 迁来。
//
// ⚠️ **本页有两处结构性改动，都不是机械搬迁**：
//
// 1. **`AiPromptTaskItem` 不再存资源 id**。迁移前它是
//    `nameResId: Int` / `descResId: Int`，Screen 用 `stringResource(item.nameResId)` 取文案
//    ——**把 Android 资源 id 泄漏进了 UI 状态**。CMP 的资源不是 `Int`（是 `StringResource`），
//    而且共享层的状态里出现「平台资源句柄」本身就违反边界。
//    ⇒ 改成 `task: AiPromptTask` 枚举，文案由 **Screen 侧查表**（`AiPromptMessages.kt`）。
//    这条**编译期不会报**（`Int` 在 commonMain 里合法），只有真跑起来才会发现取不到文案。
//
// 2. **Effect 分成两个**（与 `ai/summary` 同一个理由）：`save()` / `reset()` 失败时原文是
//    `error.message ?: getString(ai_config_save_failed)`，即「有异常文案就用它，没有才回落
//    资源文案」。合成枚举的一个参数会丢掉这个 fallback。
//
//    另外：**保存成功的提示不走 Effect** —— 迁移前它是 `appCtx.toastOnUi(...)`（Toast），
//    而 `ShowMessage` 在 Screen 里是 Snackbar。为保持「Toast 而非 Snackbar」这个差异，
//    那一条改由 VM 注入的 `Toaster`（`:core:platform` 的既有共享契约）直接发。

/**
 * 可配置提示词的 AI 任务类型。
 *
 * 每个值绑定三条本地化文案（显示名 / 描述 / 默认提示词），查表见 [AiPromptMessages.kt]；
 * `taskType` 是写给 gateway 的键（沿用 `AiTaskType` 的字符串常量）。
 */
enum class AiPromptTask(val taskType: String) {
    Chat(AiTaskType.CHAT),
    TranslateChapter(AiTaskType.TRANSLATE_CHAPTER),
    SummarizeChapter(AiTaskType.SUMMARIZE_CHAPTER),
    CleanSelection(AiTaskType.CLEAN_SELECTION),
    TextFactory(AiTaskType.TEXT_FACTORY),
    AnalyzeSpeech(AiTaskType.ANALYZE_SPEECH),
    IdentifyCharacters(AiTaskType.IDENTIFY_CHARACTERS),
    BookshelfAutoGroup(AiTaskType.BOOKSHELF_AUTO_GROUP),
}

@Stable
data class AiPromptTaskItem(
    val task: AiPromptTask,
    val defaultPrompt: String,
    val currentPrompt: String
) {
    /** 写给 gateway 的键——由枚举携带，避免再存一份字符串。 */
    val taskType: String get() = task.taskType
}

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

/** 来自本地化资源的提示（查表见 `AiPromptMessages.kt`）。 */
enum class AiPromptMessage {
    ResetSuccess,
    RestoredAll,
    SaveFailed,
}

sealed interface AiPromptConfigEffect {
    data class ShowMessage(val message: AiPromptMessage) : AiPromptConfigEffect

    /** 运行期文本（异常 message）——不走资源表。 */
    data class ShowRawMessage(val text: String) : AiPromptConfigEffect
}
