package io.legado.app.domain.gateway

import io.legado.app.domain.model.settings.ReadAloudSettings
import kotlinx.coroutines.flow.Flow

interface ReadAloudSettingsGateway {
    val currentSettings: ReadAloudSettings
    val settings: Flow<ReadAloudSettings>
    suspend fun update(transform: (ReadAloudSettings) -> ReadAloudSettings)
}
