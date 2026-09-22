package io.legado.app.feature.settings.lab

import android.app.Application
import android.os.Looper
import io.legado.app.domain.gateway.LabSettingsGateway
import io.legado.app.domain.model.settings.LabSettings
import io.legado.app.feature.reader.core.pageestimate.LocalPageEstimateMetrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * `LabConfigViewModel` 的行为基线（M5-2a 新增）。
 *
 * 迁移前这个 VM **没有任何测试**，而它有两处只能靠测试钉住的语义：
 *  1. `init` 里 `settingsGateway.settings.collect{}` 是**唯一**的状态下发入口——
 *     写设置后不能另行 `_uiState.update`，否则会出现两条真相来源；
 *     用例「设置经唯一 UiState 入口下发」断言的是**回流的**值（gateway 推什么、
 *     uiState 就是什么），不是本地乐观更新。
 *  2. `ExportPageEstimateDiagnostics` **先于** `settingsGateway.update` 返回：它不改设置，
 *     且要在同一帧内产出 Effect。这条分支写错（比如落进 `when` 里）会退化成
 *     「点了导出，但设置也被写一次」。用例断言 gateway 的 `update` **没有**被调用。
 *
 * Robolectric 在这里是必需的：`viewModelScope` 需要真实的 `Dispatchers.Main`
 * （与 `AboutViewModelTest` 同一个理由）。**不需要** `appCtx`——本 VM 的两个依赖
 * （`LabSettingsGateway` / `LocalPageEstimateMetrics`）都不碰它。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class LabConfigViewModelTest {

    @Test
    fun `设置经唯一UiState入口下发`() {
        val gateway = FakeLabSettingsGateway()
        val viewModel = LabConfigViewModel(gateway)
        idle()

        assertFalse("LabSettings() 的 enabled 默认 false", viewModel.uiState.value.settings.enabled)

        viewModel.onIntent(LabConfigIntent.SetEnabled(true))
        idle()

        assertTrue(viewModel.uiState.value.settings.enabled)
        assertEquals(1, gateway.updateCount)
    }

    @Test
    fun `导出诊断发Share效果且不写设置`() {
        val gateway = FakeLabSettingsGateway()
        val viewModel = LabConfigViewModel(gateway)
        idle()

        val effects = mutableListOf<LabConfigEffect>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        scope.launch { viewModel.effects.collect { effects += it } }

        viewModel.onIntent(LabConfigIntent.ExportPageEstimateDiagnostics)
        idle()

        assertEquals(1, effects.size)
        val text = (effects.single() as LabConfigEffect.SharePageEstimateDiagnostics).text
        assertFalse("导出文本不能为空", text.isEmpty())
        assertEquals("导出不该写设置", 0, gateway.updateCount)
    }

    @Test
    fun `诊断计数初值来自共享计数器`() {
        val viewModel = LabConfigViewModel(FakeLabSettingsGateway())
        idle()

        assertEquals(
            LocalPageEstimateMetrics.size(),
            viewModel.uiState.value.pageEstimateDiagnosticCount,
        )
    }

    private fun idle() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }
}

/**
 * `LabSettingsGateway` 的内存实现。
 *
 * `updateCount` 是**给断言用的**（用例 2 要证明导出路径不写设置）；
 * `state` 既当 `currentSettings` 又当 `settings` 流，与真实实现同形。
 */
private class FakeLabSettingsGateway(
    initial: LabSettings = LabSettings(),
) : LabSettingsGateway {

    private val state = MutableStateFlow(initial)

    var updateCount = 0
        private set

    override val currentSettings: LabSettings
        get() = state.value

    override val settings: Flow<LabSettings>
        get() = state

    override suspend fun update(transform: (LabSettings) -> LabSettings) {
        updateCount++
        state.value = transform(state.value)
    }
}
