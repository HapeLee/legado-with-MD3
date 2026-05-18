package io.legado.app.ui.config.downloadCacheConfig

import io.legado.app.BuildConfig
import io.legado.app.constant.PreferKey
import io.legado.app.ui.config.prefDelegate

object DownloadCacheConfig {

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

    var threadCount by prefDelegate(
        PreferKey.threadCount,
        16
    )

    var cacheBookThreadCount by prefDelegate(
        PreferKey.cacheBookThreadCount,
        16
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

    var cronetEnable by prefDelegate(
        PreferKey.cronet,
        false
    )

    private val defaultUserAgent: String
        get() = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/${BuildConfig.Cronet_Main_Version} Safari/537.36"

}
