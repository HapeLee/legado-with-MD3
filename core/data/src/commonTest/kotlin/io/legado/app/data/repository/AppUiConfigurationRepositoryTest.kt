package io.legado.app.data.repository

import io.legado.app.constant.PreferKey
import io.legado.app.domain.gateway.AppLocaleGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AppUiConfigurationRepositoryTest {

    @Test
    fun `一次 Preferences 更新只产生一份完整根配置`() = runTest {
        val locale = FakeAppLocaleGateway()
        val preferences = FakePreferenceStore()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = AppUiConfigurationRepository(locale, preferences, scope)
        val observed = mutableListOf(repository.currentConfiguration)
        val collection = scope.launch {
            repository.configuration.collect { configuration ->
                if (observed.lastOrNull() != configuration) observed += configuration
            }
        }

        try {
            preferences.setAllAndAwait(
                mapOf(
                    PreferKey.themeMode to PreferenceValue.StringValue("2"),
                    PreferKey.cPrimary to PreferenceValue.IntValue(0x123456),
                    PreferKey.coverShowShadow to PreferenceValue.BooleanValue(true),
                ),
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
    fun `系统主题变化进入同一份根配置`() = runTest {
        val locale = FakeAppLocaleGateway()
        val preferences = FakePreferenceStore()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = AppUiConfigurationRepository(
            appLocaleGateway = locale,
            preferences = preferences,
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
