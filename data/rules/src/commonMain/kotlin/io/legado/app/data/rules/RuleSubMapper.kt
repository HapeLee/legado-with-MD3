package io.legado.app.data.rules

import io.legado.app.data.entities.RuleSub as RuleSubEntity
import io.legado.app.domain.rules.RuleSub

/**
 * Room 实体 `io.legado.app.data.entities.RuleSub` ↔ 领域模型
 * `io.legado.app.domain.rules.RuleSub` 的双向映射（M3-5）。
 *
 * 位置：映射器住 `:data:rules`（M3 模板：domain 只放模型/端口，实现与映射住 `data:<域>`）。
 * 可见性与同域其余四个映射器（`ReplaceRuleMapper` / `HighlightTagRuleMapper` /
 * `DictRuleMapper` / `TxtTocRuleMapper`）一致。本片 `:app` **不**需要边界映射——它的两个调用点
 * （`RuleSubViewModel` / `RuleSubScreen`）全程只见领域模型，`Restore.kt` 走的是实体，与领域
 * 模型无交集——所以没有前几片那种「composition root 也要用」的公开理由。
 *
 * ⚠️ 逐字段显式赋值，**不用反射/序列化**，也不做默认值回退：映射必须是恒等的。
 *
 * ⚠️ **与其余四片相反，本片可以用整对象 `assertEquals` 做映射断言**：`RuleSub` 的实体与领域
 * 模型都**没有**重写 `equals`，判等是 data class 的全字段比较（迁移前实体就是这样，本模型照抄），
 * 所以 `assertEquals(entity, entity.toDomain().toEntity())` 已经覆盖了所有字段。但
 * `RuleSubMapperTest` 仍然逐字段断言（失败信息更精确），并另加一条用例钉住「判等是全字段」这个
 * 语义本身——防止后来者"对齐前四片"给它补一个只按主键判等的 `equals`。
 *
 * ⚠️ [RuleSub.type] 是裸 `Int`：`RuleSubType` 的四个常量不参与映射（它们只是这个 `Int` 的
 * 取值说明），所以本文件不需要引用任何一侧的 `RuleSubType`。
 */
fun RuleSubEntity.toDomain(): RuleSub = RuleSub(
    id = id,
    name = name,
    url = url,
    type = type,
    customOrder = customOrder,
    autoUpdate = autoUpdate,
    update = update,
)

fun RuleSub.toEntity(): RuleSubEntity = RuleSubEntity(
    id = id,
    name = name,
    url = url,
    type = type,
    customOrder = customOrder,
    autoUpdate = autoUpdate,
    update = update,
)
