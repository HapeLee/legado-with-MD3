package io.legado.app.domain.gateway

import io.legado.app.domain.model.settings.ImportBookSettings
import kotlinx.coroutines.flow.Flow

interface ImportBookSettingsGateway {
    val currentSettings: ImportBookSettings
    val settings: Flow<ImportBookSettings>
    suspend fun update(transform: (ImportBookSettings) -> ImportBookSettings)
}
