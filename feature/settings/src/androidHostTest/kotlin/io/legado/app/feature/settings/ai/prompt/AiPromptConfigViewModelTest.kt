package io.legado.app.feature.settings.ai.prompt

import android.app.Application
import android.os.Looper
import io.legado.app.core.platform.Toaster
import io.legado.app.domain.ai.AiModelProfile
import io.legado.app.domain.ai.AiProfileGateway
import io.legado.app.domain.ai.AiProviderProfile
import io.legado.app.domain.ai.AiTaskPreset
import io.legado.app.domain.model.AiAvailableModel
import io.legado.app.domain.model.AiGenerationParams
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
 * `AiPromptConfigViewModel` 的行为基线（M5-4e 补回；M5-4c 迁移时因 VM 不可构造而欠下）。
 *
 * ## 这片测试为什么以前写不了
 *
 * M5-4c 把 VM 迁进共享层时，它在内部直接调 `getString(Res.string.*)`（默认提示词要写回
 * gateway）。M5-4d 的探针量出：**VM 里调 `getString` 会让 VM 在 `androidHostTest` 下无法构造**
 * （`MissingResourceException: Android context is not initialized`）。⇒ 本片把「VM 需要的
 * 资源字符串」抽成可注入的 [AiPromptStringSource]，VM 才重新可测。
 *
 * ## 钉住的三条
 *
 * 1. `init` 的**取值优先级**：gateway 里有已存提示词就用它，没有才用默认提示词
 *    （写反了会让用户的自定义提示词在每次进页面时被重置）。
 * 2. 「保存成功」走 **Toast**（`Toaster`）而不是 Effect/Snackbar —— 这是本页与
 *    ai/summary 的差异，行为上必须保住。
 * 3. 失败路径的 fallback：**有**异常文案用异常文案（`ShowRawMessage`），
 *    **没有**才回落到资源里的「保存失败」（`ShowMessage`）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class AiPromptConfigViewModelTest {

    @Test
    fun `已存提示词优先于默认提示词`() {
        val gateway = FakeAiProfileGateway().apply {
            stored[AiPromptTask.Chat.taskType] = "用户自定义"
        }
        val viewModel = createViewModel(gateway)
        idle()

        val chat = viewModel.uiState.value.items.single { it.task == AiPromptTask.Chat }
        assertEquals("用户自定义", chat.currentPrompt)
        assertEquals("默认:Chat", chat.defaultPrompt)

        val other = viewModel.uiState.value.items.single { it.task == AiPromptTask.TextFactory }
        assertEquals("默认:TextFactory", other.currentPrompt)
    }

    @Test
    fun `保存成功发Toast而不是Effect`() {
        val gateway = FakeAiProfileGateway()
        val toaster = RecordingToaster()
        val viewModel = createViewModel(gateway, toaster)
        idle()
        val effects = collect(viewModel)

        viewModel.onIntent(
            AiPromptConfigIntent.SavePrompt(AiPromptTask.Chat.taskType, "新的提示词")
        )
        idle()

        assertEquals(listOf("saved"), toaster.messages)
        assertTrue("不该走 Effect/Snackbar", effects.isEmpty())
        assertEquals(
            "新的提示词",
            viewModel.uiState.value.items.single { it.task == AiPromptTask.Chat }.currentPrompt
        )
    }

    @Test
    fun `保存失败时有异常文案就用它没有才回落资源文案`() {
        val gateway = FakeAiProfileGateway().apply { saveError = RuntimeException() }
        val toaster = RecordingToaster()
        val viewModel = createViewModel(gateway, toaster)
        idle()
        val effects = collect(viewModel)

        viewModel.onIntent(
            AiPromptConfigIntent.SavePrompt(AiPromptTask.Chat.taskType, "x")
        )
        idle()

        val shown = effects.filterIsInstance<AiPromptConfigEffect.ShowMessage>()
        assertEquals(1, shown.size)
        assertEquals(AiPromptMessage.SaveFailed, shown.single().message)
        assertTrue("失败时不该 Toast", toaster.messages.isEmpty())
    }

    @Test
    fun `重置单个用默认提示词并提示成功`() {
        val gateway = FakeAiProfileGateway().apply {
            stored[AiPromptTask.Chat.taskType] = "用户自定义"
        }
        val viewModel = createViewModel(gateway)
        idle()
        val effects = collect(viewModel)

        viewModel.onIntent(AiPromptConfigIntent.ResetPrompt(AiPromptTask.Chat.taskType))
        idle()

        assertEquals(
            AiPromptMessage.ResetSuccess,
            effects.filterIsInstance<AiPromptConfigEffect.ShowMessage>().singleOrNull()?.message
        )
        assertEquals(
            "默认:Chat",
            viewModel.uiState.value.items.single { it.task == AiPromptTask.Chat }.currentPrompt
        )
    }

    private fun createViewModel(
        gateway: AiProfileGateway,
        toaster: Toaster = RecordingToaster(),
    ) = AiPromptConfigViewModel(
        aiProfileGateway = gateway,
        toaster = toaster,
        strings = object : AiPromptStringSource {
            override suspend fun defaultFor(task: AiPromptTask): String = "默认:${task.name}"
            override suspend fun savedMessage(): String = "saved"
        },
    )

    private fun collect(viewModel: AiPromptConfigViewModel): MutableList<AiPromptConfigEffect> {
        val out = mutableListOf<AiPromptConfigEffect>()
        CoroutineScope(Dispatchers.Unconfined).launch { viewModel.effects.collect { out += it } }
        return out
    }

    private fun idle() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }
}

private class RecordingToaster : Toaster {
    val messages = mutableListOf<String>()
    override fun toast(message: String) {
        messages += message
    }

    override fun longToast(message: String) {
        messages += message
    }
}

/** 只需要 `getTaskPreset` / `saveTaskPreset`，其余用 `error()` 兜底（真被调用时会立刻暴露）。 */
private class FakeAiProfileGateway : AiProfileGateway {

    val stored = mutableMapOf<String, String>()
    var saveError: Throwable? = null

    override fun observeProviders(): Flow<List<AiProviderProfile>> = flowOf(emptyList())
    override fun observeModels(): Flow<List<AiModelProfile>> = flowOf(emptyList())
    override fun observePresets(): Flow<List<AiTaskPreset>> = flowOf(emptyList())
    override suspend fun getProvider(id: String): AiProviderProfile? = null
    override suspend fun getModel(id: String): AiModelProfile? = null

    override suspend fun getTaskPreset(taskType: String): AiTaskPresetConfig? =
        stored[taskType]?.let { preset(taskType, it) }

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
        stored[taskType] = promptTemplate
        return preset(taskType, promptTemplate)
    }

    override suspend fun deleteProvider(providerId: String) = error("未使用")
    override suspend fun deleteModel(modelId: String) = error("未使用")

    private fun preset(taskType: String, prompt: String) = AiTaskPresetConfig(
        id = taskType,
        taskType = taskType,
        name = "",
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
        promptTemplate = prompt,
        params = AiGenerationParams(),
    )
}
