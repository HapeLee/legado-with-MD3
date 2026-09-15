package io.legado.app.feature.replacerules

import io.legado.app.domain.rules.ReplaceRule

/**
 * 替换规则导入文本的**兼容解析**能力（平台契约）。
 *
 * 为什么需要这个 seam：替换规则的历史导入格式有两套键名——
 *   1. 标准格式：`ReplaceRule` 自己的字段（`pattern` / `replacement` / `isRegex` / `scope` …）；
 *   2. 旧版本 / 第三方备份格式：`regex` / `replaceSummary` / `useTo` / `enable` / `serialNumber`
 *      （见 `:core:data` 的 `ReplaceAnalyzer`）。
 *
 * 标准格式在共享层用 `JsonCodec` 就能解；旧格式的解析依赖 `com.jayway.jsonpath`
 * （JVM 三方库，见 `core/data/build.gradle.kts` 的注解），**只能由平台侧提供**。于是把它收成
 * 本契约：Android 实现（`AndroidReplaceRuleImportCompat`，住 `:app`）直接委托 `ReplaceAnalyzer`，
 * 与迁移前逐字同一份实现；desktop 侧待有需要时再提供实现。
 *
 * 与 `:core:platform` 那些契约的区别：本契约的签名里出现领域模型 `ReplaceRule`
 * （`:domain:rules`），而 `:core:platform` 在依赖图里低于它 ⇒ 放不进去。
 * 住在 Feature 模块内是因为**只有本 Feature 消费它**；实现由 `:app`（composition root）
 * 在 Koin 里绑定，模式与 `RuleTransferPlatform` / `BuiltInRulesImporter` 一致。
 * ⚠️ Android 实现（`AndroidReplaceRuleImportCompat`）拿到的是 `ReplaceAnalyzer` 解析出的
 * **Room 实体**，要在实现内部经 `:data:rules` 的映射器转成领域模型再返回。
 *
 * 失败语义：解析不了**必须抛异常**，因为 `RuleTransferUseCase.importSource` 依赖异常把导入
 * 状态置成 `Error`（与迁移前 `ReplaceAnalyzer` 的 `Result.getOrThrow()` 一致）。
 */
interface ReplaceRuleImportCompat {

    /** 顶层是**数组**的导入文本（逐条解析，任一条失败即整体失败）。 */
    fun parseRules(json: String): List<ReplaceRule>

    /** 顶层是**单个对象**的导入文本。 */
    fun parseRule(json: String): ReplaceRule
}
