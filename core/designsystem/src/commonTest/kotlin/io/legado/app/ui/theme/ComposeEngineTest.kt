package io.legado.app.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `:core:ui` 的 `ThemeResolver.isMiuixEngine(String)` 在 M1-2 委托到了 [parseComposeEngine]，
 * 原实现是 `composeEngine.equals("miuix", ignoreCase = true)`——大小写不敏感、其余一律假。
 * 这里把那条语义锁住，避免将来"顺手规范化"成区分大小写或改成白名单时静默改变引擎判定
 * （改了会让整站间距在 Miuix 主题下退回 Material3 刻度）。
 */
class ComposeEngineTest {

    @Test
    fun miuixEngineIsRecognisedCaseInsensitively() {
        assertEquals(ComposeEngine.Miuix, parseComposeEngine("miuix"))
        assertEquals(ComposeEngine.Miuix, parseComposeEngine("Miuix"))
        assertEquals(ComposeEngine.Miuix, parseComposeEngine("MIUIX"))
    }

    @Test
    fun everythingElseFallsBackToMaterial3() {
        assertEquals(ComposeEngine.Material3, parseComposeEngine("material"))
        assertEquals(ComposeEngine.Material3, parseComposeEngine("material3Expressive"))
        assertEquals(ComposeEngine.Material3, parseComposeEngine(""))
        assertEquals(ComposeEngine.Material3, parseComposeEngine(" miuix ")) // 不 trim，与旧实现一致
        assertEquals(ComposeEngine.Material3, parseComposeEngine(null))
    }

    // 注：`LocalComposeEngine` 的默认值（Material3）与 `LegadoThemeMode` 默认的
    // `composeEngine = "material"` 必须一致；这条约束在测试里读不到（`current` 是
    // `@Composable`），由 `:core:ui` 在提供 `LocalLegadoThemeColors` 处显式
    // `LocalComposeEngine provides parseComposeEngine(...)` 保证。
}
