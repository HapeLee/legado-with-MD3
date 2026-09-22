package io.legado.app.feature.settings.ai

import android.app.Application
import android.os.Looper
import io.legado.app.domain.ai.AiModelProfile
import io.legado.app.domain.ai.AiProfileGateway
import io.legado.app.domain.ai.AiProviderProfile
import io.legado.app.domain.ai.AiTaskPreset
import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.model.AiAvailableModel
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiGenerateResponse
import io.legado.app.domain.model.AiModelDraft
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiProviderDraft
import io.legado.app.domain.model.AiTaskPresetConfig
import io.legado.app.domain.model.AiTaskType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * `AiModelEditViewModel` 的行为基线（M5-5b 新增；迁移前零测试）。
 *
 * 本片有一处**实质改动**（其余是机械搬迁），测试主要钉它：
 * `GSON.fromJson(json, AiGenerationParams::class.java)` →
 * `JsonCodec.fromJsonObject(json, AiGenerationParams::class)`。
 * 这条路径只在「模型带 `defaultParamsJson`」时才走到 ⇒ 用例 1 专门造这个数据。
 *
 * 另外两条钉编排语义：
 *  - `init` 的**只填一次**语义（`initialized` 之后 providers 会持续更新，但用户正在编辑的
 *    字段**不能被流刷新覆盖**——写反了会让用户输入的字在保存前被抹掉）；
 *  - 保存成功的两条 Effect（提示 + 返回）与 `modelProfileId` 的回写。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class AiModelEditViewModelTest {

    @Test
    fun `默认参数JSON经JsonCodec反序列化进状态`() {
        val gateway = FakeModelEditGateway(
            providers = listOf(meProvider("p1")),
            models = listOf(meModel("m1", "p1", defaultParamsJson = """{"temperature":0.3}""")),
        )
        val viewModel = createViewModel(gateway, modelProfileId = "m1")
        idle()

        val state = viewModel.uiState.value
        assertEquals("JSON 里的温度要进状态", 0.3f, state.temperature)
        assertEquals("m1", state.modelProfileId)
        assertEquals("model-m1", state.modelName)
        assertTrue("init 之后应标记 initialized", state.initialized)
    }

    @Test
    fun `非法JSON回落到默认参数而不是崩溃`() {
        val gateway = FakeModelEditGateway(
            providers = listOf(meProvider("p1")),
            models = listOf(meModel("m1", "p1", defaultParamsJson = "{ 这不是 JSON")),
        )
        val viewModel = createViewModel(gateway, modelProfileId = "m1")
        idle()

        // 状态里保留 UiState 的默认温度（没有被解析结果覆盖，也没有抛异常）
        assertEquals(AiModelEditUiState().temperature, viewModel.uiState.value.temperature)
    }

    @Test
    fun `initialized之后流刷新不再覆盖正在编辑的字段`() {
        val gateway = FakeModelEditGateway(
            providers = listOf(meProvider("p1")),
            models = listOf(meModel("m1", "p1", displayName = "原名")),
        )
        val viewModel = createViewModel(gateway, modelProfileId = "m1")
        idle()
        assertEquals("原名", viewModel.uiState.value.modelName)

        viewModel.onIntent(AiModelEditIntent.UpdateModelName("用户改的名字"))
        idle()

        // 再让 provider 流发一次（触发 init 里的 collect）
        gateway.emitProviders(listOf(meProvider("p1"), meProvider("p2")))
        idle()

        assertEquals(
            "用户正在编辑的字段不能被流刷新覆盖",
            "用户改的名字",
            viewModel.uiState.value.modelName
        )
        assertEquals("providers 仍应更新", 2, viewModel.uiState.value.providers.size)
    }

    @Test
    fun `保存成功发提示与返回并回写modelProfileId`() {
        val gateway = FakeModelEditGateway(
            providers = listOf(meProvider("p1")),
            models = emptyList(),
        )
        val viewModel = createViewModel(gateway, modelProfileId = null)
        idle()
        val effects = collect(viewModel)

        viewModel.onIntent(AiModelEditIntent.UpdateModelName("新模型"))
        idle()
        viewModel.onIntent(AiModelEditIntent.Save)
        idle()

        assertEquals(
            "Default AI model saved",
            effects.filterIsInstance<AiModelEditEffect.ShowMessage>().singleOrNull()?.message
        )
        assertEquals(1, effects.count { it == AiModelEditEffect.NavigateBack })
        assertEquals("保存后要把新建档案的 id 回写进状态", "saved-id", viewModel.uiState.value.modelProfileId)
    }

    private fun createViewModel(
        gateway: AiProfileGateway,
        modelProfileId: String?,
    ) = AiModelEditViewModel(
        initialProviderId = null,
        initialModelProfileId = modelProfileId,
        aiProfileGateway = gateway,
        aiTextGateway = FakeAiTextGateway(),
    )

    private fun collect(viewModel: AiModelEditViewModel): MutableList<AiModelEditEffect> {
        val out = mutableListOf<AiModelEditEffect>()
        CoroutineScope(Dispatchers.Unconfined).launch { viewModel.effects.collect { out += it } }
        return out
    }

    private fun idle() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }
}

