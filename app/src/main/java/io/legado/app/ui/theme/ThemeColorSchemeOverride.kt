package io.legado.app.ui.theme

import androidx.compose.animation.animateColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

fun ColorScheme.toLegadoColorScheme(
    customBgColor: Color = background,
    customFontColor: Color = onSurface,
    customTopBarColor: Color = surface,
    customNavBarColor: Color = surface,
    surfaceInput: Color = Color.Unspecified,
): LegadoColorScheme {
    return LegadoColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = inversePrimary,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        background = customBgColor,
        onBackground = customFontColor,
        surface = surface,
        onSurface = customFontColor,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = surfaceTint,
        inverseSurface = inverseSurface,
        inverseOnSurface = inverseOnSurface,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        outline = outline,
        outlineVariant = outlineVariant,
        scrim = scrim,
        surfaceBright = surfaceBright,
        surfaceDim = surfaceDim,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainerLowest = surfaceContainerLowest,
        primaryFixed = primaryFixed,
        primaryFixedDim = primaryFixedDim,
        onPrimaryFixed = onPrimaryFixed,
        onPrimaryFixedVariant = onPrimaryFixedVariant,
        secondaryFixed = secondaryFixed,
        secondaryFixedDim = secondaryFixedDim,
        onSecondaryFixed = onSecondaryFixed,
        onSecondaryFixedVariant = onSecondaryFixedVariant,
        tertiaryFixed = tertiaryFixed,
        tertiaryFixedDim = tertiaryFixedDim,
        onTertiaryFixed = onTertiaryFixed,
        onTertiaryFixedVariant = onTertiaryFixedVariant,
        cardContainer = primaryContainer.copy(alpha = 0.5f),
        onCardContainer = primary,
        onSheetContent = surface,
        cardPrimaryContainer = primaryContainer,
        surfaceInput = surfaceInput
    )
}

fun ColorScheme.toLegadoColorScheme(): LegadoColorScheme {
    return toLegadoColorScheme(
        customBgColor = background,
        customFontColor = onSurface,
        customTopBarColor = surface,
        customNavBarColor = surface
    )
}

@Composable
fun ProvideColorSchemeOverride(
    colorScheme: ColorScheme,
    seedColor: Color = colorScheme.primary,
    overrideIsDark: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val themeSettings = LocalAppUiConfiguration.current.theme
    val baseThemeMode = LocalLegadoThemeColors.current
    val surfaceInput = themeSettings.bookInfoInputColor
        .takeIf { it != 0 }
        ?.let(::Color)
        ?: Color.Unspecified
    val legadoColorScheme = remember(colorScheme, surfaceInput) {
        colorScheme.toLegadoColorScheme(surfaceInput = surfaceInput)
    }
    val resolvedIsDark = overrideIsDark ?: baseThemeMode.isDark
    val overrideThemeMode = remember(baseThemeMode, colorScheme, seedColor, resolvedIsDark) {
        baseThemeMode.copy(
            colorScheme = colorScheme,
            seedColor = seedColor,
            isDark = resolvedIsDark,
        )
    }
    val isMiuixEngine = ThemeResolver.isMiuixEngine(overrideThemeMode.composeEngine)
    val miuixColorSchemeMode = remember(overrideThemeMode.themeMode) {
        overrideThemeMode.themeMode.toMiuixMonetMode()
    }
    val miuixPaletteStyle = remember(themeSettings.paletteStyle) {
        ThemeResolver.resolveMiuixPaletteStyle(themeSettings.paletteStyle)
    }
    val miuixColorSpec = remember(themeSettings.materialVersion, themeSettings.paletteStyle) {
        ThemeResolver.resolveMiuixColorSpec(
            themeSettings.materialVersion,
            themeSettings.paletteStyle,
        )
    }
    val miuixController = remember(
        isMiuixEngine,
        miuixColorSchemeMode,
        overrideThemeMode.isDark,
        seedColor,
        miuixPaletteStyle,
        miuixColorSpec
    ) {
        if (!isMiuixEngine) {
            null
        } else {
            ThemeController(
                colorSchemeMode = miuixColorSchemeMode,
                keyColor = seedColor,
                paletteStyle = miuixPaletteStyle,
                colorSpec = miuixColorSpec,
                isDark = overrideThemeMode.isDark
            )
        }
    }

    CompositionLocalProvider(
        LocalLegadoThemeColors provides overrideThemeMode,
        LocalLegadoColorScheme provides legadoColorScheme
    ) {
        if (miuixController != null) {
            MiuixTheme(controller = miuixController) {
                content()
            }
        } else {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = MaterialTheme.typography,
                shapes = MaterialTheme.shapes
            ) {
                content()
            }
        }
    }
}

private fun ColorSchemeMode.toMiuixMonetMode(): ColorSchemeMode {
    return when (this) {
        ColorSchemeMode.Light,
        ColorSchemeMode.MonetLight -> ColorSchemeMode.MonetLight

        ColorSchemeMode.Dark,
        ColorSchemeMode.MonetDark -> ColorSchemeMode.MonetDark

        else -> ColorSchemeMode.MonetSystem
    }
}

