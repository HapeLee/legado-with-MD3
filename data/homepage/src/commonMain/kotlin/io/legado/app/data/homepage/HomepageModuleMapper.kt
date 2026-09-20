package io.legado.app.data.homepage

import io.legado.app.data.entities.HomepageModule as HomepageModuleEntity
import io.legado.app.domain.model.ModuleItem

/**
 * Room 实体 `HomepageModule` ↔ 领域模型 `ModuleItem` 的双向映射（M4-7）。
 *
 * ⚠️ **一实体一文件**：`HomepageCustomSet` 的映射在 `HomepageCustomSetMapper.kt`。两条
 * `List<XEntity>.toDomainList()` 放进同一文件会擦除成同一个 JVM facade 签名 ⇒
 * *platform declaration clash*（M4-4 的判据）。
 *
 * ⚠️ **两侧都是全字段判等**（实体与模型都没有 `equals` / `hashCode` 覆写，都是 data class
 * 默认行为，与 M3-5 `RuleSub` / M4-1 `AiPromptPreset` 同侧）⇒ 整对象 `assertEquals` 成立；
 * 但仍配反向用例，见 `HomepageModuleMapperTest`。
 *
 * ⚠️ **字段顺序不同但必须一一对应**：实体声明顺序是
 * `id / sourceUrl / moduleKey / type / title / args / layoutConfig / url / isEnabled /
 * sortOrder / customSetId / isUserCreated / customTitle / customSetTitle / sourceJsonHash /
 * syncedAt`，而 `ModuleItem` 的构造参数顺序是
 * `id / sourceUrl / moduleKey / type / title / customTitle / customSetTitle / args /
 * layoutConfig / url / isEnabled / customSetId / isUserCreated / sortOrder / sourceJsonHash /
 * syncedAt`。逐字照抄迁移前 `HomepageModulesRepository` 里那两个私有扩展，**不要重排**。
 *
 * ⚠️ `ModuleItem.displayTitle` 是**计算属性**（`customTitle ?: title`），不在映射里——
 * 它不是持久化字段。
 *
 * ⚠️ 六个可空字段（`customTitle` / `customSetTitle` / `args` / `layoutConfig` / `url` /
 * `customSetId` / `sourceJsonHash`）`null` 原样搬运，不归一成空串。
 */
fun HomepageModuleEntity.toDomain(): ModuleItem = ModuleItem(
    id = id,
    sourceUrl = sourceUrl,
    moduleKey = moduleKey,
    type = type,
    title = title,
    customTitle = customTitle,
    customSetTitle = customSetTitle,
    args = args,
    layoutConfig = layoutConfig,
    url = url,
    isEnabled = isEnabled,
    customSetId = customSetId,
    isUserCreated = isUserCreated,
    sortOrder = sortOrder,
    sourceJsonHash = sourceJsonHash,
    syncedAt = syncedAt,
)

fun ModuleItem.toEntity(): HomepageModuleEntity = HomepageModuleEntity(
    id = id,
    sourceUrl = sourceUrl,
    moduleKey = moduleKey,
    type = type,
    title = title,
    args = args,
    layoutConfig = layoutConfig,
    url = url,
    isEnabled = isEnabled,
    sortOrder = sortOrder,
    customSetId = customSetId,
    isUserCreated = isUserCreated,
    customTitle = customTitle,
    customSetTitle = customSetTitle,
    sourceJsonHash = sourceJsonHash,
    syncedAt = syncedAt,
)

fun List<HomepageModuleEntity>.toDomainList(): List<ModuleItem> = map { it.toDomain() }
