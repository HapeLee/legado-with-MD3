package io.legado.app.feature.settings.translation

import android.app.Application
import android.os.Looper
import io.legado.app.domain.gateway.TranslationSettingsGateway
import io.legado.app.domain.model.TranslationConstants
import io.legado.app.domain.model.settings.TranslationSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * `TranslationConfigViewModel` 的行为基线（M5-2d 新增；迁移前这个 VM **零测试**）。
 *
 * 与 `LabConfigViewModelTest` 同口径，钉的是同一类语义：
 *  1. `init` 里 `settingsGateway.settings.collect{}` 是**唯一**的状态下发入口——三个 Intent
 *     都必须经由 `update` 回流到 uiState，不能在 VM 里另做本地乐观更新（否则两条真相来源）。
 *     用例断言的是**回流的**值。
 *  2. 初值来自 `currentSettings`（而不是 VM 自己 new 一个 `TranslationSettings()`）——
 *     这条决定了「打开页面时看到的是不是持久化的设置」。
 *  3. `onIntent` 的 `when` 对 sealed interface 是**穷举**的，所以「新增 Intent 忘了处理」
 *     由编译期兜住，不需要用例；用例只覆盖「每个 Intent 映射到它对应的那个字段」，
 *     防止复制粘贴时把 `SetProvider` 写成改 `targetLanguage`。
 *
 * Robolectric 是必需的：`viewModelScope` 需要真实的 `Dispatchers.Main`。
 * **不需要** `appCtx`——本 VM 只依赖一个 gateway。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class TranslationConfigViewModelTest {

    @Test
    fun `初值来自gateway的当前设置`() {
        val gateway = FakeTranslationSettingsGateway(
            TranslationSettings(provider = "openai", targetLanguage = "en", maxCharsPerChunk = 3000)
        )
        val viewModel = TranslationConfigViewModel(gateway)
        idle()

        val settings = viewModel.uiState.value.settings
        assertEquals("openai", settings.provider)
        assertEquals("en", settings.targetLanguage)
        assertEquals(3000, settings.maxCharsPerChunk)
    }

    @Test
    fun `SetProvider经唯一UiState入口下发`() {
        val gateway = FakeTranslationSettingsGateway()
        val viewModel = TranslationConfigViewModel(gateway)
        idle()
        assertEquals(TranslationConstants.PROVIDER_GOOGLE, viewModel.uiState.value.settings.provider)

        viewModel.onIntent(TranslationConfigIntent.SetProvider(TranslationConstants.PROVIDER_APP_AI))
        idle()

        assertEquals(TranslationConstants.PROVIDER_APP_AI, viewModel.uiState.value.settings.provider)
        assertEquals(1, gateway.updateCount)
    }

    @Test
    fun `另两个Intent各自映射到自己的字段`() {
        val gateway = FakeTranslationSettingsGateway()
        val viewModel = TranslationConfigViewModel(gateway)
        idle()

        viewModel.onIntent(TranslationConfigIntent.SetTargetLanguage("ja"))
        idle()
        assertEquals("ja", viewModel.uiState.value.settings.targetLanguage)
        // 改 targetLanguage 不应该动 provider（防 `when` 分支复制粘贴串行）
        assertEquals(TranslationConstants.PROVIDER_GOOGLE, viewModel.uiState.value.settings.provider)

        viewModel.onIntent(TranslationConfigIntent.SetMaxCharsPerChunk(2500))
        idle()
        assertEquals(2500, viewModel.uiState.value.settings.maxCharsPerChunk)
        assertEquals(2, gateway.updateCount)
    }

    private fun idle() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }
}

/** `TranslationSettingsGateway` 的内存实现。`updateCount` 供「是否写了设置」断言用。 */
private class FakeTranslationSettingsGateway(
    initial: TranslationSettings = TranslationSettings(),
) : TranslationSettingsGateway {

    private val state = MutableStateFlow(initial)

    var updateCount = 0
        private set

    override val currentSettings: TranslationSettings
        get() = state.value

    override val settings: Flow<TranslationSettings>
        get() = state

    override suspend fun update(transform: (TranslationSettings) -> TranslationSettings) {
        updateCount++
        state.value = transform(state.value)
    }
}
