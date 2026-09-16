package io.legado.app.data.ai

import io.legado.app.data.entities.AiChatConversation as AiChatConversationEntity
import io.legado.app.domain.ai.AiChatConversation

/**
 * Room 实体 `io.legado.app.data.entities.AiChatConversation` ↔ 领域模型
 * `io.legado.app.domain.ai.AiChatConversation` 的双向映射（M4-4）。
 *
 * 位置：映射器住 `:data:ai`（M3/M4 模板：domain 只放模型/端口，实现与映射住 `data:<域>`）。
 *
 * ⚠️ **本片与 M4-1 / M4-2 / M4-3 同侧（全字段判等）**：实体与领域模型**都没有**重写
 * `equals` / `hashCode`（data class 的全字段比较）⇒ 映射用例**可以**用整对象 `assertEquals`，
 * 但仍需逐字段断言（失败信息更精确）与反向用例。
 *
 * ⚠️ **唯一的可空字段 `modelProfileId` 必须原样搬运 `null`**：`null` 表示「这条会话没有绑定
 * 模型档案」。归一化成空串会让「回落到默认模型」的判断（`modelProfileId.isNullOrBlank()` 一类）
 * 静默改变行为。
 *
 * ⚠️ **本域的集合重载住本文件，而不是像 [AiArtifactMapper] 那样住 `*RepositoryImpl.kt`**：
 * 本域有**两个**实体，若把两条 `List<*>.toDomainList()` 放进同一个文件（无论哪个文件），
 * 它们会编译成同一个 JVM facade 类里的同名同形方法（泛型擦除后都是 `toDomainList(List)`），
 * 构成 platform declaration clash。**一实体一文件的拆分是为了绕开这个签名冲突**——
 * 仓内 `data/rules` 的六个域也都是「一个 `toDomainList` 一个文件」，所以这同时回到了既有惯例。
 */
fun AiChatConversationEntity.toDomain(): AiChatConversation = AiChatConversation(
    id = id,
    title = title,
    reasoningLevel = reasoningLevel,
    modelProfileId = modelProfileId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun AiChatConversation.toEntity(): AiChatConversationEntity = AiChatConversationEntity(
    id = id,
    title = title,
    reasoningLevel = reasoningLevel,
    modelProfileId = modelProfileId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun List<AiChatConversationEntity>.toDomainList(): List<AiChatConversation> = map { it.toDomain() }
