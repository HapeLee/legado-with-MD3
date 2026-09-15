package io.legado.app.domain.rules

import kotlinx.coroutines.flow.Flow

/**
 * TXT 目录规则的仓储**端口**（M3-4）。
 *
 * 由 `:data:rules` 的 `TxtTocRuleRepositoryImpl` 实现：过渡期它直接持有 `:core:data` 的
 * `TxtTocRuleDao`（Room 的 DAO/实体仍归 `:core:data`），把方法逐条委派给 DAO 并在边界上
 * 做 实体 ↔ 领域模型 的映射。等 `data:database`（Room 唯一 owner）拆出来，实现只需换 DAO
 * 来源，本接口与调用方不动。
 *
 * ⚠️ **只暴露实际被消费的方法**。迁移前 `:core:data` 的 `TxtTocRuleRepository` 还有两个
 * 公开方法，都不进端口：
 * - `flowSearch(key)`：全仓**零调用方**（`DictRuleRepository` / `SearchRepository` 的同名
 *   方法各自独立），随本片删除，不把死代码带进端口；
 * - `enabled()`：仓储层的同名方法**零调用方**（`app/model/localBook/TextFile.kt` 走的是
 *   `appDb.txtTocRuleDao.enabled()` 直连，属留给 `data:database` 的既有债务），随本片删除。
 *   `TxtTocRuleDao.enabled()` 本身保留——`TextFile.kt` 还要用。
 *
 * ⚠️ `saveOrder` 的语义要留意：它**原地修改**传入对象的 [TxtTocRule.serialNumber]
 * （`index + 1`，**从 1 开始**）再落库——链路上 Feature 侧就是靠这个回写把本地暂存列表
 * 与库对齐的，不是"传出去算好再传回来"。端口保留这一副作用，实现不得改成不可变风格。
 *
 * 语义约束（换实现即改行为，逐条与迁移前的实现对齐）：
 * - 除 `flowAll` 外所有方法在 `Dispatchers.IO` 上执行（迁移前每个方法都包了 `withContext(IO)`）；
 * - `deleteByIds` / `enableByIds` 是"读出来改再写回"（`copy(enable = …)`），
 *   不是 DAO 上的单条 UPDATE；
 * - `deleteByIds` 在 `ids` 为空时**不是**短路返回：它照旧查一次 DAO 再删空数组
 *   （迁移前就是这个形态，不"顺手优化"）；
 * - Room 的 `@Insert` / `@Update` 都是 `OnConflictStrategy.REPLACE`，且都是 `vararg`。
 */
interface TxtTocRuleRepository {

    fun flowAll(): Flow<List<TxtTocRule>>

    suspend fun insert(vararg rules: TxtTocRule)

    suspend fun update(vararg rules: TxtTocRule)

    suspend fun delete(vararg rules: TxtTocRule)

    suspend fun findById(id: Long): TxtTocRule?

    suspend fun deleteByIds(ids: Collection<Long>)

    /** 批量启用/禁用；读出来改再写回。 */
    suspend fun enableByIds(ids: Collection<Long>, enable: Boolean)

    /** 按传入顺序把 [TxtTocRule.serialNumber] 重写成 `index + 1` 并落库（**原地修改入参**）。 */
    suspend fun saveOrder(rules: List<TxtTocRule>)

    /**
     * 全部规则，按 `serialNumber` 升序。
     *
     * ⚠️ **迁移前这是同步阻塞方法**（`runBlocking { dao.all() }`）。本片改成 `suspend`
     * 并去掉 `runBlocking`，因为 `runBlocking` 是 JVM/Native 的 `actual`、在 `commonMain`
     * 里只是踩了"`compileCommonMainKotlinMetadata` 全 SKIPPED"这个门禁盲区才编得过；
     * 搬进带 desktop target 的 `:data:rules` 后不该把这种 JVM-only API 一起带过去。
     *
     * 行为等价：唯一调用方 `:app` 的 `TxtTocRulePreviewViewModel.getAllRules` 本来就是
     * `suspend` 函数、且在 `Dispatchers.IO` 的协程里跑，改 `suspend` 后逐字兼容。
     */
    suspend fun all(): List<TxtTocRule>

    /**
     * 规则总数。
     *
     * ⚠️ 同样由同步阻塞改 `suspend`（原 `runBlocking { dao.count() }`），理由见 [all]。
     * 唯一调用方 `TxtTocRulePreviewViewModel.getAllRules` 在同一个 `suspend` 函数里紧随
     * [all] 之后调用，改后逐字兼容。
     */
    suspend fun count(): Int
}
