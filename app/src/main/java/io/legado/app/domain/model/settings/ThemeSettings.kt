package io.legado.app.domain.model.settings

data class ThemeSettings(
    val appTheme: String = "0",
    val useMiuixMonet: Boolean = false,
    val isPureBlack: Boolean = false,
    val paletteStyle: String = "tonalSpot",
    val materialVersion: String = "material3",
    val customContrast: String = "Default",
    val customMode: String = "tonalSpot",
    val appFontPath: String? = null,
    val customPrimary: Int = 0,
    val customNightPrimary: Int = 0,
    val enableDeepPersonalization: Boolean = false,
    val themeColor: Int = 0,
    val secondaryThemeColor: Int = 0,
    val primaryTextColor: Int = 0,
    val secondaryTextColor: Int = 0,
    val themeBackgroundColor: Int = 0,
    val labelContainerColor: Int = 0,
    val themeColorNight: Int = 0,
    val secondaryThemeColorNight: Int = 0,
    val primaryTextColorNight: Int = 0,
    val secondaryTextColorNight: Int = 0,
    val themeBackgroundColorNight: Int = 0,
    val labelContainerColorNight: Int = 0,
    val containerOpacity: Int = 100,
    val overrideBaseCardCornerRadius: Boolean = false,
    val baseCardCornerRadius: Float = 16f,
    val overrideBaseCardBorder: Boolean = false,
    val baseCardBorderWidth: Float = 1f,
    val baseCardBorderColor: Int = 0,
    val baseCardBorderColorNight: Int = 0,
    val disableSplicedColumnGroupCornerRadius: Boolean = false,
    val topBarOpacity: Int = 100,
    val bottomBarOpacity: Int = 100,
    val enableBlur: Boolean = false,
    val enableProgressiveBlur: Boolean = false,
    val topBarBlurRadius: Int = 24,
    val bottomBarBlurRadius: Int = 8,
    val topBarBlurAlpha: Int = 73,
    val bottomBarBlurAlpha: Int = 40,
    val bottomBarLensRadius: Float = 24f,
    val useFlexibleTopAppBar: Boolean = true,
    val bookInfoFollowCoverColor: Boolean = true,
    val bookInfoNetworkCoverBackground: String = "on",
    val bookInfoDefaultCoverBackground: String = "on",
    val bookInfoInputColor: Int = 0,
    val backgroundImageLight: String? = null,
    val backgroundImageDark: String? = null,
    val backgroundImageBlurring: Int = 0,
    val backgroundImageDarkBlurring: Int = 0,
    val enableItemDivider: Boolean = false,
    val itemDividerWidth: Float = 1f,
    val itemDividerLength: Float = 80f,
    val itemDividerColor: Int = 0,
    val eyeProtectionEnabled: Boolean = false,
    val colorTemperature: Int = 50,
    val eyeProtectionSchedule: Boolean = false,
    val eyeProtectionStartTime: String = "22:00",
    val eyeProtectionEndTime: String = "07:00",
    val showRefactorTip: Boolean = true,
    val enableCustomTagColors: Boolean = false,
    val customTagColorsJson: String? = null,
)

data class ThemeCustomColors(
    val primary: Int,
    val secondary: Int,
    val primaryText: Int,
    val secondaryText: Int,
    val background: Int,
    val labelContainer: Int,
) {
    val hasCustomColor: Boolean
        get() = primary != 0 || secondary != 0 || primaryText != 0 ||
            secondaryText != 0 || background != 0 || labelContainer != 0
}

fun ThemeSettings.customColors(isDark: Boolean): ThemeCustomColors =
    if (isDark) {
        ThemeCustomColors(
            primary = themeColorNight.takeIf { it != 0 } ?: themeColor,
            secondary = secondaryThemeColorNight.takeIf { it != 0 } ?: secondaryThemeColor,
            primaryText = primaryTextColorNight.takeIf { it != 0 } ?: primaryTextColor,
            secondaryText = secondaryTextColorNight.takeIf { it != 0 } ?: secondaryTextColor,
            background = themeBackgroundColorNight.takeIf { it != 0 } ?: themeBackgroundColor,
            labelContainer = labelContainerColorNight.takeIf { it != 0 } ?: labelContainerColor,
        )
    } else {
        ThemeCustomColors(
            primary = themeColor,
            secondary = secondaryThemeColor,
            primaryText = primaryTextColor,
            secondaryText = secondaryTextColor,
            background = themeBackgroundColor,
            labelContainer = labelContainerColor,
        )
    }

fun ThemeSettings.hasBackgroundImage(isDark: Boolean): Boolean =
    if (isDark) !backgroundImageDark.isNullOrBlank() else !backgroundImageLight.isNullOrBlank()
