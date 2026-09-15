package io.legado.app.domain.ai

/**
 * AI 提示词预设的**端口**（M4-1）。
 *
 * 由 `:data:ai` 的 `AiPromptPresetRepositoryImpl` 实现：过渡期它直接持有 `:core:data` 的
 * `AiPromptPresetDao`（Room 的 DAO/实体仍归 `:core:data`），把方法逐条委派给 DAO 并在边界上做
 * 实体 ↔ 领域模型 的映射。等 `data:database`（Room 唯一 owner）拆出来，实现只需换 DAO 来源，
 * 本接口与调用方不动。
 *
 * ⚠️ **本接口是搬迁，不是新造**：迁移前叫 `:core:data` 的
 * `io.legado.app.domain.gateway.AiPromptPresetGateway`（同名、同方法，只是收发的是 Room 实体）。
 * 名字沿用而不改成 `AiPromptPresetRepository`，是因为本仓 `domain/gateway` 下有 60+ 个同形态的
 * 既有契约（AI 域自己就有 `AiArtifactGateway` / `AiMemoryGateway` / `AiChatGateway` …），改名
 * 会把「既有契约下沉」变成一次无收益的 rename churn，还要连带改注入点的变量名；后续 AI 域的
 * 其余 Gateway 也会陆续搬进本模块，届时命名是同一家族。旧文件随本片删除，**不留门面、
 * 不留 typealias**。
 *
 * ⚠️ **只暴露实际被消费的方法**。旧的 `savePreset(preset)`（单条保存）全仓除接口声明与实现外
 * **零调用方**，随本片不进端口（与 M3-5 删 `RuleSubDao.maxOrder()` 同处理）。它没有留在 DAO
 * 上的对应项——底层是 DAO 的 `upsert`，本接口用 [savePresets] 覆盖批量场景。
 *
 * 语义约束（换实现即改行为，逐条与迁移前的实现对齐）：
 * - 迁移前的实现**每个方法都包 `withContext(Dispatchers.IO)`**，实现侧照抄（这与 M3-5 的
 *   `RuleSubRepositoryImpl` 全程不加 IO 不同：那边迁前就没包，因为 Room 的 `suspend` DAO 自带
 *   调度；两边不要互相"对齐"，多包一层会改变实际调度行为）；
 * - [savePresets] 底层是 `@Insert(onConflict = OnConflictStrategy.REPLACE)`——**整行替换**，
 *   不是「按列更新」：主键冲突时旧行被删除、再插入新行，调用方**没有**提供的字段会落回
 *   默认值。`ReadAiDelegate` 传的是全字段构造（`AiPromptPreset(...)`），所以行为与迁移前一致；
 *   新增调用点时**必须**传完整对象，不能只带 `id` + 待改字段；
 * - [deletePreset] 按主键 [AiPromptPreset.id] 删除单条。
 */
interface AiPromptPresetGateway {

    /** 取某任务类型下已启用的预设（按 sortNumber 升序）。 */
    suspend fun getEnabledByTaskType(taskType: String): List<AiPromptPreset>

    /** 某任务类型下的预设数量（用于判断是否需要灌入内置预设）。 */
    suspend fun countByTaskType(taskType: String): Int

    /** 批量保存（upsert）。 */
    suspend fun savePresets(presets: List<AiPromptPreset>)

    /** 按主键删除单条。 */
    suspend fun deletePreset(id: String)
}
