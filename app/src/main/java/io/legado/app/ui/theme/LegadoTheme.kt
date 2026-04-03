package io.legado.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.materialkolor.PaletteStyle
import dev.chrisbanes.haze.HazeState
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

data class LegadoThemeMode(
    val colorScheme: ColorScheme,
    val isDark: Boolean,
    val seedColor: Color,
    val paletteStyle: PaletteStyle,
    val themeMode: ColorSchemeMode,
    val useDynamicColor: Boolean,
    val composeEngine: String,
)

data class LegadoColorScheme(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val inversePrimary: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val surfaceTint: Color,
    val inverseSurface: Color,
    val inverseOnSurface: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val outline: Color,
    val outlineVariant: Color,
    val scrim: Color,
    val surfaceBright: Color,
    val surfaceDim: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainerLowest: Color,
    val primaryFixed: Color,
    val primaryFixedDim: Color,
    val onPrimaryFixed: Color,
    val onPrimaryFixedVariant: Color,
    val secondaryFixed: Color,
    val secondaryFixedDim: Color,
    val onSecondaryFixed: Color,
    val onSecondaryFixedVariant: Color,
    val tertiaryFixed: Color,
    val tertiaryFixedDim: Color,
    val onTertiaryFixed: Color,
    val onTertiaryFixedVariant: Color,

    val cardContainer: Color,
    val onCardContainer: Color
)

data class LegadoTypography(
    val headlineLarge: TextStyle,
    val headlineLargeEmphasized: TextStyle,
    val headlineMedium: TextStyle,
    val headlineMediumEmphasized: TextStyle,
    val headlineSmall: TextStyle,
    val headlineSmallEmphasized: TextStyle,

    val titleLarge: TextStyle,
    val titleLargeEmphasized: TextStyle,
    val titleMedium: TextStyle,
    val titleMediumEmphasized: TextStyle,
    val titleSmall: TextStyle,
    val titleSmallEmphasized: TextStyle,


    val bodyLarge: TextStyle,
    val bodyLargeEmphasized: TextStyle,
    val bodyMedium: TextStyle,
    val bodyMediumEmphasized: TextStyle,
    val bodySmall: TextStyle,
    val bodySmallEmphasized: TextStyle,

    val labelLarge: TextStyle,
    val labelLargeEmphasized: TextStyle,
    val labelMedium: TextStyle,
    val labelMediumEmphasized: TextStyle,
    val labelSmall: TextStyle,
    val labelSmallEmphasized: TextStyle,

    )

val LocalLegadoColorScheme = staticCompositionLocalOf<LegadoColorScheme> {
    error("No ColorScheme provided")
}

val LocalLegadoTypography = staticCompositionLocalOf<LegadoTypography> {
    error("No Typography provided")
}

val LocalLegadoThemeColors = staticCompositionLocalOf {
    LegadoThemeMode(
        colorScheme = lightColorScheme(),
        isDark = false,
        seedColor = Color.Unspecified,
        paletteStyle = PaletteStyle.TonalSpot,
        themeMode = ColorSchemeMode.System,
        useDynamicColor = true,
        composeEngine = "material"
    )
}

val LocalHazeState = compositionLocalOf<HazeState?> { null }

object LegadoTheme {

    val colorScheme: LegadoColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalLegadoColorScheme.current

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

    val typography: LegadoTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalLegadoTypography.current

}
