package io.legado.app.data.repository

import io.legado.app.data.appDb
import io.legado.app.data.entities.RssSource
import io.legado.app.help.source.SourceHelp
import kotlinx.coroutines.flow.Flow

class RssRepository {
    private val dao = appDb.rssSourceDao

    fun getEnabledSources(): Flow<List<RssSource>> = dao.flowEnabled()

    fun getEnabledSources(searchKey: String): Flow<List<RssSource>> = dao.flowEnabled(searchKey)

    fun getEnabledSourcesByGroup(group: String): Flow<List<RssSource>> =
        dao.flowEnabledByGroup(group)

    fun getEnabledGroups(): Flow<List<String>> = dao.flowEnabledGroups()

    suspend fun updateSources(vararg sources: RssSource) {
        dao.update(*sources)
    }

    suspend fun deleteSources(sources: List<RssSource>) {
        SourceHelp.deleteRssSources(sources)
    }

    fun getMinOrder(): Int = dao.minOrder

    fun getMaxOrder(): Int = dao.maxOrder
}
