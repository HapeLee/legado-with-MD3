package io.legado.app.data.ai

import io.legado.app.data.entities.AiMemory as AiMemoryEntity
import io.legado.app.domain.ai.AiMemory

/**
 * Room 实体 `io.legado.app.data.entities.AiMemory` ↔ 领域模型
 * `io.legado.app.domain.ai.AiMemory` 的双向映射（M4-2）。
 *
 * 位置：映射器住 `:data:ai`（M3/M4 模板：domain 只放模型/端口，实现与映射住 `data:<域>`）。
 *
 * ⚠️ **本片与 M4-1 同侧（全字段判等）**：实体与领域模型**都没有**重写 `equals` / `hashCode`
 * （都是 data class 的全字段比较，与 M3-5 的 `RuleSub` 同侧、与 M3-6 的 `TagGroupRule` 相反）。
 * 所以映射用例**可以**用整对象 `assertEquals`——但那还不够，仍需一条反向用例钉住
 * 「`updatedAt` 不同的两条记忆不相等」，否则后来者给模型补一个「按复合主键判等」的 `equals`
 * 时用例照样全绿（本域主键是 `conversationId` + `key`，很容易被误当成判等依据）。
 *
 * ⚠️ 逐字段显式赋值，**不做归一化**：`conversationId` 为空串表示「全局记忆」（DAO 的查询写的是
 * `WHERE conversationId = ''`），映射必须原样搬运——把空串归一化成 `null`、或反过来，都会让
 * 全局记忆被静默改写成会话记忆。**不用反射/序列化，也不做默认值回退**：映射必须是恒等的。
 *
 * ⚠️ 字段顺序照实体写（`conversationId` / `key` / `value` / `updatedAt`），便于与实体声明逐行
 * 比对。`key` 是 SQL 关键字（DAO 里写作 `` `key` ``），但 Kotlin 侧就是 `key`，不要改名。
 */
fun AiMemoryEntity.toDomain(): AiMemory = AiMemory(
    conversationId = conversationId,
    key = key,
    value = value,
    updatedAt = updatedAt,
)

fun AiMemory.toEntity(): AiMemoryEntity = AiMemoryEntity(
    conversationId = conversationId,
    key = key,
    value = value,
    updatedAt = updatedAt,
)
