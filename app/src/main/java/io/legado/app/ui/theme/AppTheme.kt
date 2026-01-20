package io.legado.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val appThemeMode by ThemeState.themeMode.collectAsState()
    val isPureBlack by ThemeState.isPureBlack.collectAsState()
    val hasImageBg by ThemeState.hasImageBg.collectAsState()
    val paletteStyle by ThemeState.paletteStyle.collectAsState()

    val colorScheme = ThemeManager.getColorScheme(
        mode = appThemeMode,
        darkTheme = darkTheme,
        isAmoled = isPureBlack,
        isImageBg = hasImageBg,
        paletteStyle = paletteStyle
    )

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        motionScheme = MotionScheme.expressive(),
        shapes = Shapes(),
        content = content
    )

}