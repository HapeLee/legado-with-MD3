package io.legado.app.data.repository

import android.text.TextUtils
import io.legado.app.data.dao.ReplaceRuleDao
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.utils.splitNotBlank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class ReplaceRuleRepository(
    private val dao: ReplaceRuleDao,
) {

    fun flowGroups(): Flow<List<String>> {
        return dao.flowGroups().flowOn(Dispatchers.IO)
    }

    fun flowAll(): Flow<List<ReplaceRule>> {
        return dao.flowAll().flowOn(Dispatchers.IO)
    }

    fun flowNoGroup(): Flow<List<ReplaceRule>> {
        return dao.flowNoGroup().flowOn(Dispatchers.IO)
    }

    fun flowGroupSearch(key: String): Flow<List<ReplaceRule>> {
        return dao.flowGroupSearch(key).flowOn(Dispatchers.IO)
    }

    fun flowSearch(key: String): Flow<List<ReplaceRule>> {
        return dao.flowSearch(key).flowOn(Dispatchers.IO)
    }

    suspend fun findById(id: Long): ReplaceRule? = withContext(Dispatchers.IO) {
        dao.findById(id)
    }

    suspend fun getNextOrder(): Int = withContext(Dispatchers.IO) {
        dao.maxOrder + 1
    }

    suspend fun update(vararg rule: ReplaceRule) {
        withContext(Dispatchers.IO) {
            dao.update(*rule)
        }
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        withContext(Dispatchers.IO) {
            dao.updateEnabled(id, enabled)
        }
    }

    suspend fun insert(vararg rule: ReplaceRule) {
        withContext(Dispatchers.IO) {
            dao.insert(*rule)
        }
    }

    suspend fun delete(rule: ReplaceRule) {
        withContext(Dispatchers.IO) {
            dao.delete(rule)
        }
    }

    suspend fun toTop(rule: ReplaceRule, isDesc: Boolean = false) {
        withContext(Dispatchers.IO) {
            if (isDesc) {
                rule.order = dao.maxOrder + 1
            } else {
                rule.order = dao.minOrder - 1
            }
            dao.update(rule)
        }
    }

    suspend fun toBottom(rule: ReplaceRule, isDesc: Boolean = false) {
        withContext(Dispatchers.IO) {
            if (isDesc) {
                rule.order = dao.minOrder - 1
            } else {
                rule.order = dao.maxOrder + 1
            }
            dao.update(rule)
        }
    }

    suspend fun upOrder() {
        withContext(Dispatchers.IO) {
            val rules = dao.all
            var normalOrder = 1
            rules.forEach { rule ->
                if (rule.order >= 0) {
                    rule.order = normalOrder++
                }
            }
            dao.update(*rules.toTypedArray())
        }
    }

    suspend fun addGroup(group: String) {
        withContext(Dispatchers.IO) {
            val sources = dao.noGroup
            sources.forEach { source ->
                source.group = group
            }
            dao.update(*sources.toTypedArray())
        }
    }

    suspend fun upGroup(oldGroup: String, newGroup: String?) {
        withContext(Dispatchers.IO) {
            val sources = dao.getByGroup(oldGroup)
            sources.forEach { source ->
                source.group?.splitNotBlank(",")?.toHashSet()?.let {
                    it.remove(oldGroup)
                    if (!newGroup.isNullOrEmpty())
                        it.add(newGroup)
                    source.group = TextUtils.join(",", it)
                }
            }
            dao.update(*sources.toTypedArray())
        }
    }

    suspend fun delGroup(group: String) {
        withContext(Dispatchers.IO) {
            val sources = dao.getByGroup(group)
            sources.forEach { source ->
                source.group?.splitNotBlank(",")?.toHashSet()?.let {
                    it.remove(group)
                    source.group = TextUtils.join(",", it)
                }
            }
            dao.update(*sources.toTypedArray())
        }
    }

    suspend fun clearGroups(groups: List<String>) = withContext(Dispatchers.IO) {
        dao.clearGroups(groups)
    }

    suspend fun enableByIds(ids: Set<Long>) =
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext
            dao.updateEnabled(ids.toList(), true)
        }

    suspend fun disableByIds(ids: Set<Long>) =
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext
            dao.updateEnabled(ids.toList(), false)
        }

    suspend fun deleteByIds(ids: Set<Long>) =
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext

            val rules = dao.getByIds(ids)
            dao.delete(*rules.toTypedArray())
        }

    suspend fun topByIds(ids: Set<Long>, isDesc: Boolean = false) =
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext
            val rules = dao.getByIds(ids)
            if (isDesc) {
                var maxOrder = dao.maxOrder
                val updated = rules.map {
                    maxOrder++
                    it.copy(order = maxOrder)
                }
                dao.update(*updated.toTypedArray())
            } else {
                var minOrder = dao.minOrder
                val updated = rules.map {
                    minOrder--
                    it.copy(order = minOrder)
                }
                dao.update(*updated.toTypedArray())
            }
        }

    suspend fun bottomByIds(ids: Set<Long>, isDesc: Boolean = false) =
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext

            val rules = dao.getByIds(ids)
            if (isDesc) {
                var minOrder = dao.minOrder
                val updated = rules.map {
                    minOrder--
                    it.copy(order = minOrder)
                }
                dao.update(*updated.toTypedArray())
            } else {
                var maxOrder = dao.maxOrder
                val updated = rules.map {
                    maxOrder++
                    it.copy(order = maxOrder)
                }
                dao.update(*updated.toTypedArray())
            }
        }

    /**
     * 把 [draggedId] 规则移动到 [anchorId] 规则旁边（[afterAnchor] 为 true 时在其后，否则在其前）。
     * 列表顺序始终按 sortOrder 升序，移动后统一重写全部规则序号。
     */
    suspend fun moveReplaceRule(draggedId: Long, anchorId: Long, afterAnchor: Boolean) {
        withContext(Dispatchers.IO) {
            val rules = dao.all
            val draggedIndex = rules.indexOfFirst { it.id == draggedId }
            if (draggedIndex < 0) return@withContext
            val dragged = rules[draggedIndex]
            val remaining = rules.toMutableList().apply { removeAt(draggedIndex) }
            val anchorIndex = remaining.indexOfFirst { it.id == anchorId }
            if (anchorIndex < 0) return@withContext
            val insertIndex = (anchorIndex + if (afterAnchor) 1 else 0).coerceIn(0, remaining.size)
            remaining.add(insertIndex, dragged)
            val updated = remaining.mapIndexed { index, rule -> rule.copy(order = index + 1) }
            dao.update(*updated.toTypedArray())
        }
    }

    suspend fun moveOrder(currentRules: List<ReplaceRule>, isDesc: Boolean = false) {
        withContext(Dispatchers.IO) {
            val size = currentRules.size
            val updatedRules = currentRules.mapIndexed { index, rule ->
                val order = if (isDesc) size - index else index + 1
                rule.copy(order = order)
            }
            dao.update(*updatedRules.toTypedArray())
        }
    }

}
