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
 * `AiProviderEditViewModel` 的行为基线（M5-5c 新增；迁移前零测试）。
 *
 * 本片有两处实质改动，测试主要钉它们：
 *
 * 1. **`appCtx.getString` 三处 → 注入的 [AiProviderStringSource]**。用例 2/3/4 专门覆盖
 *    「测试连接」的三个结果分支（0 个模型 / N 个模型 / 失败），并断言**取到的是注入进来的
 *    文案**（假实现给的是可识别的假值）——这样「VM 是否真的走了注入路径」是被验证的，
 *    而不是"能编译就行"。
 * 2. **`GSON.fromJson` → `JsonCodec.fromJsonObject`**（用例 5，同 M5-5b 的做法）。
 *
 * 另外一条钉的是 `init` 的**只填一次**语义：`initialized` 之后流刷新只更新 `providerModels`，
 * **不覆盖**用户正在编辑的 provider 字段（写反了会让用户输入的字在保存前被抹掉）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class AiProviderEditViewModelTest {

    @Test
    fun `init填充provider字段且initialized后不再覆盖`() {
        val gateway = FakeProviderGateway(
            providers = listOf(peProvider("p1", name = "原名", baseUrl = "https://a.example.com")),
            models = emptyList(),
        )
        val viewModel = createViewModel(gateway, providerId = "p1")
        idle()

        assertEquals("原名", viewModel.uiState.value.providerName)
        assertEquals("https://a.example.com", viewModel.uiState.value.baseUrl)
        assertTrue(viewModel.uiState.value.initialized)

        viewModel.onIntent(AiProviderEditIntent.UpdateProviderName("用户改的名字"))
        idle()
        gateway.emitProviders(listOf(peProvider("p1", name = "流里改成别的")))
        idle()

        assertEquals(
            "用户正在编辑的字段不能被流刷新覆盖",
            "用户改的名字",
            viewModel.uiState.value.providerName
        )
    }

    @Test
    fun `测试连接成功但没取到模型时用注入的文案`() {
        val gateway = FakeProviderGateway(providers = listOf(peProvider("p1")), models = emptyList())
        val text = FakeProviderTextGateway(models = emptyList())
        val viewModel = createViewModel(gateway, text = text)
        idle()
        val effects = collect(viewModel)

        viewModel.onIntent(AiProviderEditIntent.TestConnection)
        idle()

        assertEquals(
            listOf("[no-models]"),
            effects.filterIsInstance<AiProviderEditEffect.ShowMessage>().map { it.message }
        )
        assertEquals("测试完要把 isTesting 复位", false, viewModel.uiState.value.isTesting)
    }

    @Test
    fun `测试连接成功带模型数时把count传进文案`() {
        val gateway = FakeProviderGateway(providers = listOf(peProvider("p1")), models = emptyList())
        val text = FakeProviderTextGateway(
            models = listOf(
                AiAvailableModel(id = "m1", name = "one"),
                AiAvailableModel(id = "m2", name = "two"),
                AiAvailableModel(id = "m3", name = "three"),
            )
        )
        val viewModel = createViewModel(gateway, text = text)
        idle()
        val effects = collect(viewModel)

        viewModel.onIntent(AiProviderEditIntent.TestConnection)
        idle()

        assertEquals(
            "count 要作为格式参数传进去",
            listOf("[with-models:3]"),
            effects.filterIsInstance<AiProviderEditEffect.ShowMessage>().map { it.message }
        )
    }

    @Test
    fun `测试连接失败时把错误详情拼在注入的兜底文案后面`() {
        val gateway = FakeProviderGateway(providers = listOf(peProvider("p1")), models = emptyList())
        val text = FakeProviderTextGateway(fetchError = RuntimeException("连接超时"))
        val viewModel = createViewModel(gateway, text = text)
        idle()
        val effects = collect(viewModel)

        viewModel.onIntent(AiProviderEditIntent.TestConnection)
        idle()

        assertEquals(
            "有错误详情时拼成「兜底文案: 详情」",
            listOf("[failed]: 连接超时"),
            effects.filterIsInstance<AiProviderEditEffect.ShowMessage>().map { it.message }
        )

        // 没有详情（message 为空）时只发兜底文案本身
        val blankError = FakeProviderTextGateway(
            fetchError = RuntimeException(),
        )
        val viewModel2 = createViewModel(gateway, text = blankError)
        idle()
        val effects2 = collect(viewModel2)
        viewModel2.onIntent(AiProviderEditIntent.TestConnection)
        idle()
        assertEquals(
            listOf("[failed]"),
            effects2.filterIsInstance<AiProviderEditEffect.ShowMessage>().map { it.message }
        )
    }

    @Test
    fun `默认参数JSON经JsonCodec反序列化进模型列表`() {
        val gateway = FakeProviderGateway(
            providers = listOf(peProvider("p1")),
            models = listOf(
                peModel(
                    id = "m1",
                    providerId = "p1",
                    defaultParamsJson = """{"temperature":0.85}"""
                )
            ),
        )
        val viewModel = createViewModel(gateway, providerId = "p1")
        idle()

        val model = viewModel.uiState.value.providerModels.single()
        assertEquals("JSON 里的温度要进状态", 0.85f, model.temperature)
    }

    private fun createViewModel(
        gateway: AiProfileGateway,
        text: AiTextGateway = FakeProviderTextGateway(),
        providerId: String? = null,
    ) = AiProviderEditViewModel(
        initialProviderId = providerId,
        aiProfileGateway = gateway,
        aiTextGateway = text,
        strings = FakeProviderStrings(),
    )

    private fun collect(viewModel: AiProviderEditViewModel): MutableList<AiProviderEditEffect> {
        val out = mutableListOf<AiProviderEditEffect>()
        CoroutineScope(Dispatchers.Unconfined).launch { viewModel.effects.collect { out += it } }
        return out
    }

    private fun idle() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }
}

