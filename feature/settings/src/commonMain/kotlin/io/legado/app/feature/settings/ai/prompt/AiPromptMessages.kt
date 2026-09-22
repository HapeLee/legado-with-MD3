package io.legado.app.feature.settings.ai.prompt

import androidx.compose.runtime.Composable
import io.legado.app.feature.settings.res.Res
import io.legado.app.feature.settings.res.ai_config_save_failed
import io.legado.app.feature.settings.res.ai_prompt_default_analyze_speech
import io.legado.app.feature.settings.res.ai_prompt_default_bookshelf_auto_group
import io.legado.app.feature.settings.res.ai_prompt_default_chat
import io.legado.app.feature.settings.res.ai_prompt_default_clean
import io.legado.app.feature.settings.res.ai_prompt_default_identify_characters
import io.legado.app.feature.settings.res.ai_prompt_default_summary
import io.legado.app.feature.settings.res.ai_prompt_default_text_factory
import io.legado.app.feature.settings.res.ai_prompt_default_translate
import io.legado.app.feature.settings.res.ai_prompt_reset_success
import io.legado.app.feature.settings.res.ai_prompt_restored_all
import io.legado.app.feature.settings.res.ai_prompt_task_analyze_speech
import io.legado.app.feature.settings.res.ai_prompt_task_analyze_speech_desc
import io.legado.app.feature.settings.res.ai_prompt_task_bookshelf_auto_group
import io.legado.app.feature.settings.res.ai_prompt_task_bookshelf_auto_group_desc
import io.legado.app.feature.settings.res.ai_prompt_task_chat
import io.legado.app.feature.settings.res.ai_prompt_task_chat_desc
import io.legado.app.feature.settings.res.ai_prompt_task_clean
import io.legado.app.feature.settings.res.ai_prompt_task_clean_desc
import io.legado.app.feature.settings.res.ai_prompt_task_identify_characters
import io.legado.app.feature.settings.res.ai_prompt_task_identify_characters_desc
import io.legado.app.feature.settings.res.ai_prompt_task_summary
import io.legado.app.feature.settings.res.ai_prompt_task_summary_desc
import io.legado.app.feature.settings.res.ai_prompt_task_text_factory
import io.legado.app.feature.settings.res.ai_prompt_task_text_factory_desc
import io.legado.app.feature.settings.res.ai_prompt_task_translate
import io.legado.app.feature.settings.res.ai_prompt_task_translate_desc
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * [AiPromptTask] / [AiPromptMessage] → 本地化文案（M5-4c）。
 *
 * 与 `ai/summary` 的 `AiSummaryMessages.kt` 同一个模式：**只有本文件 import `Res`**，
 * 模块的公开契约（状态 / 意图 / 效果）零资源依赖。
 *
 * ## 为什么这一层在本页是**必需**的（不只是风格）
 *
 * 迁移前 `AiPromptTaskItem` 直接存 `nameResId: Int` / `descResId: Int`，Screen 用
 * `stringResource(item.nameResId)` 取文案。CMP 的资源不是 `Int` ⇒ 那两个字段**必须**换成
 * 别的东西，否则「能编译、跑起来取不到文案」。换成枚举后，文案只能由 UI 侧查表——
 * 就是本文件。
 *
 * ## 两套取值入口
 *
 * - [AiPromptTask.displayName] / [AiPromptTask.description]：`@Composable`，Screen 里用；
 * - [AiPromptTask.defaultPrompt] / [AiPromptMessage.localizedText]：`suspend`，VM 里用
 *   （默认提示词要**写回 gateway**，VM 必须拿到实际字符串）。
 */
@Composable
fun AiPromptTask.displayName(): String = stringResource(
    when (this) {
        AiPromptTask.Chat -> Res.string.ai_prompt_task_chat
        AiPromptTask.TranslateChapter -> Res.string.ai_prompt_task_translate
        AiPromptTask.SummarizeChapter -> Res.string.ai_prompt_task_summary
        AiPromptTask.CleanSelection -> Res.string.ai_prompt_task_clean
        AiPromptTask.TextFactory -> Res.string.ai_prompt_task_text_factory
        AiPromptTask.AnalyzeSpeech -> Res.string.ai_prompt_task_analyze_speech
        AiPromptTask.IdentifyCharacters -> Res.string.ai_prompt_task_identify_characters
        AiPromptTask.BookshelfAutoGroup -> Res.string.ai_prompt_task_bookshelf_auto_group
    }
)

@Composable
fun AiPromptTask.description(): String = stringResource(
    when (this) {
        AiPromptTask.Chat -> Res.string.ai_prompt_task_chat_desc
        AiPromptTask.TranslateChapter -> Res.string.ai_prompt_task_translate_desc
        AiPromptTask.SummarizeChapter -> Res.string.ai_prompt_task_summary_desc
        AiPromptTask.CleanSelection -> Res.string.ai_prompt_task_clean_desc
        AiPromptTask.TextFactory -> Res.string.ai_prompt_task_text_factory_desc
        AiPromptTask.AnalyzeSpeech -> Res.string.ai_prompt_task_analyze_speech_desc
        AiPromptTask.IdentifyCharacters -> Res.string.ai_prompt_task_identify_characters_desc
        AiPromptTask.BookshelfAutoGroup -> Res.string.ai_prompt_task_bookshelf_auto_group_desc
    }
)

suspend fun AiPromptTask.defaultPrompt(): String = getString(
    when (this) {
        AiPromptTask.Chat -> Res.string.ai_prompt_default_chat
        AiPromptTask.TranslateChapter -> Res.string.ai_prompt_default_translate
        AiPromptTask.SummarizeChapter -> Res.string.ai_prompt_default_summary
        AiPromptTask.CleanSelection -> Res.string.ai_prompt_default_clean
        AiPromptTask.TextFactory -> Res.string.ai_prompt_default_text_factory
        AiPromptTask.AnalyzeSpeech -> Res.string.ai_prompt_default_analyze_speech
        AiPromptTask.IdentifyCharacters -> Res.string.ai_prompt_default_identify_characters
        AiPromptTask.BookshelfAutoGroup -> Res.string.ai_prompt_default_bookshelf_auto_group
    }
)

suspend fun AiPromptMessage.localizedText(): String = getString(
    when (this) {
        AiPromptMessage.ResetSuccess -> Res.string.ai_prompt_reset_success
        AiPromptMessage.RestoredAll -> Res.string.ai_prompt_restored_all
        AiPromptMessage.SaveFailed -> Res.string.ai_config_save_failed
    }
)
