package io.legado.app.domain.rules

import kotlinx.coroutines.flow.Flow

/**
 * 字典规则的仓储**端口**（M3-3）。
 *
 * 由 `:data:rules` 的 `DictRuleRepositoryImpl` 实现：过渡期它直接持有 `:core:data` 的
 * `DictRuleDao`（Room 的 DAO/实体仍归 `:core:data`），把方法逐条委派给 DAO 并在边界上
 * 做 实体 ↔ 领域模型 的映射。等 `data:database`（Room 唯一 owner）拆出来，实现只需换 DAO
 * 来源，本接口与调用方不动。
 *
 * ⚠️ **只暴露实际被消费的方法**。迁移前 `:core:data` 的 `DictRuleRepository` 另有三个
 * 公开方法，都不进端口：
 * - `flowSearch(key)`：全仓**零调用方**（`TxtTocRuleRepository` / `SearchRepository` 的同名
 *   方法各自独立），随本片删除，不把死代码带进端口；
 * - `getAll()`：全仓**零调用方**，同上删除；
 * - `getByNames(names)`：只被本类内部的 `enableByIds` / `disableByIds` / `deleteByIds`
 *   使用，是实现细节，端口不暴露。
 *
 * ⚠️ 命名沿用迁移前的实现（`findById` 的 `id` 实为 `name`，主键是 String）——整个链路上
 * "id" 本来就指 `name`（Feature 侧 `_selectedIds: Set<String>`、`DictRuleItemUi.id: String`
 * 都是它），换个名字反而会让审查者怀疑是不是同一个方法。
 *
 * 语义约束（换实现即改行为，逐条与迁移前的实现对齐）：
 * - 除 `flowAll` 外所有方法在 `Dispatchers.IO` 上执行（迁移前每个方法都包了 `withContext(IO)`）；
 * - `enableByIds` / `disableByIds` / `deleteByIds` 在 `names` 为空时**直接返回**；
 * - `enableByIds` / `disableByIds` 是"读出来改再写回"（`copy(enabled = …)`），
 *   不是 DAO 上的单条 UPDATE——DAO 虽然另有 `updateEnabled`，但迁移前的实现没走它；
 * - `moveOrder` 落库时把序号统一重写成 `index + 1`（**从 1 开始**，与 `HighlightTagRule`
 *   的 `index` 不同）；
 * - `replacePrimaryKey` 是"按旧主键删 + 插新"的 `@Transaction`，住 DAO。
 */
interface DictRuleRepository {

    fun flowAll(): Flow<List<DictRule>>

    /**
     * 已启用的规则（`enabled = 1`），按 `sortNumber` 升序。
     *
     * ⚠️ **迁移前这是同步阻塞方法**（`runBlocking { dao.enabled() }`）。本片改成 `suspend`
     * 并去掉 `runBlocking`，因为 `runBlocking` 是 JVM/Native 的 `actual`、在 `commonMain`
     * 里只是踩了"`compileCommonMainKotlinMetadata` 全 SKIPPED"这个门禁盲区才编得过；
     * 搬进带 desktop target 的 `:data:rules` 后不该把这种 JVM-only API 一起带过去。
     *
     * 行为等价：唯一调用方 `:app` 的 `DictViewModel.load` 本来就在
     * `withContext(Dispatchers.IO) { … }` 里调它，改 `suspend` 后**逐字兼容**，
     * 执行线程与阻塞语义都不变（原来也是在 IO 线程上跑，只是白白占着一个线程等待）。
     */
    suspend fun getEnabled(): List<DictRule>

    suspend fun insert(vararg rule: DictRule)

    suspend fun delete(vararg rule: DictRule)

    suspend fun update(vararg rule: DictRule)

    /** 主键（`name`）变更：按 [oldName] 删除后插入 [rule]。 */
    suspend fun replacePrimaryKey(oldName: String, rule: DictRule)

    suspend fun findById(id: String): DictRule?

    suspend fun enableByIds(names: Set<String>)

    suspend fun disableByIds(names: Set<String>)

    suspend fun deleteByIds(names: Set<String>)

    /** 按传入顺序重写全部规则的 `sortNumber`（`index + 1`）。 */
    suspend fun moveOrder(rules: List<DictRule>)
}
