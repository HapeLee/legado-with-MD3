package io.legado.app.data.ai

import io.legado.app.data.entities.AiModelProfile as AiModelProfileEntity
import io.legado.app.domain.ai.AiModelProfile

/**
 * Room 实体 `io.legado.app.data.entities.AiModelProfile` ↔ 领域模型
 * `io.legado.app.domain.ai.AiModelProfile` 的双向映射（M4-5c）。
 *
 * ⚠️ **`capabilities` 与 `defaultParamsJson` 都是字符串列，映射原样搬运、不在这里编解码**：
 * `capabilities` 是逗号分隔的能力名，`defaultParamsJson` 是 `AiGenerationParams` 的 JSON 文本。
 * 在这里顺手解析会让「实体 → 领域 → 实体」的往返不再逐字节无损（键序、数字定型、空白都可能变），
 * 而下游拿这串文本只是为了透传。编解码是**实现侧**的职责。
 *
 * ⚠️ **唯一的可空字段 `defaultParamsJson` 必须原样搬运 `null`**：`null` = 「还没有参数档案」，
 * 实现侧的 `parseParams(null)` 会给出全默认的 `AiGenerationParams()`；归一化成 `""` 虽然走同一条
 * 分支（`isNullOrBlank()`），但映射必须恒等，别在这里做等价化。
 *
 * ⚠️ 本文件同时承载集合重载 `toDomainList`——原因见 [AiProviderProfileMapper] 的说明
 * （一实体一文件是为了绕开 JVM facade 签名冲突）。
 *
 * ⚠️ **[id] 是稳定的 UUID v3 派生物**（`"model_<hex>"`，见领域模型的 KDoc），
 * 映射必须原样搬运，**不得重算**。
 */
fun AiModelProfileEntity.toDomain(): AiModelProfile = AiModelProfile(
    id = id,
    providerId = providerId,
    displayName = displayName,
    modelId = modelId,
    contextWindow = contextWindow,
    maxOutputTokens = maxOutputTokens,
    capabilities = capabilities,
    defaultParamsJson = defaultParamsJson,
    enabled = enabled,
    sortNumber = sortNumber,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun AiModelProfile.toEntity(): AiModelProfileEntity = AiModelProfileEntity(
    id = id,
    providerId = providerId,
    displayName = displayName,
    modelId = modelId,
    contextWindow = contextWindow,
    maxOutputTokens = maxOutputTokens,
    capabilities = capabilities,
    defaultParamsJson = defaultParamsJson,
    enabled = enabled,
    sortNumber = sortNumber,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun List<AiModelProfileEntity>.toDomainList(): List<AiModelProfile> = map { it.toDomain() }
