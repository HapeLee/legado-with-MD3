package io.legado.app.domain.gateway

import io.legado.app.domain.model.settings.MangaSettings
import kotlinx.coroutines.flow.Flow

interface MangaSettingsGateway {
    val currentSettings: MangaSettings
    val settings: Flow<MangaSettings>
    suspend fun update(transform: (MangaSettings) -> MangaSettings)
}
