package io.legado.app.data.ai

import io.legado.app.data.entities.AiChatMessage as AiChatMessageEntity
import io.legado.app.domain.ai.AiChatMessage

/**
 * Room 实体 `io.legado.app.data.entities.AiChatMessage` ↔ 领域模型
 * `io.legado.app.domain.ai.AiChatMessage` 的双向映射（M4-4）。
 *
 * ⚠️ **`partsJson` 是字符串列，映射原样搬运，不在这里编解码**。消息分片的
 * `AiMessagePartJson.encode(parts)` 由实现（`AiChatRepositoryImpl`）在写入前调用——
 * 那是**存储形态**的职责；映射只负责在实体与领域模型之间搬同一个字符串。
 * 在这里顺手 `decode` 会让「往返无损」不再成立（实体 → 领域 → 实体 会得到不同的 JSON 文本）。
 *
 * ⚠️ **唯一的可空字段 `parentMessageId` 必须原样搬运 `null`**：`null` = 「用户发的根消息」，
 * 非空 = 「对某条消息的回复，可被重新生成」。界面上「是否显示重新生成按钮」正是
 * `parentMessageId != null`；归一化成空串会让**所有**根消息都长出重新生成按钮。
 *
 * ⚠️ 实体上的 `@ColumnInfo(defaultValue = "0")`（挂在 `thinkingDuration`）**不是字段**，
 * 不复刻：那是 Room 的建表默认值（旧库升级时不出现 `NULL`），与 Kotlin 默认值无关。
 *
 * ⚠️ 本文件同时承载集合重载 `toDomainList`——原因见 [AiChatConversationMapper] 的说明
 * （同文件放两个同名 `List<*>.toDomainList()` 会撞 JVM facade 签名，故一实体一文件）。
 */
fun AiChatMessageEntity.toDomain(): AiChatMessage = AiChatMessage(
    id = id,
    conversationId = conversationId,
    role = role,
    partsJson = partsJson,
    createdAt = createdAt,
    branchIndex = branchIndex,
    isSelected = isSelected,
    parentMessageId = parentMessageId,
    thinkingDuration = thinkingDuration,
)

fun AiChatMessage.toEntity(): AiChatMessageEntity = AiChatMessageEntity(
    id = id,
    conversationId = conversationId,
    role = role,
    partsJson = partsJson,
    createdAt = createdAt,
    branchIndex = branchIndex,
    isSelected = isSelected,
    parentMessageId = parentMessageId,
    thinkingDuration = thinkingDuration,
)

internal fun List<AiChatMessageEntity>.toDomainList(): List<AiChatMessage> = map { it.toDomain() }
