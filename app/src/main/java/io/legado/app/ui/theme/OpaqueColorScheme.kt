package io.legado.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import io.legado.app.ui.config.themeConfig.ThemeConfig
import io.legado.app.ui.theme.ThemeEngine.getColorScheme

@Composable
fun rememberOpaqueColorScheme(): ColorScheme {
    val context = LocalContext.current
    val currentTheme = LocalLegadoThemeColors.current
    val appThemeMode = ThemeResolver.resolveThemeMode(ThemeConfig.appTheme)
    val isDark = currentTheme.isDark
    val isPureBlack = ThemeConfig.isPureBlack
    val hasImageBg = ThemeConfig.hasImageBg(isDark)
    val paletteStyle = ThemeConfig.paletteStyle
    val materialVersion = ThemeConfig.materialVersion
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
