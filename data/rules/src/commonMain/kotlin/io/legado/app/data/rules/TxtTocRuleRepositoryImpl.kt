package io.legado.app.data.rules

import io.legado.app.data.dao.TxtTocRuleDao
import io.legado.app.data.entities.TxtTocRule as TxtTocRuleEntity
import io.legado.app.domain.rules.TxtTocRule
import io.legado.app.domain.rules.TxtTocRuleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * [TxtTocRuleRepository] 的实现（M3-4）。
 *
 * **本类是 `:core:data` 的 `data/repository/TxtTocRuleRepository.kt` 的整份搬迁**：
 * 方法体、`Dispatchers.IO` 包裹、`copy(enable = …)` 的批量改写方式全部逐条保留，唯一的差异
 * 是公开签名从 Room 实体换成领域模型 [TxtTocRule]，于是在 DAO 边界上多了映射
 * （[toDomain] / [toEntity]，见 [TxtTocRuleMapper]）。
 *
 * 与前四片一致的形态：构造参数**直接收 DAO**（原实现收的就是 DAO，这里只是把它提成构造
 * 参数并换来源）——`appDatabaseModule` 本来就绑定了 `TxtTocRuleDao`，注入点因此更窄。
 *
 * ⚠️ **本片有两处不是"逐字搬迁"**，都在端口那一侧：
 * 1. `all()` / `count()` 从同步阻塞改成 `suspend`。原实现是 `runBlocking { dao.… }`，而
 *    `runBlocking` 是 JVM/Native 的 `actual`、在 `commonMain` 里只是踩了
 *    "`compileCommonMainKotlinMetadata` 全 SKIPPED"这个门禁盲区才编得过。这层含义见
 *    [TxtTocRuleRepository.all] / [TxtTocRuleRepository.count] 的 KDoc；实现侧对应地包
 *    `withContext(Dispatchers.IO)`，与其余方法一致。
 * 2. `flowSearch(key)` 与 `enabled()` 全仓零调用方，随本片删除、不进端口（`TextFile.kt`
 *    走的是 `appDb.txtTocRuleDao.enabled()` 直连，卡 `data:database` 那条里程碑级闸门，
 *    所以 `TxtTocRuleDao.enabled()` **保留**）。
 *
 * ⚠️ [saveOrder] **原地修改入参**的 `serialNumber`（迁移前也是这么写的：先
 * `rules.forEachIndexed { … serialNumber = index + 1 }` 再 `dao.update`）。这不是可以
 * "顺手改成纯函数"的地方——调用方 `TxtTocRuleViewModel.saveSortOrder` 的本地暂存列表
 * 依赖这次回写与库对齐。
 *
 * ⚠️ 过渡期依赖：Room 的 DAO 与实体仍归 `:core:data`（`data:database`「Room 唯一 owner」
 * 尚未拆出），所以本模块 `implementation(project(":core:data"))`。等它落地后，本类只换
 * DAO 的来源，构造签名与调用方都不动。
 */
class TxtTocRuleRepositoryImpl(
    private val dao: TxtTocRuleDao,
) : TxtTocRuleRepository {

    override fun flowAll(): Flow<List<TxtTocRule>> {
        return dao.observeAll().map { it.toDomainList() }
    }

    override suspend fun insert(vararg rules: TxtTocRule) {
        withContext(Dispatchers.IO) {
            dao.insert(*rules.toEntityArray())
        }
    }

    override suspend fun update(vararg rules: TxtTocRule) {
        withContext(Dispatchers.IO) {
            dao.update(*rules.toEntityArray())
        }
    }

    override suspend fun delete(vararg rules: TxtTocRule) {
        withContext(Dispatchers.IO) {
            dao.delete(*rules.toEntityArray())
        }
    }

    override suspend fun findById(id: Long): TxtTocRule? = withContext(Dispatchers.IO) {
        dao.get(id)?.toDomain()
    }

    // `ids` 里的规则是"读出来 → 整条删"，全程在实体上完成，不经过领域模型——与迁移前的
    // 实现逐字一致（那时两边本来就是实体）。⚠️ 这里**没有**空集合短路：原实现也没写，
    // 保持原样（`HighlightTagRuleRepositoryImpl` 那边有空集合短路，两片不要互相"对齐"）。
    override suspend fun deleteByIds(ids: Collection<Long>) = withContext(Dispatchers.IO) {
        val rules = dao.getByIds(ids.toSet())
        dao.delete(*rules.toTypedArray())
    }

    override suspend fun enableByIds(ids: Collection<Long>, enable: Boolean) =
        withContext(Dispatchers.IO) {
            val rules = dao.getByIds(ids.toSet())
            val updated = rules.map { it.copy(enable = enable) }
            dao.update(*updated.toTypedArray())
        }

    // ⚠️ 序号从 1 开始（`index + 1`），与 `HighlightTagRuleRepositoryImpl` 的 `index` 不同
    // ——这是迁移前的实现语义，不是笔误。且是**原地改入参**，见类注释。
    override suspend fun saveOrder(rules: List<TxtTocRule>) = withContext(Dispatchers.IO) {
        rules.forEachIndexed { index, rule ->
            rule.serialNumber = index + 1
        }
        dao.update(*rules.toEntityArray())
    }

    override suspend fun all(): List<TxtTocRule> = withContext(Dispatchers.IO) {
        dao.all().toDomainList()
    }

    override suspend fun count(): Int = withContext(Dispatchers.IO) {
        dao.count()
    }
}

// ---------- 边界映射的小工具 ----------
//
// 一对一的两条（`toDomain` / `toEntity`）在 TxtTocRuleMapper.kt 里，这里只放集合形态的重载：
// 它们的唯一价值是让上面的方法体保持「一行一个 DAO 调用」的形状，不至于被
// `.map { it.toDomain() }.toTypedArray()` 之类的样板撑散。
//
// ⚠️ 三个重载方向必须与调用点对齐，否则 `*` 展开会撞上「receiver type mismatch」：
//   - `List<实体>` → 领域：`flowAll` / `all` 从 DAO 取回实体列表；
//   - `Array<out 领域>` → 实体：`insert` / `delete` / `update` 的 `vararg` 在函数体里是
//     `Array<out TxtTocRule>`（不是 `List`）；
//   - `List<领域>` → 实体：`saveOrder` 收的是 `List`。
// （`findById` 是一对一，直接用 `toDomain()`。）

internal fun List<TxtTocRuleEntity>.toDomainList(): List<TxtTocRule> =
    map { it.toDomain() }

internal fun Array<out TxtTocRule>.toEntityArray(): Array<TxtTocRuleEntity> =
    map { it.toEntity() }.toTypedArray()

internal fun List<TxtTocRule>.toEntityArray(): Array<TxtTocRuleEntity> =
    map { it.toEntity() }.toTypedArray()
