package io.legado.app.data.repository

import io.legado.app.data.dao.HomepageCustomSetDao
import io.legado.app.data.dao.HomepageModuleDao
import io.legado.app.data.entities.HomepageCustomSet
import io.legado.app.data.entities.HomepageModule
import io.legado.app.domain.gateway.HomepageModulesGateway
import io.legado.app.domain.model.CustomSetItem
import io.legado.app.domain.model.ModuleItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HomepageModulesRepository(
    private val moduleDao: HomepageModuleDao,
    private val customSetDao: HomepageCustomSetDao,
) : HomepageModulesGateway {

    override fun flowEnabled(): Flow<List<ModuleItem>> =
        moduleDao.flowEnabled().map { list -> list.map { it.toModuleItem() } }

    override fun flowAll(): Flow<List<ModuleItem>> =
        moduleDao.flowAll().map { list -> list.map { it.toModuleItem() } }

    override fun flowBySource(sourceUrl: String): Flow<List<ModuleItem>> =
        moduleDao.flowBySource(sourceUrl).map { list -> list.map { it.toModuleItem() } }

    override suspend fun getById(id: String): ModuleItem? =
        moduleDao.getById(id)?.toModuleItem()

    override suspend fun upsertAll(modules: List<ModuleItem>) =
        moduleDao.upsertAll(modules.map { it.toModuleEntity() })

    override suspend fun setEnabled(id: String, enabled: Boolean) =
        moduleDao.setEnabled(id, enabled)

    override suspend fun setSortOrder(id: String, order: Int) = moduleDao.setSortOrder(id, order)
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
        customSetDao.flowAll().map { list -> list.map { it.toCustomSetItem() } }

    override suspend fun getCustomSetById(id: String): CustomSetItem? =
        customSetDao.getById(id)?.toCustomSetItem()

    override suspend fun upsertCustomSet(set: CustomSetItem) =
        customSetDao.upsert(set.toCustomSetEntity())

    override suspend fun setCustomSetSortOrder(id: String, order: Int) =
        customSetDao.setSortOrder(id, order)
    override suspend fun batchSetCustomSetSortOrders(orders: Map<String, Int>) =
        customSetDao.batchSetSortOrders(orders)

    override suspend fun createCustomSet(name: String): CustomSetItem {
        val entity = HomepageCustomSet(
            id = "cs_${System.currentTimeMillis()}", name = name
        )
        customSetDao.upsert(entity)
        return entity.toCustomSetItem()
    }

    override suspend fun renameCustomSet(id: String, name: String) = customSetDao.rename(id, name)
    override suspend fun deleteCustomSet(id: String) {
        moduleDao.deleteByCustomSetId(id)
        customSetDao.delete(id)
    }

    private fun HomepageModule.toModuleItem() = ModuleItem(
        id = id, sourceUrl = sourceUrl, moduleKey = moduleKey, type = type,
        title = title, customTitle = customTitle, customSetTitle = customSetTitle,
        args = args, layoutConfig = layoutConfig, url = url, isEnabled = isEnabled,
        customSetId = customSetId, isUserCreated = isUserCreated,
        sortOrder = sortOrder, sourceJsonHash = sourceJsonHash, syncedAt = syncedAt,
    )

    private fun ModuleItem.toModuleEntity() = HomepageModule(
        id = id, sourceUrl = sourceUrl, moduleKey = moduleKey, type = type,
        title = title, customTitle = customTitle, customSetTitle = customSetTitle,
        args = args, layoutConfig = layoutConfig, url = url, isEnabled = isEnabled,
        customSetId = customSetId, isUserCreated = isUserCreated,
        sortOrder = sortOrder, sourceJsonHash = sourceJsonHash, syncedAt = syncedAt,
    )

    private fun HomepageCustomSet.toCustomSetItem() =
        CustomSetItem(id = id, name = name, sortOrder = sortOrder)

    private fun CustomSetItem.toCustomSetEntity() =
        HomepageCustomSet(id = id, name = name, sortOrder = sortOrder)
}
