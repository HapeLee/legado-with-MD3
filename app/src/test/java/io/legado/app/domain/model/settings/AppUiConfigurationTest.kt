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

    @Test
    fun resolvedTheme_followsSystemOnlyInAutoMode() {
        val systemDark = AppUiConfiguration(isSystemDarkTheme = true)

        assertTrue(systemDark.isDarkTheme)
        assertFalse(
            systemDark.copy(appShell = systemDark.appShell.copy(themeMode = "1")).isDarkTheme
        )
        assertTrue(
            systemDark.copy(
                appShell = systemDark.appShell.copy(themeMode = "2"),
                isSystemDarkTheme = false,
            ).isDarkTheme
        )
    }

    @Test
    fun diff_reportsSystemThemeChangeOnlyInAutoMode() {
        val autoLight = AppUiConfiguration(isSystemDarkTheme = false)
        val autoDark = autoLight.copy(isSystemDarkTheme = true)
        val fixedLight = autoLight.copy(appShell = autoLight.appShell.copy(themeMode = "1"))

        assertTrue(autoDark.diffFrom(autoLight).themeChanged)
        assertFalse(
            fixedLight.copy(isSystemDarkTheme = true).diffFrom(fixedLight).themeChanged
        )
    }
}
