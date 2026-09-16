package io.legado.app.data.ai

import io.legado.app.core.platform.Digest
import io.legado.app.core.platform.JsonCodec
import io.legado.app.core.platform.nameUuidFromBytes
import io.legado.app.core.platform.systemTimeMillis
import io.legado.app.data.dao.AiProfileDao
import io.legado.app.domain.ai.AiModelProfile
import io.legado.app.domain.ai.AiProfileGateway
import io.legado.app.domain.ai.AiProviderProfile
import io.legado.app.domain.ai.AiTaskPreset
import io.legado.app.domain.model.AiAvailableModel
import io.legado.app.domain.model.AiGenerationParams
import io.legado.app.domain.model.AiModelConfig
import io.legado.app.domain.model.AiModelDraft
import io.legado.app.domain.model.AiModelRegistry
import io.legado.app.domain.model.AiPromptTemplate
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiProviderDraft
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.domain.model.AiTaskPresetConfig
import io.legado.app.domain.model.AiTaskRuntimeOptions
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.TranslationConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid

/**
 * [AiProfileGateway] 的实现（M4-5c）。
 *
 * **本类是 `:app` 的 `data/repository/AiProfileRepository.kt` 的整份搬迁**：方法体与 DAO 调用
 * 形态逐条保留，差异只有三处，都是「共享层拿不到平台 API」的必然结果：
 *
 * 1. **公开签名从 Room 实体换成领域模型**，于是在 DAO 边界上多了映射
 *    （[toDomain] / [toEntity] / [toDomainList]，见三个 `*Mapper.kt`），类名加 `Impl` 后缀；
 * 2. **`GSON` → `:core:platform` 的 [JsonCodec]**。`GSON` 门面住
 *    `core/data/src/androidMain`，而本模块**只有 commonMain** ⇒ 编译期就拿不到它。
 *    `JsonCodec` 的 Gson 配置与 `INITIAL_GSON` **逐行相同**（`MapDeserializerDoubleAsIntFix` +
 *    `Int` / `String` deserializer + `ToNumberPolicy.LONG_OR_DOUBLE` + `disableHtmlEscaping` +
 *    `setPrettyPrinting`），唯一差异是 `GSON` 额外注册的 7 个 **rule 类型** deserializer——
 *    本域序列化的是 `AiGenerationParams` / `AiTaskRuntimeOptions` / `Map`，都不是 rule 类型
 *    ⇒ 字节输出与解析行为一致。替换点：`toJson` / `fromJsonObject` / `decodeAnyMap`。
 * 3. **`System.currentTimeMillis()` → [systemTimeMillis]**（`java.lang.System` 是 JVM-only），
 *    与实体上的默认值用的是同一个 `expect fun`，取值恒等。
 *
 * ⚠️ **[digest] 是构造注入的平台能力，不是可选依赖**：`stableModelId` 要复刻
 * `java.util.UUID.nameUUIDFromBytes`（UUID v3 名称空间哈希），而 MD5 是平台原语
 * ⇒ 由调用方（`appModule`）注入 `JcaDigest`。选**参数注入**而不是 `expect/actual` 或
 * CompositionLocal：它没有任何可回落默认（漏传即编译错误），按 AGENTS.md 的平台能力分派
 * 它就是「无回落 ⇒ 参数注入」。算法本身留在 `:core:platform` 的 `nameUuidFromBytes`。
 *
 * ⚠️ **`withContext(Dispatchers.IO)` 逐条照抄迁移前行为**：迁移前**除三个 `Flow` 方法外每个
 * 方法都包了 IO**，本片照抄（与 M4-1 / M4-2 / M4-4 同侧，与 M4-3「迁前就是裸调 DAO」相反）。
 * 不要为了「向上一片对齐」多包或少包一层——那会改变实际调度行为。
 *
 * ⚠️ **三个 `observeXxx()` 必须在流内映射**：`map` 挂在 DAO 的 `Flow` 上而不是先 `first()`，
 * 否则后续每次发射都不会再映射（mapper 用例碰不到这条路径 ⇒ 由
 * `AiProfileRepositoryImplTest` 用假 DAO 发射**多次**来钉住）。
 *
 * ⚠️ 本片随端口删掉了 `getProviderApiKey` / `saveDefaultChatProfile` 两个零调用方的方法
 * （见 [AiProfileGateway] 的 KDoc）。它们背后的 DAO 方法一个都没删。
 *
 * ⚠️ 过渡期依赖：Room 的 DAO 与实体仍归 `:core:data`（`data:database`「Room 唯一 owner」
 * 尚未拆出），所以本模块 `implementation(project(":core:data"))`。等它落地后，本类只换 DAO 的
 * 来源，构造签名与调用方都不动。
 */
