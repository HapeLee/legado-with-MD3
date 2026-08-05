package io.legado.app.data.repository

import io.legado.app.data.dao.RuleSubDao
import io.legado.app.data.entities.RuleSub
import kotlinx.coroutines.flow.Flow

class RuleSubscriptionRepository(private val dao: RuleSubDao) {
    fun observeAll(): Flow<List<RuleSub>> = dao.flowAll()
    suspend fun findByUrl(url: String): RuleSub? = dao.findByUrl(url)
    suspend fun insert(rule: RuleSub) = dao.insert(rule)
    suspend fun delete(rule: RuleSub) = dao.delete(rule)
    suspend fun all(): List<RuleSub> = dao.all
    suspend fun update(vararg rules: RuleSub) = dao.update(*rules)
}
