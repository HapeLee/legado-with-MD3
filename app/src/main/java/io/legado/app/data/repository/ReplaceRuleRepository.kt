package io.legado.app.data.repository

import android.text.TextUtils
import io.legado.app.data.appDb
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.ui.replace.ReplaceRuleItemUi
import io.legado.app.utils.splitNotBlank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlin.collections.map

class ReplaceRuleRepository {

    fun flowGroups(): Flow<List<String>> {
        return appDb.replaceRuleDao.flowGroups().flowOn(Dispatchers.IO)
    }

    fun flowAll(): Flow<List<ReplaceRule>> {
        return appDb.replaceRuleDao.flowAll().flowOn(Dispatchers.IO)
    }

    fun flowNoGroup(): Flow<List<ReplaceRule>> {
        return appDb.replaceRuleDao.flowNoGroup().flowOn(Dispatchers.IO)
    }

    fun flowGroupSearch(key: String): Flow<List<ReplaceRule>> {
        return appDb.replaceRuleDao.flowGroupSearch(key).flowOn(Dispatchers.IO)
    }

    fun flowSearch(key: String): Flow<List<ReplaceRule>> {
        return appDb.replaceRuleDao.flowSearch(key).flowOn(Dispatchers.IO)
    }

    suspend fun update(vararg rule: ReplaceRule) {
        withContext(Dispatchers.IO) {
            appDb.replaceRuleDao.update(*rule)
        }
    }

    suspend fun delete(rule: ReplaceRule) {
        withContext(Dispatchers.IO) {
            appDb.replaceRuleDao.delete(rule)
        }
    }

    suspend fun toTop(rule: ReplaceRule, isDesc: Boolean = false) {
        withContext(Dispatchers.IO) {
            if (isDesc) {
                rule.order = appDb.replaceRuleDao.maxOrder + 1
            } else {
                rule.order = appDb.replaceRuleDao.minOrder - 1
            }
            appDb.replaceRuleDao.update(rule)
        }
    }

    suspend fun toBottom(rule: ReplaceRule, isDesc: Boolean = false) {
        withContext(Dispatchers.IO) {
            if (isDesc) {
                rule.order = appDb.replaceRuleDao.minOrder - 1
            } else {
                rule.order = appDb.replaceRuleDao.maxOrder + 1
            }
            appDb.replaceRuleDao.update(rule)
        }
    }

    suspend fun upOrder() {
        withContext(Dispatchers.IO) {
            val rules = appDb.replaceRuleDao.all
            var normalOrder = 1
            rules.forEach { rule ->
                if (rule.order >= 0) {
                    rule.order = normalOrder++
                }
            }
            appDb.replaceRuleDao.update(*rules.toTypedArray())
        }
    }

    suspend fun enableSelection(rules: List<ReplaceRule>) {
        withContext(Dispatchers.IO) {
            val array = Array(rules.size) {
                rules[it].copy(isEnabled = true)
            }
            appDb.replaceRuleDao.update(*array)
        }
    }

    suspend fun disableSelection(rules: List<ReplaceRule>) {
        withContext(Dispatchers.IO) {
            val array = Array(rules.size) {
                rules[it].copy(isEnabled = false)
            }
            appDb.replaceRuleDao.update(*array)
        }
    }

    suspend fun addGroup(group: String) {
        withContext(Dispatchers.IO) {
            val sources = appDb.replaceRuleDao.noGroup
            sources.forEach { source ->
                source.group = group
            }
            appDb.replaceRuleDao.update(*sources.toTypedArray())
        }
    }

    suspend fun upGroup(oldGroup: String, newGroup: String?) {
        withContext(Dispatchers.IO) {
            val sources = appDb.replaceRuleDao.getByGroup(oldGroup)
            sources.forEach { source ->
                source.group?.splitNotBlank(",")?.toHashSet()?.let {
                    it.remove(oldGroup)
                    if (!newGroup.isNullOrEmpty())
                        it.add(newGroup)
                    source.group = TextUtils.join(",", it)
                }
            }
            appDb.replaceRuleDao.update(*sources.toTypedArray())
        }
    }

    suspend fun delGroup(group: String) {
        withContext(Dispatchers.IO) {
            val sources = appDb.replaceRuleDao.getByGroup(group)
            sources.forEach { source ->
                source.group?.splitNotBlank(",")?.toHashSet()?.let {
                    it.remove(group)
                    source.group = TextUtils.join(",", it)
                }
            }
            appDb.replaceRuleDao.update(*sources.toTypedArray())
        }
    }

    suspend fun enableByIds(ids: Set<Long>) =
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext

            val rules = appDb.replaceRuleDao.getByIds(ids)
            val updated = rules.map { it.copy(isEnabled = true) }
            appDb.replaceRuleDao.update(*updated.toTypedArray())
        }

    suspend fun disableByIds(ids: Set<Long>) =
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext

            val rules = appDb.replaceRuleDao.getByIds(ids)
            val updated = rules.map { it.copy(isEnabled = false) }
            appDb.replaceRuleDao.update(*updated.toTypedArray())
        }

    suspend fun deleteByIds(ids: Set<Long>) =
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext

            val rules = appDb.replaceRuleDao.getByIds(ids)
            appDb.replaceRuleDao.delete(*rules.toTypedArray())
        }

    suspend fun topByIds(ids: Set<Long>, isDesc: Boolean = false) =
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext
            val rules = appDb.replaceRuleDao.getByIds(ids)
            if (isDesc) {
                var maxOrder = appDb.replaceRuleDao.maxOrder
                val updated = rules.map {
                    maxOrder++
                    it.copy(order = maxOrder)
                }
                appDb.replaceRuleDao.update(*updated.toTypedArray())
            } else {
                var minOrder = appDb.replaceRuleDao.minOrder
                val updated = rules.map {
                    minOrder--
                    it.copy(order = minOrder)
                }
                appDb.replaceRuleDao.update(*updated.toTypedArray())
            }
        }

    suspend fun bottomByIds(ids: Set<Long>, isDesc: Boolean = false) =
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext

            val rules = appDb.replaceRuleDao.getByIds(ids)
            if (isDesc) {
                var minOrder = appDb.replaceRuleDao.minOrder
                val updated = rules.map {
                    minOrder--
                    it.copy(order = minOrder)
                }
                appDb.replaceRuleDao.update(*updated.toTypedArray())
            } else {
                var maxOrder = appDb.replaceRuleDao.maxOrder
                val updated = rules.map {
                    maxOrder++
                    it.copy(order = maxOrder)
                }
                appDb.replaceRuleDao.update(*updated.toTypedArray())
            }
        }

    suspend fun moveOrder(currentRules: List<ReplaceRuleItemUi>, isDesc: Boolean = false) {
        withContext(Dispatchers.IO) {
            val size = currentRules.size
            val updatedRules = currentRules.mapIndexed { index, itemUi ->
                val order = if (isDesc) size - index else index + 1
                itemUi.rule.copy(order = order)
            }

            appDb.replaceRuleDao.update(*updatedRules.toTypedArray())
        }
    }

}
