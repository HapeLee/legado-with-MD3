package io.legado.app.feature.settings.ai

import android.app.Application
import android.os.Looper
import io.legado.app.domain.ai.AiModelProfile
import io.legado.app.domain.ai.AiProfileGateway
import io.legado.app.domain.ai.AiProviderProfile
import io.legado.app.domain.ai.AiTaskPreset
import io.legado.app.domain.model.AiAvailableModel
import io.legado.app.domain.model.AiModelConfig
import io.legado.app.domain.model.AiModelDraft
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiProviderDraft
import io.legado.app.domain.model.AiTaskPresetConfig
import io.legado.app.domain.model.AiTaskType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * `AiConfigViewModel` 的行为基线（M5-5a 新增；迁移前零测试）。
 *
 * 本页 VM 是本批**最干净**的一个（只依赖 `AiProfileGateway`，不碰资源 / appCtx / GSON），
 * 但它有真正值得钉的**编排语义**：
 *
 * 1. **模型按 provider 归组，且归属于未知 provider 的模型要被丢掉**（`mapNotNull`）——
 *    否则下拉面板会出现一个没有 provider 名可显示的分组。
 * 2. **「当前模型」的取值优先级**：先看**默认的翻译预设**指向哪个模型，没有预设才退到
 *    「第一个模型」。写反了会让主页面显示的当前模型与翻译页实际用的模型不一致。
 * 3. `SetDefaultModel` 的两条提示是**硬编码英文**（迁移前就如此）⇒ 逐字钉住，
 *    免得迁移过程中被"顺手"改成资源或改文案。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class AiConfigViewModelTest {

    @Test
    fun `模型按provider归组且未知provider的模型被丢弃`() {
        val gateway = FakeAiProfileGateway(
            providers = listOf(provider("p1"), provider("p2")),
            models = listOf(
                model("m1", "p1"),
                model("m2", "p1"),
                model("m3", "p2"),
                model("m-orphan", "p-unknown"), // 所属 provider 不存在
            ),
        )
        val viewModel = AiConfigViewModel(gateway)
        idle()

        val state = viewModel.uiState.value
        assertEquals(2, state.providers.size)
        assertEquals(2, state.providers.single { it.providerId == "p1" }.modelCount)
        assertEquals(1, state.providers.single { it.providerId == "p2" }.modelCount)
        assertEquals("孤儿模型不该出现在列表里", 3, state.models.size)
        // ⚠️ `modelCount` 与 `models.size` **本来就不等**：前者是 `models.size`（原始列表，
        // **含**所属 provider 未知的孤儿），后者是过滤后的可展示项。这不是本片引入的，
        // 是迁移前的既有语义 ⇒ 保持等价，并把差异钉在这里（写测试时我先按 3 断言，被它纠正）。
        assertEquals("modelCount 是原始模型数（含孤儿）", 4, state.modelCount)
    }

    @Test
    fun `当前模型优先取默认翻译预设指向的模型`() {
        val gateway = FakeAiProfileGateway(
            providers = listOf(provider("p1")),
            models = listOf(model("m1", "p1", displayName = "第一个"), model("m2", "p1", displayName = "预设指定")),
            presets = listOf(
                preset(taskType = AiTaskType.TRANSLATE_CHAPTER, modelProfileId = "m2", isDefault = true)
            ),
        )
        val viewModel = AiConfigViewModel(gateway)
        idle()

        assertEquals("m2", viewModel.uiState.value.currentModelProfileId)
        assertEquals("预设指定", viewModel.uiState.value.currentModelName)
        assertTrue(viewModel.uiState.value.models.single { it.modelProfileId == "m2" }.isCurrent)
    }

    @Test
    fun `没有默认翻译预设时退到第一个模型`() {
        val gateway = FakeAiProfileGateway(
            providers = listOf(provider("p1")),
            models = listOf(model("m1", "p1", displayName = "第一个"), model("m2", "p1")),
            presets = emptyList(),
        )
        val viewModel = AiConfigViewModel(gateway)
        idle()

        assertEquals("m1", viewModel.uiState.value.currentModelProfileId)
        assertEquals("第一个", viewModel.uiState.value.currentModelName)
    }

    @Test
    fun `设为默认模型成功与失败各发一条硬编码英文提示`() {
        val gateway = FakeAiProfileGateway(providers = listOf(provider("p1")), models = listOf(model("m1", "p1")))
        val viewModel = AiConfigViewModel(gateway)
        idle()
        val effects = collect(viewModel)

        viewModel.onIntent(AiConfigIntent.SetDefaultModel("m1"))
        idle()
        assertEquals(
            "Default AI model saved",
            effects.filterIsInstance<AiConfigEffect.ShowMessage>().singleOrNull()?.message
        )

        gateway.setDefaultError = IllegalStateException("boom")
        viewModel.onIntent(AiConfigIntent.SetDefaultModel("m1"))
        idle()
        val messages = effects.filterIsInstance<AiConfigEffect.ShowMessage>().map { it.message }
        assertEquals(listOf("Default AI model saved", "boom"), messages)
    }

    private fun collect(viewModel: AiConfigViewModel): MutableList<AiConfigEffect> {
        val out = mutableListOf<AiConfigEffect>()
        CoroutineScope(Dispatchers.Unconfined).launch { viewModel.effects.collect { out += it } }
        return out
    }

    private fun idle() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }
}

