package io.legado.app.domain.ai

import io.legado.app.core.platform.systemTimeMillis

/**
 * AI 会话消息（message）的领域模型（M4-4）。
 *
 * 字段集合、字段顺序、可变性、默认值与判等语义与 `:core:data` 的 Room 实体
 * `io.legado.app.data.entities.AiChatMessage` **逐字对齐**——本片只搬不重新设计。
 *
 * ⚠️ **字段一律 `val`**，判等是 data class 默认的**全字段**比较（与 M4-1 / M4-2 / M4-3 同侧）。
 * 本域不进备份/恢复、无反序列化进本类的路径 ⇒ 理由同 [AiChatConversation]。
 * 本域还有一处只按主键判等会**直接吃掉业务语义**的地方：分支切换靠 `upsert` 改写同一个 `id`
 * 的行的 [isSelected]（`AiChatRepositoryImpl.selectBranch`），全字段判等才能看出这个变化。
 *
 * ⚠️ **[partsJson] 是「消息分片的 JSON 字符串」，不是结构化字段——本片刻意保留字符串形态**。
 * 实体里它就是 Room 的一列（`partsJson: String`），由 `:core:data` 的 `AiChatRepository` 在写入前
 * 用 `AiMessagePartJson.encode(parts)` 编码。**不要**顺势把它改写成
 * `parts: List<AiMessagePart>`：
 * ① 本仓红线是「领域模型逐字照抄实体」，改写字段形态属于借搬迁之名做重新设计；
 * ② 现有消费方自己解码——`AiChatViewModel` 收到消息后第一件事是
 * `AiMessagePartJson.decode(msg.partsJson)`（它要按 UI 需要把分片重组成正文 / 推理 / 工具轨迹 /
 * 书目卡片四类块）；
 * ③ 改写会让映射不再是恒等的，mapper 的「往返无损」用例失去意义。
 * `AiMessagePart` / `AiMessagePartJson` 本身住 `:core:model`（**已经共享**），所以端口签名照旧可以
 * 收发 `List<AiMessagePart>`——那是**入参**，与这里存不存字符串无关。
 *
 * ⚠️ 三个「有默认值但不该被当成随便一个默认值」的字段：
 * - [isSelected] 默认 `true`：新落库的消息默认在选中的分支路径上（`observeSelectedMessages`
 *   只查 `isSelected = 1`，写错会让新消息在界面上直接不可见）；
 * - [branchIndex] 默认 `0`：同一 `parentMessageId` 下的第几条分支，`saveRegeneratedMessage`
 *   用 DAO 的 `countBranches(parentMessageId)` 决定这个值（从 0 起）；
 * - [parentMessageId] 默认 `null`：`null` 表示「用户发的根消息」，非空表示「AI 回复，可重新生成」。
 *   界面上「是否显示重新生成按钮」的判断正是 `parentMessageId != null`。
 *
 * ⚠️ 实体上还有 `@ColumnInfo(defaultValue = "0")`（挂在 [thinkingDuration] 上）：那是 **Room 的建表
 * 默认值**，用来让旧版本升级时不至于 `NULL`，与 Kotlin 默认值无关，领域模型**不复刻注解**。
 *
 * ⚠️ 字段名与**声明顺序**都与实体一致：它们是 Room 的列名，**不得重命名、不得重排**。
 */
data class AiChatMessage(
    val id: String,
    val conversationId: String,
    val role: String,
    val partsJson: String,
    val createdAt: Long = systemTimeMillis(),
    val branchIndex: Int = 0,
    val isSelected: Boolean = true,
    val parentMessageId: String? = null,
    val thinkingDuration: Int = 0,
)
