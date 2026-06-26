package io.legado.app.data.repository

import io.legado.app.data.appDb
import io.legado.app.data.entities.TagGroupRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class TagGroupRuleRepository {

    private val dao = appDb.tagGroupRuleDao

    fun flowAll(): Flow<List<TagGroupRule>> {
        return dao.flowAll()
    }

    suspend fun insert(vararg rule: TagGroupRule) {
        withContext(Dispatchers.IO) {
            dao.insert(*rule)
        }
    }

    suspend fun delete(vararg rule: TagGroupRule) {
        withContext(Dispatchers.IO) {
            dao.delete(*rule)
        }
    }

    suspend fun getByGroupName(groupName: String): TagGroupRule? = withContext(Dispatchers.IO) {
        dao.getByGroupName(groupName)
    }

    suspend fun update(vararg rule: TagGroupRule) {
        withContext(Dispatchers.IO) {
            dao.update(*rule)
        }
    }

    suspend fun findById(id: Long): TagGroupRule? = withContext(Dispatchers.IO) {
        dao.getById(id)
    }

    suspend fun getByIds(ids: Set<Long>): List<TagGroupRule> =
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) emptyList() else dao.getByIds(ids)
        }

    suspend fun deleteByIds(ids: Set<Long>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        val rules = dao.getByIds(ids)
        dao.delete(*rules.toTypedArray())
    }

    suspend fun moveOrder(rules: List<TagGroupRule>) = withContext(Dispatchers.IO) {
        val updatedRules = rules.mapIndexed { index, rule ->
            rule.copy(order = index)
        }
        dao.update(*updatedRules.toTypedArray())
    }

    fun getMaxOrder(): Int = dao.maxOrder

}
