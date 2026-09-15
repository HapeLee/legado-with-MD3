package io.legado.app.domain.rules

import kotlinx.coroutines.flow.Flow

/**
 * 高亮标签规则的仓储**端口**（M3-2，样板见 M3-1 的 [ReplaceRuleRepository]）。
 *
 * 由 `:data:rules` 的 `HighlightTagRuleRepositoryImpl` 实现：过渡期它直接持有 `:core:data` 的
 * `HighlightTagRuleDao`（Room 的 DAO/实体仍归 `:core:data`），把方法逐条委派给 DAO 并在边界上
 * 做 实体 ↔ 领域模型 的映射。等 `data:database`（Room 唯一 owner）拆出来，实现只需换 DAO 来源，
 * 本接口与调用方不动。
 *
 * ⚠️ **只暴露实际被消费的方法**。迁移前 `:core:data` 的 `HighlightTagRuleRepository` 是
 * 9 个方法的全集，其中 `update(vararg)` 只有 Feature 的 `SaveRule`/`SetRuleEnabled` 与
 * `moveOrder` 内部用到，仍在；而 `delete(vararg)` 的调用方同样在 Feature 里，故全部保留。
 *
 * 语义约束（换实现即改行为，逐条与迁移前的实现对齐）：
 * - `getEnabled()` / `insert` / `delete` / `update` / `findById` / `getByIds` / `enableByIds` /
 *   `disableByIds` / `deleteByIds` / `moveOrder` 都在 `Dispatchers.IO` 上执行；
 * - `getByIds` / `enableByIds` / `disableByIds` / `deleteByIds` 在 `ids` 为空时**直接返回**
 *   （`getByIds` 返回 `emptyList()`，其余三个不落库）；
 * - `enableByIds` / `disableByIds` 先按 id 查出实体再 `copy(enabled = …)` 后整体 `update`，
 *   不写自定义 SQL —— 换取「沿用 DAO 的 REPLACE 语义」；
 * - `moveOrder` 接收的是**已经排好序**的列表，按下标重写 `order`，空列表也会走一遍 `update`
 *   （迁移前即如此，不改）。
 */
interface HighlightTagRuleRepository {

    fun flowAll(): Flow<List<HighlightTagRule>>

    suspend fun getEnabled(): List<HighlightTagRule>

    suspend fun insert(vararg rule: HighlightTagRule)

    suspend fun delete(vararg rule: HighlightTagRule)

    suspend fun update(vararg rule: HighlightTagRule)

    suspend fun findById(id: Long): HighlightTagRule?

    suspend fun getByIds(ids: Set<Long>): List<HighlightTagRule>

    suspend fun enableByIds(ids: Set<Long>)

    suspend fun disableByIds(ids: Set<Long>)

    suspend fun deleteByIds(ids: Set<Long>)

    /** 按 [rules] 的**下标**重写 `order`（调用方负责先排好序），然后整体落库。 */
    suspend fun moveOrder(rules: List<HighlightTagRule>)
}
