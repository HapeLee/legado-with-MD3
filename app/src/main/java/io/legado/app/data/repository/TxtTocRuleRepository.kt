package io.legado.app.data.repository

import io.legado.app.data.appDb
import io.legado.app.data.entities.TxtTocRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class TxtTocRuleRepository {
    private val dao = appDb.txtTocRuleDao

    fun flowAll(): Flow<List<TxtTocRule>> = dao.observeAll()

    fun flowSearch(key: String): Flow<List<TxtTocRule>> = dao.flowSearch(key)

    suspend fun insert(vararg rules: TxtTocRule) = withContext(Dispatchers.IO) {
        dao.insert(*rules)
    }

    suspend fun update(vararg rules: TxtTocRule) = withContext(Dispatchers.IO) {
        dao.update(*rules)
    }

    suspend fun delete(vararg rules: TxtTocRule) = withContext(Dispatchers.IO) {
        dao.delete(*rules)
    }

    suspend fun deleteByIds(ids: Collection<Long>) = withContext(Dispatchers.IO) {
        val rules = dao.getByIds(ids.toSet())
        dao.delete(*rules.toTypedArray())
    }

    suspend fun enableByIds(ids: Collection<Long>, enable: Boolean) = withContext(Dispatchers.IO) {
        val rules = dao.getByIds(ids.toSet())
        val updated = rules.map { it.copy(enable = enable) }
        dao.update(*updated.toTypedArray())
    }

    suspend fun saveOrder(rules: List<TxtTocRule>) = withContext(Dispatchers.IO) {
        rules.forEachIndexed { index, rule ->
            rule.serialNumber = index + 1
        }
        dao.update(*rules.toTypedArray())
    }
}