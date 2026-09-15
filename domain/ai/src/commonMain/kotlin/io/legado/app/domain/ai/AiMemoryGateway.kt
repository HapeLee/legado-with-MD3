package io.legado.app.domain.ai

/**
 * AI 长期记忆的**端口**（M4-2）。
 *
 * 由 `:data:ai` 的 `AiMemoryRepositoryImpl` 实现：过渡期它直接持有 `:core:data` 的
 * `AiMemoryDao`（Room 的 DAO/实体仍归 `:core:data`），把方法逐条委派给 DAO 并在边界上做
 * 实体 ↔ 领域模型 的映射。等 `data:database`（Room 唯一 owner）拆出来，实现只需换 DAO 来源，
 * 本接口与调用方不动。
 *
 * ⚠️ **本接口是搬迁，不是新造**：迁移前叫 `:core:data` 的
 * `io.legado.app.domain.gateway.AiMemoryGateway`（同名、同方法，只是收发的是 Room 实体）。
 * 名字沿用而不改名，理由与 M4-1 的 [AiPromptPresetGateway] 完全一致：本仓 `domain/gateway`
 * 下有 60+ 个同形态的既有契约，改名会把「既有契约下沉」变成一次无收益的 rename churn。
 * 旧文件随本片删除，**不留门面、不留 typealias**。
 *
 * ⚠️ **只暴露实际被消费的方法**——旧接口 8 个方法里只有 3 个有调用方，其余 5 个随本片不进
 * 端口（与 M4-1 删 `savePreset`、M3-5 删 `RuleSubDao.maxOrder()` 同处理）：
 * - `observeByConversation` / `observeGlobal`（两个 `Flow`）：全仓零调用方。它们对应的
 *   `AiMemoryDao.observeByConversation` / `observeGlobal` 在本片之后**也**零调用方，但按既有
 *   约定留在 DAO 上不动（删 DAO 方法要动 Room 的 schema/调用面审查，属留给 `data:database`
 *   的债务，与 `RuleSubDao.maxOrder()`、`TxtTocRuleDao.enabled()`、`TagGroupRuleDao.getAll()`
 *   同处理）。注意**本域因此不再有任何 `Flow` 形态的端口方法**——别为了"以后可能要用"
 *   把两个 `observe` 搬回来；
 * - `getByConversation` / `getGlobal`：全仓零调用方，但底层 DAO 方法**被 [getForPrompt] 内部
 *   使用**，所以只删端口方法、不动 DAO；
 * - `deleteAllForConversation`：全仓零调用方，对应 DAO 方法随之零调用方（同样留在 DAO 上）。
 *
 * 语义约束（换实现即改行为，逐条与迁移前的实现对齐）：
 * - 迁移前的实现**每个方法都包 `withContext(Dispatchers.IO)`**，实现侧照抄（与 M4-1 同侧；
 *   与 M3-5 的 `RuleSubRepositoryImpl` 全程不加 IO 不同，两边不要互相"对齐"）；
 * - [upsert] 底层是 `@Insert(onConflict = REPLACE)`——**整行替换**，且实现会在写入前用
 *   **当前时间覆盖 [AiMemory.updatedAt]**：调用方传入的 `updatedAt` 会被丢弃。这是迁移前
 *   `AiMemoryRepository.upsert` 里的 `memory.copy(updatedAt = System.currentTimeMillis())`，
 *   本片把它换成 `:core:platform` 的 `systemTimeMillis()`（android/desktop 的 `actual` 都是
 *   `System.currentTimeMillis()`，语义等价）——换的原因是 commonMain 里拿不到 `java.lang.System`；
 * - [getForPrompt] 的返回值是「全局记忆 + 本会话记忆」的拼接，**global 在前**，且
 *   `conversationId` 为空白时**只**返回 global（不会退化成查 `conversationId = ''` 之外的东西）。
 */
interface AiMemoryGateway {

    /** 取注入提示词用的记忆：全局记忆在前，本会话记忆在后。空白会话 id 只取全局。 */
    suspend fun getForPrompt(conversationId: String): List<AiMemory>

    /** 写入/覆盖一条记忆（按 `conversationId` + `key` 复合主键），并刷新 `updatedAt`。 */
    suspend fun upsert(memory: AiMemory)

    /** 删除指定会话下的一条记忆。 */
    suspend fun delete(conversationId: String, key: String)
}
