package io.legado.app.domain.ai

import io.legado.app.core.platform.systemTimeMillis

/**
 * AI 模型档案的领域模型（M4-5c）。
 *
 * 字段集合、字段顺序、可变性、默认值与判等语义与 `:core:data` 的 Room 实体
 * `io.legado.app.data.entities.AiModelProfile` **逐字对齐**——本片只搬不重新设计。
 *
 * ⚠️ **`capabilities` 是逗号分隔的字符串列，不是 `Set<String>`**。它在库里就是 `String`
 * （`defaultParamsJson` / `capabilities` 都参与「能力并集」的读写），把它在这里改成集合类型
 * 会逼着映射器做编解码，从而让「实体 → 领域 → 实体」的往返不再逐字节无损，
 * 也会与 DAO 的 `@Query`（按字符串比对）错位。**保持字符串**。
 *
 * ⚠️ **[defaultParamsJson] 是 `AiGenerationParams` 的 JSON 文本**，编解码由**实现侧**负责
 * （迁移前就是 `GSON.toJson(params)`，本片换成 `:core:platform` 的 `JsonCodec.toJson`，
 * 因为 `data/ai` 只有 commonMain、拿不到 `:core:data/src/androidMain` 的 `GSON` 门面）。
 * 映射器只搬字符串，不在这里解码——否则往返不再无损。
 *
 * ⚠️ **[id] 不是随机值**：它是 `AiProfileRepositoryImpl.stableModelId(providerId, modelId)`
 * 生成的 UUID v3 名称空间哈希（`"model_<hex>"`），用于实现「同一 providerId + 同一 modelId
 * ⇒ 同一档案」的稳定标识。**既有用户的档案 ID 已按这个规则落库** ⇒ 生成规则任何改动都会
 * 表现为「升级后模型列表空了 / 重复建了一整套档案」。见 `:core:platform` 的
 * `nameUuidFromBytes` 与 `Digest.md5` 的契约说明。
 *
 * ⚠️ **判等是全字段**（同 M4-1～M4-4），字段一律 `val`（理由见 [AiProviderProfile] 的 KDoc）。
 * 字段名与声明顺序都是 Room 的列名（且 `providerId` 参与一条索引），**不得重命名、不得重排**。
 */
data class AiModelProfile(
    val id: String,
    val providerId: String,
    val displayName: String,
    val modelId: String,
    val contextWindow: Int = 0,
    val maxOutputTokens: Int = 0,
    val capabilities: String = "",
    val defaultParamsJson: String? = null,
    val enabled: Boolean = true,
    val sortNumber: Int = 0,
    val createdAt: Long = systemTimeMillis(),
    val updatedAt: Long = systemTimeMillis()
)
