package io.legado.app.domain.rules

import kotlinx.coroutines.flow.Flow

/**
 * 标签分组规则的仓储**端口**（M3-6）。
 *
 * 由 `:data:rules` 的 `TagGroupRuleRepositoryImpl` 实现：过渡期它直接持有 `:core:data` 的
 * `TagGroupRuleDao`（Room 的 DAO/实体仍归 `:core:data`），把方法逐条委派给 DAO 并在边界上做
 * 实体 ↔ 领域模型 的映射。等 `data:database`（Room 唯一 owner）拆出来，实现只需换 DAO 来源，
 * 本接口与调用方不动。
 *
 * ⚠️ **本端口是重命名**：迁移前叫 `:core:data` 的 `io.legado.app.data.repository.TagGroupRuleRepository`
 * （同名但不同物）。名字保留是因为它与领域模型 [TagGroupRule] 同名、也与同域其余五个端口
 * 的命名一致，所以没有可改之处；被删除的旧类**不留门面、不留 typealias**。
 *
 * ⚠️ **只暴露实际被消费的方法**。旧 `TagGroupRuleDao` 上的三处零调用方方法随本片不进端口：
 * - `getAll()`（全量读）——全仓的读路径都走 `flowAll()` 或 DAO 直连（`Backup.kt`、
 *   `TagGroupRuleApplier`），没有经本端口要 `List` 的调用方；
 * - `getByIds(ids)`——旧实现里只有 `deleteByIds` 自己用，现在那一步在实现内部调 DAO，不必上浮；
 * - `getMaxOrder()`——零调用方（`BookGroupMutationRepository` 用 `tagGroupRuleDao.maxOrder()` 直连）。
 *   它们留在 DAO 上，属留给 `data:database` 的既有债务（与 `RuleSubDao.maxOrder()`、
 *   `TxtTocRuleDao.enabled()` 同理）。
 *
 * ⚠️ 方法签名**逐字保留**迁移前的形态，不要"顺手统一"：`insert` / `update` / `delete` 都是
 * `vararg`（调用方 `TagGroupRuleViewModel` 与 `TagGroupRuleTransferSpec` 正是按 `vararg` 用的，
 * 传 `*rules.toTypedArray()`），而 `deleteByIds` 收 `Set<Long>`、`moveOrder` 收 `List`。
 *
 * 语义约束（换实现即改行为，逐条与迁移前的实现对齐）：
 * - 迁移前的实现**每个方法都包 `withContext(Dispatchers.IO)`**（只有 `flowAll` 不包），实现侧
 *   照抄——这与 M3-5 的 `RuleSubRepositoryImpl`（全程不加 IO）**不同**：那边迁前就没包，
 *   因为 Room 的 `suspend` DAO 自带调度；两边不要互相"对齐"，多包一层会改变实际调度行为；
 * - `moveOrder` 是**就地重排**：按列表下标把每条规则的 `order` 改写成 `index`（从 **0** 起，
 *   与 `RuleSubRepository.saveOrder` 的 `serialNumber = index + 1` 从 1 起不同）；
 * - `deleteByIds` 在 `ids` 为空时**直接返回**（不落库）。
 */
interface TagGroupRuleRepository {

    /** 全部规则，按 `order` 升序（底层是 DAO 的 `flowAll`）。 */
    fun flowAll(): Flow<List<TagGroupRule>>

    /** 按主键查（导入流程用它取旧规则做对比）。 */
    suspend fun findById(id: Long): TagGroupRule?

    /** 按分组名查（分组管理页「编辑分组」时回填规则）。 */
    suspend fun getByGroupName(groupName: String): TagGroupRule?

    suspend fun insert(vararg rule: TagGroupRule)

    suspend fun update(vararg rule: TagGroupRule)

    suspend fun delete(vararg rule: TagGroupRule)

    /** 按主键批量删除；`ids` 为空时不落库。 */
    suspend fun deleteByIds(ids: Set<Long>)

    /** 拖拽排序后落库：按下标改写 `order`（从 0 起）。 */
    suspend fun moveOrder(rules: List<TagGroupRule>)
}
