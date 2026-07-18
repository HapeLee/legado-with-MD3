package io.legado.app.data.repository

import io.legado.app.data.dao.RssStarDao
import io.legado.app.data.entities.RssStar
import kotlinx.coroutines.flow.Flow

class RssFavoriteRepository(private val dao: RssStarDao) {
    fun observeGroups(): Flow<List<String>> = dao.flowGroups()
    fun observeAll(): Flow<List<RssStar>> = dao.liveAll()
    fun observeGroup(group: String): Flow<List<RssStar>> = dao.flowByGroup(group)
    suspend fun update(star: RssStar) = dao.update(star)
    suspend fun delete(star: RssStar) = dao.delete(star.origin, star.link)
    suspend fun deleteGroup(group: String) = dao.deleteByGroup(group)
    suspend fun deleteAll() = dao.deleteAll()
}
