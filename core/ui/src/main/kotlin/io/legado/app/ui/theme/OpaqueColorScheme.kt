package io.legado.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import io.legado.app.domain.model.settings.hasBackgroundImage
import io.legado.app.ui.theme.ThemeEngine.getColorScheme

@Composable
fun rememberOpaqueColorScheme(): ColorScheme {
    val context = LocalContext.current
    val themeSettings = LocalAppUiConfiguration.current.theme
    val currentTheme = LocalLegadoThemeColors.current
    val appThemeMode = ThemeResolver.resolveThemeMode(themeSettings.appTheme)
    val isDark = currentTheme.isDark
    val isPureBlack = themeSettings.isPureBlack
    val hasImageBg = themeSettings.hasBackgroundImage(isDark)
    val paletteStyle = themeSettings.paletteStyle
    val materialVersion = themeSettings.materialVersion
    val seedColorInt = currentTheme.seedColor
        .takeUnless { it == Color.Unspecified }
        ?.toArgb()

    return remember(
        context,
        currentTheme.colorScheme,
        appThemeMode,
        isDark,
        isPureBlack,
        hasImageBg,
        paletteStyle,
        materialVersion,
        seedColorInt
    ) {
        if (appThemeMode != AppThemeMode.Transparent) {
            currentTheme.colorScheme
        } else {
            getColorScheme(
                context = context,
                mode = appThemeMode,
                darkTheme = isDark,
                isAmoled = isPureBlack,
                paletteStyle = paletteStyle,
                materialVersion = materialVersion,
                forceOpaque = true,
                customSeedColor = seedColorInt
            )
        }
    }
}
