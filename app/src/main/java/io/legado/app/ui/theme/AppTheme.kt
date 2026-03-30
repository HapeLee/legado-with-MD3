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

    val themeColors = remember(colorScheme, darkTheme, seedColor, paletteStyle, colorSchemeMode) {
        LegadoThemeColors(
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
        when {
            ThemeResolver.isMiuixEngine(themeColors.composeEngine) -> {
                val controller = remember(colorSchemeMode, darkTheme) {
                    ThemeController(
                        colorSchemeMode = colorSchemeMode,
                        isDark = darkTheme
                    )
                }

                MiuixTheme(
                    controller = controller
                ) {
                    val miuixTextStyles = MiuixTheme.textStyles
                    val mappedTypography = remember(miuixTextStyles) {
                        miuixStylesToM3Typography(miuixTextStyles)
                    }

                    CompositionLocalProvider(
                        LocalLegadoTypography provides mappedTypography
                    ) {
                        AppBackground(darkTheme = darkTheme) {
                            content()
                        }
                    }
                }
            }

            else -> {
                MaterialExpressiveTheme(
                    colorScheme = colorScheme,
                    typography = Typography(),
                    motionScheme = MotionScheme.expressive(),
                    shapes = Shapes()
                ) {
                    AppBackground(darkTheme = darkTheme) {
                        content()
                    }
                }
            }
        }
    }
}