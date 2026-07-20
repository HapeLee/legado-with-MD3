package io.legado.app.domain.gateway

import io.legado.app.domain.model.settings.ReadSettings
import kotlinx.coroutines.flow.Flow

interface ReadSettingsGateway {
    val currentSettings: ReadSettings
    val settings: Flow<ReadSettings>

    suspend fun update(transform: (ReadSettings) -> ReadSettings)
}
