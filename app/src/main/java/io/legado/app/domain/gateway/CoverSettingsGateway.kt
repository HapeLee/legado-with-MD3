package io.legado.app.domain.gateway

import io.legado.app.domain.model.settings.CoverSettings
import kotlinx.coroutines.flow.Flow

interface CoverSettingsGateway {
    val currentSettings: CoverSettings
    val settings: Flow<CoverSettings>
    suspend fun update(transform: (CoverSettings) -> CoverSettings)
}
