package io.legado.app.domain.rules

import kotlinx.coroutines.flow.Flow

/**
 * 规则订阅的仓储**端口**（M3-5）。
 *
 * 由 `:data:rules` 的 `RuleSubRepositoryImpl` 实现：过渡期它直接
 * 持有 `:core:data` 的 `RuleSubDao`（Room 的 DAO/实体仍归 `:core:data`），把方法逐条委派给
 * DAO 并在边界上做 实体 ↔ 领域模型 的映射。等 `data:database`（Room 唯一 owner）拆出来，
 * 实现只需换 DAO 来源，本接口与调用方不动。
 *
 * ⚠️ **本端口是重命名**：迁移前叫 `:core:data` 的 `RuleSubscriptionRepository`。改名是为了与
 * 领域模型 [RuleSub] 及同域其余四个端口（`ReplaceRuleRepository` / `HighlightTagRuleRepository`
 * / `DictRuleRepository` / `TxtTocRuleRepository`）的命名一致，全仓只有两个调用点
 * （`appModule` 的绑定与 `RuleSubViewModel` 的构造）。被删除的旧类**不留门面、不留 typealias**。
 *
 * ⚠️ **只暴露实际被消费的方法**。`RuleSubDao` 的 `maxOrder()`（`select customOrder from ruleSubs
 * order by customOrder limit 0,1`）全仓**零调用方**，随本片不进端口；它留在 DAO 上，属留给
 * `data:database` 的既有债务（与 `TxtTocRuleDao.enabled()` 同理，但那一个还有 `TextFile.kt`
 * 直连，所以当时保留的理由不同）。
 *
 * ⚠️ 方法签名**逐字保留**迁移前的形态，不要"顺手统一"：`insert` / `delete` 收**单条**，
 * 只有 `update` 是 `vararg`——唯一调用方 `RuleSubViewModel` 正是这么用的
 * （`insert(rule)` / `delete(rule)` / `update(*rules.toTypedArray())`）。
 *
 * 语义约束（换实现即改行为，逐条与迁移前的实现对齐）：
 * - 迁移前的实现**没有** `withContext(Dispatchers.IO)`——Room 的 `suspend` DAO 方法自带调度，
 *   所以实现侧同样不加（这与 `TxtTocRuleRepositoryImpl` 每个方法都包 `Dispatchers.IO` 不同，
 *   两边不要互相"对齐"）；
 * - `observeAll` 的名字保留自迁移前（底层 DAO 方法叫 `flowAll`，两者本来就不同名）；
 * - Room 的 `@Insert` / `@Update` 都是 `OnConflictStrategy.REPLACE`；
 * - ⚠️ 迁名前 `all()` **已经是 `suspend`**（直接调 `dao.all()`，无 `runBlocking`），
 *   所以本片没有 M3-4 那种「同步阻塞改 suspend」的改动。
 */
interface RuleSubRepository {

    /** 全部规则，按 `customOrder` 升序（底层是 DAO 的 `flowAll`）。 */
    fun observeAll(): Flow<List<RuleSub>>

    /** 按 url 查重（`RuleSubViewModel.save` 用它拦重复订阅）。 */
    suspend fun findByUrl(url: String): RuleSub?

    suspend fun insert(rule: RuleSub)

    suspend fun delete(rule: RuleSub)

    /** 全部规则，按 `customOrder` 升序。 */
    suspend fun all(): List<RuleSub>

    /** 批量更新（`RuleSubViewModel` 的排序重排走这里）。 */
    suspend fun update(vararg rules: RuleSub)
}
