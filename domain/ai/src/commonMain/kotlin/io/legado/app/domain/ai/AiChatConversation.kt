package io.legado.app.domain.ai

import io.legado.app.core.platform.systemTimeMillis

/**
 * AI 会话（conversation）的领域模型（M4-4）。
 *
 * 字段集合、字段顺序、可变性、默认值与判等语义与 `:core:data` 的 Room 实体
 * `io.legado.app.data.entities.AiChatConversation` **逐字对齐**——本片只搬不重新设计。
 *
 * ⚠️ **字段一律 `val`**，判等是 data class 默认的**全字段**比较（与 M4-1 / M4-2 / M4-3 同侧，
 * 与 M3-6 `TagGroupRule` 的「全 `var` + 只按主键判等」相反）。判断依据同 M4-1：本域
 * **不进备份/恢复**（`Restore.kt` / `Backup.kt` 里没有 `ai_chat_conversations` /
 * `ai_chat_messages`），也没有「粘贴/导入进领域模型」的路径 ⇒ 不经 Gson 反射直写字段，
 * `val` 不会踩 M3-4 那个「`final` 字段在 JVM 反射下与 ART 行为不一致」的坑。
 *
 * ⚠️ **[reasoningLevel] 的默认值是字符串 `"auto"`，这是一个语义值而不是占位符**：它与
 * `:core:model` 的 `AiReasoningLevel.AUTO` 对应（调用方 `AiChatViewModel` 用
 * `level.name.lowercase()` 写入，所以库里存的是小写枚举名）。把它改成 `""` 或 `"AUTO"`
 * 会让「新建会话」的推理等级落到 UI 不认识的值上，而两侧都不会编译报错。
 *
 * ⚠️ **[createdAt] / [updatedAt] 的默认值取 `systemTimeMillis()`**，与实体一致。
 * `domain/ai` 从建模块起就 `implementation(":core:platform")`，引用它不越界——G2 的 pure 判据
 * 只看 import 前缀（`android.*` / `java.io.File` / `kotlin.jvm.*` / `androidx.*` / 三方实现库），
 * `io.legado.app.core.platform` 不在禁列。
 *
 * ⚠️ 字段名与**声明顺序**都与实体一致：它们是 Room 的列名，**不得重命名、不得重排**。
 */
data class AiChatConversation(
    val id: String,
    val title: String,
    val reasoningLevel: String = "auto",
    val modelProfileId: String? = null,
    val createdAt: Long = systemTimeMillis(),
    val updatedAt: Long = systemTimeMillis(),
)
