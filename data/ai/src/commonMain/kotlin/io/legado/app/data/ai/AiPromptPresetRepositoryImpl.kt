package io.legado.app.data.ai

import io.legado.app.data.dao.AiPromptPresetDao
import io.legado.app.data.entities.AiPromptPreset as AiPromptPresetEntity
import io.legado.app.domain.ai.AiPromptPreset
import io.legado.app.domain.ai.AiPromptPresetGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [AiPromptPresetGateway] 的实现（M4-1）。
 *
 * **本类是 `:core:data` 的 `data/repository/AiPromptPresetRepository.kt` 的整份搬迁**：方法体与
 * DAO 调用形态逐条保留，唯一的差异是公开签名从 Room 实体换成领域模型 [AiPromptPreset]，于是
 * 在 DAO 边界上多了映射（[toDomain] / [toEntity]，见 `AiPromptPresetMapper.kt`），类名加了
 * `Impl` 后缀。
 *
 * 与 M3 各片一致的形态：构造参数**直接收 DAO**（原实现收的就是 `AiPromptPresetDao`，这里原样
 * 保留；`appDatabaseModule` 本来就绑定了它）。
 *
 * ⚠️ **本实现完整保留 `withContext(Dispatchers.IO)`**——迁移前的实现每个方法都自己包了 IO，
 * 保留它是为了不改实际调度行为。这与 M3-5 的 `RuleSubRepositoryImpl`（全程不加 IO）**不同**：
 * 那边迁前就是裸调 DAO 的 `suspend` 方法，Room 的 `suspend` 查询自带调度。两边不要互相
 * "对齐"，多包一层会改变实际调度。
 *
 * ⚠️ **端口删掉了零调用方的 `savePreset(preset)`**（见端口 KDoc），因此 DAO 的 `upsert(preset)`
 * 在本片之后**零调用方**。它留在 DAO 上不动——删 DAO 方法要动 Room 的 schema/调用面审查，
 * 属留给 `data:database` 的既有债务（与 `RuleSubDao.maxOrder()`、`TxtTocRuleDao.enabled()`、
 * `TagGroupRuleDao.getAll()` 同处理）。本类不提供单条保存。
 *
 * ⚠️ 过渡期依赖：Room 的 DAO 与实体仍归 `:core:data`（`data:database`「Room 唯一 owner」
 * 尚未拆出），所以本模块 `implementation(project(":core:data"))`。等它落地后，本类只换
 * DAO 的来源，构造签名与调用方都不动。
 */
class AiPromptPresetRepositoryImpl(
    private val dao: AiPromptPresetDao,
) : AiPromptPresetGateway {

    override suspend fun getEnabledByTaskType(taskType: String): List<AiPromptPreset> =
        withContext(Dispatchers.IO) {
            dao.getEnabledByTaskType(taskType).toDomainList()
        }

    override suspend fun countByTaskType(taskType: String): Int = withContext(Dispatchers.IO) {
        dao.countByTaskType(taskType)
    }

    override suspend fun savePresets(presets: List<AiPromptPreset>) = withContext(Dispatchers.IO) {
        dao.upsertAll(presets.toEntityList())
    }

    override suspend fun deletePreset(id: String) = withContext(Dispatchers.IO) {
        dao.delete(id)
    }
}

// ---------- 边界映射的小工具 ----------
//
// 一对一的两条（`toDomain` / `toEntity`）在 `AiPromptPresetMapper.kt` 里，这里只放集合形态的
// 重载。⚠️ 方向由 DAO 的签名决定：`getEnabledByTaskType` 返回 `List` ⇒ 需要
// `List<实体> → 领域`；`upsertAll` 收 `List` ⇒ 需要 `List<领域> → 实体`。
// 这两个与 M3-6 `TagGroupRuleRepositoryImpl` 的三个重载不同——那边因为有 `vararg` 参数而多一个
// `Array<out 领域>` 形态，本域端口全是 `List`，不要照抄第三个重载。

internal fun List<AiPromptPresetEntity>.toDomainList(): List<AiPromptPreset> = map { it.toDomain() }

internal fun List<AiPromptPreset>.toEntityList(): List<AiPromptPresetEntity> = map { it.toEntity() }
