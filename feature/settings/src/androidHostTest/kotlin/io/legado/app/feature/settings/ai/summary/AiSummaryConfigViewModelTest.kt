package io.legado.app.feature.settings.ai.summary

import android.app.Application
import android.os.Looper
import io.legado.app.domain.ai.AiProfileGateway
import io.legado.app.domain.model.AiAvailableModel
import io.legado.app.domain.model.AiGenerationParams
import io.legado.app.domain.ai.AiModelProfile
import io.legado.app.domain.ai.AiProviderProfile
import io.legado.app.domain.ai.AiTaskPreset
import io.legado.app.domain.model.AiModelConfig
import io.legado.app.domain.model.AiModelDraft
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiProviderDraft
import io.legado.app.domain.model.AiTaskPresetConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
 * `AiSummaryConfigViewModel` 的行为基线（M5-4b 新增；迁移前零测试）。
 *
 * 本片把 VM 里 `appCtx.getString(R.string.x)` 换成了「发枚举 + UI 侧查表」，因此**最需要钉的
 * 是「什么时候用资源文案、什么时候用运行期文本」这条 fallback 语义**：
 *
 * `save()` 失败时迁移前的原文是 `error.message ?: getString(ai_config_save_failed)`
 * —— 有异常文案就用异常文案，没有才回落到资源里的「保存失败」。合成枚举的一个参数会丢掉
 * 这个语义，所以契约分成 `ShowMessage`（资源）与 `ShowRawMessage`（运行期）两个。
 *
 * 另外三条覆盖：重置提示走**资源**枚举、加载失败走**运行期**文本（迁移前就是硬编码，
 * 不在资源表里）、保存成功要「提示 + 返回」两个 Effect 都发。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class AiSummaryConfigViewModelTest {

    @Test
    fun `重置提示词发的是资源枚举而不是运行期文本`() {
        val gateway = FakeAiProfileGateway()
        val viewModel = AiSummaryConfigViewModel(gateway)
        idle()
        val effects = collect(viewModel)

        viewModel.onIntent(AiSummaryConfigIntent.ResetPrompt)
        idle()

        val shown = effects.filterIsInstance<AiSummaryConfigEffect.ShowMessage>()
        assertEquals(1, shown.size)
        assertEquals(AiSummaryMessage.ResetPromptSuccess, shown.single().message)
        assertTrue(
            "不该走 RawMessage",
            effects.none { it is AiSummaryConfigEffect.ShowRawMessage }
        )
    }

    @Test
    fun `保存失败且异常没有文案时回落到资源里的保存失败`() {
        val gateway = FakeAiProfileGateway().apply {
            saveError = RuntimeException() // message == null
        }
        val viewModel = AiSummaryConfigViewModel(gateway)
        idle()
        val effects = collect(viewModel)

        viewModel.onIntent(AiSummaryConfigIntent.Save)
        idle()

        val shown = effects.filterIsInstance<AiSummaryConfigEffect.ShowMessage>()
        assertEquals(1, shown.size)
        assertEquals(AiSummaryMessage.SaveFailed, shown.single().message)
    }

    @Test
    fun `保存失败且异常有文案时用异常文案`() {
        val gateway = FakeAiProfileGateway().apply {
            saveError = RuntimeException("网络不可用")
        }
        val viewModel = AiSummaryConfigViewModel(gateway)
        idle()
        val effects = collect(viewModel)

        viewModel.onIntent(AiSummaryConfigIntent.Save)
        idle()

        val raw = effects.filterIsInstance<AiSummaryConfigEffect.ShowRawMessage>()
        assertEquals(1, raw.size)
        assertEquals("网络不可用", raw.single().text)
        assertTrue(
            "不该再发资源枚举",
            effects.none { it is AiSummaryConfigEffect.ShowMessage }
        )
    }

    @Test
    fun `保存成功要同时发提示和返回`() {
        val gateway = FakeAiProfileGateway()
        val viewModel = AiSummaryConfigViewModel(gateway)
        idle()
        val effects = collect(viewModel)

        viewModel.onIntent(AiSummaryConfigIntent.Save)
        idle()

        assertEquals(AiSummaryMessage.SaveSuccess, effects.filterIsInstance<AiSummaryConfigEffect.ShowMessage>().singleOrNull()?.message)
        assertEquals(1, effects.count { it == AiSummaryConfigEffect.NavigateBack })
    }

    private fun collect(viewModel: AiSummaryConfigViewModel): MutableList<AiSummaryConfigEffect> {
        val out = mutableListOf<AiSummaryConfigEffect>()
        CoroutineScope(Dispatchers.Unconfined).launch { viewModel.effects.collect { out += it } }
        return out
    }

    private fun idle() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }
}

/**
 * 内存实现。`AiProfileGateway` 有 13 个方法，但本 VM 只用 `getTaskPreset` / `saveTaskPreset`
 * ⇒ 其余用 `error()` 兜底（**故意不写空实现**：真被调用时能立刻发现，而不是静默返回假数据）。
 */
private class FakeAiProfileGateway : AiProfileGateway {

    var saveError: Throwable? = null

    var promptTemplate: String = "default"
    var temperature: Float = 0.7f
    var maxOutputTokens: Int = 0

    override fun observeProviders(): Flow<List<AiProviderProfile>> = flowOf(emptyList())

    override fun observeModels(): Flow<List<AiModelProfile>> = flowOf(emptyList())

    override fun observePresets(): Flow<List<AiTaskPreset>> = flowOf(emptyList())

    override suspend fun getProvider(id: String): AiProviderProfile? = null

    override suspend fun getModel(id: String): AiModelProfile? = null

    override suspend fun getTaskPreset(taskType: String) = preset(taskType)

    override suspend fun saveProvider(draft: AiProviderDraft): AiProviderProfile = error("未使用")

    override suspend fun saveModel(draft: AiModelDraft): AiModelProfile = error("未使用")

    override suspend fun importProviderModels(
        providerId: String,
        models: List<AiAvailableModel>
    ): List<AiModelProfile> = error("未使用")

    override suspend fun setDefaultModel(modelProfileId: String): AiTaskPresetConfig = error("未使用")

    override suspend fun saveTaskPreset(
        taskType: String,
        promptTemplate: String,
        temperature: Float,
        maxOutputTokens: Int
    ): AiTaskPresetConfig {
        saveError?.let { throw it }
        this.promptTemplate = promptTemplate
        this.temperature = temperature
        this.maxOutputTokens = maxOutputTokens
        return preset(taskType).copy(promptTemplate = promptTemplate)
    }

    override suspend fun deleteProvider(providerId: String) = error("未使用")

    override suspend fun deleteModel(modelId: String) = error("未使用")

    private fun preset(taskType: String) = AiTaskPresetConfig(
        id = taskType,
        taskType = taskType,
        name = "",
        // 只为让 `saveTaskPreset` 返回一个合法配置（本 VM 只读 `promptTemplate` / `params`）。
        model = AiModelConfig(
            id = "",
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
        promptTemplate = promptTemplate,
        params = AiGenerationParams(temperature = temperature, maxOutputTokens = maxOutputTokens),
    )
}
