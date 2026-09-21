package io.legado.app.data.rules

import io.legado.app.data.dao.ReplaceRuleDao
import io.legado.app.data.entities.ReplaceRule as ReplaceRuleEntity
import io.legado.app.domain.model.text.splitNotBlank
import io.legado.app.domain.rules.ReplaceRule
import io.legado.app.domain.rules.ReplaceRuleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * [ReplaceRuleRepository] 的实现（M3-1）。
 *
 * **本类是 `:core:data` 的 `data/repository/ReplaceRuleRepository.kt` 的整份搬迁**：
 * 方法体、`Dispatchers.IO` 包裹、空集合短路、排序算法的分支全部逐条保留，唯一的差异是
 * 公开签名从 Room 实体换成领域模型 [ReplaceRule]，于是在 DAO 边界上多了映射
 * （[toDomain] / [toEntity]，见 [ReplaceRuleMapper]）。
 *
 * ⚠️ 过渡期依赖：Room 的 DAO 与实体仍归 `:core:data`（`data:database`「Room 唯一 owner」
 * 尚未拆出），所以本模块 `implementation(project(":core:data"))`。等它落地后，本类只换
 * DAO 的来源，构造签名与调用方都不动。
 *
 * ⚠️ 两处**保留副作用**的写法，别顺手"清理"：
 * - [toTop] / [toBottom] 会就地改写传入的 [ReplaceRule] 的 `order`（迁移前就是改写调用方
 *   传入的实体），改完再落库；
 * - [moveReplaceRule] 落库时统一重写**全部**规则序号（不是只改两条）——列表顺序依赖它。
 */
class ReplaceRuleRepositoryImpl(
    private val dao: ReplaceRuleDao,
) : ReplaceRuleRepository {

    override fun flowGroups(): Flow<List<String>> {
        return dao.flowGroups().flowOn(Dispatchers.IO)
    }

    override fun flowAll(): Flow<List<ReplaceRule>> {
        return dao.flowAll().map { it.toDomainList() }.flowOn(Dispatchers.IO)
    }

    override fun flowNoGroup(): Flow<List<ReplaceRule>> {
        return dao.flowNoGroup().map { it.toDomainList() }.flowOn(Dispatchers.IO)
    }

    override fun flowGroupSearch(key: String): Flow<List<ReplaceRule>> {
        return dao.flowGroupSearch(key).map { it.toDomainList() }.flowOn(Dispatchers.IO)
    }

    override suspend fun findById(id: Long): ReplaceRule? = withContext(Dispatchers.IO) {
        dao.findById(id)?.toDomain()
    }

    override suspend fun getNextOrder(): Int = withContext(Dispatchers.IO) {
        dao.maxOrder() + 1
    }

    override suspend fun insert(vararg rule: ReplaceRule) {
        withContext(Dispatchers.IO) {
            dao.insert(*rule.map { it.toEntity() }.toTypedArray())
        }
    }

    override suspend fun delete(rule: ReplaceRule) {
        withContext(Dispatchers.IO) {
            dao.delete(rule.toEntity())
        }
    }

    override suspend fun setEnabled(id: Long, enabled: Boolean) {
        withContext(Dispatchers.IO) {
            dao.updateEnabled(id, enabled)
        }
    }

    override suspend fun enableByIds(ids: Set<Long>) {
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext
            dao.updateEnabled(ids.toList(), true)
        }
    }

    override suspend fun disableByIds(ids: Set<Long>) {
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext
            dao.updateEnabled(ids.toList(), false)
        }
    }

    override suspend fun deleteByIds(ids: Set<Long>) {
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext

            val rules = dao.getByIds(ids)
            dao.delete(*rules.toTypedArray())
        }
    }

    override suspend fun toTop(rule: ReplaceRule, isDesc: Boolean) {
        withContext(Dispatchers.IO) {
            if (isDesc) {
                rule.order = dao.maxOrder() + 1
            } else {
                rule.order = dao.minOrder() - 1
            }
            dao.update(rule.toEntity())
        }
    }

    override suspend fun toBottom(rule: ReplaceRule, isDesc: Boolean) {
        withContext(Dispatchers.IO) {
            if (isDesc) {
                rule.order = dao.minOrder() - 1
            } else {
                rule.order = dao.maxOrder() + 1
            }
            dao.update(rule.toEntity())
        }
    }

    override suspend fun topByIds(ids: Set<Long>, isDesc: Boolean) {
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext
            val rules = dao.getByIds(ids)
            if (isDesc) {
                var maxOrder = dao.maxOrder()
                val updated = rules.map {
                    maxOrder++
                    it.copy(order = maxOrder)
                }
                dao.update(*updated.toTypedArray())
            } else {
                var minOrder = dao.minOrder()
                val updated = rules.map {
                    minOrder--
                    it.copy(order = minOrder)
                }
                dao.update(*updated.toTypedArray())
            }
        }
    }

    override suspend fun bottomByIds(ids: Set<Long>, isDesc: Boolean) {
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext

            val rules = dao.getByIds(ids)
            if (isDesc) {
                var minOrder = dao.minOrder()
                val updated = rules.map {
                    minOrder--
                    it.copy(order = minOrder)
                }
                dao.update(*updated.toTypedArray())
            } else {
                var maxOrder = dao.maxOrder()
                val updated = rules.map {
                    maxOrder++
                    it.copy(order = maxOrder)
                }
                dao.update(*updated.toTypedArray())
            }
        }
    }

    override suspend fun moveReplaceRule(draggedId: Long, anchorId: Long, afterAnchor: Boolean) {
        withContext(Dispatchers.IO) {
            val rules = dao.all()
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

    override suspend fun moveOrder(currentRules: List<ReplaceRule>, isDesc: Boolean) {
        withContext(Dispatchers.IO) {
            val size = currentRules.size
            val updatedRules = currentRules.mapIndexed { index, rule ->
                val order = if (isDesc) size - index else index + 1
                rule.toEntity().also { it.order = order }
            }
            dao.update(*updatedRules.toTypedArray())
        }
    }

    override suspend fun addGroup(group: String) {
        withContext(Dispatchers.IO) {
            val sources = dao.noGroup()
            sources.forEach { source ->
                source.group = group
            }
            dao.update(*sources.toTypedArray())
        }
    }

    override suspend fun upGroup(oldGroup: String, newGroup: String?) {
        withContext(Dispatchers.IO) {
            val sources = dao.getByGroup(oldGroup)
            sources.forEach { source ->
                source.group?.splitNotBlank(",")?.toHashSet()?.let {
                    it.remove(oldGroup)
                    if (!newGroup.isNullOrEmpty())
                        it.add(newGroup)
                    source.group = it.joinToString(",")
                }
            }
            dao.update(*sources.toTypedArray())
        }
    }

    override suspend fun delGroup(group: String) {
        withContext(Dispatchers.IO) {
            val sources = dao.getByGroup(group)
            sources.forEach { source ->
                source.group?.splitNotBlank(",")?.toHashSet()?.let {
                    it.remove(group)
                    source.group = it.joinToString(",")
                }
            }
            dao.update(*sources.toTypedArray())
        }
    }

    override suspend fun clearGroups(groups: List<String>) {
        withContext(Dispatchers.IO) {
            dao.clearGroups(groups)
        }
    }
}

private fun List<ReplaceRuleEntity>.toDomainList(): List<ReplaceRule> = map { it.toDomain() }
