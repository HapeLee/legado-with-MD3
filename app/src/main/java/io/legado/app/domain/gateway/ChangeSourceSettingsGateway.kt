package io.legado.app.domain.gateway

import io.legado.app.domain.model.settings.ChangeSourceSettings
import io.legado.app.domain.usecase.ChangeSourceMigrationOptions
import kotlinx.coroutines.flow.Flow

interface ChangeSourceSettingsGateway {
    val currentSettings: ChangeSourceSettings
    val settings: Flow<ChangeSourceSettings>
    suspend fun update(transform: (ChangeSourceSettings) -> ChangeSourceSettings)
    suspend fun setMigrationOptions(options: ChangeSourceMigrationOptions)
}
