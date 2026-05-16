package io.legado.app.ui.config.otherConfig

import io.legado.app.constant.PreferKey
import io.legado.app.ui.config.downloadCacheConfig.DownloadCacheConfig
import io.legado.app.ui.config.prefDelegate

object OtherConfig {

    var language by prefDelegate(
        PreferKey.language,
        "auto"
    )

    var updateToVariant by prefDelegate(
        PreferKey.updateToVariant,
        "official_version"
    )

    var webServiceAutoStart by prefDelegate(
        PreferKey.webServiceAutoStart,
        false
    )

    var autoRefresh by prefDelegate(
        PreferKey.autoRefresh,
        false
    )

    var defaultToRead by prefDelegate(
        PreferKey.defaultToRead,
        false
    )

    var notificationsPost by prefDelegate(
        PreferKey.notificationsPost,
        true
    )

    var ignoreBatteryPermission by prefDelegate(
        PreferKey.ignoreBatteryPermission,
        true
    )

    var firebaseEnable by prefDelegate(
        PreferKey.firebaseEnable,
        true
    )

    var defaultBookTreeUri by prefDelegate(
        PreferKey.defaultBookTreeUri,
        null as String?
    )

    var antiAlias by prefDelegate(
        PreferKey.antiAlias,
        false
    )

    var replaceEnableDefault by prefDelegate(
        PreferKey.replaceEnableDefault,
        true
    )

    var mediaButtonOnExit by prefDelegate(
        PreferKey.mediaButtonOnExit,
        true
    )

    var readAloudByMediaButton by prefDelegate(
        PreferKey.readAloudByMediaButton,
        false
    )

    var ignoreAudioFocus by prefDelegate(
        PreferKey.ignoreAudioFocus,
        false
    )

    var autoClearExpired by prefDelegate(
        PreferKey.autoClearExpired,
        true
    )

    var showAddToShelfAlert by prefDelegate(
        PreferKey.showAddToShelfAlert,
        true
    )

    var showMangaUi by prefDelegate(
        PreferKey.showMangaUi,
        true
    )

    var userAgent: String
        get() = DownloadCacheConfig.userAgent
        set(value) {
            DownloadCacheConfig.userAgent = value
        }

    var cronetEnable: Boolean
        get() = DownloadCacheConfig.cronetEnable
        set(value) {
            DownloadCacheConfig.cronetEnable = value
        }

    var webServiceWakeLock by prefDelegate(
        PreferKey.webServiceWakeLock,
        false
    )

    private var _sourceEditMaxLine by prefDelegate(
        PreferKey.sourceEditMaxLine,
        Int.MAX_VALUE
    )

    var sourceEditMaxLine: Int
        get() = if (_sourceEditMaxLine < 10) Int.MAX_VALUE else _sourceEditMaxLine
        set(value) {
            _sourceEditMaxLine = value
        }

    var webPort by prefDelegate(
        PreferKey.webPort,
        1122
    )

    var threadCount: Int
        get() = DownloadCacheConfig.threadCount
        set(value) {
            DownloadCacheConfig.threadCount = value
        }

    var cacheBookThreadCount: Int
        get() = DownloadCacheConfig.cacheBookThreadCount
        set(value) {
            DownloadCacheConfig.cacheBookThreadCount = value
        }

    var preDownloadNum: Int
        get() = DownloadCacheConfig.preDownloadNum
        set(value) {
            DownloadCacheConfig.preDownloadNum = value
        }

    var bitmapCacheSize: Int
        get() = DownloadCacheConfig.bitmapCacheSize
        set(value) {
            DownloadCacheConfig.bitmapCacheSize = value
        }

    var imageRetainNum: Int
        get() = DownloadCacheConfig.imageRetainNum
        set(value) {
            DownloadCacheConfig.imageRetainNum = value
        }

    var processText by prefDelegate(
        PreferKey.processText,
        true
    )

    var recordLog by prefDelegate(
        PreferKey.recordLog,
        false
    )

    var recordHeapDump by prefDelegate(
        PreferKey.recordHeapDump,
        false
    )

}
