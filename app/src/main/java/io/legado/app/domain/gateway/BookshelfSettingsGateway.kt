package io.legado.app.domain.gateway

import io.legado.app.domain.model.settings.BookshelfSettings
import kotlinx.coroutines.flow.Flow

interface BookshelfSettingsGateway {
    val currentSettings: BookshelfSettings
    val settings: Flow<BookshelfSettings>
    suspend fun update(transform: (BookshelfSettings) -> BookshelfSettings)
}
