package io.legado.app.domain.gateway

import io.legado.app.domain.model.settings.OtherSettings
import kotlinx.coroutines.flow.Flow

interface OtherSettingsGateway {
    val currentSettings: OtherSettings
    val settings: Flow<OtherSettings>
    suspend fun update(transform: (OtherSettings) -> OtherSettings)
}
