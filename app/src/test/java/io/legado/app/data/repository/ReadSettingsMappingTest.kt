package io.legado.app.data.repository

import androidx.datastore.preferences.core.mutablePreferencesOf
import io.legado.app.constant.ReadMenuBlurStyle
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadSettingsMappingTest {

    @Test
    fun `空快照使用精简阅读菜单默认值`() {
        val preferences = mutablePreferencesOf()
        val repository = ReadSettingsRepository(
            settingsRepository = SettingsRepository(),
            preferencesFlow = MutableStateFlow(preferences),
        )

        val settings = with(repository) {
            preferences.toReadSettings()
        }

        assertEquals("0", settings.showBrightnessView)
        assertEquals(1, settings.readBarStyle)
        assertFalse(settings.readMenuIconShowText)
        assertTrue(settings.readMenuFloatingBottomBar)
        assertEquals(ReadMenuBlurStyle.Solid, settings.readMenuTopBarBlurStyle)
        assertEquals(100, settings.readMenuBlurAlpha)
        assertEquals(1, settings.readMenuBorderWidth)
        assertEquals(3, settings.titleBarIconPosition)
        assertFalse(settings.showTitleBarIcons)
        assertFalse(settings.showMenuIcon)
        assertFalse(settings.titleBarCompact)
    }
}
