package io.legado.app.ui.theme

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import io.legado.app.domain.model.settings.customColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.defaultTextStyles

@Composable
fun rememberCustomFont(fontPath: String?): FontFamily? {
    val context = LocalContext.current.applicationContext
    val cachedFont = remember(fontPath) {
        fontPath?.let { synchronized(customFontCache) { customFontCache.get(it) } }
    }
    return produceState(cachedFont, fontPath, context) {
        if (value == null) {
            value = fontPath?.takeIf(String::isNotBlank)?.let { path ->
                withContext(Dispatchers.IO) {
                    loadCustomFont(context, path)?.also { fontFamily ->
                        synchronized(customFontCache) { customFontCache.put(path, fontFamily) }
                    }
                }
            }
        }
    }.value
}

private val customFontCache = LruCache<String, FontFamily>(4)

private fun loadCustomFont(context: Context, fontPath: String): FontFamily? =
    runCatching {
        val uri = Uri.parse(fontPath)
        val typeface = if (uri.scheme == "content") {
            context.contentResolver.openFileDescriptor(uri, "r")?.use {
                Typeface.Builder(it.fileDescriptor).build()
            }
        } else {
            Typeface.createFromFile(uri.path)
        }
        typeface?.let(::FontFamily)
    }.getOrNull()

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MiuixThemeWrapper(
    themeColors: LegadoThemeMode,
    customFontFamily: FontFamily?,
    content: @Composable () -> Unit
) {
    val configuration = LocalAppUiConfiguration.current
    val themeSettings = configuration.theme
    val useMiuixMonet = themeSettings.useMiuixMonet
    val paletteStyleValue = themeSettings.paletteStyle
    val materialVersion = themeSettings.materialVersion
    val darkTheme = themeColors.isDark

    // AppTheme has already resolved system mode to an explicit light/dark value.
    // Do not pass System/MonetSystem to Miuix here: MainActivity handles uiMode
    // changes without recreation, so Miuix must not read a second, stale system mode.
    val miuixColorSchemeMode = remember(darkTheme, useMiuixMonet) {
        when {
            useMiuixMonet && darkTheme -> ColorSchemeMode.MonetDark
            useMiuixMonet -> ColorSchemeMode.MonetLight
            darkTheme -> ColorSchemeMode.Dark
            else -> ColorSchemeMode.Light
        }
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

    val miuixTextStyles = remember(customFontFamily) {
        defaultTextStyles().withFont(customFontFamily)
    }

    MiuixTheme(
        controller = controller,
        textStyles = miuixTextStyles,
    ) {
        val miuixStyles = MiuixTheme.textStyles
        val legadoTypography = remember(miuixStyles, customFontFamily) {
            miuixStylesToM3Typography(miuixStyles)
                .toLegadoTypography()
                .withFont(customFontFamily)
        }

        val miuixColorScheme = MiuixTheme.colorScheme
        val customColors = themeSettings.customColors(darkTheme)
        val isDeepPersonalizationActive =
            themeSettings.appTheme == "12" && themeSettings.enableDeepPersonalization
        // MiuixTheme keeps one Colors instance and updates its state-backed fields in place.
        // Caching by miuixColorScheme would therefore retain an obsolete LegadoColorScheme
        // after a light/dark change.
        val mappedColorScheme = run {
            val customBgColor = if (isDeepPersonalizationActive && customColors.background != 0) {
                Color(customColors.background)
            } else {
                miuixColorScheme.background
            }
            val customFontColor = if (isDeepPersonalizationActive && customColors.primaryText != 0) {
                Color(customColors.primaryText)
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
                outlineVariant = miuixColorScheme.secondary.copy(alpha = 0.32f),
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
                onSheetContent = miuixColorScheme.surface.copy(alpha = 0.5f),
                cardPrimaryContainer = miuixColorScheme.primary.copy(alpha = 0.1f)
                    .compositeOver(miuixColorScheme.surface),
                surfaceInput = if (themeSettings.bookInfoInputColor != 0) {
                    Color(themeSettings.bookInfoInputColor)
                } else {
                    Color.Unspecified
                }
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
    val themeSettings = LocalAppUiConfiguration.current.theme
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
        val surfaceInput = themeSettings.bookInfoInputColor
            .takeIf { it != 0 }
            ?.let(::Color)
            ?: Color.Unspecified
        val semanticColors = remember(colorScheme, surfaceInput) {
            colorScheme.toLegadoColorScheme(
                customBgColor = colorScheme.background,
                customFontColor = colorScheme.onSurface,
                customTopBarColor = colorScheme.surface,
                customNavBarColor = colorScheme.surface,
                surfaceInput = surfaceInput,
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
