package io.legado.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.legado.app.ui.config.themeConfig.ThemeConfig

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val appThemeMode = ThemeResolver.resolveThemeMode(ThemeConfig.appTheme)
    val isPureBlack = ThemeConfig.isPureBlack
    val hasImageBg = ThemeConfig.hasImageBg(darkTheme)
    val paletteStyle = ThemeConfig.paletteStyle

    val colorScheme =
        remember(context, appThemeMode, darkTheme, isPureBlack, hasImageBg, paletteStyle) {
            ThemeManager.getColorScheme(
                context = context,
                mode = appThemeMode,
                darkTheme = darkTheme,
                isAmoled = isPureBlack,
                isImageBg = hasImageBg,
                paletteStyle = paletteStyle
            )
        }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        motionScheme = MotionScheme.expressive(),
        shapes = Shapes(),
        content = content
    )
}