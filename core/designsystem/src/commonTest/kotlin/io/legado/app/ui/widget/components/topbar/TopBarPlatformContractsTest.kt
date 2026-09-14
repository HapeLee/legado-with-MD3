package io.legado.app.ui.widget.components.topbar

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 锁住 M1-3r 新增的两条顶栏平台契约的**宿主语义**（[StatusBarInsetsProvider] 与
 * [LiquidGlassEffectsProvider]）。
 *
 * 为什么只测这些：两条契约的方法（`StatusBarInsets.get` / `LiquidGlassEffects.enabled` /
 * `Modifier.liquidGlass`）**全是 `@Composable`**——它们的取值源是 CompositionLocal
 * （`WindowInsetsHolder.current()` / `LocalTopBarBackdrop.current`），离开 Composition 无处取值。
 * 所以 `commonTest` 里能锁、且值得锁的是宿主的两条语义：
 *
 * 1. **未注入不抛异常，且回落实现是稳定的同一个实例**（每次访问都 new 一个会让调用方
 *    的身份比较、`remember` 之类的语义悄悄漂掉）；
 * 2. **install / uninstall 是幂等可往返的**，卸载后回到与「从未注入」完全相同的实例。
 *
 * 真正的取值行为（Android 取 `statusBarsIgnoringVisibility`、desktop 取 `statusBars`；
 * Android 走 AGSL 着色器、其它平台关闭液态玻璃）由各平台的实现自己保证，不在这层断言。
 */
class TopBarPlatformContractsTest {

    @AfterTest
    fun tearDown() {
        StatusBarInsetsProvider.uninstall()
        LiquidGlassEffectsProvider.uninstall()
    }

    @Test
    fun statusBarInsetsFallsBackToTheSameInstanceWhenNotInstalled() {
        assertFalse(StatusBarInsetsProvider.isInstalled)

        val first = StatusBarInsetsProvider.current
        val second = StatusBarInsetsProvider.current
        assertSame(first, second, "回落实现必须是稳定实例：每次访问都新建一个会让身份语义漂移")
    }

    @Test
    fun statusBarInsetsInstallUninstallRoundTrip() {
        val fallback = StatusBarInsetsProvider.current
        val installed = fixedInsets()

        StatusBarInsetsProvider.install(installed)
        assertTrue(StatusBarInsetsProvider.isInstalled)
        assertSame(installed, StatusBarInsetsProvider.current)

        StatusBarInsetsProvider.uninstall()
        assertFalse(StatusBarInsetsProvider.isInstalled)
        assertSame(fallback, StatusBarInsetsProvider.current, "卸载后必须回到同一个回落实例")
    }

    @Test
    fun liquidGlassFallsBackToTheSameInstanceWhenNotInstalled() {
        assertFalse(LiquidGlassEffectsProvider.isInstalled)

        val first = LiquidGlassEffectsProvider.current
        val second = LiquidGlassEffectsProvider.current
        assertSame(first, second, "回落实现必须是稳定实例（未注入=关闭液态玻璃，不抛异常）")
    }

    @Test
    fun liquidGlassInstallUninstallRoundTrip() {
        val fallback = LiquidGlassEffectsProvider.current
        val installed = noopGlass()

        LiquidGlassEffectsProvider.install(installed)
        assertTrue(LiquidGlassEffectsProvider.isInstalled)
        assertSame(installed, LiquidGlassEffectsProvider.current)

        LiquidGlassEffectsProvider.uninstall()
        assertFalse(LiquidGlassEffectsProvider.isInstalled)
        assertSame(fallback, LiquidGlassEffectsProvider.current, "卸载后必须回到同一个回落实例")
    }

    private fun fixedInsets(): StatusBarInsets = object : StatusBarInsets {
        @Composable
        override fun get(): WindowInsets = WindowInsets(0)
    }

    private fun noopGlass(): LiquidGlassEffects = object : LiquidGlassEffects {
        @Composable
        override fun enabled(): Boolean = false

        @Composable
        override fun Modifier.liquidGlass(shape: Shape): Modifier = this
    }
}
