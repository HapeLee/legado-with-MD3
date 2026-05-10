package io.legado.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontFamily
import android.graphics.Typeface
import android.net.Uri
import io.legado.app.ui.config.themeConfig.ThemeConfig
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import io.legado.app.ui.theme.CustomColorScheme
import io.legado.app.ui.theme.ThemeResolver

// 用户自定义的颜色集合
data class UserColorPalette(
    val primaryColor: Color,        // 用户定义的主题色
    val secondaryColor: Color,      // 用户定义的次要主题色
    val backgroundColor: Color,     // 用户定义的背景色
    val primaryFontColor: Color,    // 用户定义的主要字体色
    val secondaryFontColor: Color,  // 用户定义的次要字体色
    val labelContainerColor: Color  // 用户定义的标签容器色
)

// 将用户自定义颜色映射到 Material Theme 的 ColorScheme
fun generateColorScheme(userPalette: UserColorPalette, isDark: Boolean): androidx.compose.material3.ColorScheme {
    return if (isDark) {
        darkColorScheme(
            primary = userPalette.primaryColor,
            onPrimary = userPalette.primaryFontColor, // 关键映射：主要字体色 -> onPrimary
            primaryContainer = userPalette.labelContainerColor, // 关键映射：标签容器色 -> primaryContainer
            onPrimaryContainer = userPalette.primaryFontColor,

            secondary = userPalette.secondaryColor,
            onSecondary = userPalette.secondaryFontColor,
            secondaryContainer = userPalette.labelContainerColor,
            onSecondaryContainer = userPalette.secondaryFontColor,

            tertiary = userPalette.secondaryColor, // 可以将次要主题色也用作第三色
            onTertiary = userPalette.secondaryFontColor,

            background = userPalette.backgroundColor,
            surface = userPalette.backgroundColor,
            onBackground = userPalette.primaryFontColor,
            onSurface = userPalette.primaryFontColor, // 关键映射：主要字体色 -> onSurface
            surfaceVariant = userPalette.labelContainerColor,
            onSurfaceVariant = userPalette.secondaryFontColor, // 关键映射：次要字体色 -> onSurfaceVariant

            // 其他颜色
            error = Color(0xFFB3261E),
            onError = Color(0xFFFFFFFF),
            errorContainer = Color(0xFFF9DEDC),
            onErrorContainer = Color(0xFF410E0B),
            outline = Color(0xFF79747E),
            outlineVariant = Color(0xFFC4C7C5),
            scrim = Color(0xFF000000),

            // 表面颜色变体
            surfaceBright = userPalette.backgroundColor,
            surfaceDim = userPalette.backgroundColor,
            surfaceContainer = userPalette.backgroundColor,
            surfaceContainerHigh = userPalette.backgroundColor,
            surfaceContainerHighest = userPalette.backgroundColor,
            surfaceContainerLow = userPalette.labelContainerColor,
            surfaceContainerLowest = userPalette.backgroundColor,

            // 固定颜色
            primaryFixed = userPalette.primaryColor,
            primaryFixedDim = userPalette.primaryColor.copy(alpha = 0.8f),
            onPrimaryFixed = userPalette.primaryFontColor,
            onPrimaryFixedVariant = userPalette.primaryFontColor,
            secondaryFixed = userPalette.secondaryColor,
            secondaryFixedDim = userPalette.secondaryColor.copy(alpha = 0.8f),
            onSecondaryFixed = userPalette.secondaryFontColor,
            onSecondaryFixedVariant = userPalette.secondaryFontColor,
            tertiaryFixed = userPalette.secondaryColor,
            tertiaryFixedDim = userPalette.secondaryColor.copy(alpha = 0.8f),
            onTertiaryFixed = userPalette.secondaryFontColor,
            onTertiaryFixedVariant = userPalette.secondaryFontColor
        )
    } else {
        lightColorScheme(
            primary = userPalette.primaryColor,
            onPrimary = userPalette.primaryFontColor,
            primaryContainer = userPalette.labelContainerColor,
            onPrimaryContainer = userPalette.primaryFontColor,

            secondary = userPalette.secondaryColor,
            onSecondary = userPalette.secondaryFontColor,
            secondaryContainer = userPalette.labelContainerColor,
            onSecondaryContainer = userPalette.secondaryFontColor,

            tertiary = userPalette.secondaryColor,
            onTertiary = userPalette.secondaryFontColor,

            background = userPalette.backgroundColor,
            surface = userPalette.backgroundColor,
            onBackground = userPalette.primaryFontColor,
            onSurface = userPalette.primaryFontColor,
            surfaceVariant = userPalette.labelContainerColor,
            onSurfaceVariant = userPalette.secondaryFontColor,

            // 其他颜色
            error = Color(0xFFB3261E),
            onError = Color(0xFFFFFFFF),
            errorContainer = Color(0xFFF9DEDC),
            onErrorContainer = Color(0xFF410E0B),
            outline = Color(0xFF79747E),
            outlineVariant = Color(0xFFC4C7C5),
            scrim = Color(0xFF000000),

            // 表面颜色变体
            surfaceBright = userPalette.backgroundColor,
            surfaceDim = userPalette.backgroundColor,
            surfaceContainer = userPalette.backgroundColor,
            surfaceContainerHigh = userPalette.backgroundColor,
            surfaceContainerHighest = userPalette.backgroundColor,
            surfaceContainerLow = userPalette.labelContainerColor,
            surfaceContainerLowest = userPalette.backgroundColor,

            // 固定颜色
            primaryFixed = userPalette.primaryColor,
            primaryFixedDim = userPalette.primaryColor.copy(alpha = 0.8f),
            onPrimaryFixed = userPalette.primaryFontColor,
            onPrimaryFixedVariant = userPalette.primaryFontColor,
            secondaryFixed = userPalette.secondaryColor,
            secondaryFixedDim = userPalette.secondaryColor.copy(alpha = 0.8f),
            onSecondaryFixed = userPalette.secondaryFontColor,
            onSecondaryFixedVariant = userPalette.secondaryFontColor,
            tertiaryFixed = userPalette.secondaryColor,
            tertiaryFixedDim = userPalette.secondaryColor.copy(alpha = 0.8f),
            onTertiaryFixed = userPalette.secondaryFontColor,
            onTertiaryFixedVariant = userPalette.secondaryFontColor
        )
    }
}

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
    val appFontPath = ThemeConfig.appFontPath
    
    val enableDeepPersonalization = ThemeConfig.enableDeepPersonalization
    val themeColor = ThemeConfig.themeColor
    val secondaryThemeColor = ThemeConfig.secondaryThemeColor
    val primaryTextColor = ThemeConfig.primaryTextColor
    val secondaryTextColor = ThemeConfig.secondaryTextColor
    val themeBackgroundColor = ThemeConfig.themeBackgroundColor
    val customLabelContainerColor = ThemeConfig.labelContainerColor
    
    val colorSchemeMode = ThemeResolver.resolveColorSchemeMode(themeModeValue)
    val miuixColorSchemeMode = remember(themeModeValue, useMiuixMonet) {
        ThemeResolver.resolveMiuixColorSchemeMode(themeModeValue, useMiuixMonet)
    }

    // 加载自定义字体
    val customFontFamily = remember(ThemeConfig.appFontPath, context) {
        if (!ThemeConfig.appFontPath.isNullOrEmpty()) {
            try {
                val uri = Uri.parse(ThemeConfig.appFontPath)
                val typeface: Typeface? = if (uri.scheme == "content") {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use {
                        Typeface.Builder(it.fileDescriptor).build()
                    }
                } else {
                    Typeface.createFromFile(uri.path)
                }
                typeface?.let {
                    FontFamily(it)
                }
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    val paletteStyle = remember(paletteStyleValue) {
        ThemeResolver.resolvePaletteStyle(paletteStyleValue)
    }

    val colorScheme =
        remember(
            context,
            appThemeMode,
            darkTheme,
            isPureBlack,
            customPrimary,
            customNightPrimary,
            enableDeepPersonalization,
            themeColor,
            secondaryThemeColor,
            primaryTextColor,
            secondaryTextColor,
            themeBackgroundColor,
            customLabelContainerColor,
            paletteStyleValue,
            materialVersion
        ) {
            if (enableDeepPersonalization &&
                (themeColor != 0 ||
                 secondaryThemeColor != 0 ||
                 primaryTextColor != 0 ||
                 secondaryTextColor != 0 ||
                 themeBackgroundColor != 0 ||
                 customLabelContainerColor != 0)) {
                val userPalette = UserColorPalette(
                    primaryColor = if (themeColor != 0) Color(themeColor) else Color(0xFF6750A4),
                    secondaryColor = if (secondaryThemeColor != 0) Color(secondaryThemeColor) else Color(0xFF625B71),
                    backgroundColor = if (themeBackgroundColor != 0) Color(themeBackgroundColor) else Color(0xFFFEF7FF),
                    primaryFontColor = if (primaryTextColor != 0) Color(primaryTextColor) else Color(0xFF1C1B1F),
                    secondaryFontColor = if (secondaryTextColor != 0) Color(secondaryTextColor) else Color(0xFF49454F),
                    labelContainerColor = if (customLabelContainerColor != 0) Color(customLabelContainerColor) else Color(0xFFF7F2FA)
                )
                generateColorScheme(userPalette, darkTheme)
            } else {
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
        }

    val themeSeedColor = remember(appThemeMode, colorScheme.primary) {
        if (appThemeMode == AppThemeMode.Custom) {
            val seed = if (darkTheme) customNightPrimary else customPrimary
            if (seed != 0) Color(seed) else colorScheme.primary
        } else {
            colorScheme.primary
        }
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
                // colorResource 只能在 @Composable 上下文中调用
                // 这里使用一个固定颜色作为替代
                Color(0xFF6750A4)
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
                val legadoTypography = remember<LegadoTypography>(miuixStyles, customFontFamily) {
                    val typography = miuixStylesToM3Typography(miuixStyles)
                    val baseLegadoTypography = typography.toLegadoTypography()
                    if (customFontFamily != null) {
                        baseLegadoTypography.copy(
                            headlineLarge = baseLegadoTypography.headlineLarge.copy(fontFamily = customFontFamily),
                            headlineLargeEmphasized = baseLegadoTypography.headlineLargeEmphasized.copy(fontFamily = customFontFamily),
                            headlineMedium = baseLegadoTypography.headlineMedium.copy(fontFamily = customFontFamily),
                            headlineMediumEmphasized = baseLegadoTypography.headlineMediumEmphasized.copy(fontFamily = customFontFamily),
                            headlineSmall = baseLegadoTypography.headlineSmall.copy(fontFamily = customFontFamily),
                            headlineSmallEmphasized = baseLegadoTypography.headlineSmallEmphasized.copy(fontFamily = customFontFamily),
                            titleLarge = baseLegadoTypography.titleLarge.copy(fontFamily = customFontFamily),
                            titleLargeEmphasized = baseLegadoTypography.titleLargeEmphasized.copy(fontFamily = customFontFamily),
                            titleMedium = baseLegadoTypography.titleMedium.copy(fontFamily = customFontFamily),
                            titleMediumEmphasized = baseLegadoTypography.titleMediumEmphasized.copy(fontFamily = customFontFamily),
                            titleSmall = baseLegadoTypography.titleSmall.copy(fontFamily = customFontFamily),
                            titleSmallEmphasized = baseLegadoTypography.titleSmallEmphasized.copy(fontFamily = customFontFamily),
                            bodyLarge = baseLegadoTypography.bodyLarge.copy(fontFamily = customFontFamily),
                            bodyLargeEmphasized = baseLegadoTypography.bodyLargeEmphasized.copy(fontFamily = customFontFamily),
                            bodyMedium = baseLegadoTypography.bodyMedium.copy(fontFamily = customFontFamily),
                            bodyMediumEmphasized = baseLegadoTypography.bodyMediumEmphasized.copy(fontFamily = customFontFamily),
                            bodySmall = baseLegadoTypography.bodySmall.copy(fontFamily = customFontFamily),
                            bodySmallEmphasized = baseLegadoTypography.bodySmallEmphasized.copy(fontFamily = customFontFamily),
                            labelLarge = baseLegadoTypography.labelLarge.copy(fontFamily = customFontFamily),
                            labelLargeEmphasized = baseLegadoTypography.labelLargeEmphasized.copy(fontFamily = customFontFamily),
                            labelMedium = baseLegadoTypography.labelMedium.copy(fontFamily = customFontFamily),
                            labelMediumEmphasized = baseLegadoTypography.labelMediumEmphasized.copy(fontFamily = customFontFamily),
                            labelSmall = baseLegadoTypography.labelSmall.copy(fontFamily = customFontFamily),
                            labelSmallEmphasized = baseLegadoTypography.labelSmallEmphasized.copy(fontFamily = customFontFamily)
                        )
                    } else {
                        baseLegadoTypography
                    }
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
                    val customTopBarColor = miuixColorScheme.surface
                    val customNavBarColor = miuixColorScheme.surface

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
                        surfaceContainerLow = miuixColorScheme.secondaryContainer.copy(alpha = 0.32f),
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

                        cardContainer = miuixColorScheme.disabledPrimary,
                        onCardContainer = miuixColorScheme.primary,
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
        } else {
            val materialTypography = remember(customFontFamily) {
                if (customFontFamily != null) {
                    Typography(
                        headlineLarge = Typography().headlineLarge.copy(fontFamily = customFontFamily),
                        headlineMedium = Typography().headlineMedium.copy(fontFamily = customFontFamily),
                        headlineSmall = Typography().headlineSmall.copy(fontFamily = customFontFamily),
                        titleLarge = Typography().titleLarge.copy(fontFamily = customFontFamily),
                        titleMedium = Typography().titleMedium.copy(fontFamily = customFontFamily),
                        titleSmall = Typography().titleSmall.copy(fontFamily = customFontFamily),
                        bodyLarge = Typography().bodyLarge.copy(fontFamily = customFontFamily),
                        bodyMedium = Typography().bodyMedium.copy(fontFamily = customFontFamily),
                        bodySmall = Typography().bodySmall.copy(fontFamily = customFontFamily),
                        labelLarge = Typography().labelLarge.copy(fontFamily = customFontFamily),
                        labelMedium = Typography().labelMedium.copy(fontFamily = customFontFamily),
                        labelSmall = Typography().labelSmall.copy(fontFamily = customFontFamily)
                    )
                } else {
                    Typography()
                }
            }
            MaterialExpressiveTheme(
                colorScheme = colorScheme,
                typography = materialTypography,
                motionScheme = MotionScheme.expressive(),
                shapes = Shapes()
            ) {
                val legadoTypography = remember(materialTypography, customFontFamily) {
                    val baseLegadoTypography = materialTypography.toLegadoTypography()
                    if (customFontFamily != null) {
                        baseLegadoTypography.copy(
                            headlineLarge = baseLegadoTypography.headlineLarge.copy(fontFamily = customFontFamily),
                            headlineLargeEmphasized = baseLegadoTypography.headlineLargeEmphasized.copy(fontFamily = customFontFamily),
                            headlineMedium = baseLegadoTypography.headlineMedium.copy(fontFamily = customFontFamily),
                            headlineMediumEmphasized = baseLegadoTypography.headlineMediumEmphasized.copy(fontFamily = customFontFamily),
                            headlineSmall = baseLegadoTypography.headlineSmall.copy(fontFamily = customFontFamily),
                            headlineSmallEmphasized = baseLegadoTypography.headlineSmallEmphasized.copy(fontFamily = customFontFamily),
                            titleLarge = baseLegadoTypography.titleLarge.copy(fontFamily = customFontFamily),
                            titleLargeEmphasized = baseLegadoTypography.titleLargeEmphasized.copy(fontFamily = customFontFamily),
                            titleMedium = baseLegadoTypography.titleMedium.copy(fontFamily = customFontFamily),
                            titleMediumEmphasized = baseLegadoTypography.titleMediumEmphasized.copy(fontFamily = customFontFamily),
                            titleSmall = baseLegadoTypography.titleSmall.copy(fontFamily = customFontFamily),
                            titleSmallEmphasized = baseLegadoTypography.titleSmallEmphasized.copy(fontFamily = customFontFamily),
                            bodyLarge = baseLegadoTypography.bodyLarge.copy(fontFamily = customFontFamily),
                            bodyLargeEmphasized = baseLegadoTypography.bodyLargeEmphasized.copy(fontFamily = customFontFamily),
                            bodyMedium = baseLegadoTypography.bodyMedium.copy(fontFamily = customFontFamily),
                            bodyMediumEmphasized = baseLegadoTypography.bodyMediumEmphasized.copy(fontFamily = customFontFamily),
                            bodySmall = baseLegadoTypography.bodySmall.copy(fontFamily = customFontFamily),
                            bodySmallEmphasized = baseLegadoTypography.bodySmallEmphasized.copy(fontFamily = customFontFamily),
                            labelLarge = baseLegadoTypography.labelLarge.copy(fontFamily = customFontFamily),
                            labelLargeEmphasized = baseLegadoTypography.labelLargeEmphasized.copy(fontFamily = customFontFamily),
                            labelMedium = baseLegadoTypography.labelMedium.copy(fontFamily = customFontFamily),
                            labelMediumEmphasized = baseLegadoTypography.labelMediumEmphasized.copy(fontFamily = customFontFamily),
                            labelSmall = baseLegadoTypography.labelSmall.copy(fontFamily = customFontFamily),
                            labelSmallEmphasized = baseLegadoTypography.labelSmallEmphasized.copy(fontFamily = customFontFamily)
                        )
                    } else {
                        baseLegadoTypography
                    }
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
                    val customTopBarColor = colorScheme.surface
                    val customNavBarColor = colorScheme.surface

                    colorScheme.toLegadoColorScheme(
                        customBgColor = customBgColor,
                        customFontColor = customFontColor,
                        customTopBarColor = customTopBarColor,
                        customNavBarColor = customNavBarColor
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
    }
}
