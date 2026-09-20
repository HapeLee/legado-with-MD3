package io.legado.app.data.homepage

import io.legado.app.data.entities.HomepageCustomSet as HomepageCustomSetEntity
import io.legado.app.domain.model.CustomSetItem

/**
 * Room 实体 `HomepageCustomSet` ↔ 领域模型 `CustomSetItem` 的双向映射（M4-7）。
 *
 * ⚠️ **一实体一文件**（`HomepageModule` 的在 `HomepageModuleMapper.kt`）：两条
 * `List<XEntity>.toDomainList()` 同文件会因 JVM 泛型擦除撞成同一个 facade 签名（M4-4）。
 *
 * 三个字段全部原样搬运；`sortOrder` 是 DAO 里 `ORDER BY sortOrder ASC` 的排序键，
 * 映射不得改动。
 */
fun HomepageCustomSetEntity.toDomain(): CustomSetItem = CustomSetItem(
    id = id,
    name = name,
    sortOrder = sortOrder,
)

fun CustomSetItem.toEntity(): HomepageCustomSetEntity = HomepageCustomSetEntity(
    id = id,
    name = name,
    sortOrder = sortOrder,
)

fun List<HomepageCustomSetEntity>.toDomainList(): List<CustomSetItem> = map { it.toDomain() }
