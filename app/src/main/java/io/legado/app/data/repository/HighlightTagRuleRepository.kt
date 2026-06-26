package io.legado.app.data.repository

import io.legado.app.data.appDb
import io.legado.app.data.entities.HighlightTagRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class HighlightTagRuleRepository {

    private val dao = appDb.highlightTagRuleDao

    fun flowAll(): Flow<List<HighlightTagRule>> {
        return dao.flowAll()
    }

    suspend fun insert(vararg rule: HighlightTagRule) {
        withContext(Dispatchers.IO) {
            dao.insert(*rule)
        }
    }

    suspend fun delete(vararg rule: HighlightTagRule) {
        withContext(Dispatchers.IO) {
            dao.delete(*rule)
        }
    }

    suspend fun update(vararg rule: HighlightTagRule) {
        withContext(Dispatchers.IO) {
            dao.update(*rule)
        }
    }

    suspend fun findById(id: Long): HighlightTagRule? = withContext(Dispatchers.IO) {
        dao.getById(id)
    }

    suspend fun getByIds(ids: Set<Long>): List<HighlightTagRule> =
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) emptyList() else dao.getByIds(ids)
        }

    suspend fun enableByIds(ids: Set<Long>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        val rules = dao.getByIds(ids)
        val updated = rules.map { it.copy(enabled = true) }
        dao.update(*updated.toTypedArray())
    }

    suspend fun disableByIds(ids: Set<Long>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        val rules = dao.getByIds(ids)
        val updated = rules.map { it.copy(enabled = false) }
        dao.update(*updated.toTypedArray())
    }

    suspend fun deleteByIds(ids: Set<Long>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        val rules = dao.getByIds(ids)
        dao.delete(*rules.toTypedArray())
    }

    suspend fun moveOrder(rules: List<HighlightTagRule>) = withContext(Dispatchers.IO) {
        val updatedRules = rules.mapIndexed { index, rule ->
            rule.copy(order = index)
        }
        dao.update(*updatedRules.toTypedArray())
    }

}
