package io.legado.app.domain.gateway

import io.legado.app.domain.model.settings.BackupSettings
import kotlinx.coroutines.flow.Flow

interface BackupSettingsGateway {
    val currentSettings: BackupSettings
    val settings: Flow<BackupSettings>
    suspend fun update(transform: (BackupSettings) -> BackupSettings)
}
