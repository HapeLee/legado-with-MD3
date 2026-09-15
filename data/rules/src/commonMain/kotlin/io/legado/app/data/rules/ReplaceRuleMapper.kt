package io.legado.app.data.rules

import io.legado.app.data.entities.ReplaceRule as ReplaceRuleEntity
import io.legado.app.domain.rules.ReplaceRule

/**
 * Room 实体 `io.legado.app.data.entities.ReplaceRule` ↔ 领域模型
 * `io.legado.app.domain.rules.ReplaceRule` 的双向映射（M3-1）。
 *
 * 位置：映射器住 `:data:rules`（M3 模板：domain 只放模型/端口，实现与映射住 `data:<域>`）。
 * 公开可见是因为 `:app` 这个 composition root 也需要它：
 * - `AndroidReplaceRuleImportCompat`：`ReplaceAnalyzer`（住 `:core:data/androidMain`，
 *   依赖 jsonpath）返回的是实体，要在边界上转成领域模型；
 * - `AndroidReadBookReplaceSessionGateway`：`ReadBook` 的章节内容里存的是实体。
 * 这两处都是「契约的 Android 实现」，住 `:app` 是本仓既有模式
 * （与 `AndroidRuleTransferPlatform` / `AndroidBuiltInRulesImporter` 一致）。
 *
 * ⚠️ 逐字段显式赋值，**不用反射/序列化**，也不做默认值回退——映射必须是恒等的：
 * 两边 `equals`/`hashCode` 都只按 `id` 判等（见领域模型注释），所以任何"整对象比较"式的
 * 测试都发现不了漏映射的字段。`ReplaceRuleMapperTest` 因此逐字段断言。
 */
fun ReplaceRuleEntity.toDomain(): ReplaceRule = ReplaceRule(
    id = id,
    name = name,
    group = group,
    pattern = pattern,
    replacement = replacement,
    scope = scope,
    scopeTitle = scopeTitle,
    scopeContent = scopeContent,
    excludeScope = excludeScope,
    isEnabled = isEnabled,
    isRegex = isRegex,
    timeoutMillisecond = timeoutMillisecond,
    order = order,
)

fun ReplaceRule.toEntity(): ReplaceRuleEntity = ReplaceRuleEntity(
    id = id,
    name = name,
    group = group,
    pattern = pattern,
    replacement = replacement,
    scope = scope,
    scopeTitle = scopeTitle,
    scopeContent = scopeContent,
    excludeScope = excludeScope,
    isEnabled = isEnabled,
    isRegex = isRegex,
    timeoutMillisecond = timeoutMillisecond,
    order = order,
)
