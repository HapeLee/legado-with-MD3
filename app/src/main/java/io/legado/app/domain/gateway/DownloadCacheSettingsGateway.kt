package io.legado.app.domain.gateway

import io.legado.app.domain.model.settings.DownloadCacheSettings
import kotlinx.coroutines.flow.Flow

interface DownloadCacheSettingsGateway {
    val currentSettings: DownloadCacheSettings
    val settings: Flow<DownloadCacheSettings>
    suspend fun update(update: DownloadCacheSettingsUpdate)
}

sealed interface DownloadCacheSettingsUpdate {
    data class BitmapCacheSize(val value: Int) : DownloadCacheSettingsUpdate
    data class ImageRetainNum(val value: Int) : DownloadCacheSettingsUpdate
    data class PreDownloadNum(val value: Int) : DownloadCacheSettingsUpdate
    data class ThreadCount(val value: Int) : DownloadCacheSettingsUpdate
    data class CacheBookThreadCount(val value: Int) : DownloadCacheSettingsUpdate
    data class UserAgent(val value: String) : DownloadCacheSettingsUpdate
    data class CronetEnabled(val value: Boolean) : DownloadCacheSettingsUpdate
}
