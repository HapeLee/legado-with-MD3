package io.legado.app.data.repository

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.Preferences
import io.legado.app.constant.PreferKey
import io.legado.app.domain.gateway.AppLocaleGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Test

class AppUiConfigurationRepositoryTest {

    @Test
    fun `一次 Preferences 更新只产生一份完整根配置`() {
        val locale = FakeAppLocaleGateway()
        val preferences = MutableStateFlow<Preferences>(mutablePreferencesOf())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = AppUiConfigurationRepository(locale, preferences, scope)
        val observed = mutableListOf(repository.currentConfiguration)
        val collection = scope.launch {
            repository.configuration.collect { configuration ->
                if (observed.lastOrNull() != configuration) observed += configuration
            }
        }

        try {
            preferences.value = mutablePreferencesOf(
                stringPreferencesKey(PreferKey.themeMode) to "2",
                intPreferencesKey(PreferKey.cPrimary) to 0x123456,
                booleanPreferencesKey(PreferKey.coverShowShadow) to true,
            )

            assertEquals(2, observed.size)
            with(observed.last()) {
                assertEquals("2", appShell.themeMode)
                assertEquals(0x123456, theme.customPrimary)
                assertEquals(true, cover.showShadow)
            }
        } finally {
            collection.cancel()
            scope.cancel()
        }
    }

    @Test
    fun `系统主题变化进入同一份根配置`() {
        val locale = FakeAppLocaleGateway()
        val preferences = MutableStateFlow<Preferences>(mutablePreferencesOf())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = AppUiConfigurationRepository(
            appLocaleGateway = locale,
            preferencesFlow = preferences,
            processScope = scope,
            initialSystemDarkTheme = false,
        )

        try {
            repository.synchronizeSystemDarkTheme(true)

            assertEquals(true, repository.currentConfiguration.isSystemDarkTheme)
            assertEquals(true, repository.currentConfiguration.isDarkTheme)
        } finally {
            scope.cancel()
        }
    }

    private class FakeAppLocaleGateway : AppLocaleGateway {
        private val state = MutableStateFlow("auto")
        override val language = state.asStateFlow()
        override val currentLanguage: String get() = state.value

        override fun setLanguage(language: String) {
            state.value = language
        }

        override fun synchronizeFromPlatform() = Unit
        override fun migrateLegacyLanguage(language: String) = Unit
    }
}
