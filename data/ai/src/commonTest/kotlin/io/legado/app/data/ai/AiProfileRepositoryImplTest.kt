package io.legado.app.data.ai

import io.legado.app.core.platform.Digest
import io.legado.app.core.platform.JsonCodec
import io.legado.app.data.dao.AiProfileDao
import io.legado.app.data.entities.AiModelProfile as AiModelProfileEntity
import io.legado.app.data.entities.AiProviderProfile as AiProviderProfileEntity
import io.legado.app.data.entities.AiTaskPreset as AiTaskPresetEntity
import io.legado.app.domain.ai.AiProviderProfile
import io.legado.app.domain.model.AiAvailableModel
import io.legado.app.domain.model.AiGenerationParams
import io.legado.app.domain.model.AiModelDraft
import io.legado.app.domain.model.AiProviderDraft
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.domain.model.AiTaskType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

/**
 * `AiProfileRepositoryImpl` 的**行为**护栏（M4-5c）。
 *
 * ⚠️ **为什么本片必须有它**（判据来自 M4-2，M4-3 补了「Flow 端口」这一条）：本实现不是纯
 * DAO 委派——13 个端口方法里有 10 个带真实逻辑，且**全是 mapper 测试碰不到的**：
 * - 三个 `observeXxx()`：映射必须在**流内**（用假 DAO 发射两次来验）；
 * - `saveProvider`：`apiKey` 回落、`modelsUrl` 空白归一、既有档案的 7 个可选字段被沿用；
 * - `saveModel`：`stableModelId` 生成稳定 id、`displayName` 回落、能力合并、`JsonCodec` 编码参数；
 * - `importProviderModels`：`distinctBy` 去重、「>0 才覆盖」的两级回落、既有 JSON 优先；
 * - `setDefaultModel`：**一次写三个内建预设**并回读翻译预设；
 * - `deleteProvider`：**两条** DAO 调用（先删模型、再删供应商）；
 * - `toConfig()`：三条默认路径回落 + `mergeWithFallback` 的**方向**（预设覆盖模型）；
 * - `parseParams` / `parseRuntimeOptions` / `parseHeaders`：坏 JSON 的容错回退。
 *
 * ⚠️ 用 `runBlocking` 而不是 `kotlinx.coroutines.test` 的 `runTest`：本模块只依赖
 * `kotlinx.coroutines.core`，不为一个测试文件引入新的测试依赖（同 M4-2 / M4-3 / M4-4）。
 */
class AiProfileRepositoryImplTest {

    /**
     * Room `@Dao` 的手写假实现：`@Insert` 写进内存表（这样 `getPreset` / `getDefaultPreset`
     * 这类回读才有意义），其余只记录调用，不碰 Room 运行时。
     *
     * ⚠️ `getDefaultPreset` 复刻了 DAO 的排序语义
     * （`where taskType = :taskType and enabled = 1 order by isDefault desc, sortNumber, createdAt limit 1`）
     * ——**这不是 SQL**，只是一个够用的近似；它的用途是让 `setDefaultModel` 的回读路径可测。
     */
    private class FakeAiProfileDao(
        private val providerEmissions: List<List<AiProviderProfileEntity>>? = null,
        private val modelEmissions: List<List<AiModelProfileEntity>>? = null,
        private val presetEmissions: List<List<AiTaskPresetEntity>>? = null,
    ) : AiProfileDao {

        val providers = linkedMapOf<String, AiProviderProfileEntity>()
        val models = linkedMapOf<String, AiModelProfileEntity>()
        val presets = linkedMapOf<String, AiTaskPresetEntity>()

        val insertedProviders = mutableListOf<AiProviderProfileEntity>()
        val insertedModels = mutableListOf<AiModelProfileEntity>()
        val insertedPresets = mutableListOf<AiTaskPresetEntity>()
        val deletedProviders = mutableListOf<String>()
        val deletedModels = mutableListOf<String>()
        val deletedModelsByProvider = mutableListOf<String>()

        /** 只记「有顺序语义」的写操作：两个独立列表看不出 `deleteProvider` 的先后。 */
        val callLog = mutableListOf<String>()

        override fun observeProviders(): Flow<List<AiProviderProfileEntity>> {
            val emissions = providerEmissions ?: listOf(providers.values.toList())
            return flow { emissions.forEach { emit(it) } }
        }

        override fun observeModels(): Flow<List<AiModelProfileEntity>> {
            val emissions = modelEmissions ?: listOf(models.values.toList())
            return flow { emissions.forEach { emit(it) } }
        }

        override fun observePresets(): Flow<List<AiTaskPresetEntity>> {
            val emissions = presetEmissions ?: listOf(presets.values.toList())
            return flow { emissions.forEach { emit(it) } }
        }

        override suspend fun getProvider(id: String): AiProviderProfileEntity? = providers[id]

        override suspend fun getModel(id: String): AiModelProfileEntity? = models[id]

        override suspend fun getModelsByProvider(providerId: String): List<AiModelProfileEntity> =
            models.values.filter { it.providerId == providerId }

        override suspend fun getPreset(id: String): AiTaskPresetEntity? = presets[id]

        override suspend fun getDefaultPreset(taskType: String): AiTaskPresetEntity? =
            presets.values
                .filter { it.taskType == taskType && it.enabled }
                .sortedWith(
                    compareByDescending<AiTaskPresetEntity> { it.isDefault }
                        .thenBy { it.sortNumber }
                        .thenBy { it.createdAt }
                )
                .firstOrNull()

        override suspend fun countProviders(): Int = providers.size

        override suspend fun insertProvider(provider: AiProviderProfileEntity) {
            callLog += "insertProvider"
            insertedProviders += provider
            providers[provider.id] = provider
        }

        override suspend fun insertModel(model: AiModelProfileEntity) {
            callLog += "insertModel"
            insertedModels += model
            models[model.id] = model
        }

        override suspend fun insertPreset(preset: AiTaskPresetEntity) {
            callLog += "insertPreset"
            insertedPresets += preset
            presets[preset.id] = preset
        }

        override suspend fun updateProvider(provider: AiProviderProfileEntity) {
            providers[provider.id] = provider
        }

        override suspend fun updateModel(model: AiModelProfileEntity) {
            models[model.id] = model
        }

        override suspend fun updatePreset(preset: AiTaskPresetEntity) {
            presets[preset.id] = preset
        }

        override suspend fun deleteProvider(providerId: String) {
            callLog += "deleteProvider"
            deletedProviders += providerId
            providers.remove(providerId)
        }

        override suspend fun deleteModel(modelId: String) {
            callLog += "deleteModel"
            deletedModels += modelId
            models.remove(modelId)
        }

        override suspend fun deleteModelsByProvider(providerId: String) {
            callLog += "deleteModelsByProvider"
            deletedModelsByProvider += providerId
            models.entries.removeAll { it.value.providerId == providerId }
        }
    }

