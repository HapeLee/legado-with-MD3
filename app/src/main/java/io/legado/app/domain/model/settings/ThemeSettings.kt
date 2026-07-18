package io.legado.app.domain.model.settings

data class ThemeSettings(
    val appTheme: String = "0",
    val isPureBlack: Boolean = false,
    val paletteStyle: String = "tonalSpot",
    val materialVersion: String = "material3",
    val customContrast: String = "Default",
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
