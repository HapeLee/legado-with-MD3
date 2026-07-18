package io.legado.app.domain.model.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUiConfigurationTest {

    @Test
    fun diff_reportsOnlyChangedConfigurationSlices() {
        val previous = AppUiConfiguration()
        val current = previous.copy(
            language = "en",
            appShell = previous.appShell.copy(fontScale = 12),
        )

        val diff = current.diffFrom(previous)

        assertTrue(diff.localeChanged)
        assertTrue(diff.fontScaleChanged)
        assertFalse(diff.themeChanged)
        assertFalse(diff.windowChanged)
    }

    @Test
    fun diff_reportsThemeBackgroundAsWindowOnlyChange() {
        val previous = AppUiConfiguration()
        val current = previous.copy(
            theme = previous.theme.copy(backgroundImageLight = "/tmp/background.png")
        )

        val diff = current.diffFrom(previous)

        assertFalse(diff.themeChanged)
        assertTrue(diff.windowChanged)
        assertFalse(diff.requiresLegacyContentRefresh)
    }

    @Test
    fun diff_ignoresComposeOnlyThemeChangesForLegacyContent() {
        val previous = AppUiConfiguration()
        val current = previous.copy(
            appShell = previous.appShell.copy(composeEngine = "miuix"),
            theme = previous.theme.copy(enableItemDivider = true),
        )

        val diff = current.diffFrom(previous)

        assertFalse(diff.hasChanges)
    }
}
