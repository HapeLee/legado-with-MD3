package io.legado.app.ui.config.otherConfig

import io.legado.app.BuildConfig
import io.legado.app.constant.PreferKey
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
        ""
    )

    var antiAlias by prefDelegate(
        PreferKey.antiAlias,
        false
    )

    var bitmapCacheSize by prefDelegate(
        PreferKey.bitmapCacheSize,
        50
    )

    var imageRetainNum by prefDelegate(
        PreferKey.imageRetainNum,
        0
    )

    var preDownloadNum by prefDelegate(
        PreferKey.preDownloadNum,
        10
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

    var sharedElementEnterTransitionEnable by prefDelegate(
        PreferKey.sharedElementEnterTransitionEnable,
        false
    )

    var delayBookLoadEnable by prefDelegate(
        PreferKey.delayBookLoadEnable,
        true
    )

    private var _userAgent by prefDelegate(
        PreferKey.userAgent,
        ""
    )

    var userAgent: String
        get() = _userAgent.ifBlank {
            defaultUserAgent
        }
        set(value) {
            _userAgent = value
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

    var cronetEnable by prefDelegate(
        PreferKey.cronet,
        false
    )

    var webPort by prefDelegate(
        PreferKey.webPort,
        1122
    )

    var threadCount by prefDelegate(
        PreferKey.threadCount,
        16
    )

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

    private val defaultUserAgent: String
        get() = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/${BuildConfig.Cronet_Main_Version} Safari/537.36"

}
