package io.legado.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class AppDensityTest {

    @Test
    fun resolve_usesSettingWithinSupportedRange() {
        assertEquals(0.8f, resolveAppFontScale(8, systemFontScale = 1f), 0f)
        assertEquals(1f, resolveAppFontScale(10, systemFontScale = 1.3f), 0f)
        assertEquals(1.6f, resolveAppFontScale(16, systemFontScale = 1f), 0f)
    }

    @Test
    fun resolve_fallsBackToSystemScaleOutsideSupportedRange() {
        assertEquals(1.3f, resolveAppFontScale(7, systemFontScale = 1.3f), 0f)
        assertEquals(1.3f, resolveAppFontScale(17, systemFontScale = 1.3f), 0f)
        assertEquals(1.3f, resolveAppFontScale(0, systemFontScale = 1.3f), 0f)
    }
}
