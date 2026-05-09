package io.legado.app.ui.config.themeConfig

import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.ui.config.prefDelegate
import io.legado.app.utils.GSON
import io.legado.app.utils.getPrefString
import io.legado.app.utils.postEvent
import io.legado.app.utils.putPrefString
import splitties.init.appCtx

data class TagColorPair(
    val textColor: Int = 0,
    val bgColor: Int = 0
)

object ThemeConfig {

    var containerOpacity by prefDelegate(PreferKey.containerOpacity, 100)

    var topBarOpacity by prefDelegate(PreferKey.topBarOpacity, 100)

    var bottomBarOpacity by prefDelegate(PreferKey.bottomBarOpacity, 100)

    var enableBlur by prefDelegate(PreferKey.enableBlur, false)

    var enableProgressiveBlur by prefDelegate(PreferKey.enableProgressiveBlur, false)

    var topBarBlurRadius by prefDelegate(PreferKey.topBarBlurRadius, 24)

    var bottomBarBlurRadius by prefDelegate(PreferKey.bottomBarBlurRadius, 8)

    var topBarBlurAlpha by prefDelegate(PreferKey.topBarBlurAlpha, 73)

    var bottomBarBlurAlpha by prefDelegate(PreferKey.bottomBarBlurAlpha, 40)

    var bottomBarLensRadius by prefDelegate(PreferKey.bottomBarLensRadius, 24f)

    var useFlexibleTopAppBar by prefDelegate(PreferKey.useFlexibleTopAppBar, true)

    var paletteStyle by prefDelegate(PreferKey.paletteStyle, "tonalSpot")

    //m3 or miuix
    var composeEngine by prefDelegate(PreferKey.composeEngine, "material")

    var useMiuixMonet by prefDelegate(PreferKey.useMiuixMonet, false) {
        postEvent(EventBus.RECREATE, "")
    }

    var materialVersion by prefDelegate(PreferKey.materialVersion, "material3")

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

    var appFontPath: String?
        get() = appCtx.getPrefString(PreferKey.appFontPath)
        set(value) {
            appCtx.putPrefString(PreferKey.appFontPath, value)
            postEvent(EventBus.RECREATE, "")
        }

    var cPrimary by prefDelegate(PreferKey.cPrimary, 0)

    var enableDeepPersonalization by prefDelegate(PreferKey.enableDeepPersonalization, false)

    var themeColor by prefDelegate(PreferKey.themeColor, 0)

    var secondaryThemeColor by prefDelegate(PreferKey.secondaryThemeColor, 0)

    var primaryTextColor by prefDelegate(PreferKey.primaryTextColor, 0)

    var secondaryTextColor by prefDelegate(PreferKey.secondaryTextColor, 0)

    var themeBackgroundColor by prefDelegate(PreferKey.themeBackgroundColor, 0)

    var labelContainerColor by prefDelegate(PreferKey.labelContainerColor, 0)

    var enableContainerBorder by prefDelegate(PreferKey.enableContainerBorder, false)

    var containerBorderWidth by prefDelegate(PreferKey.containerBorderWidth, 1f)

    var containerBorderStyle by prefDelegate(PreferKey.containerBorderStyle, "solid")

    var containerBorderColor by prefDelegate(PreferKey.containerBorderColor, 0)

    var containerBorderDashWidth by prefDelegate(PreferKey.containerBorderDashWidth, 4f)

    var enableItemDivider by prefDelegate(PreferKey.enableItemDivider, false)

    var itemDividerWidth by prefDelegate(PreferKey.itemDividerWidth, 1f)

    var itemDividerLength by prefDelegate(PreferKey.itemDividerLength, 80f)
    var itemDividerColor by prefDelegate(PreferKey.itemDividerColor, 0)

    var bookInfoInputColor by prefDelegate(PreferKey.bookInfoInputColor, 0)

    var cNPrimary by prefDelegate(PreferKey.cNPrimary, 0) {
        postEvent(EventBus.RECREATE, "")
    }

    var customContrast by prefDelegate(PreferKey.customContrast, "Default") {
        postEvent(EventBus.RECREATE, "")
    }

    var launcherIcon by prefDelegate(PreferKey.launcherIcon, "ic_launcher")

    var enableCustomTagColors by prefDelegate(PreferKey.enableCustomTagColors, false)

    var customTagColorsJson: String?
        get() = appCtx.getPrefString(PreferKey.customTagColors)
        set(value) {
            appCtx.putPrefString(PreferKey.customTagColors, value)
        }

    fun getCustomTagColors(): List<TagColorPair> {
        return try {
            val json = customTagColorsJson
            if (json.isNullOrBlank()) emptyList()
            else GSON.fromJson(json, Array<TagColorPair>::class.java).toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveCustomTagColors(colors: List<TagColorPair>) {
        customTagColorsJson = GSON.toJson(colors)
    }

    var showDiscovery by prefDelegate(PreferKey.showDiscovery, true)

    var showRss by prefDelegate(PreferKey.showRss, true)

    var showStatusBar by prefDelegate(PreferKey.showStatusBar, true)

    var swipeAnimation by prefDelegate(PreferKey.swipeAnimation, true)

    var showBottomView by prefDelegate(PreferKey.showBottomView, true)

    var useFloatingBottomBar by prefDelegate(PreferKey.useFloatingBottomBar, false)

    var useFloatingBottomBarLiquidGlass by prefDelegate(
        PreferKey.useFloatingBottomBarLiquidGlass,
        false
    )

    var tabletInterface by prefDelegate(PreferKey.tabletInterface, "auto")

    var labelVisibilityMode by prefDelegate(PreferKey.labelVisibilityMode, "auto")

    var defaultHomePage by prefDelegate(PreferKey.defaultHomePage, "bookshelf")

    fun hasImageBg(isDark: Boolean): Boolean =
        !(if (isDark) bgImageDark else bgImageLight).isNullOrBlank()

}
