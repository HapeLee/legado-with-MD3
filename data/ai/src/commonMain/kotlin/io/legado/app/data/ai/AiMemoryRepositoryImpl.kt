package io.legado.app.data.ai

import io.legado.app.core.platform.systemTimeMillis
import io.legado.app.data.dao.AiMemoryDao
import io.legado.app.data.entities.AiMemory as AiMemoryEntity
import io.legado.app.domain.ai.AiMemory
import io.legado.app.domain.ai.AiMemoryGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [AiMemoryGateway] 的实现（M4-2）。
 *
 * **本类是 `:core:data` 的 `data/repository/AiMemoryRepository.kt` 的整份搬迁**：方法体与 DAO
 * 调用形态逐条保留，唯一的差异是公开签名从 Room 实体换成领域模型 [AiMemory]，于是在 DAO 边界
 * 上多了映射（[toDomain] / [toEntity]，见 `AiMemoryMapper.kt`），类名加了 `Impl` 后缀。
 *
 * 与 M3/M4-1 一致的形态：构造参数**直接收 DAO**（原实现收的就是 `AiMemoryDao`，这里原样保留；
 * `appDatabaseModule` 本来就绑定了它）。
 *
 * ⚠️ **保留 `withContext(Dispatchers.IO)`**——迁移前的实现每个方法都自己包了 IO，保留它是不改
 * 实际调度行为。与 M4-1 同侧、与 M3-5 的 `RuleSubRepositoryImpl`（全程不加 IO）不同，两边不要
 * 互相"对齐"。
 *
 * ⚠️ **[upsert] 保留「写前覆盖 `updatedAt`」这一条真实逻辑**：迁移前写作
 * `memory.copy(updatedAt = System.currentTimeMillis())`，这里换成 `:core:platform` 的
 * [systemTimeMillis]（android / desktop 的 `actual` 都是 `System.currentTimeMillis()`，语义
 * 等价）——换的原因是 commonMain 里拿不到 `java.lang.System`。**不要**因为「映射已经完整搬运了
 * `updatedAt`」就删掉这次覆盖：唯一的写入方（`:app` 的 `AiToolRepository`，走 AI 工具调用）
 * 构造记忆时从不传 `updatedAt`，落库时间全靠这一行。`AiMemoryRepositoryImplTest` 钉住了它。
 *
 * ⚠️ **[getForPrompt] 是唯一有组合逻辑的方法**：「全局记忆 + 本会话记忆」，`global` 在前，且
 * `conversationId` 为空白时**不查**会话记忆。空白判断用 `isNotBlank()`（不是 `isNotEmpty()`），
 * 与迁移前一致——纯空白的会话 id 会被当作「无会话」。
 *
 * ⚠️ **端口删掉了 5 个零调用方的方法**（见端口 KDoc），因此 DAO 的 `observeByConversation` /
 * `observeGlobal` / `deleteAllForConversation` 在本片之后零调用方。它们留在 DAO 上不动
 * （与 M4-1 对 `upsert` 的处理同款）。本类不提供 `Flow` 形态的查询。
 *
 * ⚠️ 过渡期依赖：Room 的 DAO 与实体仍归 `:core:data`（`data:database`「Room 唯一 owner」
 * 尚未拆出），所以本模块 `implementation(project(":core:data"))`。等它落地后，本类只换 DAO 的
 * 来源，构造签名与调用方都不动。
 */
class AiMemoryRepositoryImpl(
    private val dao: AiMemoryDao,
) : AiMemoryGateway {

    override suspend fun getForPrompt(conversationId: String): List<AiMemory> =
        withContext(Dispatchers.IO) {
            val global = dao.getGlobal().toDomainList()
            val scoped = if (conversationId.isNotBlank()) {
                dao.getByConversation(conversationId).toDomainList()
            } else {
                emptyList()
            }
            global + scoped
        }

    override suspend fun upsert(memory: AiMemory) = withContext(Dispatchers.IO) {
        dao.upsert(memory.copy(updatedAt = systemTimeMillis()).toEntity())
    }

    override suspend fun delete(conversationId: String, key: String) = withContext(Dispatchers.IO) {
        dao.delete(conversationId, key)
    }
}

// ---------- 边界映射的小工具 ----------
//
// 一对一的两条（`toDomain` / `toEntity`）在 `AiMemoryMapper.kt` 里，这里只放集合形态的重载。
// ⚠️ 方向由 DAO 的签名决定：`getGlobal` / `getByConversation` 返回 `List` ⇒ 只需要
// `List<实体> → 领域`；本域端口没有批量写入方法（`upsert` 收单条），所以**没有**
// `List<领域> → 实体` 的重载——别照抄 M4-1 的第二条。

internal fun List<AiMemoryEntity>.toDomainList(): List<AiMemory> = map { it.toDomain() }
