package io.legado.app.data.contentprocess

import io.legado.app.data.entities.BookContentProcess as BookContentProcessEntity
import io.legado.app.domain.contentprocess.BookContentProcess

/**
 * Room 实体 `io.legado.app.data.entities.BookContentProcess` ↔ 领域模型
 * `io.legado.app.domain.contentprocess.BookContentProcess` 的双向映射（M4-8）。
 *
 * ⚠️ **本片在「可以用整对象断言」的一侧**：实体与领域模型都没有 `equals` / `hashCode`
 * 覆写（全字段判等，与 M3-5 / M4-1 / M4-6 / M4-7 同侧）。仍配反向用例。
 *
 * ⚠️ 逐字段显式赋值、不做归一化：可空字段（`chapterIndex` / `styleJson` / `aiArtifactId` /
 * `sourceContentHash`）的 `null` 原样搬运；三个 JSON 串（`anchorJson` / `actionJson` /
 * `styleJson`）不解析、不重排。
 *
 * ⚠️⚠️ **companion 常量不在映射里**，但在领域模型上有一份**必须与实体逐值一致的副本**
 * （实体那侧被 DAO 的 SQL 插值引用）。它们的等价性由 `BookContentProcessMapperTest` 的
 * `constantsMatchEntityAndLiterals` 一条用例守护 —— 见 [BookContentProcess] 的 KDoc。
 */
fun BookContentProcessEntity.toDomain(): BookContentProcess = BookContentProcess(
    id = id,
    bookUrl = bookUrl,
    chapterIndex = chapterIndex,
    kind = kind,
    stage = stage,
    target = target,
    anchorJson = anchorJson,
    actionJson = actionJson,
    styleJson = styleJson,
    source = source,
    aiArtifactId = aiArtifactId,
    sourceContentHash = sourceContentHash,
    enabled = enabled,
    sortOrder = sortOrder,
    status = status,
    schemaVersion = schemaVersion,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun BookContentProcess.toEntity(): BookContentProcessEntity = BookContentProcessEntity(
    id = id,
    bookUrl = bookUrl,
    chapterIndex = chapterIndex,
    kind = kind,
    stage = stage,
    target = target,
    anchorJson = anchorJson,
    actionJson = actionJson,
    styleJson = styleJson,
    source = source,
    aiArtifactId = aiArtifactId,
    sourceContentHash = sourceContentHash,
    enabled = enabled,
    sortOrder = sortOrder,
    status = status,
    schemaVersion = schemaVersion,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun List<BookContentProcessEntity>.toDomainList(): List<BookContentProcess> = map { it.toDomain() }
