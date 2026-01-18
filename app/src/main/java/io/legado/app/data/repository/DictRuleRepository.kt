package io.legado.app.data.repository

import io.legado.app.data.appDb
import io.legado.app.data.entities.DictRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class DictRuleRepository {

    private val dao = appDb.dictRuleDao

    fun flowAll(): Flow<List<DictRule>> {
        return dao.flowAll()
    }

    fun flowSearch(key: String): Flow<List<DictRule>> {
        return dao.flowSearch(key)
    }

    fun getAll(): List<DictRule> {
        return dao.all
    }

    fun insert(vararg rule: DictRule) {
        dao.insert(*rule)
    }

    fun delete(vararg rule: DictRule) {
        dao.delete(*rule)
    }

    fun update(vararg rule: DictRule) {
        dao.update(*rule)
    }

    suspend fun enableByIds(names: Set<String>) = withContext(Dispatchers.IO) {
        if (names.isEmpty()) return@withContext
        val rules = dao.getByNames(names)
        val updated = rules.map { it.copy(enabled = true) }
        dao.update(*updated.toTypedArray())
    }

    suspend fun disableByIds(names: Set<String>) = withContext(Dispatchers.IO) {
        if (names.isEmpty()) return@withContext
        val rules = dao.getByNames(names)
        val updated = rules.map { it.copy(enabled = false) }
        dao.update(*updated.toTypedArray())
    }

    suspend fun deleteByIds(names: Set<String>) = withContext(Dispatchers.IO) {
        if (names.isEmpty()) return@withContext
        val rules = dao.getByNames(names)
        dao.delete(*rules.toTypedArray())
    }

    suspend fun moveOrder(rules: List<DictRule>) = withContext(Dispatchers.IO) {
        val updatedRules = rules.mapIndexed { index, rule ->
            rule.copy(sortNumber = index + 1)
        }
        dao.update(*updatedRules.toTypedArray())
    }

}
