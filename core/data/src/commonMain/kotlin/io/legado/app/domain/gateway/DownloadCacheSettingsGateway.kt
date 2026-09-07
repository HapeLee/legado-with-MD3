package io.legado.app.domain.gateway

import io.legado.app.domain.model.settings.DownloadCacheSettings
import kotlinx.coroutines.flow.Flow

interface DownloadCacheSettingsGateway {
    val currentSettings: DownloadCacheSettings
    val settings: Flow<DownloadCacheSettings>
    suspend fun update(transform: (DownloadCacheSettings) -> DownloadCacheSettings)
}
