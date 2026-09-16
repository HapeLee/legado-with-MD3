package io.legado.app.domain.ai

import kotlinx.coroutines.flow.Flow

/**
 * AI 产物的**端口**（M4-3）。
 *
 * 由 `:data:ai` 的 `AiArtifactRepositoryImpl` 实现：过渡期它直接持有 `:core:data` 的
 * `AiArtifactDao`（Room 的 DAO/实体仍归 `:core:data`），把方法逐条委派给 DAO 并在边界上做
 * 实体 ↔ 领域模型 的映射。等 `data:database`（Room 唯一 owner）拆出来，实现只需换 DAO 来源，
 * 本接口与调用方不动。
 *
 * ⚠️ **本接口是搬迁，不是新造**：迁移前叫 `:core:data` 的
 * `io.legado.app.domain.gateway.AiArtifactGateway`（同名、同方法，只是收发的是 Room 实体）。
 * 名字沿用而不改名，理由与 M4-1 / M4-2 一致：本仓 `domain/gateway` 下有 60+ 个同形态的既有
 * 契约，改名会把「既有契约下沉」变成一次无收益的 rename churn。旧文件随本片删除，
 * **不留门面、不留 typealias**。
 *
 * ⚠️ **五个方法全有真实调用方**，因此本片没有随片删除的端口方法（与 M4-1 删 `savePreset`、
 * M4-2 一次删 5 个不同）：
 * - [observeBookArtifacts]：`ReadBookViewModel`；
 * - [getCachedArtifact]：4 个 UseCase（`AiTextFactoryUseCase` / `CleanSelectedTextUseCase` /
 *   `GenerateChapterSummaryUseCase` / `IdentifyBookCharactersUseCase`）——缓存命中判定；
 * - [getArtifactsByContentHash]：`IdentifyBookCharactersUseCase`；
 * - [queryArtifacts]：`AiToolRepository`（**本片新收进端口**，见下）；
 * - [upsertArtifact]：4 个 UseCase + `AiToolRepository` + `ReadAiDelegate`。
 * 对应的 DAO 方法 `deleteBookArtifacts` 本来就是零调用方（迁移前就不是端口方法），本片同样不动
 * 它——留在 DAO 上，归 `data:database` 债务（同 `RuleSubDao.maxOrder()` /
 * `AiMemoryDao.observeGlobal()` 的处理）。
 *
 * ⚠️ **[queryArtifacts] 是本片「扩出来」的方法，不是搬迁**。迁移前 `:app` 的 `AiToolRepository`
 * **直连 `aiArtifactDao`** 调它（AI 工具 `get_ai_artifacts` / `save_ai_artifact`），那是 `:app`
 * 侧的 DAO 直连债务。本片构造的产物对象变成领域模型后，`aiArtifactDao.upsert(实体)` 与
 * `queryArtifacts()` 返回的实体都不再匹配 ⇒ 有两个选择：让 `:app` 自己调 `toEntity()`
 * （把映射细节漏到消费方），或**把它收进端口**。本片选后者：它**有真实调用方**（不是一个
 * 「为了架构完整」造出来的抽象），且收进端口后 `AiToolRepository` 不再持有任何 DAO。
 * 这收窄了 `:app` 的 DAO 直连面（`AiToolRepository` 从「1 个 DAO + 3 个 Gateway」变成
 * 「0 个 DAO + 4 个 Gateway」）。
 *
 * ⚠️ [queryArtifacts] 的前三个参数**全可空**，语义是「传 `null` 就不筛该维度」——与
 * [getCachedArtifact] 的 `chapterIndex`（单维度可空）不是一回事，实现里不得把三者折叠成
 * 一个「必填 + 默认值」的形态。另外它**不过滤 `status`**（与 [getCachedArtifact] /
 * [getArtifactsByContentHash] 都不同，那两个只返回成功产物）——调用方看得到失败产物。
 *
 * ⚠️ **[observeBookArtifacts] 是本模块（`domain/ai`）下沉后唯一的 `Flow` 端口方法**——
 * M4-2 的 `AiMemory` 把两个 `Flow` 方法随片删掉了，别以为本模块「没有 Flow 端口」。实现侧需要
 * 在 `Flow` 上做映射（`map { it.map(实体::toDomain) }`），而不是直接把 DAO 的 `Flow` 透传出去。
 *
 * 语义约束（换实现即行为对齐，逐条与迁移前的实现一致）：
 * - 迁移前的实现**不包 `withContext`**（四个方法都是裸调 DAO）——与 M4-1 / M4-2 的实现都不同
 *   （那两片迁前每个方法都包了 `Dispatchers.IO`）。Room 的 `suspend` DAO 自带调度，
 *   这里**不要**为了"对齐上一片"多包一层，那会改变实际调度行为；
 * - [getCachedArtifact] 的 `chapterIndex` 是**可空**参数，DAO 的 SQL 用
 *   `(:chapterIndex is null or chapterIndex = :chapterIndex)` 表达「不筛章节」⇒ 传 `null`
 *   与传具体值是两种语义，实现不得把 `null` 归一化成 `0`；
 * - [getCachedArtifact] 与 [getArtifactsByContentHash] 在 DAO 里都带 `status = 成功` 过滤
 *   （见 [AiArtifact.STATUS_SUCCESS]）⇒ 端口只返回成功产物，调用方不必再筛；[upsertArtifact]
 *   底层是 `@Insert(onConflict = REPLACE)`——**整行替换**，调用方必须传完整对象。
 */
interface AiArtifactGateway {

    /** 观察某本书某任务类型下的产物（按章节、更新时间排序）。 */
    fun observeBookArtifacts(bookUrl: String, taskType: String): Flow<List<AiArtifact>>

    /** 取命中的缓存产物（只返回成功状态；`chapterIndex` 传 `null` 表示不筛章节）。 */
    suspend fun getCachedArtifact(
        bookUrl: String,
        chapterIndex: Int?,
        taskType: String,
        contentHash: String,
        promptHash: String,
        modelProfileId: String,
    ): AiArtifact?

    /** 按内容哈希取成功产物（限 `limit` 条，按更新时间倒序）。 */
    suspend fun getArtifactsByContentHash(
        bookUrl: String,
        chapterIndex: Int,
        taskType: String,
        contentHash: String,
        limit: Int = 20,
    ): List<AiArtifact>

    /**
     * 通用查询（限 `limit` 条，按更新时间倒序）。三个筛选项**传 `null` 表示不筛该维度**；
     * **不过滤 `status`**（与 [getCachedArtifact] / [getArtifactsByContentHash] 不同）。
     *
     * 消费方：`:app` 的 `AiToolRepository`（AI 工具 `get_ai_artifacts`）。
     */
    suspend fun queryArtifacts(
        bookUrl: String?,
        taskType: String?,
        chapterIndex: Int?,
        limit: Int,
    ): List<AiArtifact>

    /** 写入/覆盖一条产物（按主键 `id`）。 */
    suspend fun upsertArtifact(artifact: AiArtifact)
}
