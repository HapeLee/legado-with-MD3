package io.legado.app.domain.ai

import io.legado.app.domain.model.AiAvailableModel
import io.legado.app.domain.model.AiModelDraft
import io.legado.app.domain.model.AiProfileDraft
import io.legado.app.domain.model.AiProviderDraft
import io.legado.app.domain.model.AiTaskPresetConfig
import kotlinx.coroutines.flow.Flow

/**
 * AI 供应商 / 模型 / 任务预设域的端口（M4-5c）。
 *
 * **这是既有契约 `io.legado.app.domain.gateway.AiProfileGateway` 的整份搬迁**：
 * 沿用原名（本仓 `domain/gateway` 有 60+ 个 `XxxGateway`，既有契约搬家不改名——改了要连带
 * 改注入点变量名），只把返回类型从 Room 实体换成领域模型
 * （[AiProviderProfile] / [AiModelProfile] / [AiTaskPreset]）。
 * 入参侧 `AiXxxDraft` / `AiAvailableModel` / `AiTaskPresetConfig` 本来就住 `:core:model`
 * 且**已经共享**，无需改动。
 *
 * ⚠️ **本片随契约删掉了两个零调用方的方法**（同 M4-2 删 5 个、M4-4 删 2 个的处理）：
 * - `getProviderApiKey(providerId)`：全仓只有「声明 + 实现 + 测试假实现」三处，**零真实调用**
 *   —— 需要 key 的调用方都走 `getProvider(id)?.apiKey`；
 * - `saveDefaultChatProfile(draft)`：同上零调用（写在 M4-1 之前，此后被
 *   `setDefaultModel` + `saveTaskPreset` 的组合替代）。
 * 原实现里它们背后的 DAO 方法一个都不删（`insertProvider` / `insertModel` / `insertPreset` /
 * `getProvider` 都还有别的调用方；DAO 的清理归 `data:database`）。⇒ **15 → 13 个方法**。
 *
 * ⚠️ **三个 `observeXxx()` 是本域仅有的 `Flow` 端口方法**，因此：
 * ① `domain/ai` 必须显式依赖 `kotlinx-coroutines-core`（`Flow` 是它的类型）；
 * ② 实现侧必须用 `map` 在**流内**做映射（每次发射都做一次），不能 `first()` 了再映射
 *    （后者会被类型系统拒绝，但更危险的是「只映射第一次」的写法能编译能过 mapper 用例）。
 *
 * ⚠️ **端口只留有调用方的方法**，但**方法内部用到的 DAO 能力要留**：`getPreset` /
 * `getDefaultPreset` / `deleteModelsByProvider` 等都不在端口上，却由实现内部调用
 * ⇒ 它们留在 `AiProfileDao` 上不动。
 */
interface AiProfileGateway {

    fun observeProviders(): Flow<List<AiProviderProfile>>

    fun observeModels(): Flow<List<AiModelProfile>>

    fun observePresets(): Flow<List<AiTaskPreset>>

    suspend fun getProvider(id: String): AiProviderProfile?

    suspend fun getModel(id: String): AiModelProfile?

    /** 取某个任务类型的**默认**预设并组装成运行时配置（`null` = 没有可用预设 / 模型 / 供应商）。 */
    suspend fun getTaskPreset(taskType: String): AiTaskPresetConfig?

    suspend fun saveProvider(draft: AiProviderDraft): AiProviderProfile

    suspend fun saveModel(draft: AiModelDraft): AiModelProfile

    suspend fun importProviderModels(
        providerId: String,
        models: List<AiAvailableModel>
    ): List<AiModelProfile>

    /** 把某个模型档案设为默认，并同步三个内建预设的 `modelProfileId`。 */
    suspend fun setDefaultModel(modelProfileId: String): AiTaskPresetConfig

    suspend fun saveTaskPreset(
        taskType: String,
        promptTemplate: String,
        temperature: Float,
        maxOutputTokens: Int
    ): AiTaskPresetConfig

    /** 删除供应商**及其全部模型档案**（两条 DAO 调用，顺序有意义）。 */
    suspend fun deleteProvider(providerId: String)

    suspend fun deleteModel(modelId: String)
}
