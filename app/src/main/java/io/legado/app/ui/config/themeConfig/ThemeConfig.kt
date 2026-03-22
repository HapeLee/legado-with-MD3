package io.legado.app.ui.config.themeConfig

import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.ui.config.prefDelegate
import io.legado.app.utils.postEvent

object ThemeConfig {

    var containerOpacity by prefDelegate(PreferKey.containerOpacity, 100)

    var enableBlur by prefDelegate(PreferKey.enableBlur, false)

    var enableProgressiveBlur by prefDelegate(PreferKey.enableProgressiveBlur, true)

    var useFlexibleTopAppBar by prefDelegate(PreferKey.useFlexibleTopAppBar, true)

    var paletteStyle by prefDelegate(PreferKey.paletteStyle, "tonalSpot")

    var appTheme by prefDelegate(PreferKey.appTheme, "0")

    var themeMode by prefDelegate(PreferKey.themeMode, "0")

    var isPureBlack by prefDelegate(PreferKey.pureBlack, false)

    var bgImageLight by prefDelegate<String?>(PreferKey.bgImage, null) {
        postEvent(EventBus.RECREATE, false)
    }

    var bgImageDark by prefDelegate<String?>(PreferKey.bgImageN, null) {
        postEvent(EventBus.RECREATE, false)
    }

    var bgImageBlurring by prefDelegate(PreferKey.bgImageBlurring, 0)

    var bgImageNBlurring by prefDelegate(PreferKey.bgImageNBlurring, 0)

    var isPredictiveBackEnabled by prefDelegate(PreferKey.isPredictiveBackEnabled, true)

    var customMode by prefDelegate<String?>(PreferKey.customMode, "tonalSpot")

    var fontScale by prefDelegate(PreferKey.fontScale, 10) {
        postEvent(EventBus.RECREATE, "")
    }

    var cPrimary by prefDelegate(PreferKey.cPrimary, 0) {
        postEvent(EventBus.RECREATE, "")
    }

    var launcherIcon by prefDelegate(PreferKey.launcherIcon, "ic_launcher")

    var showDiscovery by prefDelegate(PreferKey.showDiscovery, true)

    var showRss by prefDelegate(PreferKey.showRss, true)

    var showStatusBar by prefDelegate(PreferKey.showStatusBar, true)

    var swipeAnimation by prefDelegate(PreferKey.swipeAnimation, true)

    var showBottomView by prefDelegate(PreferKey.showBottomView, true)

    var tabletInterface by prefDelegate(PreferKey.tabletInterface, "auto")

    var labelVisibilityMode by prefDelegate(PreferKey.labelVisibilityMode, "auto")

    var defaultHomePage by prefDelegate(PreferKey.defaultHomePage, "bookshelf")

    fun hasImageBg(isDark: Boolean): Boolean =
        !(if (isDark) bgImageDark else bgImageLight).isNullOrBlank()

}