package io.legado.app.data.rules

import io.legado.app.data.entities.HighlightTagRule as HighlightTagRuleEntity
import io.legado.app.domain.rules.HighlightTagRule

/**
 * Room 实体 `io.legado.app.data.entities.HighlightTagRule` ↔ 领域模型
 * `io.legado.app.domain.rules.HighlightTagRule` 的双向映射（M3-2）。
 *
 * 位置：映射器住 `:data:rules`（M3 模板：domain 只放模型/端口，实现与映射住 `data:<域>`）。
 * 公开可见是因为 `:app` 这个 composition root 也需要它：
 * - `help/book/BookExtensions.kt` 的 `parseHighlightedTags`：调用方 `BookInfoViewModel`
 *   拿到的是领域模型，而该函数住 `:app`（老 `ui/` 路径）且签名跟着切到领域模型；
 * - `help/storage/Restore.kt` / `Backup.kt`：备份文件走 GSON 门面按实体反序列化，
 *   边界上要转成领域模型（Restore）或从实体写出（Backup 仍直连 DAO，不经过本映射器）。
 *
 * ⚠️ 逐字段显式赋值，**不用反射/序列化**，也不做默认值回退——映射必须是恒等的：
 * 两边 `equals`/`hashCode` 都只按 `id` 判等（见领域模型注释），所以任何"整对象比较"式的
 * 测试都发现不了漏映射的字段。`HighlightTagRuleMapperTest` 因此逐字段断言。
 */
fun HighlightTagRuleEntity.toDomain(): HighlightTagRule = HighlightTagRule(
    id = id,
    title = title,
    pattern = pattern,
    enabled = enabled,
    order = order,
)

fun HighlightTagRule.toEntity(): HighlightTagRuleEntity = HighlightTagRuleEntity(
    id = id,
    title = title,
    pattern = pattern,
    enabled = enabled,
    order = order,
)
