package io.legado.app.ui.config.downloadCacheConfig

import io.legado.app.domain.gateway.DownloadCacheSettingsGateway
import org.koin.core.context.GlobalContext

@Deprecated("使用 DownloadCacheSettingsGateway.currentSettings")
object DownloadCacheConfig {
    private val settings get() = GlobalContext.get().get<DownloadCacheSettingsGateway>().currentSettings
    val bitmapCacheSize get() = settings.bitmapCacheSize
    val imageRetainNum get() = settings.imageRetainNum
    val preDownloadNum get() = settings.preDownloadNum
    val threadCount get() = settings.threadCount
    val cacheBookThreadCount get() = settings.cacheBookThreadCount
    val userAgent get() = settings.userAgent
    val cronetEnable get() = settings.cronetEnabled
}