private fun provider(id: String) = AiProviderProfile(
    id = id,
    name = "provider-$id",
    protocol = "openai",
    baseUrl = "https://$id.example.com",
)

private fun model(id: String, providerId: String, displayName: String = "model-$id") = AiModelProfile(
    id = id,
    providerId = providerId,
    displayName = displayName,
    modelId = "id-$id",
)

private fun preset(
    taskType: String,
    modelProfileId: String,
    isDefault: Boolean,
) = AiTaskPreset(
    id = "preset-$taskType",
    taskType = taskType,
    name = "",
    modelProfileId = modelProfileId,
    promptTemplate = "",
    isDefault = isDefault,
)

/** 只需要三个 observe 流与 `setDefaultModel`，其余用 `error()` 兜底。 */
private class FakeAiProfileGateway(
    providers: List<AiProviderProfile>,
    models: List<AiModelProfile>,
    presets: List<AiTaskPreset> = emptyList(),
) : AiProfileGateway {

    private val providersFlow = MutableStateFlow(providers)
    private val modelsFlow = MutableStateFlow(models)
    private val presetsFlow = MutableStateFlow(presets)

    var setDefaultError: Throwable? = null

    override fun observeProviders(): Flow<List<AiProviderProfile>> = providersFlow
    override fun observeModels(): Flow<List<AiModelProfile>> = modelsFlow
    override fun observePresets(): Flow<List<AiTaskPreset>> = presetsFlow

    override suspend fun getProvider(id: String): AiProviderProfile? = error("未使用")
    override suspend fun getModel(id: String): AiModelProfile? = error("未使用")
    override suspend fun getTaskPreset(taskType: String): AiTaskPresetConfig? = error("未使用")
    override suspend fun saveProvider(draft: AiProviderDraft): AiProviderProfile = error("未使用")
    override suspend fun saveModel(draft: AiModelDraft): AiModelProfile = error("未使用")
    override suspend fun importProviderModels(
        providerId: String,
        models: List<AiAvailableModel>
    ): List<AiModelProfile> = error("未使用")

    override suspend fun setDefaultModel(modelProfileId: String): AiTaskPresetConfig {
        setDefaultError?.let { throw it }
        // ⚠️ 成功路径必须返回**合法**配置：写成 `error(...)` 会让本该成功的分支抛异常，
        // 于是「成功发哪条提示」那条断言就会测到失败路径（本片写完初版时就踩了）。
        return AiTaskPresetConfig(
            id = modelProfileId,
            taskType = AiTaskType.TRANSLATE_CHAPTER,
            name = "",
            model = AiModelConfig(
                id = modelProfileId,
                provider = AiProviderConfig(
                    id = "",
                    name = "",
                    protocol = "",
                    baseUrl = "",
                    apiKey = ""
                ),
                displayName = "",
                modelId = ""
            ),
            promptTemplate = ""
        )
    }

    override suspend fun saveTaskPreset(
        taskType: String,
        promptTemplate: String,
        temperature: Float,
        maxOutputTokens: Int
    ): AiTaskPresetConfig = error("未使用")

    override suspend fun deleteProvider(providerId: String) = error("未使用")
    override suspend fun deleteModel(modelId: String) = error("未使用")
}
