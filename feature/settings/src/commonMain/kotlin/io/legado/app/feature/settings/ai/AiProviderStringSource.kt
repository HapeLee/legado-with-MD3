package io.legado.app.feature.settings.ai

import io.legado.app.feature.settings.res.Res
import io.legado.app.feature.settings.res.ai_test_failed
import io.legado.app.feature.settings.res.ai_test_success_no_models
import io.legado.app.feature.settings.res.ai_test_success_with_models
import org.jetbrains.compose.resources.getString

/**
 * `AiProviderEditViewModel` 所需的**资源字符串来源**（M5-5c）。
 *
 * 与 `AiPromptStringSource`（M5-4e）是同一个模式、同一个理由：这个 VM 迁移前用
 * `appCtx.getString(R.string.*)` 拼「测试连接」的结果提示（3 处）。共享层没有 `Context`，
 * 而 M5-4d 的探针已经量出**不能**改成在 VM 里调 `getString`——那会让 VM 在
 * `androidHostTest` 下无法构造（`MissingResourceException: Android context is not initialized`），
 * 也就是**不可测**。
 *
 * ⇒ 抽成可注入的接口：**生产实现读 CMP 资源，测试实现给假值**。
 *
 * ## 与「发枚举 + UI 侧查表」的分工（同 M5-4e）
 *
 * | 场景 | 做法 |
 * |---|---|
 * | 文案只用于**展示** | VM 发枚举，UI 侧 `localizedText()`（`AboutMessage` / `AiSummaryMessage`） |
 * | 文案要作为**数据**用 | 注入本接口——**不要**在 VM 里直接 `getString` |
 *
 * 本页这三条属于后者：它们不是「页面上某处显示的一段文字」，而是 VM 在**业务分支里拼出来的
 * 结果**（`fetchModels` 返回了几条 / 失败原因附加在提示后面）⇒ VM 必须拿到字符串本身。
 *
 * 判据写在 `.agents/skills/legado-kmp-migration/references/slice-checklist.md`。
 */
interface AiProviderStringSource {

    /** 测试连接成功、但没有取到任何模型。 */
    suspend fun testSuccessNoModels(): String

    /** 测试连接成功，取到 [count] 个模型（带格式参数）。 */
    suspend fun testSuccessWithModels(count: Int): String

    /** 测试连接的兜底失败文案（真实错误会以 `"$failMsg: $errorMsg"` 追加在后面）。 */
    suspend fun testFailed(): String
}

/**
 * 生产实现：读本模块的 composeResources。
 *
 * 由 `:app` 的 Koin module 绑定
 * （`single<AiProviderStringSource> { composeResourceProviderStrings() }`）
 * ——放着让宿主显式装配，与 `AiPromptStringSource` 一致。
 */
fun composeResourceProviderStrings(): AiProviderStringSource = object : AiProviderStringSource {
    override suspend fun testSuccessNoModels(): String =
        getString(Res.string.ai_test_success_no_models)

    override suspend fun testSuccessWithModels(count: Int): String =
        getString(Res.string.ai_test_success_with_models, count)

    override suspend fun testFailed(): String = getString(Res.string.ai_test_failed)
}