    /**
     * 确定性摘要假实现：**每次返回一份新数组**。
     *
     * ⚠️ 这一点是硬要求，不是风格：`nameUuidFromBytes` 会**就地改写**返回数组的版本位与变体位
     * （契约写在 `Digest.md5` 的 KDoc 里）。若这里返回同一个缓冲，连续两次调用会拿到已被改写的
     * 字节 ⇒ 第二次的 id 会漂移。本类的用例会连续调用两次来钉住这条契约。
     *
     * 另外它记录每次的**输入**，用来证明 `stableModelId` 喂进去的是 `"$providerId:$modelId"`。
     */
    private class FakeDigest(private val bytes: ByteArray = ByteArray(16) { it.toByte() }) : Digest {
        val inputs = mutableListOf<String>()

        override fun sha256(data: ByteArray): ByteArray = error("本域不使用 sha256")

        override fun md5(data: ByteArray): ByteArray {
            inputs += data.decodeToString()
            return bytes.copyOf()
        }
    }

    /** 0x00..0x0F → 版本位 0x36 / 变体位 0x88（**手算常量**，与实现无关）。 */
    private val expectedStableId = "model_000102030405360788090a0b0c0d0e0f"

    /** 全 0xFF → 版本位 0x3F / 变体位 0xBF。两个向量一起才钉得住「位运算改的是第 7、9 字节」。 */
    private val expectedStableIdAllFf = "model_ffffffffffff3fffbfffffffffffffff"

