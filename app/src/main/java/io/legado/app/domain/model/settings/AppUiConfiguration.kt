package io.legado.app.domain.model.settings

data class AppUiConfiguration(
    val language: String = "auto",
    val appShell: AppShellSettings = AppShellSettings(),
    val theme: ThemeSettings = ThemeSettings(),
    val cover: CoverSettings = CoverSettings(),
    val isSystemDarkTheme: Boolean = false,
) {
    val isDarkTheme: Boolean
        get() = when (appShell.themeMode) {
            "1" -> false
            "2" -> true
            else -> isSystemDarkTheme
        }
}

data class AppUiConfigurationDiff(
    val localeChanged: Boolean = false,
    val themeChanged: Boolean = false,
    val fontScaleChanged: Boolean = false,
    val windowChanged: Boolean = false,
) {
    val hasChanges: Boolean
        get() = localeChanged || themeChanged || fontScaleChanged || windowChanged

    val requiresLegacyContentRefresh: Boolean
        get() = localeChanged || themeChanged || fontScaleChanged
}

fun AppUiConfiguration.diffFrom(previous: AppUiConfiguration): AppUiConfigurationDiff =
    AppUiConfigurationDiff(
        localeChanged = language != previous.language,
        themeChanged = appShell.themeMode != previous.appShell.themeMode ||
            (appShell.themeMode == "0" && isSystemDarkTheme != previous.isSystemDarkTheme) ||
            theme.requiresLegacyThemeRefresh(previous.theme),
        fontScaleChanged = appShell.fontScale != previous.appShell.fontScale,
        windowChanged = appShell.showStatusBar != previous.appShell.showStatusBar ||
            theme.backgroundImageLight != previous.theme.backgroundImageLight ||
            theme.backgroundImageDark != previous.theme.backgroundImageDark ||
            theme.backgroundImageBlurring != previous.theme.backgroundImageBlurring ||
            theme.backgroundImageDarkBlurring != previous.theme.backgroundImageDarkBlurring,
    )

private fun ThemeSettings.requiresLegacyThemeRefresh(previous: ThemeSettings): Boolean =
    appTheme != previous.appTheme ||
        isPureBlack != previous.isPureBlack ||
        customMode != previous.customMode ||
        appFontPath != previous.appFontPath ||
        customPrimary != previous.customPrimary ||
        customNightPrimary != previous.customNightPrimary
