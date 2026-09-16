package io.legado.app.data.ai

import io.legado.app.data.entities.AiArtifact as AiArtifactEntity
import io.legado.app.domain.ai.AiArtifact

/**
 * Room 实体 `io.legado.app.data.entities.AiArtifact` ↔ 领域模型
 * `io.legado.app.domain.ai.AiArtifact` 的双向映射（M4-3）。
 *
 * 位置：映射器住 `:data:ai`（M3/M4 模板：domain 只放模型/端口，实现与映射住 `data:<域>`）。
 *
 * ⚠️ **本片与 M4-1 / M4-2 同侧（全字段判等）**：实体与领域模型**都没有**重写
 * `equals` / `hashCode`（都是 data class 的全字段比较，与 M3-5 `RuleSub` 同侧、与 M3-6
 * `TagGroupRule` 相反）。所以映射用例**可以**用整对象 `assertEquals`——但仍需一条反向用例。
 *
 * ⚠️ **本域有三个可空字段**（`chapterIndex: Int?`、`output: String?`、`errorMessage: String?`），
 * 映射必须**原样搬运 `null`**，不得归一化成 `0` / `""`。`chapterIndex == null` 在 DAO 里是
 * 「这本产物不属于任何章节」的语义（`getCachedArtifact` 的 `chapterIndex` 参数还有一层
 * 「传 `null` = 不筛章节」的查询语义，但那是**参数**，不是这里的字段）。
 *
 * ⚠️ **`companion object` 的 `STATUS_*` 常量不在映射范围内**（常量不是字段，不参与
 * `data class` 的 `copy`/判等）。两侧常量此刻**并存且必须取值一致**（DAO 的 `@Query` 用
 * **实体**的 `STATUS_SUCCESS` 做字符串插值，UI 侧已改为引用**领域模型**的常量）
 * ⇒ `AiArtifactMapperTest` 有一条用例逐值比对四个常量。
 *
 * ⚠️ 逐字段显式赋值，**不用反射/序列化**，也不做默认值回退：映射必须是恒等的。字段顺序照实体写
 * （`id` / `taskType` / `bookUrl` / `chapterIndex` / `contentHash` / `promptHash` /
 * `modelProfileId` / `status` / `output` / `errorMessage` / `schemaVersion` /
 * `createdAt` / `updatedAt`），便于与实体声明逐行比对。
 */
fun AiArtifactEntity.toDomain(): AiArtifact = AiArtifact(
    id = id,
    taskType = taskType,
    bookUrl = bookUrl,
    chapterIndex = chapterIndex,
    contentHash = contentHash,
    promptHash = promptHash,
    modelProfileId = modelProfileId,
    status = status,
    output = output,
    errorMessage = errorMessage,
    schemaVersion = schemaVersion,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun AiArtifact.toEntity(): AiArtifactEntity = AiArtifactEntity(
    id = id,
    taskType = taskType,
    bookUrl = bookUrl,
    chapterIndex = chapterIndex,
    contentHash = contentHash,
    promptHash = promptHash,
    modelProfileId = modelProfileId,
    status = status,
    output = output,
    errorMessage = errorMessage,
    schemaVersion = schemaVersion,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
