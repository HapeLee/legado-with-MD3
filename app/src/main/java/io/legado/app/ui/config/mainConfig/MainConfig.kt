package io.legado.app.ui.config.mainConfig

import io.legado.app.constant.PreferKey
import io.legado.app.ui.config.prefDelegate

object MainConfig {
    var showDiscovery by prefDelegate(PreferKey.showDiscovery, true)
    var showRSS by prefDelegate(PreferKey.showRss, true)
    var showBottomView by prefDelegate(PreferKey.showBottomView, true)
    var defaultHomePage by prefDelegate(PreferKey.defaultHomePage, "bookshelf")
    var tabletInterface by prefDelegate(PreferKey.tabletInterface, "auto")
    var labelVisibilityMode by prefDelegate(PreferKey.labelVisibilityMode, "auto")
    var swipeAnimation by prefDelegate(PreferKey.swipeAnimation, true)
    var navExtended by prefDelegate("navExtended", false)
    var webServiceAutoStart by prefDelegate(PreferKey.webServiceAutoStart, false)
    var autoRefreshBook by prefDelegate(PreferKey.autoRefresh, false)
    var autoCheckNewBackup by prefDelegate(PreferKey.autoCheckNewBackup, true)
    var showStatusBar by prefDelegate(PreferKey.showStatusBar, true)
}
