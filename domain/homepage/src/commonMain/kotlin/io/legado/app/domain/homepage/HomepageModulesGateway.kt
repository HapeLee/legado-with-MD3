package io.legado.app.domain.homepage

import io.legado.app.domain.model.CustomSetItem
import io.legado.app.domain.model.ModuleItem
import kotlinx.coroutines.flow.Flow

/**
 * 首页模块与自定义集合的领域端口（M4-7）。
 *
 * **既有契约搬家**，不是新造：原 `io.legado.app.domain.gateway.HomepageModulesGateway`
 * 早就在 `:core:data` 里，收发类型**本来就是领域模型**（`ModuleItem` / `CustomSetItem`，
 * 住 `:core:model`）。所以本片搬的是**端口声明 + 实体↔模型的映射 + 实现**，
 * 领域模型一动不动——它的归属地 `:core:model` 是共享层，搬到 `domain/homepage`
 * 只会让消费方多改两行 import，没有任何边界收益。
 *
 * ## 方法集：删掉两个零调用方的方法
 *
 * `setSortOrder(id, order)` 与 `setCustomSetSortOrder(id, order)` 全仓**零调用方**
 * （唯一消费方 `HomepageViewModel` 只用批量版本 `batchSetSortOrders` /
 * `batchSetCustomSetSortOrders`）⇒ 随片从端口删除。**底层 DAO 方法保留**
 * （`HomepageModuleDao.setSortOrder` / `HomepageCustomSetDao.setSortOrder` 留给
 * `data:database`；顺带一提，DAO 的 `batchSetSortOrders` 本身就是 `@Transaction`
 * 里逐条调它们）。
 *
 * ⚠️ **备份格式的 owner 是实体，不是本端口**：`Backup.kt` / `Restore.kt` 按
 * `HomepageModule` / `HomepageCustomSet` **实体**读写 `homepageModules.json` /
 * `homepageCustomSets.json`。本片不动它们（与 M3 的 `ReplaceRule` / `TagGroupRule`
 * 同律），所以备份兼容面完全没被触碰。
 */
interface HomepageModulesGateway {

    // ---- Module queries ----

    fun flowEnabled(): Flow<List<ModuleItem>>

    fun flowAll(): Flow<List<ModuleItem>>

    fun flowBySource(sourceUrl: String): Flow<List<ModuleItem>>

    suspend fun getById(id: String): ModuleItem?

    // ---- Module mutations ----

    suspend fun upsertAll(modules: List<ModuleItem>)

    suspend fun setEnabled(id: String, enabled: Boolean)

    suspend fun batchSetSortOrders(orders: Map<String, Int>)

    suspend fun setCustomSetId(id: String, setId: String?)

    suspend fun setCustomSetTitle(id: String, title: String?)

    suspend fun delete(id: String)

    /**
     * 删掉某书源下**不在** [currentIds] 里的模块（书源重新解析后清理失效模块）。
     */
    suspend fun deleteStale(sourceUrl: String, currentIds: List<String>)

    // ---- Custom set queries ----

    fun flowCustomSets(): Flow<List<CustomSetItem>>

    suspend fun getCustomSetById(id: String): CustomSetItem?

    // ---- Custom set mutations ----

    suspend fun upsertCustomSet(set: CustomSetItem)

    suspend fun batchSetCustomSetSortOrders(orders: Map<String, Int>)

    /** 新建一个自定义集合；id 由实现侧用当前时间拼（`cs_<millis>`）。 */
    suspend fun createCustomSet(name: String): CustomSetItem

    suspend fun renameCustomSet(id: String, name: String)

    /** 删集合时**先**摘掉挂在它下面的模块（`deleteByCustomSetId`）再删集合，顺序不能反。 */
    suspend fun deleteCustomSet(id: String)
}
