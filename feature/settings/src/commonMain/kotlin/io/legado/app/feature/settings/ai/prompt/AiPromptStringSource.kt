package io.legado.app.feature.settings.ai.prompt

import io.legado.app.feature.settings.res.Res
import io.legado.app.feature.settings.res.ai_config_saved_success
import org.jetbrains.compose.resources.getString

/**
 * prompt 页 VM 所需的**资源字符串来源**（M5-4e）。
 *
 * ## 为什么需要这一层
 *
 * `AiPromptConfigViewModel` 需要把资源里的文案当**数据**用，而不是只展示：
 *   - 8 个任务类型的**默认提示词**要写回 gateway（重置单个 / 恢复全部）；
 *   - 「保存成功」要经 `Toaster` 发 **Toast**（页面里其它提示走 Snackbar，这个差异要保留）。
 *
 * 但 M5-4d 的探针量出：**VM 里直接调 `getString(Res.string.*)` 会让 VM 在
 * `androidHostTest` 下无法构造**（`MissingResourceException: Android context is not
 * initialized`）⇒ VM 不可测。本片之前就是这样，属登记过的待改项。
 *
 * ⇒ 把「VM 需要的字符串」抽成这个可注入的接口：**生产实现读 CMP 资源，测试实现给假值**。
 * VM 只依赖接口，因此可构造、可断言。
 *
 * ## 与「发枚举 + UI 侧查表」的分工
 *
 * | 场景 | 做法 |
 * |---|---|
 * | 文案只用于**展示** | VM 发枚举，UI 侧 `localizedText()`（`AboutMessage` / `AiSummaryMessage`） |
 * | 文案要作为**数据**用 | 注入这个接口——**不要**在 VM 里直接 `getString` |
 *
 * 判据写在 `.agents/skills/legado-kmp-migration/references/slice-checklist.md`。
 */
interface AiPromptStringSource {

    /** 某个任务类型的默认提示词（写回 gateway 用）。 */
    suspend fun defaultFor(task: AiPromptTask): String

    /** 「保存成功」的 Toast 文案。 */
    suspend fun savedMessage(): String
}

/**
 * 生产实现：读本模块的 composeResources。
 *
 * 由 `:app` 的 Koin module 绑定（`single<AiPromptStringSource> { composeResourcePromptStrings() }`）
 * ——放着让宿主显式装配，与其它平台能力一致。
 */
fun composeResourcePromptStrings(): AiPromptStringSource = object : AiPromptStringSource {
    override suspend fun defaultFor(task: AiPromptTask): String = task.defaultPrompt()
    override suspend fun savedMessage(): String = getString(Res.string.ai_config_saved_success)
}
