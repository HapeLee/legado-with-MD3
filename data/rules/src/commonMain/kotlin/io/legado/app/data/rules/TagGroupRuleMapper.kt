package io.legado.app.data.rules

import io.legado.app.data.entities.TagGroupRule as TagGroupRuleEntity
import io.legado.app.domain.rules.TagGroupRule

/**
 * Room 实体 `io.legado.app.data.entities.TagGroupRule` ↔ 领域模型
 * `io.legado.app.domain.rules.TagGroupRule` 的双向映射（M3-6）。
 *
 * 位置：映射器住 `:data:rules`（M3 模板：domain 只放模型/端口，实现与映射住 `data:<域>`）。
 * 可见性与同域其余五个映射器（`ReplaceRuleMapper` / `HighlightTagRuleMapper` /
 * `DictRuleMapper` / `TxtTocRuleMapper` / `RuleSubMapper`）一致。
 *
 * ⚠️ **本片回到"逐字段映射、不能用整对象断言"的一侧**：`TagGroupRule` 的实体与领域模型都
 * **重写了 `equals`/`hashCode`，只按主键 `id` 判等**，所以
 * `assertEquals(entity, entity.toDomain().toEntity())` 在任何非 `id` 字段漏映射时都照样通过。
 * `TagGroupRuleMapperTest` 因此逐字段断言（失败信息也更精确）。这与 M3-5 的 `RuleSubMapper`
 * 相反——那一个两侧都是全字段判等的 data class，整对象断言才成立。别向上一片看齐。
 *
 * ⚠️ 逐字段显式赋值，**不用反射/序列化**，也不做默认值回退：映射必须是恒等的（四个字段都
 * 不可空，没有 `TxtTocRule.example` 那种 `null` 归一化的陷阱）。字段顺序也照实体写
 * （`id` / `pattern` / `groupName` / `order`），便于与实体声明逐行比对。
 */
fun TagGroupRuleEntity.toDomain(): TagGroupRule = TagGroupRule(
    id = id,
    pattern = pattern,
    groupName = groupName,
    order = order,
)

fun TagGroupRule.toEntity(): TagGroupRuleEntity = TagGroupRuleEntity(
    id = id,
    pattern = pattern,
    groupName = groupName,
    order = order,
)