    private fun providerEntity(
        id: String = "provider_1",
        name: String = "OpenAI",
        protocol: String = "openai_chat_completions",
        baseUrl: String = "https://api.openai.com/v1",
        apiKey: String = "sk-1",
        modelsUrl: String? = null,
        authType: String = "bearer",
        secretRef: String? = null,
        headersJson: String? = null,
        chatPath: String? = null,
        responsesPath: String? = null,
        messagesPath: String? = null,
        modelsPath: String? = null,
        customHeadersJson: String? = null,
        enabled: Boolean = true,
        createdAt: Long = 1L,
        updatedAt: Long = 1L,
    ) = AiProviderProfileEntity(
        id = id,
        name = name,
        protocol = protocol,
        baseUrl = baseUrl,
        modelsUrl = modelsUrl,
        apiKey = apiKey,
        authType = authType,
        secretRef = secretRef,
        headersJson = headersJson,
        chatPath = chatPath,
        responsesPath = responsesPath,
        messagesPath = messagesPath,
        modelsPath = modelsPath,
        customHeadersJson = customHeadersJson,
        enabled = enabled,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun modelEntity(
        id: String = "model_x",
        providerId: String = "provider_1",
        displayName: String = "M",
        modelId: String = "gpt-4o",
        contextWindow: Int = 0,
        maxOutputTokens: Int = 0,
        capabilities: String = "",
        defaultParamsJson: String? = null,
        enabled: Boolean = true,
        sortNumber: Int = 0,
        createdAt: Long = 1L,
        updatedAt: Long = 1L,
    ) = AiModelProfileEntity(
        id = id,
        providerId = providerId,
        displayName = displayName,
        modelId = modelId,
        contextWindow = contextWindow,
        maxOutputTokens = maxOutputTokens,
        capabilities = capabilities,
        defaultParamsJson = defaultParamsJson,
        enabled = enabled,
        sortNumber = sortNumber,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun presetEntity(
        id: String = "preset_1",
        taskType: String = AiTaskType.TRANSLATE_CHAPTER,
        name: String = "P",
        modelProfileId: String = "model_x",
        promptTemplate: String = "tpl",
        paramsJson: String? = null,
        chunkPolicyJson: String? = null,
        enabled: Boolean = true,
        isDefault: Boolean = false,
        sortNumber: Int = 0,
        createdAt: Long = 1L,
        updatedAt: Long = 1L,
    ) = AiTaskPresetEntity(
        id = id,
        taskType = taskType,
        name = name,
        modelProfileId = modelProfileId,
        promptTemplate = promptTemplate,
        paramsJson = paramsJson,
        chunkPolicyJson = chunkPolicyJson,
        enabled = enabled,
        isDefault = isDefault,
        sortNumber = sortNumber,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun repo(dao: FakeAiProfileDao, digest: Digest = FakeDigest()) = AiProfileRepositoryImpl(dao, digest)

    // ---------------------------------------------------------------- 流内映射

    @Test
    fun `observeProviders 每次发射都做映射`() = runBlocking {
        val dao = FakeAiProfileDao(
            providerEmissions = listOf(
                listOf(providerEntity(id = "p1")),
                listOf(providerEntity(id = "p1"), providerEntity(id = "p2")),
            )
        )
        val collected = repo(dao).observeProviders().toList()
        assertEquals(2, collected.size, "两次发射必须都能收到")
        assertEquals(listOf("p1"), collected[0].map { it.id })
        assertEquals(listOf("p1", "p2"), collected[1].map { it.id })
        // 类型必须是领域模型（编译期已保证）；这里再钉一次字段映射确实发生了。
        assertEquals("OpenAI", collected[0].single().name)
    }

    @Test
    fun `observeModels 每次发射都做映射`() = runBlocking {
        val dao = FakeAiProfileDao(
            modelEmissions = listOf(
                listOf(modelEntity(id = "m1", capabilities = "tools")),
                listOf(modelEntity(id = "m1", capabilities = "tools,vision")),
            )
        )
        val collected = repo(dao).observeModels().toList()
        assertEquals(listOf("tools"), collected[0].map { it.capabilities })
        assertEquals(listOf("tools,vision"), collected[1].map { it.capabilities })
    }

    @Test
    fun `observePresets 每次发射都做映射`() = runBlocking {
        val dao = FakeAiProfileDao(
            presetEmissions = listOf(
                listOf(presetEntity(id = "a", isDefault = false)),
                listOf(presetEntity(id = "a", isDefault = true)),
            )
        )
        val collected = repo(dao).observePresets().toList()
        assertEquals(listOf(false), collected[0].map { it.isDefault })
        assertEquals(listOf(true), collected[1].map { it.isDefault })
    }

    // ---------------------------------------------------------------- 读路径

    @Test
    fun `getProvider 与 getModel 映射成领域模型`() = runBlocking {
        val dao = FakeAiProfileDao()
        dao.providers["p1"] = providerEntity(id = "p1", authType = "header", enabled = false)
        dao.models["m1"] = modelEntity(id = "m1", sortNumber = 7)
        val r = repo(dao)
        assertEquals("header", r.getProvider("p1")?.authType)
        assertEquals(false, r.getProvider("p1")?.enabled)
        assertEquals(7, r.getModel("m1")?.sortNumber)
        assertNull(r.getProvider("nope"))
        assertNull(r.getModel("nope"))
    }

    // ---------------------------------------------------------------- saveProvider

    @Test
    fun `saveProvider 落库的是实体而返回的是领域模型`() = runBlocking {
        val dao = FakeAiProfileDao()
        val saved = repo(dao).saveProvider(
            AiProviderDraft(providerName = "Anthropic", protocol = "anthropic_messages", baseUrl = "https://a", apiKey = "k")
        )
        val inserted = dao.insertedProviders.single()
        assertEquals(inserted.id, saved.id)
        assertEquals("Anthropic", saved.name)
        assertEquals("https://a", saved.baseUrl)
        assertEquals("k", saved.apiKey)
        assertEquals(inserted.createdAt, saved.createdAt)
        // 落库的那份必须能逐字回成同一个领域模型（toEntity 路径正确）
        assertEquals(saved, inserted.toDomain())
    }

    @Test
    fun `saveProvider 保留既有档案的认证方式与可选路径字段`() = runBlocking {
        val dao = FakeAiProfileDao()
        dao.providers["p1"] = providerEntity(
            id = "p1",
            authType = "header",
            secretRef = "ref",
            headersJson = """{"X":"1"}""",
            chatPath = "/c",
            responsesPath = "/r",
            messagesPath = "/m",
            modelsPath = "/models",
            customHeadersJson = """{"Y":"2"}""",
            enabled = false,
            createdAt = 111L,
            apiKey = "old-key",
        )
        val saved = repo(dao).saveProvider(
            AiProviderDraft(
                providerId = "p1",
                providerName = "renamed",
                protocol = "p",
                baseUrl = "https://b",
                apiKey = "new-key",
            )
        )
        assertEquals("header", saved.authType)
        assertEquals("ref", saved.secretRef)
        assertEquals("""{"X":"1"}""", saved.headersJson)
        assertEquals("/c", saved.chatPath)
        assertEquals("/r", saved.responsesPath)
        assertEquals("/m", saved.messagesPath)
        assertEquals("/models", saved.modelsPath)
        assertEquals("""{"Y":"2"}""", saved.customHeadersJson)
        assertEquals(false, saved.enabled)
        assertEquals(111L, saved.createdAt, "既有档案的 createdAt 必须保留")
        assertEquals("renamed", saved.name, "草稿里的字段覆盖既有值")
        assertEquals("new-key", saved.apiKey)
    }

    @Test
    fun `saveProvider 在 apiKey 空白时沿用既有 key`() = runBlocking {
        val dao = FakeAiProfileDao()
        dao.providers["p1"] = providerEntity(id = "p1", apiKey = "old-key")
        val saved = repo(dao).saveProvider(
            AiProviderDraft(providerId = "p1", providerName = "n", protocol = "p", baseUrl = "https://b", apiKey = "   ")
        )
        assertEquals("old-key", saved.apiKey)
    }

    @Test
    fun `saveProvider 在 apiKey 空白且无既有档案时落成空串`() = runBlocking {
        val dao = FakeAiProfileDao()
        val saved = repo(dao).saveProvider(
            AiProviderDraft(providerName = "n", protocol = "p", baseUrl = "https://b", apiKey = "")
        )
        assertEquals("", saved.apiKey)
        assertEquals(AiProviderProfile.AUTH_TYPE_BEARER, saved.authType, "无既有档案时默认 bearer")
        assertEquals(true, saved.enabled)
    }

    @Test
    fun `saveProvider 把空白的 modelsUrl 归一成 null 而非空串`() = runBlocking {
        val dao = FakeAiProfileDao()
        val r = repo(dao)
        assertNull(
            r.saveProvider(
                AiProviderDraft(providerName = "n", protocol = "p", baseUrl = "https://b", apiKey = "k", modelsUrl = "  ")
            ).modelsUrl
        )
        assertEquals(
            "https://b/models",
            r.saveProvider(
                AiProviderDraft(providerName = "n", protocol = "p", baseUrl = "https://b", apiKey = "k", modelsUrl = "https://b/models")
            ).modelsUrl
        )
    }

    @Test
    fun `saveProvider 拒绝空名称与空 baseUrl`() = runBlocking {
        val dao = FakeAiProfileDao()
        val r = repo(dao)
        assertFailsWith<IllegalArgumentException> {
            r.saveProvider(AiProviderDraft(providerName = "  ", protocol = "p", baseUrl = "https://b", apiKey = "k"))
        }
        assertFailsWith<IllegalArgumentException> {
            r.saveProvider(AiProviderDraft(providerName = "n", protocol = "p", baseUrl = "   ", apiKey = "k"))
        }
        assertTrue(dao.insertedProviders.isEmpty(), "校验失败不得落库")
    }

    // ---------------------------------------------------------------- stableModelId

    @Test
    fun `saveModel 用稳定摘要生成 model_ 前缀的 id`() = runBlocking {
        val dao = FakeAiProfileDao()
        dao.providers["provider_1"] = providerEntity(id = "provider_1")
        val digest = FakeDigest()
        val saved = repo(dao, digest).saveModel(
            AiModelDraft(providerId = "provider_1", modelName = "GPT-4o", modelId = "gpt-4o")
        )
        assertEquals(expectedStableId, saved.id)
        assertEquals(listOf("provider_1:gpt-4o"), digest.inputs, "喂给摘要的必须是 providerId 冒号 modelId")
    }

    @Test
    fun `stableModelId 的位运算按 RFC 4122 改写第 7 与第 9 字节`() = runBlocking {
        val dao = FakeAiProfileDao()
        dao.providers["provider_1"] = providerEntity(id = "provider_1")
        val saved = repo(dao, FakeDigest(ByteArray(16) { 0xFF.toByte() })).saveModel(
            AiModelDraft(providerId = "provider_1", modelName = "n", modelId = "m")
        )
        assertEquals(expectedStableIdAllFf, saved.id)
    }

    /**
     * ⚠️ 顺带钉住一件**容易误判**的事：`nameUuidFromBytes` 的位改写是**幂等**的
     * （`0x30..0x3F` 与 `0x80..0xBF` 分别是两个变换的不动点），所以「摘要实现复用同一个缓冲」
     * 在**本函数**上恰好不会让第二次的 id 漂移——本用例早先一版正是按「会漂移」写的，是错的。
     * `Digest.md5` 的「返回新数组」契约仍然重要（缓存/记忆化的实现会把被改写的字节交给别的
     * 调用方），但那属于 `Digest` 自己的契约，由 `:core:platform` 的契约测试负责。
     *
     * 本假实现的摘要与输入无关 ⇒ 它只能证明「稳定」（同输入同输出）与「喂进去的是什么」，
     * **不能**证明「不同输入得到不同 id」——后者由真实 MD5 提供，见 `DigestContractTest` 与
     * `NameUuidContractTest` 里的硬编码向量。
     */
    @Test
    fun `stableModelId 是确定性的且摘要输入固定为 providerId 冒号 modelId`() = runBlocking {
        val dao = FakeAiProfileDao()
        dao.providers["p"] = providerEntity(id = "p")
        val digest = FakeDigest()
        val r = repo(dao, digest)
        val a = r.saveModel(AiModelDraft(providerId = "p", modelName = "n", modelId = "m")).id
        val b = r.saveModel(AiModelDraft(providerId = "p", modelName = "n", modelId = "m")).id
        assertEquals(expectedStableId, a)
        assertEquals(a, b, "同一 providerId + modelId 必须得到同一个档案 id")
        assertEquals(listOf("p:m", "p:m"), digest.inputs, "分隔符与两侧原样，不能加前缀或 trim")
    }


    // ---------------------------------------------------------------- saveModel

    @Test
    fun `saveModel 的 displayName 在空名时回落到 modelId`() = runBlocking {
        val dao = FakeAiProfileDao()
        dao.providers["provider_1"] = providerEntity(id = "provider_1")
        val r = repo(dao)
        assertEquals(
            "gpt-4o",
            r.saveModel(AiModelDraft(providerId = "provider_1", modelName = "   ", modelId = "gpt-4o")).displayName
        )
        assertEquals(
            "自定义",
            r.saveModel(AiModelDraft(providerId = "provider_1", modelName = "自定义", modelId = "gpt-4o")).displayName
        )
    }

    @Test
    fun `saveModel 合并既有能力并去重保序`() = runBlocking {
        val dao = FakeAiProfileDao()
        dao.providers["provider_1"] = providerEntity(id = "provider_1")
        dao.models[expectedStableId] = modelEntity(id = expectedStableId, capabilities = "reasoning, custom ,reasoning")
        val saved = repo(dao).saveModel(
            AiModelDraft(providerId = "provider_1", modelName = "n", modelId = "gpt-4o")
        )
        // 既有在前（去重后保序），新推断的 gpt-4o 能力在后
        assertEquals("reasoning,custom,tools,vision,streaming", saved.capabilities)
    }

    @Test
    fun `saveModel 保留既有档案的启用位与排序号并刷新 updatedAt`() = runBlocking {
        val dao = FakeAiProfileDao()
        dao.providers["provider_1"] = providerEntity(id = "provider_1")
        dao.models[expectedStableId] =
            modelEntity(id = expectedStableId, enabled = false, sortNumber = 5, createdAt = 111L)
        val saved = repo(dao).saveModel(
            AiModelDraft(providerId = "provider_1", modelName = "n", modelId = "gpt-4o")
        )
        assertEquals(false, saved.enabled)
        assertEquals(5, saved.sortNumber)
        assertEquals(111L, saved.createdAt)
        assertTrue(saved.updatedAt >= 111L)
    }

    @Test
    fun `saveModel 写出的 defaultParamsJson 能往返解析回同一组参数`() = runBlocking {
        val dao = FakeAiProfileDao()
        dao.providers["provider_1"] = providerEntity(id = "provider_1")
        val draft = AiModelDraft(
            providerId = "provider_1",
            modelName = "n",
            modelId = "gpt-4o",
            temperature = 0.3f,
            reasoningLevel = AiReasoningLevel.HIGH,
        )
        repo(dao).saveModel(draft)
        val json = dao.insertedModels.single().defaultParamsJson
        assertNotNull(json)
        val parsed = JsonCodec.fromJsonObject(json, AiGenerationParams::class)
        assertEquals(AiGenerationParams(temperature = 0.3f, reasoningLevel = AiReasoningLevel.HIGH), parsed)
    }

    @Test
    fun `saveModel 拒绝空 providerId 与空 modelId`() = runBlocking {
        val dao = FakeAiProfileDao()
        val r = repo(dao)
        assertFailsWith<IllegalArgumentException> {
            r.saveModel(AiModelDraft(providerId = "  ", modelName = "n", modelId = "m"))
        }
        assertFailsWith<IllegalArgumentException> {
            r.saveModel(AiModelDraft(providerId = "p", modelName = "n", modelId = "  "))
        }
        assertTrue(dao.insertedModels.isEmpty(), "校验失败不得落库")
    }

    @Test
    fun `saveModel 在供应商不存在时抛错且不落库`() = runBlocking {
        val dao = FakeAiProfileDao()
        assertFailsWith<IllegalArgumentException> {
            repo(dao).saveModel(AiModelDraft(providerId = "missing", modelName = "n", modelId = "m"))
        }
        assertTrue(dao.insertedModels.isEmpty())
    }

    // ---------------------------------------------------------------- importProviderModels

    @Test
    fun `importProviderModels 按 id 去重且逐个落库`() = runBlocking {
        val dao = FakeAiProfileDao()
        dao.providers["provider_1"] = providerEntity(id = "provider_1")
        val imported = repo(dao).importProviderModels(
            "provider_1",
            listOf(
                AiAvailableModel(id = "m1", name = "One"),
                AiAvailableModel(id = "m1", name = "One again"),
                AiAvailableModel(id = "m2", name = "Two"),
            ),
        )
        assertEquals(listOf("m1", "m2"), imported.map { it.modelId })
        assertEquals(2, dao.insertedModels.size)
    }

    /** ⚠️ 两条回落各测一次，且**分两个用例**：同一批 `models` 里出现重复 id 会被 `distinctBy` 去掉，
     *  把「>0 覆盖」与「0 保留」塞进同一个 id 会只剩一条。 */
    @Test
    fun `importProviderModels 的窗口与上限在大于 0 时覆盖既有值`() = runBlocking {
        val dao = FakeAiProfileDao()
        dao.providers["provider_1"] = providerEntity(id = "provider_1")
        dao.models[expectedStableId] = modelEntity(id = expectedStableId, contextWindow = 100, maxOutputTokens = 200)
        val imported = repo(dao).importProviderModels(
            "provider_1",
            listOf(AiAvailableModel(id = "gpt-4o", name = "x", contextWindow = 300, maxOutputTokens = 400)),
        )
        assertEquals(300, imported.single().contextWindow)
        assertEquals(400, imported.single().maxOutputTokens)
    }

    @Test
    fun `importProviderModels 的窗口与上限为 0 时保留既有值`() = runBlocking {
        val dao = FakeAiProfileDao()
        dao.providers["provider_1"] = providerEntity(id = "provider_1")
        dao.models[expectedStableId] = modelEntity(id = expectedStableId, contextWindow = 100, maxOutputTokens = 200)
        val imported = repo(dao).importProviderModels(
            "provider_1",
            listOf(AiAvailableModel(id = "gpt-4o", name = "x")),
        )
        assertEquals(100, imported.single().contextWindow)
        assertEquals(200, imported.single().maxOutputTokens)
    }

    @Test
    fun `importProviderModels 保留既有 defaultParamsJson 并刷新 updatedAt`() = runBlocking {
        val dao = FakeAiProfileDao()
        dao.providers["provider_1"] = providerEntity(id = "provider_1")
        val existingJson = """{"temperature": 0.11}"""
        dao.models[expectedStableId] = modelEntity(id = expectedStableId, defaultParamsJson = existingJson, createdAt = 55L)
        val imported = repo(dao).importProviderModels("provider_1", listOf(AiAvailableModel(id = "gpt-4o")))
        val saved = imported.single()
        assertEquals(existingJson, saved.defaultParamsJson)
        assertEquals(55L, saved.createdAt)
        assertTrue(saved.updatedAt >= 55L)
    }

    @Test
    fun `importProviderModels 在供应商不存在时抛错`() = runBlocking {
        val dao = FakeAiProfileDao()
        assertFailsWith<IllegalArgumentException> {
            repo(dao).importProviderModels("missing", listOf(AiAvailableModel(id = "m")))
        }
        assertTrue(dao.insertedModels.isEmpty(), "校验失败不得落库")
    }

    @Test
    fun `importProviderModels 给没有既有档案的模型写出默认参数 JSON`() = runBlocking {
        val dao = FakeAiProfileDao()
        dao.providers["provider_1"] = providerEntity(id = "provider_1")
        repo(dao).importProviderModels("provider_1", listOf(AiAvailableModel(id = "gpt-4o")))
        val json = dao.insertedModels.single().defaultParamsJson
        assertNotNull(json)
        assertEquals(
            AiGenerationParams(reasoningLevel = AiReasoningLevel.MEDIUM),
            JsonCodec.fromJsonObject(json, AiGenerationParams::class),
        )
    }

    // ---------------------------------------------------------------- setDefaultModel

    @Test
    fun `setDefaultModel 写三个内建预设并返回翻译预设的配置`() = runBlocking {
        val dao = FakeAiProfileDao()
        dao.providers["provider_1"] = providerEntity(id = "provider_1")
        dao.models["m1"] = modelEntity(id = "m1", modelId = "gpt-4o", maxOutputTokens = 4096)

        val config = repo(dao).setDefaultModel("m1")

        assertEquals(
            listOf("default_translate_chapter", "default_summarize_chapter", "default_chat"),
            dao.insertedPresets.map { it.id },
        )
        assertEquals(
            listOf(
                AiTaskType.TRANSLATE_CHAPTER,
                AiTaskType.SUMMARIZE_CHAPTER,
                AiTaskType.CHAT,
            ),
            dao.insertedPresets.map { it.taskType },
        )
        assertTrue(dao.insertedPresets.all { it.isDefault }, "三个内建预设都必须 isDefault = true")
        assertTrue(dao.insertedPresets.all { it.modelProfileId == "m1" })
        // 返回值来自回读翻译预设 + 组装模型配置。⚠️ `id` 是**预设**的 id（不是模型 id）：
        // 迁移前 `aiProfileDao.getPreset(DEFAULT_TRANSLATE_PRESET_ID)?.toConfig()` 就是这么给的，
        // 已用 `git show HEAD:app/.../AiProfileRepository.kt` 逐行核对过。
        assertEquals("default_translate_chapter", config.id)
        assertEquals(AiTaskType.TRANSLATE_CHAPTER, config.taskType)
        assertEquals("m1", config.model.id, "模型配置的 id 才是模型档案 id")
        assertEquals("gpt-4o", config.model.modelId)
        assertEquals(4096, config.model.maxOutputTokens)
    }

    @Test
    fun `setDefaultModel 沿用既有翻译预设的 createdAt 与运行时选项`() = runBlocking {
        val dao = FakeAiProfileDao()
        dao.providers["provider_1"] = providerEntity(id = "provider_1")
        dao.models["m1"] = modelEntity(id = "m1")
        dao.presets["default_translate_chapter"] = presetEntity(
            id = "default_translate_chapter",
            taskType = AiTaskType.TRANSLATE_CHAPTER,
            modelProfileId = "m1",
            chunkPolicyJson = """{"maxInputChars": 222, "retryCount": 7}""",
            createdAt = 999L,
        )
        val config = repo(dao).setDefaultModel("m1")
        val written = dao.insertedPresets.first { it.id == "default_translate_chapter" }
        assertEquals(999L, written.createdAt)
        assertEquals(222, config.runtimeOptions.maxInputChars)
        assertEquals(7, config.runtimeOptions.retryCount)
    }

    @Test
    fun `setDefaultModel 在模型不存在时抛错`() = runBlocking {
        val dao = FakeAiProfileDao()
        val thrown = assertFailsWith<IllegalStateException> { repo(dao).setDefaultModel("nope") }
        assertTrue(thrown.message.orEmpty().contains("Model is required"), "缺模型时必须显式报错")
    }

    // ---------------------------------------------------------------- delete

    @Test
    fun `deleteProvider 先删模型档案再删供应商`() = runBlocking {
        val dao = FakeAiProfileDao()
        dao.providers["p1"] = providerEntity(id = "p1")
        dao.models["m1"] = modelEntity(id = "m1", providerId = "p1")
        repo(dao).deleteProvider("p1")
        assertEquals(listOf("p1"), dao.deletedModelsByProvider)
        assertEquals(listOf("p1"), dao.deletedProviders)
        assertTrue(
            dao.callLog.indexOf("deleteModelsByProvider") < dao.callLog.indexOf("deleteProvider"),
            "顺序必须是先删模型档案（否则会留下孤儿档案）：${dao.callLog}",
        )
        assertTrue(dao.models.isEmpty(), "该供应商名下的模型档案应被清掉")
    }

    @Test
    fun `deleteModel 只删指定的一个`() = runBlocking {
        val dao = FakeAiProfileDao()
        dao.models["m1"] = modelEntity(id = "m1")
        dao.models["m2"] = modelEntity(id = "m2")
        repo(dao).deleteModel("m1")
        assertEquals(listOf("m1"), dao.deletedModels)
        assertTrue(dao.models.containsKey("m2"))
    }

    // ---------------------------------------------------------------- getTaskPreset / toConfig

    @Test
    fun `getTaskPreset 走默认预设并组装成运行时配置`() = runBlocking {
        val dao = FakeAiProfileDao()
        dao.providers["provider_1"] = providerEntity(id = "provider_1")
        dao.models["m1"] = modelEntity(id = "m1", modelId = "gpt-4o")
        dao.presets["p1"] = presetEntity(id = "p1", taskType = AiTaskType.CHAT, modelProfileId = "m1", promptTemplate = "hello")
        val config = repo(dao).getTaskPreset(AiTaskType.CHAT)
        assertNotNull(config)
        assertEquals("p1", config.id)
        assertEquals("hello", config.promptTemplate)
    }

    @Test
    fun `getTaskPreset 跳过被禁用的预设`() = runBlocking {
        val dao = FakeAiProfileDao()
        dao.providers["provider_1"] = providerEntity(id = "provider_1")
        dao.models["m1"] = modelEntity(id = "m1")
        dao.presets["disabled"] = presetEntity(id = "disabled", taskType = AiTaskType.CHAT, modelProfileId = "m1", enabled = false)
        assertNull(repo(dao).getTaskPreset(AiTaskType.CHAT))
    }

    @Test
    fun `getTaskPreset 缺模型档案时返回 null`() = runBlocking {
        val dao = FakeAiProfileDao()
        dao.presets["p1"] = presetEntity(id = "p1", taskType = AiTaskType.CHAT, modelProfileId = "missing")
        assertNull(repo(dao).getTaskPreset(AiTaskType.CHAT))
    }

    @Test
    fun `getTaskPreset 缺供应商时返回 null`() = runBlocking {
        val dao = FakeAiProfileDao()
        dao.models["m1"] = modelEntity(id = "m1", providerId = "missing-provider")
        dao.presets["p1"] = presetEntity(id = "p1", taskType = AiTaskType.CHAT, modelProfileId = "m1")
        assertNull(repo(dao).getTaskPreset(AiTaskType.CHAT))
    }

    /** ⚠️ 合并方向是**预设覆盖模型**。调换两侧会让用户设的温度被模型默认值压掉。 */
    @Test
    fun `getTaskPreset 的参数合并方向是预设覆盖模型`() = runBlocking {
        val dao = FakeAiProfileDao()
        dao.providers["provider_1"] = providerEntity(id = "provider_1")
        dao.models["m1"] = modelEntity(
            id = "m1",
            modelId = "gpt-4o",
            maxOutputTokens = 4096,
            defaultParamsJson = JsonCodec.toJson(
                AiGenerationParams(temperature = 0.9f, reasoningLevel = AiReasoningLevel.HIGH)
            ),
        )
        dao.presets["p1"] = presetEntity(
            id = "p1",
            taskType = AiTaskType.TRANSLATE_CHAPTER,
            modelProfileId = "m1",
            paramsJson = JsonCodec.toJson(AiGenerationParams(temperature = 0.3f)),
        )
        val params = repo(dao).getTaskPreset(AiTaskType.TRANSLATE_CHAPTER)!!.params
        assertEquals(0.3f, params.temperature, "预设侧的温度必须赢过模型侧")
        assertEquals(AiReasoningLevel.HIGH, params.reasoningLevel, "预设侧是 AUTO ⇒ 回落到模型侧的 HIGH")
        assertEquals(4096, params.maxOutputTokens, "两侧都没给上限 ⇒ 用模型的 maxOutputTokens")
    }

    /** ⚠️ 只有 `chatPath` / `responsesPath` / `messagesPath` 有默认回落；`modelsPath` / `modelsUrl` 没有。 */
    @Test
    fun `getTaskPreset 只给三条路径补默认值`() = runBlocking {
        val dao = FakeAiProfileDao()
        dao.providers["provider_1"] = providerEntity(id = "provider_1", modelsUrl = null, modelsPath = null)
        dao.models["m1"] = modelEntity(id = "m1")
        dao.presets["p1"] = presetEntity(id = "p1", taskType = AiTaskType.CHAT, modelProfileId = "m1")
        val provider = repo(dao).getTaskPreset(AiTaskType.CHAT)!!.model.provider
        assertEquals("/chat/completions", provider.chatPath)
        assertEquals("/responses", provider.responsesPath)
        assertEquals("/v1/messages", provider.messagesPath)
        assertNull(provider.modelsPath)
        assertNull(provider.modelsUrl)
    }

    @Test
    fun `getTaskPreset 保留供应商显式配置的路径`() = runBlocking {
        val dao = FakeAiProfileDao()
        dao.providers["provider_1"] = providerEntity(
            id = "provider_1",
            chatPath = "/custom/chat",
            responsesPath = "/custom/resp",
            messagesPath = "/custom/msg",
            modelsPath = "/custom/models",
        )
        dao.models["m1"] = modelEntity(id = "m1")
        dao.presets["p1"] = presetEntity(id = "p1", taskType = AiTaskType.CHAT, modelProfileId = "m1")
        val provider = repo(dao).getTaskPreset(AiTaskType.CHAT)!!.model.provider
        assertEquals("/custom/chat", provider.chatPath)
        assertEquals("/custom/resp", provider.responsesPath)
        assertEquals("/custom/msg", provider.messagesPath)
        assertEquals("/custom/models", provider.modelsPath)
    }

    @Test
    fun `能力字符串按逗号拆成集合且忽略空白项`() = runBlocking {
        val dao = FakeAiProfileDao()
        dao.providers["provider_1"] = providerEntity(id = "provider_1")
        dao.models["m1"] = modelEntity(id = "m1", capabilities = " tools , , vision ,")
        dao.presets["p1"] = presetEntity(id = "p1", taskType = AiTaskType.CHAT, modelProfileId = "m1")
        assertEquals(
            setOf("tools", "vision"),
            repo(dao).getTaskPreset(AiTaskType.CHAT)!!.model.capabilities,
        )
    }

    // ---------------------------------------------------------------- 坏 JSON 容错

    @Test
    fun `坏 JSON 的 paramsJson 回落成默认参数而不是抛错`() = runBlocking {
        val dao = FakeAiProfileDao()
        dao.providers["provider_1"] = providerEntity(id = "provider_1")
        dao.models["m1"] = modelEntity(
            id = "m1",
            defaultParamsJson = JsonCodec.toJson(AiGenerationParams(temperature = 0.9f)),
        )
        dao.presets["p1"] = presetEntity(
            id = "p1",
            taskType = AiTaskType.CHAT,
            modelProfileId = "m1",
            paramsJson = "{ this is not json",
        )
        // 预设侧解析失败 ⇒ 全默认（temperature = null）⇒ 合并回落到模型侧的 0.9
        assertEquals(0.9f, repo(dao).getTaskPreset(AiTaskType.CHAT)!!.params.temperature)
    }

    @Test
    fun `坏 JSON 的 chunkPolicyJson 回落成默认运行时选项`() = runBlocking {
        val dao = FakeAiProfileDao()
        dao.providers["provider_1"] = providerEntity(id = "provider_1")
        dao.models["m1"] = modelEntity(id = "m1")
        dao.presets["p1"] = presetEntity(
            id = "p1",
            taskType = AiTaskType.CHAT,
            modelProfileId = "m1",
            chunkPolicyJson = "]]not json[[",
        )
        val options = repo(dao).getTaskPreset(AiTaskType.CHAT)!!.runtimeOptions
        assertEquals("zh", options.targetLanguage)
        assertEquals(10000, options.maxInputChars)
        assertEquals(1, options.concurrentRequests)
        assertEquals(2, options.retryCount)
    }

    @Test
    fun `headersJson 正常解析成字符串 map`() = runBlocking {
        val dao = FakeAiProfileDao()
        dao.providers["provider_1"] = providerEntity(
            id = "provider_1",
            headersJson = """{"X-Api-Version": "2024-01", "X-Int": 7}""",
            customHeadersJson = """{"X-Custom": "yes"}""",
        )
        dao.models["m1"] = modelEntity(id = "m1")
        dao.presets["p1"] = presetEntity(id = "p1", taskType = AiTaskType.CHAT, modelProfileId = "m1")
        val provider = repo(dao).getTaskPreset(AiTaskType.CHAT)!!.model.provider
        assertEquals("2024-01", provider.headers["X-Api-Version"])
        assertEquals("7", provider.headers["X-Int"], "数字要落成十进制字符串而不是 7.0")
        assertEquals("yes", provider.customHeaders["X-Custom"])
    }

    @Test
    fun `坏 JSON 与空白的 headersJson 都回落成空 map`() = runBlocking {
        val dao = FakeAiProfileDao()
        dao.providers["provider_1"] = providerEntity(id = "provider_1", headersJson = "not json {", customHeadersJson = "   ")
        dao.models["m1"] = modelEntity(id = "m1")
        dao.presets["p1"] = presetEntity(id = "p1", taskType = AiTaskType.CHAT, modelProfileId = "m1")
        val provider = repo(dao).getTaskPreset(AiTaskType.CHAT)!!.model.provider
        assertTrue(provider.headers.isEmpty())
        assertTrue(provider.customHeaders.isEmpty())
    }

    // ---------------------------------------------------------------- saveTaskPreset

    @Test
    fun `saveTaskPreset 给内建任务类型用固定 preset id 并写入参数`() = runBlocking {
        val dao = FakeAiProfileDao()
        dao.providers["provider_1"] = providerEntity(id = "provider_1")
        dao.models["m1"] = modelEntity(id = "m1")
        val config = repo(dao).saveTaskPreset(
            taskType = AiTaskType.TRANSLATE_CHAPTER,
            promptTemplate = "translate this",
            temperature = 0.25f,
            maxOutputTokens = 1234,
        )
        val written = dao.insertedPresets.single()
        assertEquals("default_translate_chapter", written.id)
        assertEquals("translate this", written.promptTemplate)
        assertEquals(true, written.isDefault)
        assertEquals("m1", written.modelProfileId, "没有既有预设 ⇒ 取第一个模型档案")
        assertEquals("default_translate_chapter", config.id)
        assertEquals(0.25f, config.params.temperature)
        assertEquals(1234, config.params.maxOutputTokens)
    }

    @Test
    fun `saveTaskPreset 沿用既有预设的 name 与 createdAt`() = runBlocking {
        val dao = FakeAiProfileDao()
        dao.providers["provider_1"] = providerEntity(id = "provider_1")
        dao.models["m1"] = modelEntity(id = "m1")
        dao.presets["default_chat"] = presetEntity(
            id = "default_chat",
            taskType = AiTaskType.CHAT,
            name = "我的对话预设",
            modelProfileId = "m1",
            createdAt = 777L,
        )
        val config = repo(dao).saveTaskPreset(AiTaskType.CHAT, "", 0.5f, 10)
        val written = dao.insertedPresets.single()
        assertEquals("我的对话预设", written.name)
        assertEquals(777L, written.createdAt)
        assertEquals("You are a helpful AI assistant.", written.promptTemplate, "空白 prompt ⇒ 内建默认")
        assertEquals("default_chat", config.id)
    }

    @Test
    fun `saveTaskPreset 的空白 prompt 对非内建任务类型回落成通用提示词`() = runBlocking {
        val dao = FakeAiProfileDao()
        dao.providers["provider_1"] = providerEntity(id = "provider_1")
        dao.models["m1"] = modelEntity(id = "m1")
        repo(dao).saveTaskPreset("text_factory", "  ", 0.5f, 10)
        val written = dao.insertedPresets.single()
        assertEquals("You are a helpful AI assistant.", written.promptTemplate)
        assertEquals("Default Preset", written.name)
        assertTrue(written.id.startsWith("preset_"), "非内建任务类型的新预设 id 应是随机前缀：${written.id}")
    }

    @Test
    fun `saveTaskPreset 在保存失败时抛出带 cause 的 IllegalStateException`() = runBlocking {
        // 没有模型档案 ⇒ modelProfileId 落成空串 ⇒ toConfig 拿不到模型 ⇒ error("Failed to save task preset")
        val dao = FakeAiProfileDao()
        val ex = assertFailsWith<IllegalStateException> {
            repo(dao).saveTaskPreset(AiTaskType.CHAT, "p", 0.5f, 10)
        }
        assertTrue(ex.message.orEmpty().startsWith("保存 AI 预设配置失败"))
        assertTrue(ex.cause != null, "必须保留原始 cause")
    }
}
