package io.legado.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import io.legado.app.ui.config.themeConfig.ThemeConfig
import io.legado.app.ui.theme.LegadoTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val appThemeMode = ThemeResolver.resolveThemeMode(ThemeConfig.appTheme)
    val themeModeValue = ThemeConfig.themeMode
    val isPureBlack = ThemeConfig.isPureBlack
    val paletteStyleValue = ThemeConfig.paletteStyle
    val materialVersion = ThemeConfig.materialVersion
    val composeEngine = ThemeConfig.composeEngine
    val useMiuixMonet = ThemeConfig.useMiuixMonet
    val customPrimary = ThemeConfig.cPrimary
    val customNightPrimary = ThemeConfig.cNPrimary
    val colorSchemeMode = ThemeResolver.resolveColorSchemeMode(themeModeValue)
    val miuixColorSchemeMode = remember(themeModeValue, useMiuixMonet) {
        ThemeResolver.resolveMiuixColorSchemeMode(themeModeValue, useMiuixMonet)
    }
    val paletteStyle =
        remember(paletteStyleValue) { ThemeResolver.resolvePaletteStyle(paletteStyleValue) }

    val colorScheme =
        remember(
            context,
            appThemeMode,
            darkTheme,
            isPureBlack,
            customPrimary,
            customNightPrimary,
            paletteStyleValue,
            materialVersion
        ) {
            val customSeedColor = if (darkTheme) customNightPrimary else customPrimary
            ThemeEngine.getColorScheme(
                context = context,
                mode = appThemeMode,
                darkTheme = darkTheme,
                isAmoled = isPureBlack,
                paletteStyle = paletteStyleValue,
                materialVersion = materialVersion,
                customSeedColor = customSeedColor
            )
        }

    val customSeedColor = remember(
        darkTheme,
        customPrimary,
        customNightPrimary,
        colorScheme.primary
    ) {
        val seed = if (darkTheme) customNightPrimary else customPrimary
        if (seed != 0) Color(seed) else colorScheme.primary
    }
    val themeSeedColor = remember(appThemeMode, customSeedColor, colorScheme.primary) {
        if (appThemeMode == AppThemeMode.Custom) customSeedColor else colorScheme.primary
    }
    val miuixPaletteStyle = remember(paletteStyleValue) {
        ThemeResolver.resolveMiuixPaletteStyle(paletteStyleValue)
    }
    val miuixColorSpec = remember(materialVersion, paletteStyleValue) {
        ThemeResolver.resolveMiuixColorSpec(materialVersion, paletteStyleValue)
    }

    val themeColors = remember(
        colorScheme,
        darkTheme,
        themeSeedColor,
        paletteStyle,
        colorSchemeMode,
        composeEngine
    ) {
        LegadoThemeMode(
            colorScheme = colorScheme,
            isDark = darkTheme,
            seedColor = themeSeedColor,
            paletteStyle = paletteStyle,
            themeMode = colorSchemeMode,
            useDynamicColor = appThemeMode == AppThemeMode.Dynamic,
            composeEngine = composeEngine
        )
    }

    CompositionLocalProvider(
        LocalLegadoThemeColors provides themeColors
    ) {
        if (ThemeResolver.isMiuixEngine(themeColors.composeEngine)) {
            val keyColor = if (useMiuixMonet &&
                themeColors.useDynamicColor &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            ) {
                colorResource(id = android.R.color.system_accent1_500)
            } else {
                themeSeedColor
            }

            val controller = remember(
                miuixColorSchemeMode,
                useMiuixMonet,
                keyColor,
                miuixPaletteStyle,
                miuixColorSpec,
                darkTheme
            ) {
                if (useMiuixMonet) {
                    ThemeController(
                        colorSchemeMode = miuixColorSchemeMode,
                        keyColor = keyColor,
                        paletteStyle = miuixPaletteStyle,
                        colorSpec = miuixColorSpec,
                        isDark = darkTheme
                    )
                } else {
                    ThemeController(
                        colorSchemeMode = miuixColorSchemeMode,
                        isDark = darkTheme
                    )
                }
            }

            MiuixTheme(controller = controller) {
                val miuixStyles = MiuixTheme.textStyles
                val legadoTypography = remember(miuixStyles) {
                    miuixStylesToM3Typography(miuixStyles).toLegadoTypography()
                }

                val miuixColorScheme = MiuixTheme.colorScheme

                val mappedColorScheme = remember(miuixColorScheme) {
                    LegadoColorScheme(
                        primary = miuixColorScheme.primary,
                        onPrimary = miuixColorScheme.onPrimary,
                        primaryContainer = miuixColorScheme.primaryContainer,
                        onPrimaryContainer = miuixColorScheme.onPrimaryContainer,
                        inversePrimary = miuixColorScheme.primaryVariant,

                        secondary = miuixColorScheme.secondary,
                        onSecondary = miuixColorScheme.onSecondary,
                        secondaryContainer = miuixColorScheme.secondaryContainer,
                        onSecondaryContainer = miuixColorScheme.onSecondaryContainer,

                        tertiary = miuixColorScheme.primary,
                        onTertiary = miuixColorScheme.onPrimary,
                        tertiaryContainer = miuixColorScheme.primaryContainer,
                        onTertiaryContainer = miuixColorScheme.primaryVariant,

                        background = miuixColorScheme.background,
                        onBackground = miuixColorScheme.onBackground,

                        surface = miuixColorScheme.surface,
                        onSurface = miuixColorScheme.onSurface,
                        surfaceVariant = miuixColorScheme.surfaceVariant,
                        onSurfaceVariant = miuixColorScheme.onSurfaceSecondary,
                        surfaceTint = miuixColorScheme.primary,
                        inverseSurface = miuixColorScheme.onSurface,
                        inverseOnSurface = miuixColorScheme.surface,

                        error = miuixColorScheme.error,
                        onError = miuixColorScheme.onError,
                        errorContainer = miuixColorScheme.errorContainer,
                        onErrorContainer = miuixColorScheme.onErrorContainer,

                        outline = miuixColorScheme.outline,
                        outlineVariant = miuixColorScheme.dividerLine,
                        scrim = miuixColorScheme.windowDimming,

                        surfaceBright = miuixColorScheme.surface,
                        surfaceDim = miuixColorScheme.background,
                        surfaceContainer = miuixColorScheme.surfaceContainer,
                        surfaceContainerHigh = miuixColorScheme.surfaceContainerHigh,
                        surfaceContainerHighest = miuixColorScheme.surfaceContainerHighest,
                        surfaceContainerLow = miuixColorScheme.secondaryContainer.copy(alpha = 0.32f)
                            .compositeOver(miuixColorScheme.surface),
                        surfaceContainerLowest = miuixColorScheme.background,

                        primaryFixed = miuixColorScheme.primaryContainer,
                        primaryFixedDim = miuixColorScheme.primary,
                        onPrimaryFixed = miuixColorScheme.onPrimaryContainer,
                        onPrimaryFixedVariant = miuixColorScheme.onPrimary,
                        secondaryFixed = miuixColorScheme.secondaryContainer,
                        secondaryFixedDim = miuixColorScheme.secondary,
                        onSecondaryFixed = miuixColorScheme.onSecondaryContainer,
                        onSecondaryFixedVariant = miuixColorScheme.onSecondary,
                        tertiaryFixed = miuixColorScheme.tertiaryContainer,
                        tertiaryFixedDim = miuixColorScheme.tertiaryContainerVariant,
                        onTertiaryFixed = miuixColorScheme.onTertiaryContainer,
                        onTertiaryFixedVariant = miuixColorScheme.onTertiaryContainer,

                        cardContainer = miuixColorScheme.primaryContainer.copy(alpha = 0.32f)
                            .compositeOver(miuixColorScheme.surfaceContainer),
                        onCardContainer = miuixColorScheme.primary,
                        onSheetContent = miuixColorScheme.surface.copy(alpha = 0.5f),
                    )
                }

                CompositionLocalProvider(
                    LocalLegadoTypography provides legadoTypography,
                    LocalLegadoColorScheme provides mappedColorScheme
                ) {
                    AppBackground(darkTheme = darkTheme) { content() }
                }
            }
        } else {
            val materialTypography = remember { Typography() }
            MaterialExpressiveTheme(
                colorScheme = colorScheme,
                typography = materialTypography,
                motionScheme = MotionScheme.expressive(),
                shapes = Shapes()
            ) {
                val legadoTypography = remember(materialTypography) {
                    materialTypography.toLegadoTypography()
                }
                val semanticColors = remember(colorScheme) { colorScheme.toLegadoColorScheme() }

                CompositionLocalProvider(
                    LocalLegadoTypography provides legadoTypography,
                    LocalLegadoColorScheme provides semanticColors
                ) {
                    AppBackground(darkTheme = darkTheme) { content() }
                }
            }
        }
    }
}