@Composable
fun ColorScheme.animateColorSchemeAsState(
    animationSpec: FiniteAnimationSpec<Color> = tween(
        durationMillis = 700,
        easing = FastOutSlowInEasing
    )
): ColorScheme {
    val transition = updateTransition(
        targetState = this,
        label = "theme_color_scheme_transition"
    )

    @Composable
    fun animateColor(label: String, color: ColorScheme.() -> Color): Color {
        return transition.animateColor(
            transitionSpec = { animationSpec },
            label = label
        ) { scheme ->
            scheme.color()
        }.value
    }

    return ColorScheme(
        primary = animateColor("scheme-primary") { primary },
        onPrimary = animateColor("scheme-onPrimary") { onPrimary },
        primaryContainer = animateColor("scheme-primaryContainer") { primaryContainer },
        onPrimaryContainer = animateColor("scheme-onPrimaryContainer") { onPrimaryContainer },
        inversePrimary = animateColor("scheme-inversePrimary") { inversePrimary },
        secondary = animateColor("scheme-secondary") { secondary },
        onSecondary = animateColor("scheme-onSecondary") { onSecondary },
        secondaryContainer = animateColor("scheme-secondaryContainer") { secondaryContainer },
        onSecondaryContainer = animateColor("scheme-onSecondaryContainer") { onSecondaryContainer },
        tertiary = animateColor("scheme-tertiary") { tertiary },
        onTertiary = animateColor("scheme-onTertiary") { onTertiary },
        tertiaryContainer = animateColor("scheme-tertiaryContainer") { tertiaryContainer },
        onTertiaryContainer = animateColor("scheme-onTertiaryContainer") { onTertiaryContainer },
        background = animateColor("scheme-background") { background },
        onBackground = animateColor("scheme-onBackground") { onBackground },
        surface = animateColor("scheme-surface") { surface },
        onSurface = animateColor("scheme-onSurface") { onSurface },
        surfaceVariant = animateColor("scheme-surfaceVariant") { surfaceVariant },
        onSurfaceVariant = animateColor("scheme-onSurfaceVariant") { onSurfaceVariant },
        surfaceTint = animateColor("scheme-surfaceTint") { surfaceTint },
        inverseSurface = animateColor("scheme-inverseSurface") { inverseSurface },
        inverseOnSurface = animateColor("scheme-inverseOnSurface") { inverseOnSurface },
        error = animateColor("scheme-error") { error },
        onError = animateColor("scheme-onError") { onError },
        errorContainer = animateColor("scheme-errorContainer") { errorContainer },
        onErrorContainer = animateColor("scheme-onErrorContainer") { onErrorContainer },
        outline = animateColor("scheme-outline") { outline },
        outlineVariant = animateColor("scheme-outlineVariant") { outlineVariant },
        scrim = animateColor("scheme-scrim") { scrim },
        surfaceBright = animateColor("scheme-surfaceBright") { surfaceBright },
        surfaceDim = animateColor("scheme-surfaceDim") { surfaceDim },
        surfaceContainer = animateColor("scheme-surfaceContainer") { surfaceContainer },
        surfaceContainerHigh = animateColor("scheme-surfaceContainerHigh") { surfaceContainerHigh },
        surfaceContainerHighest = animateColor("scheme-surfaceContainerHighest") { surfaceContainerHighest },
        surfaceContainerLow = animateColor("scheme-surfaceContainerLow") { surfaceContainerLow },
        surfaceContainerLowest = animateColor("scheme-surfaceContainerLowest") { surfaceContainerLowest },
        primaryFixed = animateColor("scheme-primaryFixed") { primaryFixed },
        primaryFixedDim = animateColor("scheme-primaryFixedDim") { primaryFixedDim },
        onPrimaryFixed = animateColor("scheme-onPrimaryFixed") { onPrimaryFixed },
        onPrimaryFixedVariant = animateColor("scheme-onPrimaryFixedVariant") { onPrimaryFixedVariant },
        secondaryFixed = animateColor("scheme-secondaryFixed") { secondaryFixed },
        secondaryFixedDim = animateColor("scheme-secondaryFixedDim") { secondaryFixedDim },
        onSecondaryFixed = animateColor("scheme-onSecondaryFixed") { onSecondaryFixed },
        onSecondaryFixedVariant = animateColor("scheme-onSecondaryFixedVariant") { onSecondaryFixedVariant },
        tertiaryFixed = animateColor("scheme-tertiaryFixed") { tertiaryFixed },
        tertiaryFixedDim = animateColor("scheme-tertiaryFixedDim") { tertiaryFixedDim },
        onTertiaryFixed = animateColor("scheme-onTertiaryFixed") { onTertiaryFixed },
        onTertiaryFixedVariant = animateColor("scheme-onTertiaryFixedVariant") { onTertiaryFixedVariant }
    )
}
