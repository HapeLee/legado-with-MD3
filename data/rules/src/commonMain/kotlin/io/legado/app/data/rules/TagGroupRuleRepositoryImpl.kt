package io.legado.app.data.rules

import io.legado.app.data.dao.TagGroupRuleDao
import io.legado.app.data.entities.TagGroupRule as TagGroupRuleEntity
import io.legado.app.domain.rules.TagGroupRule
import io.legado.app.domain.rules.TagGroupRuleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * [TagGroupRuleRepository] 的实现（M3-6）。
 *
 * **本类是 `:core:data` 的 `data/repository/TagGroupRuleRepository.kt` 的整份搬迁**：方法体与
 * DAO 调用形态逐条保留，唯一的差异是公开签名从 Room 实体换成领域模型 [TagGroupRule]，于是在
 * DAO 边界上多了映射（[toDomain] / [toEntity]，见 `TagGroupRuleMapper.kt`），类名加了 `Impl`
 * 后缀。
 *
 * 与前四片一致的形态：构造参数**直接收 DAO**（原实现收的是 `AppDatabase`，这里只是把它的
 * `tagGroupRuleDao` 提成构造参数）——`appDatabaseModule` 本来就绑定了 `TagGroupRuleDao`
 * （`factory<TagGroupRuleDao> { get<AppDatabase>().tagGroupRuleDao }`），注入点因此更窄。
 *
 * ⚠️ **本实现完整保留 `withContext(Dispatchers.IO)`**（只有 `flowAll` 不包，因为它返回冷流，
 * 映射在收集侧执行）。这与 M3-5 的 `RuleSubRepositoryImpl`（全程不加 IO）不同，原因不是风格：
 * 本类的迁移前实现**每个方法都自己包了 IO**（`RuleSubRepository` 那边迁前就是裸调 DAO 的
 * `suspend` 方法），保留它是为了不改实际调度行为。两边不要互相"对齐"。
 *
 * ⚠️ **本片没有「同步阻塞改 suspend」这一项**：迁前 `getAll` / `getByGroupName` / `findById` /
 * `deleteByIds` / `moveOrder` / `getMaxOrder` 就已经是 `suspend`（没有一个用 `runBlocking`），
 * 所以端口侧没有签名性质的改动。
 *
 * ⚠️ 零调用方的三个方法随本片消失（详见端口 KDoc）：`getAll()` / `getByIds()` / `getMaxOrder()`。
 * `getByIds()` 消失了但 DAO 那一层仍在用——`deleteByIds` 的实现内部要它取回实体再删。
 *
 * ⚠️ 过渡期依赖：Room 的 DAO 与实体仍归 `:core:data`（`data:database`「Room 唯一 owner」
 * 尚未拆出），所以本模块 `implementation(project(":core:data"))`。等它落地后，本类只换
 * DAO 的来源，构造签名与调用方都不动。
 */
class TagGroupRuleRepositoryImpl(
    private val dao: TagGroupRuleDao,
) : TagGroupRuleRepository {

    override fun flowAll(): Flow<List<TagGroupRule>> = dao.flowAll().map { it.toDomainList() }

    override suspend fun findById(id: Long): TagGroupRule? = withContext(Dispatchers.IO) {
        dao.getById(id)?.toDomain()
    }

    override suspend fun getByGroupName(groupName: String): TagGroupRule? =
        withContext(Dispatchers.IO) {
            dao.getByGroupName(groupName)?.toDomain()
        }

    override suspend fun insert(vararg rule: TagGroupRule) {
        withContext(Dispatchers.IO) {
            dao.insert(*rule.toEntityArray())
        }
    }

    override suspend fun update(vararg rule: TagGroupRule) {
        withContext(Dispatchers.IO) {
            dao.update(*rule.toEntityArray())
        }
    }

    override suspend fun delete(vararg rule: TagGroupRule) {
        withContext(Dispatchers.IO) {
            dao.delete(*rule.toEntityArray())
        }
    }

    override suspend fun deleteByIds(ids: Set<Long>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        val rules = dao.getByIds(ids)
        dao.delete(*rules.toTypedArray())
    }

    override suspend fun moveOrder(rules: List<TagGroupRule>) = withContext(Dispatchers.IO) {
        val updatedRules = rules.mapIndexed { index, rule ->
            rule.copy(order = index)
        }
        dao.update(*updatedRules.toEntityArray())
    }
}

// ---------- 边界映射的小工具 ----------
//
// 一对一的两条（`toDomain` / `toEntity`）在 `TagGroupRuleMapper.kt` 里，这里只放集合形态的重载。
//
// ⚠️ 三个重载方向必须与调用点对齐，否则 `*` 展开会撞上「receiver type mismatch」：
//   - `List<实体>` → 领域：`flowAll` 从 DAO 取回实体列表；
//   - `Array<out 领域>` → 实体：`insert` / `update` / `delete` 的 `vararg` 在函数体里是
//     `Array<out TagGroupRule>`（不是 `List`）；
//   - `List<领域>` → 实体：`moveOrder` 收到的就是 `List`（`rules.mapIndexed { … }` 也是 `List`），
//     这与 `TxtTocRuleRepositoryImpl` 的第三个重载同形（那边给 `saveOrder` 用）。
// （`findById` / `getByGroupName` 是一对一，直接用 `toDomain()`。）

internal fun List<TagGroupRuleEntity>.toDomainList(): List<TagGroupRule> = map { it.toDomain() }

internal fun Array<out TagGroupRule>.toEntityArray(): Array<TagGroupRuleEntity> =
    map { it.toEntity() }.toTypedArray()

internal fun List<TagGroupRule>.toEntityArray(): Array<TagGroupRuleEntity> =
    map { it.toEntity() }.toTypedArray()
