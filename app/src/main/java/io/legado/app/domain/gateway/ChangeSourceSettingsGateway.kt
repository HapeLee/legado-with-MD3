package io.legado.app.domain.gateway

import io.legado.app.domain.model.settings.ChangeSourceSettings
import io.legado.app.domain.usecase.ChangeSourceMigrationOptions
import kotlinx.coroutines.flow.Flow

interface ChangeSourceSettingsGateway {
    val currentSettings: ChangeSourceSettings
    val settings: Flow<ChangeSourceSettings>
    suspend fun update(update: ChangeSourceSettingsUpdate)
    suspend fun setMigrationOptions(options: ChangeSourceMigrationOptions)
}

sealed interface ChangeSourceSettingsUpdate {
    data class SearchScope(val value: String) : ChangeSourceSettingsUpdate
    data class CheckAuthor(val value: Boolean) : ChangeSourceSettingsUpdate
    data class LoadInfo(val value: Boolean) : ChangeSourceSettingsUpdate
    data class LoadToc(val value: Boolean) : ChangeSourceSettingsUpdate
    data class LoadWordCount(val value: Boolean) : ChangeSourceSettingsUpdate
}
