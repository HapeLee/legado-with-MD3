package io.legado.app.domain.gateway

import io.legado.app.domain.model.settings.AppShellSettings
import kotlinx.coroutines.flow.Flow

interface AppShellSettingsGateway {
    val currentSettings: AppShellSettings
    val settings: Flow<AppShellSettings>
    suspend fun update(transform: (AppShellSettings) -> AppShellSettings)
}
