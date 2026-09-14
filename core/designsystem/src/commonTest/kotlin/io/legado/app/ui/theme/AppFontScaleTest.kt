package io.legado.app.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `resolveAppFontScale` 的边界语义：设置值在 0.8~1.6 倍区间内生效，越界回落到系统缩放。
 *
 * 该函数随 M1-3e 从 `:core:ui` 搬进 `commonMain` 时**逐字未改**（同批唯一的行为相关改动是
 * `AppDensity` 里「系统缩放来源」由 Android-only 的 `LocalConfiguration.current.fontScale`
 * 换成同源的 `LocalDensity.current.fontScale`）。这里把全部边界钉住，避免后续再动它时
 * 静默改变字号行为。
 */
class AppFontScaleTest {

    @Test
    fun `区间内的设置值生效且忽略系统缩放`() {
        assertEquals(1.0f, resolveAppFontScale(10, systemFontScale = 99f), TOLERANCE)
        assertEquals(1.3f, resolveAppFontScale(13, systemFontScale = 99f), TOLERANCE)
    }

    @Test
    fun `区间两端 0_8 与 1_6 仍在区间内`() {
        assertEquals(0.8f, resolveAppFontScale(8, systemFontScale = 99f), TOLERANCE)
        assertEquals(1.6f, resolveAppFontScale(16, systemFontScale = 99f), TOLERANCE)
    }

    @Test
    fun `越界与非法设置值回落到系统缩放`() {
        assertEquals(1.25f, resolveAppFontScale(7, systemFontScale = 1.25f), TOLERANCE)
        assertEquals(1.25f, resolveAppFontScale(17, systemFontScale = 1.25f), TOLERANCE)
        assertEquals(1.25f, resolveAppFontScale(0, systemFontScale = 1.25f), TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 1e-6f
    }
}
