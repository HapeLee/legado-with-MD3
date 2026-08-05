package io.legado.app.ui.config.otherConfig

import io.legado.app.domain.gateway.AppLocaleGateway
import io.legado.app.domain.gateway.DownloadCacheSettingsGateway
import io.legado.app.domain.gateway.OtherSettingsGateway
import org.koin.core.context.GlobalContext

@Deprecated("使用 OtherSettingsGateway.currentSettings")
object OtherConfig {
    private val settings get() = GlobalContext.get().get<OtherSettingsGateway>().currentSettings
    private val cache get() = GlobalContext.get().get<DownloadCacheSettingsGateway>().currentSettings
    val language get() = GlobalContext.get().get<AppLocaleGateway>().currentLanguage
    val updateToVariant get() = settings.updateToVariant
    val autoCheckUpdateOnStart get() = settings.autoCheckUpdateOnStart
    val webServiceAutoStart get() = settings.webServiceAutoStart
    val autoRefresh get() = settings.autoRefresh
    val defaultToRead get() = settings.defaultToRead
    val notificationsPost get() = settings.notificationsPost
    val ignoreBatteryPermission get() = settings.ignoreBatteryPermission
    val firebaseEnable get() = settings.firebaseEnable
    val defaultBookTreeUri get() = settings.defaultBookTreeUri
    val antiAlias get() = settings.antiAlias
    val replaceEnableDefault get() = settings.replaceEnableDefault
    val autoClearExpired get() = settings.autoClearExpired
    val showAddToShelfAlert get() = settings.showAddToShelfAlert
    val webServiceWakeLock get() = settings.webServiceWakeLock
    val sourceEditMaxLine get() = settings.sourceEditMaxLine
    val webPort get() = settings.webPort
    val processText get() = settings.processText
    val recordLog get() = settings.recordLog
    val recordHeapDump get() = settings.recordHeapDump
    val userAgent get() = cache.userAgent
    val cronetEnable get() = cache.cronetEnabled
    val threadCount get() = cache.threadCount
    val cacheBookThreadCount get() = cache.cacheBookThreadCount
    val preDownloadNum get() = cache.preDownloadNum
    val bitmapCacheSize get() = cache.bitmapCacheSize
    val imageRetainNum get() = cache.imageRetainNum
}
