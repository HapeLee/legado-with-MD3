package io.legado.app.feature.settings.ai.summary

import io.legado.app.feature.settings.res.Res
import io.legado.app.feature.settings.res.ai_config_save_failed
import io.legado.app.feature.settings.res.ai_config_saved_success
import io.legado.app.feature.settings.res.ai_prompt_reset_success
import org.jetbrains.compose.resources.getString

/**
 * [AiSummaryMessage] → 本地化文案（M5-4b）。
 *
 * 与 `:feature:about` 的 `AboutMessage.localizedText()` 是**同一个模式**（M5-1c-2 定的）：
 * 迁移前 VM 用 `appCtx.getString(R.string.x)` 就地取好文案，共享层没有 `Context`
 * ⇒ 契约改成枚举、由 UI 侧查表，**只有本文件 import `Res`**，模块的公开契约零资源依赖。
 *
 * 返回 `String` 而不是 `StringResource`：后者会让 `org.jetbrains.compose.resources`
 * 出现在公开 API 的签名里，从而必须把该依赖从 `implementation` 升成 `api`；返回 `String`
 * 则把资源依赖完全封闭在模块内部。
 *
 * ⚠️ 调用点必须在**协程**里（`getString` 是 `suspend`）——本模块的 Screen 在
 * `LaunchedEffect` 里收集 Effect，天然满足。
 */
suspend fun AiSummaryMessage.localizedText(): String = getString(
    when (this) {
        AiSummaryMessage.ResetPromptSuccess -> Res.string.ai_prompt_reset_success
        AiSummaryMessage.SaveSuccess -> Res.string.ai_config_saved_success
        AiSummaryMessage.SaveFailed -> Res.string.ai_config_save_failed
    }
)
