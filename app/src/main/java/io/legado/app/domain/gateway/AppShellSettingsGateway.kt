package io.legado.app.domain.gateway

import io.legado.app.domain.model.settings.AppShellSettings
import kotlinx.coroutines.flow.Flow

interface AppShellSettingsGateway {
    val currentSettings: AppShellSettings
    val settings: Flow<AppShellSettings>
    suspend fun update(update: AppShellSettingsUpdate)
}

sealed interface AppShellSettingsUpdate {
    data class ThemeMode(val value: String) : AppShellSettingsUpdate
    data class Language(val value: String) : AppShellSettingsUpdate
    data class FontScale(val value: Int) : AppShellSettingsUpdate
    data class ComposeEngine(val value: String) : AppShellSettingsUpdate
    data class MainNavigationOrder(val value: String) : AppShellSettingsUpdate
}