private fun meProvider(id: String) = AiProviderProfile(
    id = id,
    name = "provider-$id",
    protocol = "openai",
    baseUrl = "https://$id.example.com",
)

private fun meModel(
    id: String,
    providerId: String,
    displayName: String = "model-$id",
    defaultParamsJson: String? = null,
) = AiModelProfile(
    id = id,
    providerId = providerId,
    displayName = displayName,
    modelId = "id-$id",
    defaultParamsJson = defaultParamsJson,
)

/** 只需要 observe 流、`saveModel` 与 `setDefaultModel`，其余 `error()` 兜底。 */
private class FakeModelEditGateway(
    providers: List<AiProviderProfile>,
    models: List<AiModelProfile>,
) : AiProfileGateway {

    private val providersFlow = MutableStateFlow(providers)
    private val modelsFlow = MutableStateFlow(models)

    fun emitProviders(value: List<AiProviderProfile>) {
        providersFlow.value = value
    }

    override fun observeProviders(): Flow<List<AiProviderProfile>> = providersFlow
    override fun observeModels(): Flow<List<AiModelProfile>> = modelsFlow
    override fun observePresets(): Flow<List<AiTaskPreset>> = flowOf(emptyList())

    override suspend fun getProvider(id: String): AiProviderProfile? = error("未使用")
    override suspend fun getModel(id: String): AiModelProfile? = error("未使用")
    override suspend fun getTaskPreset(taskType: String): AiTaskPresetConfig? = error("未使用")
    override suspend fun saveProvider(draft: AiProviderDraft): AiProviderProfile = error("未使用")

    override suspend fun saveModel(draft: AiModelDraft): AiModelProfile =
        AiModelProfile(
            id = "saved-id",
            providerId = draft.providerId,
            displayName = draft.modelName,
            modelId = draft.modelId,
        )

    override suspend fun importProviderModels(
        providerId: String,
        models: List<AiAvailableModel>
    ): List<AiModelProfile> = error("未使用")

    // ⚠️ `save()` 里会调它，**必须返回合法值**：写成 `error(...)` 会让成功路径变成失败路径
    // （本片初版就踩了，与 M5-5a 同一个坑）。
    override suspend fun setDefaultModel(modelProfileId: String): AiTaskPresetConfig =
        AiTaskPresetConfig(
            id = modelProfileId,
            taskType = AiTaskType.TRANSLATE_CHAPTER,
            name = "",
            model = io.legado.app.domain.model.AiModelConfig(
                id = modelProfileId,
                provider = io.legado.app.domain.model.AiProviderConfig(
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

    override suspend fun saveTaskPreset(
        taskType: String,
        promptTemplate: String,
        temperature: Float,
        maxOutputTokens: Int
    ): AiTaskPresetConfig = error("未使用")

    override suspend fun deleteProvider(providerId: String) = error("未使用")
    override suspend fun deleteModel(modelId: String) = error("未使用")
}

private class FakeAiTextGateway : AiTextGateway {
    override suspend fun generate(request: AiGenerateRequest): Result<AiGenerateResponse> =
        error("未使用")

    override fun generateStream(request: AiGenerateRequest): Flow<AiStreamEvent> = flowOf()

    override suspend fun fetchModels(provider: AiProviderConfig): Result<List<AiAvailableModel>> =
        error("未使用")
}
