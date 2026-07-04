package io.legado.app.ui.config.themeConfig

import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatDelegate
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.ui.config.prefDelegate
import io.legado.app.utils.GSON
import io.legado.app.utils.postEvent

data class TagColorPair(
    val textColor: Int = 0,
    val bgColor: Int = 0
)

data class CustomThemeColors(
    val primary: Int,
    val secondary: Int,
    val primaryText: Int,
    val secondaryText: Int,
    val background: Int,
    val labelContainer: Int,
) {
    val hasCustomColor: Boolean
        get() = primary != 0 ||
                secondary != 0 ||
                primaryText != 0 ||
                secondaryText != 0 ||
                background != 0 ||
                labelContainer != 0
}

object ThemeConfig {

    private const val CUSTOM_APP_THEME = "12"

    const val BOOK_INFO_BACKGROUND_BLUR_OFF = "off"
    const val BOOK_INFO_BACKGROUND_BLUR_ON = "on"
    const val BOOK_INFO_BACKGROUND_COVER_HIDDEN = "off_for_default"

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

    var bookInfoFollowCoverColor by prefDelegate(PreferKey.bookInfoFollowCoverColor, true)

    var bookInfoBackgroundBlur by prefDelegate(
        PreferKey.bookInfoBackgroundBlur,
        BOOK_INFO_BACKGROUND_BLUR_ON
    )

    var paletteStyle by prefDelegate(PreferKey.paletteStyle, "tonalSpot")

    //m3 or miuix
    var composeEngine by prefDelegate(PreferKey.composeEngine, "material")

    var useMiuixMonet by prefDelegate(PreferKey.useMiuixMonet, false) {
        postEvent(EventBus.RECREATE, "")
    }

    var materialVersion by prefDelegate(PreferKey.materialVersion, "material3")

    var appTheme by prefDelegate(PreferKey.appTheme, "0")

    var themeMode by prefDelegate(PreferKey.themeMode, "0") {
        Handler(Looper.getMainLooper()).post { initNightMode() }
    }

    fun initNightMode() {
        when (themeMode) {
            "1" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "2" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

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

    var appFontPath by prefDelegate<String?>(PreferKey.appFontPath, null) {
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

    var themeColorNight by prefDelegate(PreferKey.themeColorNight, 0)

    var secondaryThemeColorNight by prefDelegate(PreferKey.secondaryThemeColorNight, 0)

    var primaryTextColorNight by prefDelegate(PreferKey.primaryTextColorNight, 0)

    var secondaryTextColorNight by prefDelegate(PreferKey.secondaryTextColorNight, 0)

    var themeBackgroundColorNight by prefDelegate(PreferKey.themeBackgroundColorNight, 0)

    var labelContainerColorNight by prefDelegate(PreferKey.labelContainerColorNight, 0)

    val isDeepPersonalizationActive: Boolean
        get() = appTheme == CUSTOM_APP_THEME && enableDeepPersonalization

    fun customThemeColors(isDark: Boolean): CustomThemeColors {
        if (!isDark) {
            return CustomThemeColors(
                primary = themeColor,
                secondary = secondaryThemeColor,
                primaryText = primaryTextColor,
                secondaryText = secondaryTextColor,
                background = themeBackgroundColor,
                labelContainer = labelContainerColor,
            )
        }
        return CustomThemeColors(
            primary = themeColorNight.takeIf { it != 0 } ?: themeColor,
            secondary = secondaryThemeColorNight.takeIf { it != 0 } ?: secondaryThemeColor,
            primaryText = primaryTextColorNight.takeIf { it != 0 } ?: primaryTextColor,
            secondaryText = secondaryTextColorNight.takeIf { it != 0 } ?: secondaryTextColor,
            background = themeBackgroundColorNight.takeIf { it != 0 } ?: themeBackgroundColor,
            labelContainer = labelContainerColorNight.takeIf { it != 0 } ?: labelContainerColor,
        )
    }

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

    var customTagColorsJson by prefDelegate<String?>(PreferKey.customTagColors, null)

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

    var showHome by prefDelegate(PreferKey.showHome, true)

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

    var mainNavigationOrder by prefDelegate(
        PreferKey.mainNavigationOrder,
        "home,bookshelf,explore,rss,my",
    )

    var navExtended by prefDelegate("navExtended", false)

    var webServiceAutoStart by prefDelegate(PreferKey.webServiceAutoStart, false)

    var autoRefreshBook by prefDelegate(PreferKey.autoRefresh, false)

    var autoCheckNewBackup by prefDelegate(PreferKey.autoCheckNewBackup, true)

    var navIconHome by prefDelegate(PreferKey.navIconHome, "")

    var navIconBookshelf by prefDelegate(PreferKey.navIconBookshelf, "")

    var navIconExplore by prefDelegate(PreferKey.navIconExplore, "")

    var navIconRss by prefDelegate(PreferKey.navIconRss, "")

    var navIconMy by prefDelegate(PreferKey.navIconMy, "")

    // Eye Protection
    var eyeProtectionEnabled by prefDelegate(PreferKey.eyeProtectionEnabled, false) {
        postEvent(PreferKey.eyeProtectionEnabled, it)
    }
    var colorTemperature by prefDelegate(PreferKey.colorTemperature, 50) {
        postEvent(PreferKey.colorTemperature, it)
    }
    var eyeProtectionSchedule by prefDelegate(PreferKey.eyeProtectionSchedule, false) {
        postEvent(PreferKey.eyeProtectionSchedule, it)
    }
    var eyeProtectionStartTime by prefDelegate(PreferKey.eyeProtectionStartTime, "22:00") {
        postEvent(PreferKey.eyeProtectionStartTime, it)
    }
    var eyeProtectionEndTime by prefDelegate(PreferKey.eyeProtectionEndTime, "07:00") {
        postEvent(PreferKey.eyeProtectionEndTime, it)
    }

    fun hasImageBg(isDark: Boolean): Boolean =
        !(if (isDark) bgImageDark else bgImageLight).isNullOrBlank()

}
