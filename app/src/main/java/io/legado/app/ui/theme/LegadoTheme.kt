package io.legado.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import dev.chrisbanes.haze.HazeState
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

data class LegadoThemeColors(
    val colorScheme: ColorScheme,
    val isDark: Boolean,
    val seedColor: Color,
    val paletteStyle: PaletteStyle,
    val themeMode: ColorSchemeMode,
    val useDynamicColor: Boolean,
    val composeEngine: String,
)

val LocalLegadoThemeColors = staticCompositionLocalOf {
    LegadoThemeColors(
        colorScheme = lightColorScheme(),
        isDark = false,
        seedColor = Color.Unspecified,
        paletteStyle = PaletteStyle.TonalSpot,
        themeMode = ColorSchemeMode.System,
        useDynamicColor = true,
        composeEngine = "m3"
    )
}

val LocalLegadoTypography = staticCompositionLocalOf {
    Typography()
}

val LocalHazeState = compositionLocalOf<HazeState?> { null }

object LegadoTheme {

    val colorScheme: ColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalLegadoThemeColors.current.colorScheme

    val isDark: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalLegadoThemeColors.current.isDark

    val seedColor: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalLegadoThemeColors.current.seedColor

    val paletteStyle: PaletteStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalLegadoThemeColors.current.paletteStyle

    val themeMode: ColorSchemeMode
        @Composable
        @ReadOnlyComposable
        get() = LocalLegadoThemeColors.current.themeMode

    val composeEngine: String
        @Composable
        @ReadOnlyComposable
        get() = LocalLegadoThemeColors.current.composeEngine

    val useDynamicColor: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalLegadoThemeColors.current.useDynamicColor

    val typography: Typography
        @Composable
        @ReadOnlyComposable
        get() = LocalLegadoTypography.current

}
