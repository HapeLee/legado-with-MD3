package io.legado.app.data.rules

import io.legado.app.data.entities.DictRule as DictRuleEntity
import io.legado.app.domain.rules.DictRule

/**
 * Room 实体 `io.legado.app.data.entities.DictRule` ↔ 领域模型
 * `io.legado.app.domain.rules.DictRule` 的双向映射（M3-3）。
 *
 * 位置：映射器住 `:data:rules`（M3 模板：domain 只放模型/端口，实现与映射住 `data:<域>`）。
 * 公开可见是因为 `:app` 这个 composition root 也要用：
 * - `ui/association/ImportDictRuleViewModel.kt` / `ImportDictRuleDialog.kt`：老导入路径
 *   仍用 `GSON` 门面（JVM 侧）解析规则文本，边界上要转成领域模型再交给端口；
 * - `help/DefaultData.kt` / `help/storage/Restore.kt`：备份/内置数据走 GSON 门面按**实体**
 *   反序列化（格式 owner 不变，见下），回写 DAO 时不需要本映射器——它们直连 DAO，是
 *   留给 `data:database` 收口的既有债务。
 *
 * ⚠️ 逐字段显式赋值，**不用反射/序列化**，也不做默认值回退——映射必须是恒等的：
 * 两边 `equals`/`hashCode` 都只按 `name`（主键）判等，所以任何"整对象比较"式的测试都
 * 发现不了漏映射的字段。`DictRuleMapperTest` 因此逐字段断言。
 */
fun DictRuleEntity.toDomain(): DictRule = DictRule(
    name = name,
    urlRule = urlRule,
    showRule = showRule,
    enabled = enabled,
    sortNumber = sortNumber,
)

fun DictRule.toEntity(): DictRuleEntity = DictRuleEntity(
    name = name,
    urlRule = urlRule,
    showRule = showRule,
    enabled = enabled,
    sortNumber = sortNumber,
)
