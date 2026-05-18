package io.legado.app.ui.theme

import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import io.legado.app.ui.config.themeConfig.ThemeConfig
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.TextStyles

@Composable
fun rememberCustomFont(fontPath: String?): FontFamily? {
    val context = LocalContext.current
    return remember(fontPath, context) {
        if (!fontPath.isNullOrEmpty()) {
            try {
                val uri = Uri.parse(fontPath)
                val typeface: Typeface? = if (uri.scheme == "content") {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use {
                        Typeface.Builder(it.fileDescriptor).build()
                    }
                } else {
                    Typeface.createFromFile(uri.path)
                }
                typeface?.let { FontFamily(it) }
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MiuixThemeWrapper(
    themeColors: LegadoThemeMode,
    customFontFamily: FontFamily?,
    content: @Composable () -> Unit
) {
    val themeModeValue = ThemeConfig.themeMode
    val useMiuixMonet = ThemeConfig.useMiuixMonet
    val paletteStyleValue = ThemeConfig.paletteStyle
    val materialVersion = ThemeConfig.materialVersion
    val darkTheme = themeColors.isDark
    
    val miuixColorSchemeMode = remember(themeModeValue, useMiuixMonet) {
        ThemeResolver.resolveMiuixColorSchemeMode(themeModeValue, useMiuixMonet)
    }
    val miuixPaletteStyle = remember(paletteStyleValue) {
        ThemeResolver.resolveMiuixPaletteStyle(paletteStyleValue)
    }
    val miuixColorSpec = remember(materialVersion, paletteStyleValue) {
        ThemeResolver.resolveMiuixColorSpec(materialVersion, paletteStyleValue)
    }

    val keyColor = if (useMiuixMonet &&
        themeColors.useDynamicColor &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    ) {
        Color(0xFF6750A4) // 默认颜色，因为 colorResource 只能在 Composable 中
    } else {
        themeColors.seedColor
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
        val legadoTypography = remember(miuixStyles, customFontFamily) {
            miuixStylesToM3Typography(miuixStyles)
                .toLegadoTypography()
                .withFont(customFontFamily)
        }

        val miuixColorScheme = MiuixTheme.colorScheme
        val mappedColorScheme = remember(miuixColorScheme) {
            val customBgColor = if (ThemeConfig.enableDeepPersonalization && ThemeConfig.themeBackgroundColor != 0) {
                Color(ThemeConfig.themeBackgroundColor)
            } else {
                miuixColorScheme.background
            }
            val customFontColor = if (ThemeConfig.enableDeepPersonalization && ThemeConfig.primaryTextColor != 0) {
                Color(ThemeConfig.primaryTextColor)
            } else {
                miuixColorScheme.onSurface
            }

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

                background = customBgColor,
                onBackground = customFontColor,

                surface = miuixColorScheme.surface,
                onSurface = customFontColor,
                surfaceVariant = miuixColorScheme.surfaceVariant,
                onSurfaceVariant = customFontColor,
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
                surfaceContainerLow = miuixColorScheme.surfaceContainer,
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

                cardContainer = miuixColorScheme.surfaceContainer,
                onCardContainer = miuixColorScheme.onSurface,
                onSheetContent = miuixColorScheme.surface.copy(alpha = 0.5f)
            )
        }

        CompositionLocalProvider(
            LocalLegadoTypography provides legadoTypography,
            LocalLegadoColorScheme provides mappedColorScheme
        ) {
            AppBackground(darkTheme = darkTheme) { content() }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MaterialThemeWrapper(
    themeColors: LegadoThemeMode,
    customFontFamily: FontFamily?,
    content: @Composable () -> Unit
) {
    val darkTheme = themeColors.isDark
    val colorScheme = themeColors.colorScheme
    
    val materialTypography = remember(customFontFamily) {
        val base = Typography()
        if (customFontFamily != null) {
            base.copy(
                headlineLarge = base.headlineLarge.copy(fontFamily = customFontFamily),
                headlineMedium = base.headlineMedium.copy(fontFamily = customFontFamily),
                headlineSmall = base.headlineSmall.copy(fontFamily = customFontFamily),
                titleLarge = base.titleLarge.copy(fontFamily = customFontFamily),
                titleMedium = base.titleMedium.copy(fontFamily = customFontFamily),
                titleSmall = base.titleSmall.copy(fontFamily = customFontFamily),
                bodyLarge = base.bodyLarge.copy(fontFamily = customFontFamily),
                bodyMedium = base.bodyMedium.copy(fontFamily = customFontFamily),
                bodySmall = base.bodySmall.copy(fontFamily = customFontFamily),
                labelLarge = base.labelLarge.copy(fontFamily = customFontFamily),
                labelMedium = base.labelMedium.copy(fontFamily = customFontFamily),
                labelSmall = base.labelSmall.copy(fontFamily = customFontFamily)
            )
        } else {
            base
        }
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = materialTypography,
        motionScheme = MotionScheme.expressive(),
        shapes = Shapes()
    ) {
        val legadoTypography = remember(materialTypography, customFontFamily) {
            materialTypography.toLegadoTypography().withFont(customFontFamily)
        }
        val semanticColors = remember(colorScheme) {
            val customBgColor = if (ThemeConfig.enableDeepPersonalization && ThemeConfig.themeBackgroundColor != 0) {
                Color(ThemeConfig.themeBackgroundColor)
            } else {
                colorScheme.background
            }
            val customFontColor = if (ThemeConfig.enableDeepPersonalization && ThemeConfig.primaryTextColor != 0) {
                Color(ThemeConfig.primaryTextColor)
            } else {
                colorScheme.onSurface
            }

            colorScheme.toLegadoColorScheme(
                customBgColor = customBgColor,
                customFontColor = customFontColor,
                customTopBarColor = colorScheme.surface,
                customNavBarColor = colorScheme.surface
            )
        }

        CompositionLocalProvider(
            LocalLegadoTypography provides legadoTypography,
            LocalLegadoColorScheme provides semanticColors
        ) {
            AppBackground(darkTheme = darkTheme) { content() }
        }
    }
}