/** 假文案源：返回带标记的值，好让用例断言「走的确实是注入路径」。 */
private class FakeProviderStrings : AiProviderStringSource {
    override suspend fun testSuccessNoModels(): String = "[no-models]"
    override suspend fun testSuccessWithModels(count: Int): String = "[with-models:$count]"
    override suspend fun testFailed(): String = "[failed]"
}

private fun peProvider(
    id: String,
    name: String = "provider-$id",
    baseUrl: String = "https://$id.example.com",
) = AiProviderProfile(
    id = id,
    name = name,
    protocol = "openai",
    baseUrl = baseUrl,
)

private fun peModel(
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

/** 只需要 observe 流与 `saveProvider`，其余 `error()` 兜底。 */
private class FakeProviderGateway(
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

    override suspend fun saveProvider(draft: AiProviderDraft): AiProviderProfile =
        AiProviderProfile(
            id = draft.providerId ?: "pe-saved-id",
            name = draft.providerName,
            protocol = draft.protocol,
            baseUrl = draft.baseUrl,
        )

    override suspend fun saveModel(draft: AiModelDraft): AiModelProfile = error("未使用")

    override suspend fun importProviderModels(
        providerId: String,
        models: List<AiAvailableModel>
    ): List<AiModelProfile> = emptyList()

    override suspend fun setDefaultModel(modelProfileId: String): AiTaskPresetConfig = error("未使用")

    override suspend fun saveTaskPreset(
        taskType: String,
        promptTemplate: String,
        temperature: Float,
        maxOutputTokens: Int
    ): AiTaskPresetConfig = error("未使用")

    override suspend fun deleteProvider(providerId: String) = error("未使用")
    override suspend fun deleteModel(modelId: String) = error("未使用")
}

private class FakeProviderTextGateway(
    private val models: List<AiAvailableModel> = emptyList(),
    private val fetchError: Throwable? = null,
) : AiTextGateway {

    override suspend fun fetchModels(provider: AiProviderConfig): Result<List<AiAvailableModel>> {
        fetchError?.let { return Result.failure(it) }
        return Result.success(models)
    }

    override suspend fun generate(request: AiGenerateRequest): Result<AiGenerateResponse> =
        error("未使用")

    override fun generateStream(request: AiGenerateRequest): Flow<AiStreamEvent> = flowOf()
}
