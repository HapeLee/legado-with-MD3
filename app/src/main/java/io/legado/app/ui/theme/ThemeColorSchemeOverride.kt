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
import io.legado.app.ui.config.themeConfig.ThemeConfig
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

fun ColorScheme.toLegadoColorScheme(): LegadoColorScheme {
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
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
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
        onSheetContent = surface
    )
}

@Composable
fun ProvideColorSchemeOverride(
    colorScheme: ColorScheme,
    seedColor: Color = colorScheme.primary,
    content: @Composable () -> Unit,
) {
    val themeAnimationSpec = tween<Color>(
        durationMillis = 700,
        easing = FastOutSlowInEasing
    )
    val baseThemeMode = LocalLegadoThemeColors.current
    val animatedColorScheme = colorScheme.animateColorSchemeAsState(themeAnimationSpec)
    val animatedSeedColor = animateColorAsState(
        targetValue = seedColor,
        animationSpec = themeAnimationSpec,
        label = "theme_seed_animation"
    ).value
    val legadoColorScheme = remember(animatedColorScheme) { animatedColorScheme.toLegadoColorScheme() }
    val overrideThemeMode = remember(baseThemeMode, animatedColorScheme, animatedSeedColor) {
        baseThemeMode.copy(
            colorScheme = animatedColorScheme,
            seedColor = animatedSeedColor,
        )
    }
    val materialTypography = MaterialTheme.typography
    val materialShapes = MaterialTheme.shapes
    val isMiuixEngine = ThemeResolver.isMiuixEngine(overrideThemeMode.composeEngine)
    val miuixColorSchemeMode = remember(overrideThemeMode.themeMode) {
        overrideThemeMode.themeMode.toMiuixMonetMode()
    }
    val miuixPaletteStyle = remember(ThemeConfig.paletteStyle) {
        ThemeResolver.resolveMiuixPaletteStyle(ThemeConfig.paletteStyle)
    }
    val miuixColorSpec = remember(ThemeConfig.materialVersion, ThemeConfig.paletteStyle) {
        ThemeResolver.resolveMiuixColorSpec(ThemeConfig.materialVersion, ThemeConfig.paletteStyle)
    }
    val miuixController = remember(
        isMiuixEngine,
        miuixColorSchemeMode,
        overrideThemeMode.isDark,
        animatedSeedColor,
        miuixPaletteStyle,
        miuixColorSpec
    ) {
        if (!isMiuixEngine) {
            null
        } else {
            ThemeController(
                colorSchemeMode = miuixColorSchemeMode,
                keyColor = animatedSeedColor,
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
                MaterialTheme(
                    colorScheme = animatedColorScheme,
                    typography = materialTypography,
                    shapes = materialShapes
                ) {
                    content()
                }
            }
        } else {
            MaterialTheme(
                colorScheme = animatedColorScheme,
                typography = materialTypography,
                shapes = materialShapes
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
