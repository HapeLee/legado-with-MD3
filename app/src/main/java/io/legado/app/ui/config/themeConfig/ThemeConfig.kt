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

    var cPrimary by prefDelegate(PreferKey.cPrimary, 0) {
        postEvent(EventBus.RECREATE, "")
    }

    var cTopBarColor by prefDelegate(PreferKey.cTopBarColor, 0) {
        postEvent(EventBus.RECREATE, "")
    }

    var cNavBarColor by prefDelegate(PreferKey.cNavBarColor, 0) {
        postEvent(EventBus.RECREATE, "")
    }

    var cFontColor by prefDelegate(PreferKey.cFontColor, 0) {
        postEvent(EventBus.RECREATE, "")
    }

    var cBgColor by prefDelegate(PreferKey.cBgColor, 0) {
        postEvent(EventBus.RECREATE, "")
    }

    var enableDeepPersonalization by prefDelegate(PreferKey.enableDeepPersonalization, false) {
        postEvent(EventBus.RECREATE, "")
    }

    // Material Design 3 color roles
    var cMD3Primary by prefDelegate(PreferKey.cMD3Primary, 0) {
        postEvent(EventBus.RECREATE, "")
    }
    var cMD3OnPrimary by prefDelegate(PreferKey.cMD3OnPrimary, 0) {
        postEvent(EventBus.RECREATE, "")
    }
    var cMD3PrimaryContainer by prefDelegate(PreferKey.cMD3PrimaryContainer, 0) {
        postEvent(EventBus.RECREATE, "")
    }
    var cMD3OnPrimaryContainer by prefDelegate(PreferKey.cMD3OnPrimaryContainer, 0) {
        postEvent(EventBus.RECREATE, "")
    }
    var cMD3Secondary by prefDelegate(PreferKey.cMD3Secondary, 0) {
        postEvent(EventBus.RECREATE, "")
    }
    var cMD3OnSecondary by prefDelegate(PreferKey.cMD3OnSecondary, 0) {
        postEvent(EventBus.RECREATE, "")
    }
    var cMD3SecondaryContainer by prefDelegate(PreferKey.cMD3SecondaryContainer, 0) {
        postEvent(EventBus.RECREATE, "")
    }
    var cMD3OnSecondaryContainer by prefDelegate(PreferKey.cMD3OnSecondaryContainer, 0) {
        postEvent(EventBus.RECREATE, "")
    }
    var cMD3Tertiary by prefDelegate(PreferKey.cMD3Tertiary, 0) {
        postEvent(EventBus.RECREATE, "")
    }
    var cMD3Error by prefDelegate(PreferKey.cMD3Error, 0) {
        postEvent(EventBus.RECREATE, "")
    }
    var cMD3Surface by prefDelegate(PreferKey.cMD3Surface, 0) {
        postEvent(EventBus.RECREATE, "")
    }
    var cMD3OnSurface by prefDelegate(PreferKey.cMD3OnSurface, 0) {
        postEvent(EventBus.RECREATE, "")
    }
    var cMD3Background by prefDelegate(PreferKey.cMD3Background, 0) {
        postEvent(EventBus.RECREATE, "")
    }
    var cMD3Outline by prefDelegate(PreferKey.cMD3Outline, 0) {
        postEvent(EventBus.RECREATE, "")
    }
    var cMD3SurfaceContainerLow by prefDelegate(PreferKey.cMD3SurfaceContainerLow, 0) {
        postEvent(EventBus.RECREATE, "")
    }
    var cMD3SurfaceVariant by prefDelegate(PreferKey.cMD3SurfaceVariant, 0) {
        postEvent(EventBus.RECREATE, "")
    }

    var enableContainerBorder by prefDelegate(PreferKey.enableContainerBorder, false) {
        postEvent(EventBus.RECREATE, "")
    }

    var containerBorderWidth by prefDelegate(PreferKey.containerBorderWidth, 1f) {
        postEvent(EventBus.RECREATE, "")
    }

    var containerBorderStyle by prefDelegate(PreferKey.containerBorderStyle, "solid") {
        postEvent(EventBus.RECREATE, "")
    }

    var containerBorderColor by prefDelegate(PreferKey.containerBorderColor, 0) {
        postEvent(EventBus.RECREATE, "")
    }

    var containerBorderDashWidth by prefDelegate(PreferKey.containerBorderDashWidth, 4f) {
        postEvent(EventBus.RECREATE, "")
    }

    // 中间单线间隔设置
    var enableItemDivider by prefDelegate(PreferKey.enableItemDivider, true) {
        postEvent(EventBus.RECREATE, "")
    }

    var itemDividerWidth by prefDelegate(PreferKey.itemDividerWidth, 1f) {
        postEvent(EventBus.RECREATE, "")
    }

    var itemDividerLength by prefDelegate(PreferKey.itemDividerLength, 80f) {
        postEvent(EventBus.RECREATE, "")
    }

    var itemDividerColor by prefDelegate(PreferKey.itemDividerColor, 0) {
        postEvent(EventBus.RECREATE, "")
    }

    var cBookInfoInputColor by prefDelegate(PreferKey.cBookInfoInputColor, 0) {
        postEvent(EventBus.RECREATE, "")
    }

    var cNPrimary by prefDelegate(PreferKey.cNPrimary, 0) {
        postEvent(EventBus.RECREATE, "")
    }

    var customContrast by prefDelegate(PreferKey.customContrast, "Default") {
        postEvent(EventBus.RECREATE, "")
    }

    var launcherIcon by prefDelegate(PreferKey.launcherIcon, "ic_launcher")

    var enableCustomTagColors by prefDelegate(PreferKey.enableCustomTagColors, false) {
        postEvent(EventBus.RECREATE, "")
    }

    var customTagColorsJson: String?
        get() = appCtx.getPrefString(PreferKey.customTagColors)
        set(value) {
            appCtx.putPrefString(PreferKey.customTagColors, value)
            postEvent(EventBus.RECREATE, "")
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