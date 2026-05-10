package io.legado.app.help.config

import io.legado.app.ui.config.themeConfig.ThemeConfig

data class ThemeAppearanceConfig(
    var enableDeepPersonalization: Boolean = false,
    var themeColor: Int = 0,
    var secondaryThemeColor: Int = 0,
    var primaryTextColor: Int = 0,
    var secondaryTextColor: Int = 0,
    var backgroundColor: Int = 0,
    var labelContainerColor: Int = 0,
    var bookInfoInputColor: Int = 0,
    var appFontPath: String? = null,
    var enableContainerBorder: Boolean = false,
    var containerBorderWidth: Float = 1f,
    var containerBorderStyle: String = "solid",
    var containerBorderDashWidth: Float = 4f,
    var containerBorderColor: Int = 0,
    var enableItemDivider: Boolean = true,
    var itemDividerWidth: Float = 1f,
    var itemDividerLength: Float = 80f,
    var itemDividerColor: Int = 0,
    var enableCustomTagColors: Boolean = false,
    var customTagColorsJson: String? = null,
    var enableBlur: Boolean = false,
    var topBarBlurRadius: Int = 24,
    var bottomBarBlurRadius: Int = 8,
    var topBarBlurAlpha: Int = 73,
    var bottomBarBlurAlpha: Int = 40,
    var bottomBarLensRadius: Float = 24f
) {

    val hasDeepColorOverrides: Boolean
        get() = themeColor != 0 ||
                secondaryThemeColor != 0 ||
                primaryTextColor != 0 ||
                secondaryTextColor != 0 ||
                backgroundColor != 0 ||
                labelContainerColor != 0

    fun applyToThemeConfig(appFontPathOverride: String? = appFontPath) {
        ThemeConfig.enableDeepPersonalization = enableDeepPersonalization
        ThemeConfig.themeColor = themeColor
        ThemeConfig.secondaryThemeColor = secondaryThemeColor
        ThemeConfig.primaryTextColor = primaryTextColor
        ThemeConfig.secondaryTextColor = secondaryTextColor
        ThemeConfig.themeBackgroundColor = backgroundColor
        ThemeConfig.labelContainerColor = labelContainerColor

        ThemeConfig.bookInfoInputColor = bookInfoInputColor

        ThemeConfig.appFontPath = appFontPathOverride

        ThemeConfig.enableContainerBorder = enableContainerBorder
        ThemeConfig.containerBorderWidth = containerBorderWidth
        ThemeConfig.containerBorderStyle = containerBorderStyle.ifBlank { "solid" }
        ThemeConfig.containerBorderDashWidth = containerBorderDashWidth
        ThemeConfig.containerBorderColor = containerBorderColor

        ThemeConfig.enableItemDivider = enableItemDivider
        ThemeConfig.itemDividerWidth = itemDividerWidth
        ThemeConfig.itemDividerLength = itemDividerLength
        ThemeConfig.itemDividerColor = itemDividerColor

        ThemeConfig.enableCustomTagColors = enableCustomTagColors
        ThemeConfig.customTagColorsJson = ThemeAppearanceJson.convertTagColorsFromHex(customTagColorsJson)

        ThemeConfig.enableBlur = enableBlur
        ThemeConfig.topBarBlurRadius = topBarBlurRadius
        ThemeConfig.bottomBarBlurRadius = bottomBarBlurRadius
        ThemeConfig.topBarBlurAlpha = topBarBlurAlpha
        ThemeConfig.bottomBarBlurAlpha = bottomBarBlurAlpha
        ThemeConfig.bottomBarLensRadius = bottomBarLensRadius
    }

    companion object {
        fun fromCurrent(): ThemeAppearanceConfig {
            return ThemeAppearanceConfig(
                enableDeepPersonalization = ThemeConfig.enableDeepPersonalization,
                themeColor = ThemeConfig.themeColor,
                secondaryThemeColor = ThemeConfig.secondaryThemeColor,
                primaryTextColor = ThemeConfig.primaryTextColor,
                secondaryTextColor = ThemeConfig.secondaryTextColor,
                backgroundColor = ThemeConfig.themeBackgroundColor,
                labelContainerColor = ThemeConfig.labelContainerColor,
                bookInfoInputColor = ThemeConfig.bookInfoInputColor,
                appFontPath = ThemeConfig.appFontPath,
                enableContainerBorder = ThemeConfig.enableContainerBorder,
                containerBorderWidth = ThemeConfig.containerBorderWidth,
                containerBorderStyle = ThemeConfig.containerBorderStyle,
                containerBorderDashWidth = ThemeConfig.containerBorderDashWidth,
                containerBorderColor = ThemeConfig.containerBorderColor,
                enableItemDivider = ThemeConfig.enableItemDivider,
                itemDividerWidth = ThemeConfig.itemDividerWidth,
                itemDividerLength = ThemeConfig.itemDividerLength,
                itemDividerColor = ThemeConfig.itemDividerColor,
                enableCustomTagColors = ThemeConfig.enableCustomTagColors,
                customTagColorsJson = ThemeConfig.customTagColorsJson,
                enableBlur = ThemeConfig.enableBlur,
                topBarBlurRadius = ThemeConfig.topBarBlurRadius,
                bottomBarBlurRadius = ThemeConfig.bottomBarBlurRadius,
                topBarBlurAlpha = ThemeConfig.topBarBlurAlpha,
                bottomBarBlurAlpha = ThemeConfig.bottomBarBlurAlpha,
                bottomBarLensRadius = ThemeConfig.bottomBarLensRadius
            )
        }
    }
}
