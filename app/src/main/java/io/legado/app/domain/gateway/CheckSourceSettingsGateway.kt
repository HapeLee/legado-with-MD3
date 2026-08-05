package io.legado.app.domain.gateway

import kotlinx.coroutines.flow.Flow

data class CheckSourceSettings(
    val timeoutMillis: Long = 180_000L,
    val checkSearch: Boolean = true,
    val checkDiscovery: Boolean = true,
    val checkInfo: Boolean = true,
    val checkCategory: Boolean = true,
    val checkContent: Boolean = true,
)

interface CheckSourceSettingsGateway {
    val currentSettings: CheckSourceSettings
    val settings: Flow<CheckSourceSettings>

    suspend fun update(settings: CheckSourceSettings)
}
