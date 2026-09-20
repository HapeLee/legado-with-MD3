package io.legado.app.data.homepage

import io.legado.app.core.platform.systemTimeMillis
import io.legado.app.data.dao.HomepageCustomSetDao
import io.legado.app.data.dao.HomepageModuleDao
import io.legado.app.data.entities.HomepageCustomSet
import io.legado.app.domain.homepage.HomepageModulesGateway
import io.legado.app.domain.model.CustomSetItem
import io.legado.app.domain.model.ModuleItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * [HomepageModulesGateway] 的实现（M4-7）：逐字照抄迁移前 `core:data` 的
 * `io.legado.app.data.repository.HomepageModulesRepository`，只改三处：
 *
 *  1. **裸调 DAO，不包 `withContext(Dispatchers.IO)`** —— 迁移前的实现就是这样（与 M4-3
 *     「迁前就是裸调 DAO」同侧，与 M4-1/M4-2/M4-4/M4-6 相反）。Room 的 `suspend` DAO 自己
 *     会调度，**不要**向上一片看齐：多一跳 IO 不会让任何测试变红，只会悄悄改线程。
 *  2. **四个 `Flow` 方法都在流内映射**（`.map { list -> list.map { it.toDomain() } }`），
 *     映射每次发射都跑。mapper 测试碰不到这条路径 ⇒ 由 `HomepageModulesRepositoryImplTest`
 *     驱动多次发射钉住（M4-3 立的判据，M4-6 第二次应用）。
 *  3. `createCustomSet` 的 `System.currentTimeMillis()` → `:core:platform` 的
 *     `systemTimeMillis()`（commonMain 拿不到 `java.lang.System`）。
 *
 * ⚠️ `deleteCustomSet` 的**顺序**是行为：先 `moduleDao.deleteByCustomSetId(id)` 摘掉挂在该
 * 集合下的模块，再 `customSetDao.delete(id)`。反过来会留下指向已删集合的孤儿模块。
 *
 * ⚠️ `setSortOrder` / `setCustomSetSortOrder` 随片从端口删除（零调用方），DAO 方法保留。
 */
class HomepageModulesRepositoryImpl(
    private val moduleDao: HomepageModuleDao,
    private val customSetDao: HomepageCustomSetDao,
) : HomepageModulesGateway {

    override fun flowEnabled(): Flow<List<ModuleItem>> =
        moduleDao.flowEnabled().map { list -> list.map { it.toDomain() } }

    override fun flowAll(): Flow<List<ModuleItem>> =
        moduleDao.flowAll().map { list -> list.map { it.toDomain() } }

    override fun flowBySource(sourceUrl: String): Flow<List<ModuleItem>> =
        moduleDao.flowBySource(sourceUrl).map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: String): ModuleItem? =
        moduleDao.getById(id)?.toDomain()

    override suspend fun upsertAll(modules: List<ModuleItem>) =
        moduleDao.upsertAll(modules.map { it.toEntity() })

    override suspend fun setEnabled(id: String, enabled: Boolean) =
        moduleDao.setEnabled(id, enabled)

    override suspend fun batchSetSortOrders(orders: Map<String, Int>) =
        moduleDao.batchSetSortOrders(orders)

    override suspend fun setCustomSetId(id: String, setId: String?) =
        moduleDao.setCustomSetId(id, setId)

    override suspend fun setCustomSetTitle(id: String, title: String?) =
        moduleDao.setCustomSetTitle(id, title)

    override suspend fun delete(id: String) = moduleDao.delete(id)

    override suspend fun deleteStale(sourceUrl: String, currentIds: List<String>) =
        moduleDao.deleteStale(sourceUrl, currentIds)

    override fun flowCustomSets(): Flow<List<CustomSetItem>> =
        customSetDao.flowAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getCustomSetById(id: String): CustomSetItem? =
        customSetDao.getById(id)?.toDomain()

    override suspend fun upsertCustomSet(set: CustomSetItem) =
        customSetDao.upsert(set.toEntity())

    // DAO 侧的方法名就是 `batchSetSortOrders`（两个 DAO 各有一份同名方法），逐字照抄。
    override suspend fun batchSetCustomSetSortOrders(orders: Map<String, Int>) =
        customSetDao.batchSetSortOrders(orders)

    override suspend fun createCustomSet(name: String): CustomSetItem {
        val entity = HomepageCustomSet(
            id = "cs_${systemTimeMillis()}", name = name
        )
        customSetDao.upsert(entity)
        return entity.toDomain()
    }

    override suspend fun renameCustomSet(id: String, name: String) =
        customSetDao.rename(id, name)

    override suspend fun deleteCustomSet(id: String) {
        moduleDao.deleteByCustomSetId(id)
        customSetDao.delete(id)
    }
}
