package io.legado.app.domain.ai

import io.legado.app.core.platform.systemTimeMillis

/**
 * AI 长期记忆条目的领域模型（M4-2）。
 *
 * 字段集合、字段顺序、可变性、默认值与判等语义与 `:core:data` 的 Room 实体
 * `io.legado.app.data.entities.AiMemory` **逐字对齐**——本片只搬不重新设计。
 *
 * ⚠️ **本域没有 `id` 字段**：主键是复合的 `("conversationId", "key")`，`conversationId` 为空串
 * 表示全局记忆。所以「按主键判等 / 按全字段判等」这道 M3-5 与 M3-6 的分歧在这里**换了个样子**：
 * 模型是 data class 默认的**全字段**判等（与 M4-1 `AiPromptPreset` 同侧、与 M3-6 的
 * [io.legado.app.domain.rules.TagGroupRule] 相反），`updatedAt` 参与比较。别因为「复合主键里
 * 有两个字段」就顺手给它写一个按 `conversationId + key` 的 `equals`——那会让
 * `updatedAt` 的差异被吞掉，而 `upsert` 正是在改这个字段（见 `AiMemoryRepositoryImpl`）。
 *
 * ⚠️ **字段一律 `val`**（与 `TagGroupRule` 的全 `var` 相反）。判断依据与 M4-1 一致：本域
 * **不进备份/恢复**（`Restore.kt` / `Backup.kt` 里没有 `ai_memory`），也没有「粘贴/导入进领域
 * 模型」的路径——`AiToolRepository` 只在**出方向**用 GSON 把记忆序列化成 `Map` 喂给模型，
 * 不存在反序列化进本类的路径 ⇒ 不会经 Gson 反射直写字段，`val` 不会踩 M3-4 那个
 * 「`final` 字段在 JVM 反射下与 ART 行为不一致」的坑。照抄实体即可。
 *
 * ⚠️ [updatedAt] 的默认值取 `systemTimeMillis()`，与实体一致。注意它不是「随便一个默认值」：
 * `AiMemoryRepositoryImpl.upsert` 会在写入前用当前时间**覆盖**它（复制实体的行为），所以调用
 * 方传什么都不影响落库值——保留默认值的理由只是让「不传」的构造点也拿到合理值。
 * `domain/ai` 从建模块起就 `implementation(":core:platform")`，引用它不越界——G2 的 pure 判据
 * 只看 import 前缀（`android.*` / `java.io.File` / `kotlin.jvm.*` / `androidx.*` / 三方实现库），
 * `io.legado.app.core.platform` 不在禁列。
 *
 * ⚠️ 字段名与**声明顺序**都与实体一致：它们是 Room 的列名，且 `key` 是 SQL 关键字（DAO 里写作
 * `` `key` ``），改名会同时打断查询与索引，**不得重命名、不得重排**。
 */
data class AiMemory(
    val conversationId: String,
    val key: String,
    val value: String,
    val updatedAt: Long = systemTimeMillis(),
)
