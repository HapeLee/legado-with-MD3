package io.legado.app.data.marking

import io.legado.app.data.entities.BookMarking as BookMarkingEntity
import io.legado.app.domain.marking.BookMarking

/**
 * Room 实体 `io.legado.app.data.entities.BookMarking` ↔ 领域模型
 * `io.legado.app.domain.marking.BookMarking` 的双向映射（M4-6）。
 *
 * 位置：映射器住 `:data:marking`（M3/M4 模板：domain 只放模型/端口，实现与映射住 `data:<域>`）。
 *
 * ⚠️ **本片在「可以用整对象断言」的一侧**：实体与领域模型**都没有**重写 `equals` / `hashCode`
 * （都是 data class 的全字段比较，与 M3-5 的 `RuleSub`、M4-1 的 `AiPromptPreset` 同侧，
 * 与 M3-6 `TagGroupRule` 的 id-only 判等相反）。所以 `BookMarkingMapperTest` 里整对象
 * `assertEquals` 是成立的 —— 但仍配了一条反向用例，见下。
 *
 * ⚠️ 逐字段显式赋值，**不做归一化**，也不用反射/序列化：
 *  - [BookMarkingEntity.chapterIndex] 与 [BookMarkingEntity.styleJson] 是**可空**字段，
 *    `null` 必须原样搬运；把 `styleJson` 归一成 `""` 会让「无样式」变成「空样式」。
 *  - `bookName` / `bookAuthor` 的空串是**默认值也是合法值**（跨源关联键），不做 trim。
 *  - `anchorJson` / `styleJson` 是不透明 JSON 串，映射层不解析、不重排。
 *
 * ⚠️ 字段顺序照实体写，便于与实体声明逐行比对。
 */
fun BookMarkingEntity.toDomain(): BookMarking = BookMarking(
    id = id,
    bookUrl = bookUrl,
    bookName = bookName,
    bookAuthor = bookAuthor,
    chapterIndex = chapterIndex,
    anchorJson = anchorJson,
    styleJson = styleJson,
    note = note,
    chapterName = chapterName,
    enabled = enabled,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun BookMarking.toEntity(): BookMarkingEntity = BookMarkingEntity(
    id = id,
    bookUrl = bookUrl,
    bookName = bookName,
    bookAuthor = bookAuthor,
    chapterIndex = chapterIndex,
    anchorJson = anchorJson,
    styleJson = styleJson,
    note = note,
    chapterName = chapterName,
    enabled = enabled,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun List<BookMarkingEntity>.toDomainList(): List<BookMarking> = map { it.toDomain() }
