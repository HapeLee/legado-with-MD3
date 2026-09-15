package io.legado.app.data.rules

import io.legado.app.data.dao.RuleSubDao
import io.legado.app.data.entities.RuleSub as RuleSubEntity
import io.legado.app.domain.rules.RuleSub
import io.legado.app.domain.rules.RuleSubRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * [RuleSubRepository] 的实现（M3-5）。
 *
 * **本类是 `:core:data` 的 `data/repository/RuleSubscriptionRepository.kt` 的整份搬迁**：
 * 方法体与 DAO 调用形态逐条保留，唯一的差异是公开签名从 Room 实体换成领域模型 [RuleSub]，
 * 于是在 DAO 边界上多了映射（[toDomain] / [toEntity]，见 `RuleSubMapper.kt`），而类名随端口
 * 从 `RuleSubscriptionRepository` 改为 `RuleSubRepositoryImpl`。
 *
 * 与前四片一致的形态：构造参数**直接收 DAO**（原实现收的就是 DAO，这里只是把它提成构造参数
 * 并换来源）——`appDatabaseModule` 本来就绑定了 `RuleSubDao`，注入点因此更窄。
 *
 * ⚠️ **本片没有「同步阻塞改 suspend」这一项**：迁名前 `all()` 就已经是 `suspend`
 * （直接调 `dao.all()`，**没有** `runBlocking`），所以端口侧没有签名性质的改动。
 *
 * ⚠️ **本实现全程不加 `withContext(Dispatchers.IO)`**，这在同域五个实现里是唯一的——
 * 迁前实现就没有包，因为它每个方法都直接调 Room 的 `suspend` DAO，而 Room 的 `suspend`
 * 查询自带调度（它把工作投到自己的 query executor）。`TxtTocRuleRepositoryImpl` 每个方法都包
 * `Dispatchers.IO` 是因为那边**迁前就是** `withContext(IO)`（外加 `runBlocking`），两者不要
 * 互相"对齐"，包一层会改变实际的调度行为。
 *
 * ⚠️ `RuleSubDao.maxOrder()` 全仓零调用方，随本片不进端口（它留在 DAO 上，见端口 KDoc）。
 *
 * ⚠️ 过渡期依赖：Room 的 DAO 与实体仍归 `:core:data`（`data:database`「Room 唯一 owner」
 * 尚未拆出），所以本模块 `implementation(project(":core:data"))`。等它落地后，本类只换
 * DAO 的来源，构造签名与调用方都不动。
 */
class RuleSubRepositoryImpl(
    private val dao: RuleSubDao,
) : RuleSubRepository {

    override fun observeAll(): Flow<List<RuleSub>> = dao.flowAll().map { it.toDomainList() }

    override suspend fun findByUrl(url: String): RuleSub? = dao.findByUrl(url)?.toDomain()

    override suspend fun insert(rule: RuleSub) = dao.insert(rule.toEntity())

    override suspend fun delete(rule: RuleSub) = dao.delete(rule.toEntity())

    override suspend fun all(): List<RuleSub> = dao.all().toDomainList()

    override suspend fun update(vararg rules: RuleSub) = dao.update(*rules.toEntityArray())
}

// ---------- 边界映射的小工具 ----------
//
// 一对一的两条（`toDomain` / `toEntity`）在 `RuleSubMapper.kt` 里，这里只放集合形态的重载。
// 本片**只有两个**重载——`TxtTocRuleRepositoryImpl` 的第三个（`List<领域>` → 实体）是给
// `saveOrder` 用的，而本片没有保存排序的方法（排序重写在 `RuleSubViewModel` 里做，它调
// `all()` 读出领域模型、改完 `customOrder` 后走 `update(*rules.toTypedArray())`）。
//
// ⚠️ 两个重载方向必须与调用点对齐，否则 `*` 展开会撞上「receiver type mismatch」：
//   - `List<实体>` → 领域：`observeAll` / `all` 从 DAO 取回实体列表；
//   - `Array<out 领域>` → 实体：`update` 的 `vararg` 在函数体里是 `Array<out RuleSub>`
//     （不是 `List`）。
// （`findByUrl` 是一对一，直接用 `toDomain()`。）

internal fun List<RuleSubEntity>.toDomainList(): List<RuleSub> = map { it.toDomain() }

internal fun Array<out RuleSub>.toEntityArray(): Array<RuleSubEntity> =
    map { it.toEntity() }.toTypedArray()
