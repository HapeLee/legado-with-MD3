package io.legado.app.data.rules

import io.legado.app.data.dao.HighlightTagRuleDao
import io.legado.app.data.entities.HighlightTagRule as HighlightTagRuleEntity
import io.legado.app.domain.rules.HighlightTagRule
import io.legado.app.domain.rules.HighlightTagRuleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * [HighlightTagRuleRepository] 的实现（M3-2）。
 *
 * **本类是 `:core:data` 的 `data/repository/HighlightTagRuleRepository.kt` 的整份搬迁**：
 * 方法体、`Dispatchers.IO` 包裹、空集合短路、`copy(enabled = …)` 的批量改写方式全部逐条保留，
 * 唯一的差异是公开签名从 Room 实体换成领域模型 [HighlightTagRule]，于是在 DAO 边界上多了映射
 * （[toDomain] / [toEntity]，见 [HighlightTagRuleMapper]）。
 *
 * 与 M3-1 的 [ReplaceRuleRepositoryImpl] 的唯一形态差异：原实现构造参数是
 * `AppDatabase`（内部取 `appDatabase.highlightTagRuleDao`），这里改成**直接收 DAO**——
 * `:app` 的 `appDatabaseModule` 本来就绑定了 `HighlightTagRuleDao`，注入点因此更窄，
 * 也让 `data:<域>` 不必认识 `AppDatabase` 这个 Room 唯一 owner 的聚合根。
 * 行为零变化（DAO 来源只是从 `appDatabase.xxx` 提到构造参数）。
 *
 * ⚠️ 过渡期依赖：Room 的 DAO 与实体仍归 `:core:data`（`data:database`「Room 唯一 owner」
 * 尚未拆出），所以本模块 `implementation(project(":core:data"))`。等它落地后，本类只换
 * DAO 的来源，构造签名与调用方都不动。
 */
class HighlightTagRuleRepositoryImpl(
    private val dao: HighlightTagRuleDao,
) : HighlightTagRuleRepository {

    override fun flowAll(): Flow<List<HighlightTagRule>> {
        return dao.flowAll().map { it.toDomainList() }
    }

    override suspend fun getEnabled(): List<HighlightTagRule> = withContext(Dispatchers.IO) {
        dao.getEnabled().toDomainList()
    }

    override suspend fun insert(vararg rule: HighlightTagRule) {
        withContext(Dispatchers.IO) {
            dao.insert(*rule.toEntityArray())
        }
    }

    override suspend fun delete(vararg rule: HighlightTagRule) {
        withContext(Dispatchers.IO) {
            dao.delete(*rule.toEntityArray())
        }
    }

    override suspend fun update(vararg rule: HighlightTagRule) {
        withContext(Dispatchers.IO) {
            dao.update(*rule.toEntityArray())
        }
    }

    override suspend fun findById(id: Long): HighlightTagRule? = withContext(Dispatchers.IO) {
        dao.getById(id)?.toDomain()
    }

    override suspend fun getByIds(ids: Set<Long>): List<HighlightTagRule> =
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) emptyList() else dao.getByIds(ids).toDomainList()
        }

    override suspend fun enableByIds(ids: Set<Long>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        val rules = dao.getByIds(ids)
        val updated = rules.map { it.copy(enabled = true) }
        dao.update(*updated.toTypedArray())
    }

    override suspend fun disableByIds(ids: Set<Long>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        val rules = dao.getByIds(ids)
        val updated = rules.map { it.copy(enabled = false) }
        dao.update(*updated.toTypedArray())
    }

    override suspend fun deleteByIds(ids: Set<Long>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        val rules = dao.getByIds(ids)
        dao.delete(*rules.toTypedArray())
    }

    override suspend fun moveOrder(rules: List<HighlightTagRule>) = withContext(Dispatchers.IO) {
        val updatedRules = rules.mapIndexed { index, rule ->
            rule.copy(order = index)
        }
        dao.update(*updatedRules.toEntityArray())
    }
}

// ---------- 边界映射的小工具 ----------
//
// 一对一的两条（`toDomain` / `toEntity`）在 HighlightTagRuleMapper.kt 里，这里只放
// 集合形态的重载：它们的唯一价值是让上面的方法体保持「一行一个 DAO 调用」的形状，
// 不至于被 `.map { it.toDomain() }.toTypedArray()` 之类的样板撑散。
//
// ⚠️ 三个重载缺一不可，方向必须与调用点对齐，否则 `*` 展开会撞上
// 「receiver type mismatch」：
//   - `List<实体>` → 领域：`getEnabled` / `getByIds` 从 DAO 取回实体列表；
//   - `Array<out 领域>` → 实体：`insert` / `delete` / `update` 的 `vararg` 在函数体里是
//     `Array<out HighlightTagRule>`；`moveOrder` 的 `List` 版本另有重载。

internal fun List<HighlightTagRuleEntity>.toDomainList(): List<HighlightTagRule> =
    map { it.toDomain() }

internal fun Array<out HighlightTagRule>.toEntityArray(): Array<HighlightTagRuleEntity> =
    map { it.toEntity() }.toTypedArray()

internal fun List<HighlightTagRule>.toEntityArray(): Array<HighlightTagRuleEntity> =
    map { it.toEntity() }.toTypedArray()
