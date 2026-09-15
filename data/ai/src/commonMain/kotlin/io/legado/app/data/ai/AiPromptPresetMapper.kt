package io.legado.app.data.ai

import io.legado.app.data.entities.AiPromptPreset as AiPromptPresetEntity
import io.legado.app.domain.ai.AiPromptPreset

/**
 * Room 实体 `io.legado.app.data.entities.AiPromptPreset` ↔ 领域模型
 * `io.legado.app.domain.ai.AiPromptPreset` 的双向映射（M4-1）。
 *
 * 位置：映射器住 `:data:ai`（M3/M4 模板：domain 只放模型/端口，实现与映射住 `data:<域>`）。
 *
 * ⚠️ **本片在"可以用整对象断言"的一侧**：实体与领域模型**都没有**重写 `equals`/`hashCode`
 * （都是全字段判等的 data class，与 M3-5 的 `RuleSub` 同侧、与 M3-6 的 `TagGroupRule`
 * 相反）。所以 `AiPromptPresetMapperTest` 里整对象 `assertEquals` 是成立的——但那还不够，
 * 仍需一条**反向用例**钉住「仅 `name` 不同的两条预设不相等」，否则后来者给模型补一个
 * id-only 的 `equals` 时用例照样全绿。别向上一片看齐。
 *
 * ⚠️ 逐字段显式赋值，**不用反射/序列化**，也不做默认值回退：映射必须是恒等的。字段顺序照
 * 实体写（`id` / `taskType` / `name` / `instruction` / `enabled` / `builtIn` / `sortNumber`
 * / `createdAt` / `updatedAt`），便于与实体声明逐行比对。
 *
 * ⚠️ 无 `null` 归一化陷阱：`title` 之类可空字段在本域不存在——只有 `id` / `taskType` /
 * `name` / `instruction` 四个非空串是必填（它们在构造参数里没有默认值，漏传即编译错误），
 * 其余五个都有默认值，映射时原样搬运。
 */
fun AiPromptPresetEntity.toDomain(): AiPromptPreset = AiPromptPreset(
    id = id,
    taskType = taskType,
    name = name,
    instruction = instruction,
    enabled = enabled,
    builtIn = builtIn,
    sortNumber = sortNumber,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun AiPromptPreset.toEntity(): AiPromptPresetEntity = AiPromptPresetEntity(
    id = id,
    taskType = taskType,
    name = name,
    instruction = instruction,
    enabled = enabled,
    builtIn = builtIn,
    sortNumber = sortNumber,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
