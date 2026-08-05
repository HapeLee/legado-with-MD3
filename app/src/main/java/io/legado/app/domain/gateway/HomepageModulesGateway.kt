package io.legado.app.domain.gateway

import io.legado.app.domain.model.CustomSetItem
import io.legado.app.domain.model.ModuleItem
import kotlinx.coroutines.flow.Flow

interface HomepageModulesGateway {
    // Module queries
    fun flowEnabled(): Flow<List<ModuleItem>>
    fun flowAll(): Flow<List<ModuleItem>>
    fun flowBySource(sourceUrl: String): Flow<List<ModuleItem>>
    suspend fun getById(id: String): ModuleItem?

    // Module mutations
    suspend fun upsertAll(modules: List<ModuleItem>)
    suspend fun setEnabled(id: String, enabled: Boolean)
    suspend fun setSortOrder(id: String, order: Int)
    suspend fun batchSetSortOrders(orders: Map<String, Int>)
    suspend fun setCustomSetId(id: String, setId: String?)
    suspend fun setCustomSetTitle(id: String, title: String?)
    suspend fun delete(id: String)
    suspend fun deleteStale(sourceUrl: String, currentIds: List<String>)

    // Custom set queries
    fun flowCustomSets(): Flow<List<CustomSetItem>>
    suspend fun getCustomSetById(id: String): CustomSetItem?

    // Custom set mutations
    suspend fun upsertCustomSet(set: CustomSetItem)
    suspend fun setCustomSetSortOrder(id: String, order: Int)
    suspend fun batchSetCustomSetSortOrders(orders: Map<String, Int>)
    suspend fun createCustomSet(name: String): CustomSetItem
    suspend fun renameCustomSet(id: String, name: String)
    suspend fun deleteCustomSet(id: String)
}
