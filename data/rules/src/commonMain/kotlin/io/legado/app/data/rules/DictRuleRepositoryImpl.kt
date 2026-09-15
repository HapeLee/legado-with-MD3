package io.legado.app.data.rules

import io.legado.app.data.dao.DictRuleDao
import io.legado.app.data.entities.DictRule as DictRuleEntity
import io.legado.app.domain.rules.DictRule
import io.legado.app.domain.rules.DictRuleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * [DictRuleRepository] 的实现（M3-3）。
 *
 * **本类是 `:core:data` 的 `data/repository/DictRuleRepository.kt` 的整份搬迁**：
 * 方法体、`Dispatchers.IO` 包裹、空集合短路、`copy(enabled = …)` 的批量改写方式全部逐条保留，
 * 唯一的差异是公开签名从 Room 实体换成领域模型 [DictRule]，于是在 DAO 边界上多了映射
 * （[toDomain] / [toEntity]，见 [DictRuleMapper]）。
 *
 * 与 M3-1/M3-2 一致的形态：构造参数**直接收 DAO**（原实现收的是 `AppDatabase`）——
 * `:app` 的 `appDatabaseModule` 本来就绑定了 `DictRuleDao`，注入点因此更窄，也让
 * `data:<域>` 不必认识 `AppDatabase` 这个 Room 唯一 owner 的聚合根。行为零变化
 * （DAO 来源只是从 `appDatabase.dictRuleDao` 提到构造参数）。
 *
 * ⚠️ **本片有一处不是"逐字搬迁"**：端口的 `getEnabled` 从同步阻塞改成 `suspend`。
 * 原实现是 `runBlocking { dao.enabled() }`，而 `runBlocking` 是 JVM/Native 的 `actual`、
 * 在 `commonMain` 里只是踩了"`compileCommonMainKotlinMetadata` 全 SKIPPED"这个门禁盲区
 * 才编得过。这层含义见 [DictRuleRepository.getEnabled] 的 KDoc；实现侧对应地包
 * `withContext(Dispatchers.IO)`，与其余方法一致。
 *
 * ⚠️ 过渡期依赖：Room 的 DAO 与实体仍归 `:core:data`（`data:database`「Room 唯一 owner」
 * 尚未拆出），所以本模块 `implementation(project(":core:data"))`。等它落地后，本类只换
 * DAO 的来源，构造签名与调用方都不动。
 */
class DictRuleRepositoryImpl(
    private val dao: DictRuleDao,
) : DictRuleRepository {

    override fun flowAll(): Flow<List<DictRule>> {
        return dao.flowAll().map { it.toDomainList() }
    }

    override suspend fun getEnabled(): List<DictRule> = withContext(Dispatchers.IO) {
        dao.enabled().toDomainList()
    }

    override suspend fun insert(vararg rule: DictRule) {
        withContext(Dispatchers.IO) {
            dao.insert(*rule.toEntityArray())
        }
    }

    override suspend fun delete(vararg rule: DictRule) {
        withContext(Dispatchers.IO) {
            dao.delete(*rule.toEntityArray())
        }
    }

    override suspend fun update(vararg rule: DictRule) {
        withContext(Dispatchers.IO) {
            dao.update(*rule.toEntityArray())
        }
    }

    override suspend fun replacePrimaryKey(oldName: String, rule: DictRule) {
        withContext(Dispatchers.IO) {
            dao.replacePrimaryKey(oldName, rule.toEntity())
        }
    }

    override suspend fun findById(id: String): DictRule? = withContext(Dispatchers.IO) {
        dao.getByName(id)?.toDomain()
    }

    // `names` 里的规则是"读出来 → 改 enabled → 整条写回"，全程在实体上完成，
    // 不经过领域模型——与迁移前的实现逐字一致（那时两边本来就是实体）。
    override suspend fun enableByIds(names: Set<String>) = withContext(Dispatchers.IO) {
        if (names.isEmpty()) return@withContext
        val rules = dao.getByNames(names)
        val updated = rules.map { it.copy(enabled = true) }
        dao.update(*updated.toTypedArray())
    }

    override suspend fun disableByIds(names: Set<String>) = withContext(Dispatchers.IO) {
        if (names.isEmpty()) return@withContext
        val rules = dao.getByNames(names)
        val updated = rules.map { it.copy(enabled = false) }
        dao.update(*updated.toTypedArray())
    }

    override suspend fun deleteByIds(names: Set<String>) = withContext(Dispatchers.IO) {
        if (names.isEmpty()) return@withContext
        val rules = dao.getByNames(names)
        dao.delete(*rules.toTypedArray())
    }

    // ⚠️ 序号从 1 开始（`index + 1`），与 `HighlightTagRuleRepositoryImpl` 的 `index` 不同
    // ——这是迁移前的实现语义，不是笔误。
    override suspend fun moveOrder(rules: List<DictRule>) = withContext(Dispatchers.IO) {
        val updatedRules = rules.mapIndexed { index, rule ->
            rule.copy(sortNumber = index + 1)
        }
        dao.update(*updatedRules.toEntityArray())
    }
}

// ---------- 边界映射的小工具 ----------
//
// 一对一的两条（`toDomain` / `toEntity`）在 DictRuleMapper.kt 里，这里只放集合形态的重载：
// 它们的唯一价值是让上面的方法体保持「一行一个 DAO 调用」的形状，不至于被
// `.map { it.toDomain() }.toTypedArray()` 之类的样板撑散。
//
// ⚠️ 两个重载方向必须与调用点对齐，否则 `*` 展开会撞上「receiver type mismatch」：
//   - `List<实体>` → 领域：`flowAll` / `getEnabled` 从 DAO 取回实体列表；
//   - `Array<out 领域>` → 实体：`insert` / `delete` / `update` 的 `vararg` 在函数体里是
//     `Array<out DictRule>`，`moveOrder` 的 `List` 版本另走下面那条。
// （`findById` / `replacePrimaryKey` 是一对一，直接用 `toDomain()` / `toEntity()`。）

internal fun List<DictRuleEntity>.toDomainList(): List<DictRule> =
    map { it.toDomain() }

internal fun Array<out DictRule>.toEntityArray(): Array<DictRuleEntity> =
    map { it.toEntity() }.toTypedArray()

internal fun List<DictRule>.toEntityArray(): Array<DictRuleEntity> =
    map { it.toEntity() }.toTypedArray()
