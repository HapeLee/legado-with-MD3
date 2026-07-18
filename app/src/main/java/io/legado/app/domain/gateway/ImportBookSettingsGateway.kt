package io.legado.app.domain.gateway

import io.legado.app.domain.model.settings.ImportBookSettings
import kotlinx.coroutines.flow.Flow

interface ImportBookSettingsGateway {
    val currentSettings: ImportBookSettings
    val settings: Flow<ImportBookSettings>
    suspend fun update(update: ImportBookSettingsUpdate)
}

sealed interface ImportBookSettingsUpdate {
    data class ImportBookPath(val value: String?) : ImportBookSettingsUpdate
    data class BookImportFileName(val value: String?) : ImportBookSettingsUpdate
    data class LocalBookImportSort(val value: Int) : ImportBookSettingsUpdate
    data class RemoteServerId(val value: Long) : ImportBookSettingsUpdate
}
