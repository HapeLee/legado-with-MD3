package io.legado.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.legado.app.ui.config.themeConfig.ThemeConfig
import io.legado.app.ui.theme.ThemeManager.getColorScheme

@Composable
fun rememberOpaqueColorScheme(): ColorScheme {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val appThemeMode = ThemeResolver.resolveThemeMode(ThemeConfig.appTheme)
    val isPureBlack = ThemeConfig.isPureBlack
    val hasImageBg = ThemeConfig.hasImageBg(isDark)
    val paletteStyle = ThemeConfig.paletteStyle

    return remember(context, appThemeMode, isDark, isPureBlack, hasImageBg, paletteStyle) {
        getColorScheme(
            context = context,
            mode = appThemeMode,
            darkTheme = isDark,
            isAmoled = isPureBlack,
            isImageBg = hasImageBg,
            paletteStyle = paletteStyle,
            forceOpaque = true
        )
    }
}