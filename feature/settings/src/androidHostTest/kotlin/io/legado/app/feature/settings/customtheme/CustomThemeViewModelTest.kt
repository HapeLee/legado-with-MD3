package io.legado.app.feature.settings.customtheme

import android.app.Application
import android.os.Looper
import io.legado.app.domain.gateway.ThemeSettingsGateway
import io.legado.app.domain.model.settings.ThemeSettings
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
 * `CustomThemeViewModel` 的行为基线（M5-3b 新增；迁移前零测试）。
 *
 * 本片要钉的三条，都是**容易在重构中悄悄改掉**的：
 *  1. **DaySeed 选色要做两件事**：写 `customPrimary` **并且**发 `ApplyLegacyPrimarySeed`
 *     ——后者是给旧主题引擎（`ThemeStore`）的信号，宿主靠它让老路径也跟上种子色。
 *     只写设置而漏了 Effect，表现是「改了种子色但旧主题没变」，而且**不会报错**。
 *  2. **DeepColor 选色映射到对应 slot 且不该发 DaySeed 的那个 Effect**——防 `when`
 *     分支复制粘贴时把两条路径混起来。
 *  3. **更新失败发 `SettingsUpdateFailed`（带 message），而不是静默吞掉**：VM 里是
 *     `runCatching { … }.onFailure { … }`，吞掉的话用户改颜色失败却看不到任何提示。
 *
 * 另外三条用例共同覆盖一条：选色后 `activePicker` 必须清空（否则弹层关不掉）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class CustomThemeViewModelTest {

    @Test
    fun `选白天种子色要同时写设置并通知旧主题引擎`() {
        val gateway = FakeThemeSettingsGateway()
        val viewModel = CustomThemeViewModel(gateway)
        idle()
        val effects = collect(viewModel)

        viewModel.onIntent(CustomThemeIntent.OpenPicker(CustomThemePicker.DaySeed))
        idle()
        viewModel.onIntent(CustomThemeIntent.ColorSelected(0xFF5722))
        idle()

        assertEquals(0xFF5722, gateway.current.customPrimary)
        val apply = effects.filterIsInstance<CustomThemeEffect.ApplyLegacyPrimarySeed>()
        assertEquals(1, apply.size)
        assertEquals(0xFF5722, apply.single().color)
        assertEquals("选完要清空 picker", null, viewModel.uiState.value.activePicker)
    }

    @Test
    fun `深度自定义选色映射到对应slot且不发旧引擎通知`() {
        val gateway = FakeThemeSettingsGateway()
        val viewModel = CustomThemeViewModel(gateway)
        idle()
        val effects = collect(viewModel)

        viewModel.onIntent(
            CustomThemeIntent.OpenPicker(CustomThemePicker.DeepColor(CustomThemeColorSlot.Background))
        )
        idle()
        viewModel.onIntent(CustomThemeIntent.ColorSelected(0x112233))
        idle()

        assertEquals(0x112233, gateway.current.themeBackgroundColor)
        assertEquals("不该写种子色", 0, gateway.current.customPrimary)
        assertTrue(
            "不该发 ApplyLegacyPrimarySeed",
            effects.none { it is CustomThemeEffect.ApplyLegacyPrimarySeed }
        )
    }

    @Test
    fun `写设置失败时把错误提示发出去`() {
        val gateway = FakeThemeSettingsGateway().apply { failNextUpdate = true }
        val viewModel = CustomThemeViewModel(gateway)
        idle()
        val effects = collect(viewModel)

        viewModel.onIntent(CustomThemeIntent.MaterialVersionChanged("material3Expressive"))
        idle()

        val failed = effects.filterIsInstance<CustomThemeEffect.SettingsUpdateFailed>()
        assertEquals(1, failed.size)
        assertEquals("boom", failed.single().message)
    }

    private fun collect(viewModel: CustomThemeViewModel): MutableList<CustomThemeEffect> {
        val out = mutableListOf<CustomThemeEffect>()
        CoroutineScope(Dispatchers.Unconfined).launch { viewModel.effects.collect { out += it } }
        return out
    }

    private fun idle() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }
}

/** 内存实现。`failNextUpdate` 用于触发 `SettingsUpdateFailed` 那条分支。 */
private class FakeThemeSettingsGateway : ThemeSettingsGateway {

    private val state = MutableStateFlow(ThemeSettings())

    var failNextUpdate = false

    val current: ThemeSettings
        get() = state.value

    override val currentSettings: ThemeSettings
        get() = state.value

    override val settings: Flow<ThemeSettings>
        get() = state

    override suspend fun update(transform: (ThemeSettings) -> ThemeSettings) {
        if (failNextUpdate) {
            failNextUpdate = false
            throw RuntimeException("boom")
        }
        state.value = transform(state.value)
    }
}
