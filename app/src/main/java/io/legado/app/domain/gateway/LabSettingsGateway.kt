package io.legado.app.domain.gateway

import io.legado.app.domain.model.settings.LabSettings
import kotlinx.coroutines.flow.Flow

interface LabSettingsGateway {
    val currentSettings: LabSettings
    val settings: Flow<LabSettings>
    suspend fun update(transform: (LabSettings) -> LabSettings)
}
