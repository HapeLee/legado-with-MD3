package io.legado.app.ui.config.readConfig

import io.legado.app.constant.PreferKey
import io.legado.app.ui.config.prefDelegate

/**
 * 数据同步/缓存/屏幕相关配置
 *
 * 被 ReadBookViewModel、ReadBook、ReadManga、BookHelp 读取。
 */
object ReadDataConfig {

    var syncBookProgress by prefDelegate(
        PreferKey.syncBookProgress,
        true
    )

    var syncBookProgressPlus by prefDelegate(
        PreferKey.syncBookProgressPlus,
        false
    )

    var autoChangeSource by prefDelegate(
        PreferKey.autoChangeSource,
        true
    )

    var defaultSourceChangeAll by prefDelegate(
        PreferKey.defaultSourceChangeAll,
        true
    )

    var readUrlInBrowser by prefDelegate(
        PreferKey.readUrlOpenInBrowser,
        false
    )

    var tocUiUseReplace by prefDelegate(
        PreferKey.tocUiUseReplace,
        false
    )

    var tocCountWords by prefDelegate(
        PreferKey.tocCountWords,
        true
    )

    var preDownloadNum by prefDelegate(
        PreferKey.preDownloadNum,
        10
    )

    var imageRetainNum by prefDelegate(
        PreferKey.imageRetainNum,
        0
    )

    var keepLight by prefDelegate(
        PreferKey.keepLight,
        "0"
    )

    var screenOrientation by prefDelegate(
        PreferKey.screenOrientation,
        "0"
    )
}
