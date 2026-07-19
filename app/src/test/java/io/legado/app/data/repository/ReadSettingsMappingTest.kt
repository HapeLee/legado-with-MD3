package io.legado.app.data.repository

import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadSettingsMappingTest {

    @Test
    fun `空快照默认显示阅读标题栏图标`() {
        val preferences = mutablePreferencesOf()
        val repository = ReadSettingsRepository(
            settingsRepository = SettingsRepository(),
            preferencesFlow = MutableStateFlow(preferences),
        )

        val settings = with(repository) {
            preferences.toReadSettings()
        }

        assertTrue(settings.showTitleBarIcons)
    }
}
