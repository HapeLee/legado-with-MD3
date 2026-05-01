package io.legado.app.data.repository

import io.legado.app.data.dao.RssSourceDao
import io.legado.app.data.entities.RssSource
import io.legado.app.help.source.SourceHelp
import kotlinx.coroutines.flow.Flow

class RssRepository(
    private val dao: RssSourceDao
) {

    fun getEnabledSources(): Flow<List<RssSource>> = dao.flowEnabled()

    fun getEnabledSources(searchKey: String): Flow<List<RssSource>> = dao.flowEnabled(searchKey)

    fun getEnabledSourcesByGroup(group: String): Flow<List<RssSource>> =
        dao.flowEnabledByGroup(group)

    fun getEnabledSources(searchKey: String, group: String): Flow<List<RssSource>> {
        return when {
            searchKey.isNotEmpty() -> dao.flowEnabled(searchKey)
            group.isNotEmpty() -> dao.flowEnabledByGroup(group)
            else -> dao.flowEnabled()
        }
    }

    fun getEnabledGroups(): Flow<List<String>> = dao.flowEnabledGroups()

    suspend fun updateSources(vararg sources: RssSource) {
        dao.update(*sources)
    }

    suspend fun topSources(vararg sources: RssSource) {
        val minOrder = dao.minOrder - 1
        val sortedSources = sources.sortedBy { it.customOrder }
        val updates = Array(sortedSources.size) { index ->
            sortedSources[index].copy(customOrder = minOrder - index)
        }
        dao.update(*updates)
    }

    suspend fun bottomSources(vararg sources: RssSource) {
        val maxOrder = dao.maxOrder + 1
        val sortedSources = sources.sortedBy { it.customOrder }
        val updates = Array(sortedSources.size) { index ->
            sortedSources[index].copy(customOrder = maxOrder + index)
        }
        dao.update(*updates)
    }

    suspend fun disableSource(source: RssSource) {
        dao.update(source.copy(enabled = false))
    }

    suspend fun deleteSources(sources: List<RssSource>) {
        SourceHelp.deleteRssSources(sources)
    }

    fun getMinOrder(): Int = dao.minOrder

    fun getMaxOrder(): Int = dao.maxOrder
}
