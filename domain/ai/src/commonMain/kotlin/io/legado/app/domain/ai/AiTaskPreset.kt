package io.legado.app.domain.ai

import io.legado.app.core.platform.systemTimeMillis

/**
 * AI 任务预设的领域模型（M4-5c）。
 *
 * 字段集合、字段顺序、可变性、默认值与判等语义与 `:core:data` 的 Room 实体
 * `io.legado.app.data.entities.AiTaskPreset` **逐字对齐**——本片只搬不重新设计。
 *
 * ⚠️ **[paramsJson] / [chunkPolicyJson] 都是 JSON 文本列**（分别是 `AiGenerationParams` 与
 * `AiTaskRuntimeOptions` 的序列化结果），编解码由**实现侧**负责，映射器只搬字符串。
 * 迁移前用 `GSON.toJson/fromJson`，本片换成 `:core:platform` 的 `JsonCodec`
 * （`data/ai` 只有 commonMain，拿不到 `:core:data/src/androidMain` 的 `GSON` 门面）；
 * 两者的 Gson 配置逐行相同（只差 `GSON` 额外注册的 7 个 rule 类型 deserializer，
 * 而这两个类型都不是 rule）⇒ 字节输出与解析行为一致。
 *
 * ⚠️ **三个默认值的语义要看清，它们不是随便给的**：
 * - `id` 有预设之外的取值——三个内建预设用**固定 id**（`"default_translate_chapter"` /
 *   `"default_summarize_chapter"` / `"default_chat"`），实现侧靠它们做 upsert 定位；
 * - [isDefault] 默认 `false`，但内建预设全部写成 `true`（DAO 的 `getDefaultPreset` 的
 *   `order by isDefault desc` 依赖它）；
 * - [enabled] 默认 `true`。
 *
 * ⚠️ **判等是全字段**（同 M4-1～M4-4），字段一律 `val`（理由见 [AiProviderProfile] 的 KDoc）。
 * 字段名与声明顺序都是 Room 的列名（`taskType` 与 `modelProfileId` 各参与一条索引），
 * **不得重命名、不得重排**。
 */
data class AiTaskPreset(
    val id: String,
    val taskType: String,
    val name: String,
    val modelProfileId: String,
    val promptTemplate: String,
    val paramsJson: String? = null,
    val chunkPolicyJson: String? = null,
    val enabled: Boolean = true,
    val isDefault: Boolean = false,
    val sortNumber: Int = 0,
    val createdAt: Long = systemTimeMillis(),
    val updatedAt: Long = systemTimeMillis()
)
