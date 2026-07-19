package io.legado.app.domain.gateway

import io.legado.app.domain.model.settings.BookExportSettings
import kotlinx.coroutines.flow.Flow

interface BookExportSettingsGateway {
    val currentSettings: BookExportSettings
    val settings: Flow<BookExportSettings>
    suspend fun update(transform: (BookExportSettings) -> BookExportSettings)
}
