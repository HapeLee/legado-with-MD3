package io.legado.app.ui.widget.components.settingItem

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 锁住 [MiuixPreferenceRendererProvider] 的两条语义（M1-3t）。
 *
 * 为什么只测这些：`switchPreference` 是 `@Composable`，真的渲染要进 Composition，而
 * `miuix-preference` 只有 `-android` 变体 ⇒ 成功路径在 `commonTest` 里既跑不到也没有意义。
 * 真正值得锁的是**失败侧的语义**——它决定了跨平台上会画成什么样：
 *
 * 1. **未注入时 [MiuixPreferenceRendererProvider.current] 必须是 `null`**，不是空实现对象、
 *    也不是抛异常。这是刻意选的形态：`SwitchSettingItem` 的分支条件是
 *    `renderer != null && 引擎 == Miuix`，未注入 ⇒ 落 Material3；若这里改成非空空实现，
 *    desktop 上就会「假装渲染了其实什么都没画」——正是伪造跨平台支持。
 * 2. **install / uninstall 是幂等可往返的**：注入后 `current` 拿回同一个实例（不是每次新建），
 *    卸载后回到「从未注入」的状态。
 *
 * 「未注入 ⇒ 走 Material3」这条组合语义在 Compose 里由 `SwitchSettingItem` 承载，
 * 靠的是上面第 1 条，所以锁住第 1 条就等于锁住了降级行为。
 */
class MiuixPreferenceRendererContractTest {

    @AfterTest
    fun tearDown() {
        MiuixPreferenceRendererProvider.uninstall()
    }

    @Test
    fun absentRendererExposesNullSoCallerFallsBackToMaterial3() {
        assertFalse(MiuixPreferenceRendererProvider.isInstalled)
        assertNull(
            MiuixPreferenceRendererProvider.current,
            "未注入必须给出 null 让调用方显式回落；给空实现会让 desktop 上静默什么都不画"
        )
    }

    @Test
    fun installAndUninstallRoundTripKeepsIdentity() {
        val installed = recordingRenderer()
        MiuixPreferenceRendererProvider.install(installed)

        assertTrue(MiuixPreferenceRendererProvider.isInstalled)
        assertSame(installed, MiuixPreferenceRendererProvider.current)
        assertSame(
            installed,
            MiuixPreferenceRendererProvider.current,
            "同一实例：每次访问都新建会让调用方的身份语义漂移"
        )

        MiuixPreferenceRendererProvider.uninstall()
        assertFalse(MiuixPreferenceRendererProvider.isInstalled)
        assertNull(MiuixPreferenceRendererProvider.current)
    }

    /** 什么都不画的探针：本源集验的是宿主语义，不是渲染结果。 */
    private fun recordingRenderer(): MiuixPreferenceRenderer = object : MiuixPreferenceRenderer {
        @Composable
        override fun switchPreference(
            title: String,
            summary: String?,
            checked: Boolean,
            enabled: Boolean,
            onCheckedChange: (Boolean) -> Unit,
        ) = Unit

        // M5-2a-pre：契约加了 `arrowPreference`（`ClickableSettingItem` 上提到 designsystem 时
        // 撞上同一个「`miuix-preference` 没有 desktop 变体」约束）。这个探针只需要**实现完整**
        // ——本源集验的是宿主语义（未注入 ⇒ null、install/uninstall 往返），不是渲染结果，
        // 所以不为新方法加用例。这个编译错误是**契约测试该有的反应**：扩展契约时，
        // 所有实现方（含探针）都必须显式跟上。
        @Composable
        override fun arrowPreference(
            title: String,
            summary: String?,
            onClick: () -> Unit,
        ) = Unit

        // M5-2b：契约再扩一个方法，探针同样只需实现完整（本源集验的是宿主语义）。
        @Composable
        override fun overlaySpinnerPreference(
            title: String,
            summary: String?,
            items: List<String>,
            selectedIndex: Int,
            imageVector: ImageVector?,
            onSelectedIndexChange: (Int) -> Unit,
        ) = Unit

        // M5-15c：契约扩到第三个方法（`CompactSettingItems` 上提）。同上，探针只需实现完整 ——
        // 这三处编译错误都是**契约测试该有的反应**：扩展契约时所有实现方必须显式跟上。
        @Composable
        override fun windowDropdownPreference(
            title: String,
            summary: String?,
            items: List<String>,
            selectedIndex: Int,
            imageVector: ImageVector?,
            onSelectedIndexChange: (Int) -> Unit,
        ) = Unit
    }
}
