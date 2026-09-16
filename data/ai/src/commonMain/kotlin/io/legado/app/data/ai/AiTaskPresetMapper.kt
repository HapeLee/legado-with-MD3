package io.legado.app.data.ai

import io.legado.app.data.entities.AiTaskPreset as AiTaskPresetEntity
import io.legado.app.domain.ai.AiTaskPreset

/**
 * Room 实体 `io.legado.app.data.entities.AiTaskPreset` ↔ 领域模型
 * `io.legado.app.domain.ai.AiTaskPreset` 的双向映射（M4-5c）。
 *
 * ⚠️ **`paramsJson` 与 `chunkPolicyJson` 都是字符串列，映射原样搬运、不在这里编解码**：
 * 分别是 `AiGenerationParams` 与 `AiTaskRuntimeOptions` 的 JSON 文本。编解码由**实现侧**的
 * `parseParams` / `parseRuntimeOptions` / `JsonCodec.toJson` 负责。在这里解析会让
 * 「实体 → 领域 → 实体」的往返不再逐字节无损，而实体侧的 `paramsJson` 会被
 * `saveTaskPreset` 原样搬进新行。
 *
 * ⚠️ **两个可空字段必须原样搬运 `null`**：`paramsJson` 为 `null` 时实现侧 `parseParams` 给全默认；
 * `chunkPolicyJson` 为 `null` 时 `parseRuntimeOptions` 给全默认。两者都不得归一化成 `""`。
 *
 * ⚠️ **`taskType` / `modelProfileId` / `isDefault` / `enabled` / `sortNumber` 的取值参与查询语义**：
 * DAO 的 `getDefaultPreset` 是 `where taskType = :taskType and enabled = 1
 * order by isDefault desc, sortNumber, createdAt limit 1` ⇒ 映射必须恒等，写死或错位都会让
 * 「取默认预设」选错行（`AiProfileRepositoryImplTest` 里有一条用例针对 `isDefault` / `enabled`
 * 的取值，`AiTaskPresetMapperTest` 则逐字段断言）。
 *
 * ⚠️ 本文件同时承载集合重载 `toDomainList`——原因见 [AiProviderProfileMapper] 的说明。
 */
fun AiTaskPresetEntity.toDomain(): AiTaskPreset = AiTaskPreset(
    id = id,
    taskType = taskType,
    name = name,
    modelProfileId = modelProfileId,
    promptTemplate = promptTemplate,
    paramsJson = paramsJson,
    chunkPolicyJson = chunkPolicyJson,
    enabled = enabled,
    isDefault = isDefault,
    sortNumber = sortNumber,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun AiTaskPreset.toEntity(): AiTaskPresetEntity = AiTaskPresetEntity(
    id = id,
    taskType = taskType,
    name = name,
    modelProfileId = modelProfileId,
    promptTemplate = promptTemplate,
    paramsJson = paramsJson,
    chunkPolicyJson = chunkPolicyJson,
    enabled = enabled,
    isDefault = isDefault,
    sortNumber = sortNumber,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun List<AiTaskPresetEntity>.toDomainList(): List<AiTaskPreset> = map { it.toDomain() }
