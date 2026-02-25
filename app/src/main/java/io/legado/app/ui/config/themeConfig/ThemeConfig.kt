package io.legado.app.ui.config.themeConfig

import io.legado.app.constant.PreferKey
import io.legado.app.ui.config.prefDelegate

object ThemeConfig {

    var containerOpacity by prefDelegate(PreferKey.containerOpacity, 100)

    var enableBlur by prefDelegate(PreferKey.enableBlur, false)

    var enableProgressiveBlur by prefDelegate(PreferKey.enableProgressiveBlur, true)

    var paletteStyle by prefDelegate(PreferKey.paletteStyle, "tonalSpot")

    var appTheme by prefDelegate(PreferKey.appTheme, "0")

    var isPureBlack by prefDelegate(PreferKey.pureBlack, false)

    var bgImageLight by prefDelegate<String?>(PreferKey.bgImage, null)

    var bgImageDark by prefDelegate<String?>(PreferKey.bgImageN, null)

    fun hasImageBg(isDark: Boolean): Boolean {
        return if (isDark) {
            !bgImageDark.isNullOrEmpty()
        } else {
            !bgImageLight.isNullOrEmpty()
        }
    }
}