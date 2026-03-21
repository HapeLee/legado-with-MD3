package io.legado.app.ui.config.themeConfig

import android.util.Log
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

    var isPureBlack by prefDelegate(PreferKey.pureBlack, false)

    var bgImageLight by prefDelegate<String?>(PreferKey.bgImage, null) {
        postEvent(EventBus.RECREATE, false)
    }

    var bgImageDark by prefDelegate<String?>(PreferKey.bgImageN, null) {
        postEvent(EventBus.RECREATE, false)
    }

    var bgImageBlurring by prefDelegate(PreferKey.bgImageBlurring, 0)

    var bgImageNBlurring by prefDelegate(PreferKey.bgImageNBlurring, 0)

    fun hasImageBg(isDark: Boolean): Boolean {
        val result = if (isDark) {
            !bgImageDark.isNullOrBlank()
        } else {
            !bgImageLight.isNullOrBlank()
        }

        Log.d(
            "MainConfig",
            "hasImageBg -> isDark=$isDark, " +
                    "bgImageDark=$bgImageDark, " +
                    "bgImageLight=$bgImageLight, " +
                    "result=$result"
        )

        return result
    }

    /*
    fun hasImageBg(isDark: Boolean): Boolean {
        return if (isDark) bgImageDark.isNullOrBlank() else bgImageLight.isNullOrBlank()
    }
     */

}