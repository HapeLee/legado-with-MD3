package io.legado.app.domain.rules

import kotlinx.coroutines.flow.Flow

/**
 * 替换规则的仓储**端口**（M3-1）。
 *
 * 由 `:data:rules` 的 `ReplaceRuleRepositoryImpl` 实现：过渡期它直接持有 `:core:data` 的
 * `ReplaceRuleDao`（Room 的 DAO/实体仍归 `:core:data`），把方法逐条委派给 DAO 并在边界上
 * 做 实体 ↔ 领域模型 的映射。等 `data:database`（Room 唯一 owner）拆出来，实现只需换 DAO 来源，
 * 本接口与调用方不动。
 *
 * ⚠️ **只暴露实际被消费的方法**。迁移前 `:core:data` 的 `ReplaceRuleRepository` 另有
 * `flowSearch(key)` 与 `upOrder()` 两个公开方法，全仓**零调用方**
 * （`upOrder()` 与书源仓储的同名方法无关，后者另有 5 处调用点），随本片一并删除，
 * 不把死代码带进端口。
 *
 * 语义约束（换实现即改行为，逐条与迁移前的实现对齐）：
 * - 所有 `suspend` 方法在 `Dispatchers.IO` 上执行（迁移前每个方法都包了 `withContext(IO)`）；
 * - `enableByIds` / `disableByIds` / `deleteByIds` / `topByIds` / `bottomByIds` 在 `ids` 为空时**直接返回**；
 * - `moveReplaceRule` 在 `draggedId` 或 `anchorId` 查不到时**静默返回**，落库时统一重写全部序号；
 * - `getNextOrder()` = `maxOrder() + 1`（`sortOrder` 可为负、可重复，故不能用 `COUNT`）。
 */
interface ReplaceRuleRepository {

    fun flowGroups(): Flow<List<String>>

    fun flowAll(): Flow<List<ReplaceRule>>

    fun flowNoGroup(): Flow<List<ReplaceRule>>

    fun flowGroupSearch(key: String): Flow<List<ReplaceRule>>

    suspend fun findById(id: Long): ReplaceRule?

    suspend fun getNextOrder(): Int

    suspend fun insert(vararg rule: ReplaceRule)

    suspend fun delete(rule: ReplaceRule)

    suspend fun setEnabled(id: Long, enabled: Boolean)

    suspend fun enableByIds(ids: Set<Long>)

    suspend fun disableByIds(ids: Set<Long>)

    suspend fun deleteByIds(ids: Set<Long>)

    suspend fun toTop(rule: ReplaceRule, isDesc: Boolean = false)

    suspend fun toBottom(rule: ReplaceRule, isDesc: Boolean = false)

    suspend fun topByIds(ids: Set<Long>, isDesc: Boolean = false)

    suspend fun bottomByIds(ids: Set<Long>, isDesc: Boolean = false)

    /**
     * 把 [draggedId] 规则移动到 [anchorId] 规则旁边（[afterAnchor] 为 true 时在其后，否则在其前）。
     * 列表顺序始终按 sortOrder 升序，移动后统一重写全部规则序号。
     */
    suspend fun moveReplaceRule(draggedId: Long, anchorId: Long, afterAnchor: Boolean)

    suspend fun moveOrder(currentRules: List<ReplaceRule>, isDesc: Boolean = false)

    suspend fun addGroup(group: String)

    suspend fun upGroup(oldGroup: String, newGroup: String?)

    suspend fun delGroup(group: String)

    suspend fun clearGroups(groups: List<String>)
}