class AiProfileRepositoryImpl(
    private val aiProfileDao: AiProfileDao,
    private val digest: Digest,
) : AiProfileGateway {

    override fun observeProviders(): Flow<List<AiProviderProfile>> =
        aiProfileDao.observeProviders().map { entities -> entities.toDomainList() }

    override fun observeModels(): Flow<List<AiModelProfile>> =
        aiProfileDao.observeModels().map { entities -> entities.toDomainList() }

    override fun observePresets(): Flow<List<AiTaskPreset>> =
        aiProfileDao.observePresets().map { entities -> entities.toDomainList() }

    override suspend fun getProvider(id: String): AiProviderProfile? = withContext(Dispatchers.IO) {
        aiProfileDao.getProvider(id)?.toDomain()
    }

    override suspend fun getModel(id: String): AiModelProfile? = withContext(Dispatchers.IO) {
        aiProfileDao.getModel(id)?.toDomain()
    }

    override suspend fun getTaskPreset(taskType: String): AiTaskPresetConfig? = withContext(Dispatchers.IO) {
        aiProfileDao.getDefaultPreset(taskType)?.toDomain()?.toConfig()
    }

    override suspend fun saveProvider(draft: AiProviderDraft): AiProviderProfile = withContext(Dispatchers.IO) {
        require(draft.providerName.isNotBlank()) { "Provider name is required" }
        require(draft.baseUrl.isNotBlank()) { "Base URL is required" }

        val providerId = draft.providerId?.takeIf { it.isNotBlank() } ?: newId("provider")
        val existingProvider = aiProfileDao.getProvider(providerId)
        val apiKey = draft.apiKey.ifBlank { existingProvider?.apiKey.orEmpty() }
        val now = systemTimeMillis()
        val provider = AiProviderProfile(
            id = providerId,
            name = draft.providerName,
            protocol = draft.protocol,
            baseUrl = draft.baseUrl,
            modelsUrl = draft.modelsUrl?.takeIf { it.isNotBlank() },
            apiKey = apiKey,
            authType = existingProvider?.authType ?: AiProviderProfile.AUTH_TYPE_BEARER,
            secretRef = existingProvider?.secretRef,
            headersJson = existingProvider?.headersJson,
            chatPath = existingProvider?.chatPath,
            responsesPath = existingProvider?.responsesPath,
            messagesPath = existingProvider?.messagesPath,
            modelsPath = existingProvider?.modelsPath,
            customHeadersJson = existingProvider?.customHeadersJson,
            enabled = existingProvider?.enabled ?: true,
            createdAt = existingProvider?.createdAt ?: now,
            updatedAt = now
        )
        aiProfileDao.insertProvider(provider.toEntity())
        provider
    }

    override suspend fun saveModel(draft: AiModelDraft): AiModelProfile = withContext(Dispatchers.IO) {
        require(draft.providerId.isNotBlank()) { "Provider is required" }
        require(draft.modelId.isNotBlank()) { "Model is required" }

        val existingProvider = aiProfileDao.getProvider(draft.providerId)
        require(existingProvider != null) { "Provider is required" }
        val modelProfileId = draft.modelProfileId?.takeIf { it.isNotBlank() } ?: stableModelId(
            providerId = draft.providerId,
            modelId = draft.modelId
        )
        val existingModel = aiProfileDao.getModel(modelProfileId)
        val now = systemTimeMillis()
        val params = AiGenerationParams(
            temperature = draft.temperature,
            reasoningLevel = draft.reasoningLevel
        )
        val model = AiModelProfile(
            id = modelProfileId,
            providerId = draft.providerId,
            displayName = draft.modelName.ifBlank { draft.modelId },
            modelId = draft.modelId,
            contextWindow = draft.contextWindow,
            maxOutputTokens = draft.maxOutputTokens,
            capabilities = mergeCapabilities(existingModel?.capabilities, draft.modelId),
            defaultParamsJson = JsonCodec.toJson(params),
            enabled = existingModel?.enabled ?: true,
            sortNumber = existingModel?.sortNumber ?: 0,
            createdAt = existingModel?.createdAt ?: now,
            updatedAt = now
        )
        aiProfileDao.insertModel(model.toEntity())
        model
    }

    override suspend fun importProviderModels(
        providerId: String,
        models: List<AiAvailableModel>
    ): List<AiModelProfile> = withContext(Dispatchers.IO) {
        require(aiProfileDao.getProvider(providerId) != null) { "Provider is required" }
        val now = systemTimeMillis()
        models.distinctBy { it.id }.map { availableModel ->
            val modelProfileId = stableModelId(providerId, availableModel.id)
            val existingModel = aiProfileDao.getModel(modelProfileId)
            AiModelProfile(
                id = modelProfileId,
                providerId = providerId,
                displayName = availableModel.name.ifBlank { availableModel.id },
                modelId = availableModel.id,
                contextWindow = availableModel.contextWindow.takeIf { it > 0 } ?: existingModel?.contextWindow ?: 0,
                maxOutputTokens = availableModel.maxOutputTokens.takeIf { it > 0 } ?: existingModel?.maxOutputTokens ?: 0,
                capabilities = mergeCapabilities(existingModel?.capabilities, availableModel.id),
                defaultParamsJson = existingModel?.defaultParamsJson ?: JsonCodec.toJson(
                    AiGenerationParams(reasoningLevel = AiReasoningLevel.MEDIUM)
                ),
                enabled = existingModel?.enabled ?: true,
                sortNumber = existingModel?.sortNumber ?: 0,
                createdAt = existingModel?.createdAt ?: now,
                updatedAt = now
            ).also { aiProfileDao.insertModel(it.toEntity()) }
        }
    }

    override suspend fun setDefaultModel(modelProfileId: String): AiTaskPresetConfig = withContext(Dispatchers.IO) {
        val model = aiProfileDao.getModel(modelProfileId) ?: error("Model is required")
        val params = parseParams(model.defaultParamsJson)
        saveDefaultPresets(modelProfileId, params)
        aiProfileDao.getPreset(DEFAULT_TRANSLATE_PRESET_ID)?.toDomain()?.toConfig()
            ?: error("Failed to save default model")
    }

    override suspend fun deleteProvider(providerId: String) = withContext(Dispatchers.IO) {
        aiProfileDao.deleteModelsByProvider(providerId)
        aiProfileDao.deleteProvider(providerId)
    }

    override suspend fun deleteModel(modelId: String) = withContext(Dispatchers.IO) {
        aiProfileDao.deleteModel(modelId)
    }

    override suspend fun saveTaskPreset(
        taskType: String,
        promptTemplate: String,
        temperature: Float,
        maxOutputTokens: Int
    ): AiTaskPresetConfig = withContext(Dispatchers.IO) {
        runCatching {
            val existingPreset = aiProfileDao.getDefaultPreset(taskType)
            val presetId = existingPreset?.id ?: when (taskType) {
                AiTaskType.TRANSLATE_CHAPTER -> DEFAULT_TRANSLATE_PRESET_ID
                AiTaskType.SUMMARIZE_CHAPTER -> DEFAULT_SUMMARY_PRESET_ID
                AiTaskType.CHAT -> DEFAULT_CHAT_PRESET_ID
                else -> newId("preset")
            }
            val modelsList = aiProfileDao.observeModels().firstOrNull()
            val modelProfileId = existingPreset?.modelProfileId
                ?: modelsList?.firstOrNull()?.id
                ?: ""
            val currentParams = parseParams(existingPreset?.paramsJson)
            val updatedParams = currentParams.copy(
                temperature = temperature,
                maxOutputTokens = maxOutputTokens
            )
            val now = systemTimeMillis()
            val preset = AiTaskPreset(
                id = presetId,
                taskType = taskType,
                name = existingPreset?.name ?: when (taskType) {
                    AiTaskType.TRANSLATE_CHAPTER -> "Default Translation"
                    AiTaskType.SUMMARIZE_CHAPTER -> "Default Chapter Summary"
                    AiTaskType.CHAT -> "Default Chat"
                    else -> "Default Preset"
                },
                modelProfileId = modelProfileId,
                promptTemplate = promptTemplate.ifBlank {
                    when (taskType) {
                        AiTaskType.TRANSLATE_CHAPTER -> TranslationConstants.DEFAULT_PROMPT
                        AiTaskType.SUMMARIZE_CHAPTER -> AiPromptTemplate.DEFAULT_CHAPTER_SUMMARY
                        else -> "You are a helpful AI assistant."
                    }
                },
                paramsJson = JsonCodec.toJson(updatedParams),
                chunkPolicyJson = existingPreset?.chunkPolicyJson,
                enabled = existingPreset?.enabled ?: true,
                isDefault = existingPreset?.isDefault ?: true,
                sortNumber = existingPreset?.sortNumber ?: 0,
                createdAt = existingPreset?.createdAt ?: now,
                updatedAt = now
            )
            aiProfileDao.insertPreset(preset.toEntity())
            preset.toConfig() ?: error("Failed to save task preset")
        }.getOrElse { error ->
            throw IllegalStateException("保存 AI 预设配置失败: ${error.localizedMessage ?: "未知错误"}", error)
        }
    }

    /**
     * 把某个模型档案写进三个内建预设（翻译 / 摘要 / 对话）。
     *
     * ⚠️ 三条 `insertPreset` 的**取值来源各不相同**，迁移时容易被「统一成一样的」而改错：
     * - 翻译预设：写死 `TranslationConstants.DEFAULT_PROMPT`、`isDefault = true`，
     *   `chunkPolicyJson` 取**传入**的运行时选项（或既有预设里的，或全默认）；
     * - 摘要预设：`promptTemplate` / `paramsJson` **优先沿用既有那份**（只有缺了才用传入值）；
     * - 对话预设：`promptTemplate` 写死那句英文，`paramsJson` 用传入值。
     * 三条都只带 `id` / `taskType` / `name` / `modelProfileId` / `isDefault = true`，
     * **不显式传 `enabled` / `sortNumber`** ⇒ 走领域模型默认值（`true` / `0`），与迁移前一致。
     */
    private suspend fun saveDefaultPresets(
        modelProfileId: String,
        params: AiGenerationParams,
        translationRuntimeOptions: AiTaskRuntimeOptions? = null
    ) {
        val now = systemTimeMillis()
        val existingTranslatePreset = aiProfileDao.getPreset(DEFAULT_TRANSLATE_PRESET_ID)
        val runtimeOptions = translationRuntimeOptions
            ?: existingTranslatePreset
                ?.chunkPolicyJson
                ?.let { parseRuntimeOptions(it) }
            ?: AiTaskRuntimeOptions()
        aiProfileDao.insertPreset(
            AiTaskPreset(
                id = DEFAULT_TRANSLATE_PRESET_ID,
                taskType = AiTaskType.TRANSLATE_CHAPTER,
                name = "Default Translation",
                modelProfileId = modelProfileId,
                promptTemplate = TranslationConstants.DEFAULT_PROMPT,
                paramsJson = JsonCodec.toJson(params),
                chunkPolicyJson = JsonCodec.toJson(runtimeOptions),
                isDefault = true,
                createdAt = existingTranslatePreset?.createdAt ?: now,
                updatedAt = now
            ).toEntity()
        )
        val existingSummaryPreset = aiProfileDao.getPreset(DEFAULT_SUMMARY_PRESET_ID)
        aiProfileDao.insertPreset(
            AiTaskPreset(
                id = DEFAULT_SUMMARY_PRESET_ID,
                taskType = AiTaskType.SUMMARIZE_CHAPTER,
                name = "Default Chapter Summary",
                modelProfileId = modelProfileId,
                promptTemplate = existingSummaryPreset?.promptTemplate ?: AiPromptTemplate.DEFAULT_CHAPTER_SUMMARY,
                paramsJson = existingSummaryPreset?.paramsJson ?: JsonCodec.toJson(params),
                isDefault = true,
                createdAt = existingSummaryPreset?.createdAt ?: now,
                updatedAt = now
            ).toEntity()
        )
        val existingChatPreset = aiProfileDao.getPreset(DEFAULT_CHAT_PRESET_ID)
        aiProfileDao.insertPreset(
            AiTaskPreset(
                id = DEFAULT_CHAT_PRESET_ID,
                taskType = AiTaskType.CHAT,
                name = "Default Chat",
                modelProfileId = modelProfileId,
                promptTemplate = "You are a helpful AI assistant.",
                paramsJson = JsonCodec.toJson(params),
                isDefault = true,
                createdAt = existingChatPreset?.createdAt ?: now,
                updatedAt = now
            ).toEntity()
        )
    }

    /**
     * 把预设定行组装成运行时配置；**任一环节缺失就返回 `null`**（模型档案或供应商查不到）。
     *
     * ⚠️ 参数**合并方向有语义**：预设侧 `paramsJson` 是 `this`、模型侧 `defaultParamsJson` 是
     * `modelParams`，即**预设覆盖模型**（[AiGenerationParams.mergeWithFallback]）。
     * 顺手把两侧调换会让「预设里设的温度」被模型默认值压掉。
     */
    private suspend fun AiTaskPreset.toConfig(): AiTaskPresetConfig? {
        val model = aiProfileDao.getModel(modelProfileId)?.toDomain() ?: return null
        val provider = aiProfileDao.getProvider(model.providerId)?.toDomain() ?: return null
        val presetParams = parseParams(paramsJson)
        val modelParams = parseParams(model.defaultParamsJson)
        val mergedParams = presetParams.mergeWithFallback(
            modelParams = modelParams,
            modelMaxOutputTokens = model.maxOutputTokens,
            taskType = taskType
        )
        return AiTaskPresetConfig(
            id = id,
            taskType = taskType,
            name = name,
            model = model.toConfig(provider),
            promptTemplate = promptTemplate,
            params = mergedParams,
            runtimeOptions = parseRuntimeOptions(chunkPolicyJson)
        )
    }

    /**
     * 模型档案 + 供应商 → 模型运行时配置。
     *
     * ⚠️ **三条路径有默认回落，且只有它们是「有默认值」的**：`chatPath` → `"/chat/completions"`、
     * `responsesPath` → `"/responses"`、`messagesPath` → `"/v1/messages"`。`modelsPath` 与
     * `modelsUrl` **没有回落**（保持 `null`）——补一个默认值会让「没配 modelsPath 的供应商」走上
     * 一条迁移前不存在的请求路径。`headers` / `customHeaders` 分别从 `headersJson` /
     * `customHeadersJson` 解析，空/坏 JSON ⇒ 空 map。
     */
    private fun AiModelProfile.toConfig(provider: AiProviderProfile): AiModelConfig {
        return AiModelConfig(
            id = id,
            provider = AiProviderConfig(
                id = provider.id,
                name = provider.name,
                protocol = provider.protocol,
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                modelsUrl = provider.modelsUrl,
                headers = parseHeaders(provider.headersJson),
                chatPath = provider.chatPath ?: "/chat/completions",
                responsesPath = provider.responsesPath ?: "/responses",
                messagesPath = provider.messagesPath ?: "/v1/messages",
                modelsPath = provider.modelsPath,
                customHeaders = parseHeaders(provider.customHeadersJson)
            ),
            displayName = displayName,
            modelId = modelId,
            contextWindow = contextWindow,
            maxOutputTokens = maxOutputTokens,
            capabilities = capabilities.split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet(),
            defaultParams = parseParams(defaultParamsJson)
        )
    }

    /**
     * 解析 `AiGenerationParams` JSON；空白或坏 JSON ⇒ **全默认**。
     *
     * ⚠️ 迁移前是 `runCatching { GSON.fromJson(...) }.getOrDefault(AiGenerationParams())`，
     * 现在是 `JsonCodec.fromJsonObject(...) ?: AiGenerationParams()` —— 语义等价：
     * `JsonCodec.fromJsonObject` 内部已经把解析异常吞成 `null`，与 `runCatching` 落到同一个
     * 兜底值。**不要**把这里的 `?:` 去掉换成 `!!`：坏 JSON 是既有用户库里真实存在的数据。
     */
    private fun parseParams(json: String?): AiGenerationParams {
        if (json.isNullOrBlank()) return AiGenerationParams()
        return JsonCodec.fromJsonObject(json, AiGenerationParams::class) ?: AiGenerationParams()
    }

    /** 解析 `AiTaskRuntimeOptions` JSON；空白或坏 JSON ⇒ **全默认**（理由同 [parseParams]）。 */
    private fun parseRuntimeOptions(json: String?): AiTaskRuntimeOptions {
        if (json.isNullOrBlank()) return AiTaskRuntimeOptions()
        return JsonCodec.fromJsonObject(json, AiTaskRuntimeOptions::class) ?: AiTaskRuntimeOptions()
    }

    /**
     * 解析请求头 JSON 成 `Map<String, String>`；空白、坏 JSON 或解析出 `null` ⇒ 空 map。
     *
     * ⚠️ 迁移前是 `GSON.fromJson(json, Map::class.java)`（**裸 `Map`**）再逐项 `toString()`，
     * 现在是 [JsonCodec.decodeAnyMap]（`Map<String, Any>`）。功能等价：两者都用同一份 Gson 配置，
     * 数字定型策略（`LONG_OR_DOUBLE`）相同，而这里无论如何都要 `toString()` 落地成字符串。
     * 唯一的差别在「值本身是对象/数组」这种病态输入上（裸 `Map` 给 `{k=v}` 形式的 Java
     * `toString`，`decodeAnyMap` 给 LinkedTreeMap 的 `toString`）——请求头里不可能出现，
     * 且迁移前的行为本身也不是有意设计的。
     */
    private fun parseHeaders(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        return JsonCodec.decodeAnyMap(json)
            ?.mapKeys { it.key.toString() }
            ?.mapValues { it.value.toString() }
            ?: emptyMap()
    }

    /**
     * 把既有能力串与新模型推断出的能力合并成**去重、去空白、逗号连接**的字符串。
     *
     * ⚠️ 顺序有意义：**既有在前、新推断在后**，且 `distinct()` 保首次出现
     * ⇒ 交换两侧或改成 `toSet()` 都会改变落库字符串（虽然语义上是同一个集合，
     * 但它会被原样回写进 `capabilities` 列，是可见的字节差异）。
     */
    private fun mergeCapabilities(existing: String?, modelId: String): String {
        return (existing.orEmpty().split(',') + AiModelRegistry.inferCapabilities(modelId))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(",")
    }

    /**
     * ⚠️ **`stableModelId` 是持久化兼容边界**：它复刻 `java.util.UUID.nameUUIDFromBytes`
     * 的字节语义（UUID v3 名称空间哈希），输出落库成 `ai_model_profiles.id = "model_<hex>"`。
     * 迁移前它住 `:app` 的私有 companion 里、直接调 `java.security.MessageDigest("MD5")`；
     * 现在算法在 `:core:platform` 的 `nameUuidFromBytes`，MD5 由构造注入的 [digest] 提供。
     * `toString().replace("-", "")` 的大小写与去横线方式**逐字保留**——改任何一个字符都会让
     * 既有用户的模型档案 ID 漂移，表现为「升级后模型列表空了」。
     */
    private fun stableModelId(providerId: String, modelId: String): String {
        val uuid = nameUuidFromBytes("$providerId:$modelId".toByteArray(), digest)
            .toString()
            .replace("-", "")
        return "model_$uuid"
    }

    private companion object {
        const val DEFAULT_TRANSLATE_PRESET_ID = "default_translate_chapter"
        const val DEFAULT_SUMMARY_PRESET_ID = "default_summarize_chapter"
        const val DEFAULT_CHAT_PRESET_ID = "default_chat"

        fun newId(prefix: String): String = "${prefix}_${Uuid.random().toString().replace("-", "")}"
    }
}
