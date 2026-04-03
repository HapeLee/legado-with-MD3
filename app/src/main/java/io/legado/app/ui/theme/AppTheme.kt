package io.legado.app.ui.theme

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import io.legado.app.ui.config.themeConfig.ThemeConfig
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
    val isPureBlack = ThemeConfig.isPureBlack
    val paletteStyleStr = ThemeConfig.paletteStyle
    val materialVersion = ThemeConfig.materialVersion
    val composeEngine = ThemeConfig.composeEngine
    val colorSchemeMode = ThemeResolver.resolveColorSchemeMode(ThemeConfig.themeMode)
    val paletteStyle =
        remember(paletteStyleStr) { ThemeResolver.resolvePaletteStyle(paletteStyleStr) }
    val seedColor = remember(ThemeConfig.cPrimary) {
        if (ThemeConfig.cPrimary != 0) Color(ThemeConfig.cPrimary) else Color(0xFF3482FF)
    }

    val colorScheme =
        remember(context, appThemeMode, darkTheme, isPureBlack, paletteStyleStr, materialVersion) {
            ThemeManager.getColorScheme(
                context = context,
                mode = appThemeMode,
                darkTheme = darkTheme,
                isAmoled = isPureBlack,
                paletteStyle = paletteStyleStr,
                materialVersion = materialVersion
            )
        }

    val themeColors = remember(
        colorScheme,
        darkTheme,
        seedColor,
        paletteStyle,
        colorSchemeMode,
        composeEngine
    ) {
        LegadoThemeMode(
            colorScheme = colorScheme,
            isDark = darkTheme,
            seedColor = seedColor,
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
            val controller = remember(colorSchemeMode, darkTheme) {
                ThemeController(colorSchemeMode = colorSchemeMode, isDark = darkTheme)
            }

            MiuixTheme(controller = controller) {
                val miuixStyles = MiuixTheme.textStyles
                val legadoTypography = remember(miuixStyles) {
                    LegadoTypography(
                        headlineLarge = miuixStyles.title1,
                        headlineLargeEmphasized = miuixStyles.title1.copy(fontWeight = FontWeight.Medium),
                        headlineMedium = miuixStyles.title2,
                        headlineMediumEmphasized = miuixStyles.title2.copy(fontWeight = FontWeight.Medium),
                        headlineSmall = miuixStyles.title3,
                        headlineSmallEmphasized = miuixStyles.title3.copy(fontWeight = FontWeight.Medium),


                        titleLarge = miuixStyles.headline1,
                        titleLargeEmphasized = miuixStyles.headline1.copy(fontWeight = FontWeight.Medium),
                        titleMedium = miuixStyles.headline2,
                        titleMediumEmphasized = miuixStyles.headline2.copy(fontWeight = FontWeight.Medium),
                        titleSmall = miuixStyles.subtitle,
                        titleSmallEmphasized = miuixStyles.subtitle.copy(fontWeight = FontWeight.Medium),


                        bodyLarge = miuixStyles.paragraph,
                        bodyLargeEmphasized = miuixStyles.paragraph.copy(fontWeight = FontWeight.Medium),
                        bodyMedium = miuixStyles.body1,
                        bodyMediumEmphasized = miuixStyles.body1.copy(fontWeight = FontWeight.Medium),
                        bodySmall = miuixStyles.body2,
                        bodySmallEmphasized = miuixStyles.body2.copy(fontWeight = FontWeight.Medium),

                        labelLarge = miuixStyles.button,
                        labelLargeEmphasized = miuixStyles.button.copy(fontWeight = FontWeight.Medium),
                        labelMedium = miuixStyles.footnote1,
                        labelMediumEmphasized = miuixStyles.footnote1.copy(fontWeight = FontWeight.Medium),
                        labelSmall = miuixStyles.footnote2,
                        labelSmallEmphasized = miuixStyles.footnote2.copy(fontWeight = FontWeight.Medium)
                    )
                }

                val miuixColorScheme = MiuixTheme.colorScheme

                val mappedColorScheme = remember(miuixColorScheme) {
                    LegadoColorScheme(
                        primary = miuixColorScheme.primary,
                        onPrimary = miuixColorScheme.onPrimary,
                        primaryContainer = miuixColorScheme.primaryContainer,
                        onPrimaryContainer = miuixColorScheme.onPrimaryContainer,
                        inversePrimary = miuixColorScheme.primaryVariant,

                        secondary = miuixColorScheme.primary.copy(alpha = 0.5f),
                        onSecondary = miuixColorScheme.onPrimary.copy(alpha = 0.5f),
                        secondaryContainer = miuixColorScheme.primaryContainer.copy(alpha = 0.5f),
                        onSecondaryContainer = miuixColorScheme.onPrimaryContainer.copy(alpha = 0.5f),

                        tertiary = miuixColorScheme.secondary,
                        onTertiary = miuixColorScheme.onSecondary,
                        tertiaryContainer = miuixColorScheme.secondaryContainer,
                        onTertiaryContainer = miuixColorScheme.onSecondaryContainer,

                        // ================= 4. 背景与表面 (Background & Surface) =================
                        background = miuixColorScheme.background,
                        onBackground = miuixColorScheme.onBackground,

                        surface = miuixColorScheme.surface,
                        onSurface = miuixColorScheme.onSurface,
                        surfaceVariant = miuixColorScheme.surfaceVariant,
                        // M3 的 onSurfaceVariant 通常是次级文字色。Miuix 的 onSurfaceSecondary 完美契合这个语义
                        onSurfaceVariant = miuixColorScheme.onSurfaceSecondary,

                        // M3 中用于给 Surface 叠加一层极淡主题色的属性，通常直接取 primary
                        surfaceTint = miuixColorScheme.primary,

                        // Inverse 系列通常用于深色模式下的反色提示（如 Snackbar）。
                        // 简单映射法：直接用现有的 onSurface 和 surface 交叉互换。
                        inverseSurface = miuixColorScheme.onSurface,
                        inverseOnSurface = miuixColorScheme.surface,

                        // ================= 5. 错误状态 (Error) =================
                        error = miuixColorScheme.error,
                        onError = miuixColorScheme.onError,
                        errorContainer = miuixColorScheme.errorContainer,
                        onErrorContainer = miuixColorScheme.onErrorContainer,

                        // ================= 6. 边框、分割线与遮罩 (Outline & Scrim) =================
                        outline = miuixColorScheme.outline,
                        // outlineVariant 在 M3 中常用于分割线。Miuix 刚好有 dividerLine
                        outlineVariant = miuixColorScheme.dividerLine,
                        // scrim 是 M3 的遮罩层（如弹窗背后的阴影）。Miuix 刚好有 windowDimming
                        scrim = miuixColorScheme.windowDimming,

                        surfaceBright = miuixColorScheme.surface, // Miuix 缺省，用 surface 兜底
                        surfaceDim = miuixColorScheme.background, // Miuix 缺省，用 background 兜底
                        surfaceContainer = miuixColorScheme.surfaceContainer,
                        surfaceContainerHigh = miuixColorScheme.surfaceContainerHigh,
                        surfaceContainerHighest = miuixColorScheme.surfaceContainerHighest,
                        surfaceContainerLow = miuixColorScheme.secondaryContainer,
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

                        cardContainer = miuixColorScheme.tertiaryContainer,
                        onCardContainer = miuixColorScheme.primary
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
            val Typography = Typography()
            MaterialExpressiveTheme(
                colorScheme = colorScheme,
                typography = Typography,
                motionScheme = MotionScheme.expressive(),
                shapes = Shapes()
            ) {
                val legadoTypography = remember(Typography) {
                    LegadoTypography(
                        headlineLarge = Typography.headlineLarge,
                        headlineLargeEmphasized = Typography.headlineLargeEmphasized,
                        headlineMedium = Typography.headlineMedium,
                        headlineMediumEmphasized = Typography.headlineMediumEmphasized,
                        headlineSmall = Typography.headlineSmall,
                        headlineSmallEmphasized = Typography.headlineSmallEmphasized,


                        titleLarge = Typography.titleLarge,
                        titleLargeEmphasized = Typography.titleLargeEmphasized,
                        titleMedium = Typography.titleMedium,
                        titleMediumEmphasized = Typography.titleMediumEmphasized,
                        titleSmall = Typography.titleSmall,
                        titleSmallEmphasized = Typography.titleSmallEmphasized,


                        bodyLarge = Typography.bodyLarge,
                        bodyLargeEmphasized = Typography.bodyLargeEmphasized,
                        bodyMedium = Typography.bodyMedium,
                        bodyMediumEmphasized = Typography.bodyMediumEmphasized,
                        bodySmall = Typography.bodySmall,
                        bodySmallEmphasized = Typography.bodySmallEmphasized,

                        labelLarge = Typography.labelLarge,
                        labelLargeEmphasized = Typography.labelLargeEmphasized,
                        labelMedium = Typography.labelMedium,
                        labelMediumEmphasized = Typography.labelMediumEmphasized,
                        labelSmall = Typography.labelSmall,
                        labelSmallEmphasized = Typography.labelSmallEmphasized
                    )
                }

                val semanticColors = remember(colorScheme) {
                    LegadoColorScheme(
                        primary = colorScheme.primary,
                        onPrimary = colorScheme.onPrimary,
                        primaryContainer = colorScheme.primaryContainer,
                        onPrimaryContainer = colorScheme.onPrimaryContainer,
                        inversePrimary = colorScheme.inversePrimary,
                        secondary = colorScheme.secondary,
                        onSecondary = colorScheme.onSecondary,
                        secondaryContainer = colorScheme.secondaryContainer,
                        onSecondaryContainer = colorScheme.onSecondaryContainer,
                        tertiary = colorScheme.tertiary,
                        onTertiary = colorScheme.onTertiary,
                        tertiaryContainer = colorScheme.tertiaryContainer,
                        onTertiaryContainer = colorScheme.onTertiaryContainer,
                        background = colorScheme.background,
                        onBackground = colorScheme.onBackground,
                        surface = colorScheme.surface,
                        onSurface = colorScheme.onSurface,
                        surfaceVariant = colorScheme.surfaceVariant,
                        onSurfaceVariant = colorScheme.onSurfaceVariant,
                        surfaceTint = colorScheme.surfaceTint,
                        inverseSurface = colorScheme.inverseSurface,
                        inverseOnSurface = colorScheme.inverseOnSurface,
                        error = colorScheme.error,
                        onError = colorScheme.onError,
                        errorContainer = colorScheme.errorContainer,
                        onErrorContainer = colorScheme.onErrorContainer,
                        outline = colorScheme.outline,
                        outlineVariant = colorScheme.outlineVariant,
                        scrim = colorScheme.scrim,
                        surfaceBright = colorScheme.surfaceBright,
                        surfaceDim = colorScheme.surfaceDim,
                        surfaceContainer = colorScheme.surfaceContainer,
                        surfaceContainerHigh = colorScheme.surfaceContainerHigh,
                        surfaceContainerHighest = colorScheme.surfaceContainerHighest,
                        surfaceContainerLow = colorScheme.surfaceContainerLow,
                        surfaceContainerLowest = colorScheme.surfaceContainerLowest,
                        primaryFixed = colorScheme.primaryFixed,
                        primaryFixedDim = colorScheme.primaryFixedDim,
                        onPrimaryFixed = colorScheme.onPrimaryFixed,
                        onPrimaryFixedVariant = colorScheme.onPrimaryFixedVariant,
                        secondaryFixed = colorScheme.secondaryFixed,
                        secondaryFixedDim = colorScheme.secondaryFixedDim,
                        onSecondaryFixed = colorScheme.onSecondaryFixed,
                        onSecondaryFixedVariant = colorScheme.onSecondaryFixedVariant,
                        tertiaryFixed = colorScheme.tertiaryFixed,
                        tertiaryFixedDim = colorScheme.tertiaryFixedDim,
                        onTertiaryFixed = colorScheme.onTertiaryFixed,
                        onTertiaryFixedVariant = colorScheme.onTertiaryFixedVariant,

                        cardContainer = colorScheme.primaryContainer.copy(alpha = 0.5f),
                        onCardContainer = colorScheme.primary
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
