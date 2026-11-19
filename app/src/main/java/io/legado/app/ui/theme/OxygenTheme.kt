package io.legado.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import io.oxygenx.material3.ProvideMaterial3Content
import io.oxygenx.material3.toOxygenTypography
import io.oxygenx.material3.toOxygenColorScheme
import io.oxygenx.theme.OxygenTheme

/** Native Oxygen roles projected into M3 for retained host controls. Extension roles stay native. */
internal fun oxygenDefaultColorScheme(isDark: Boolean): ColorScheme {
    val colors = if (isDark) io.oxygenx.theme.darkColorScheme() else io.oxygenx.theme.lightColorScheme()
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = colors.primary,
        onPrimary = colors.onPrimary,
        primaryContainer = colors.primaryContainer,
        onPrimaryContainer = colors.onPrimaryContainer,
        inversePrimary = colors.inversePrimary,
        secondary = colors.secondary,
        onSecondary = colors.onSecondary,
        secondaryContainer = colors.secondaryContainer,
        onSecondaryContainer = colors.onSecondaryContainer,
        surface = colors.surface,
        onSurface = colors.onSurface,
        surfaceContainer = colors.surfaceContainer,
        surfaceContainerLow = colors.surfaceContainerLow,
        surfaceContainerLowest = colors.surfaceContainerLowest,
        surfaceContainerHigh = colors.surfaceContainerHigh,
        surfaceContainerHighest = colors.surfaceContainerHighest,
        surfaceBright = colors.surfaceBright,
        surfaceDim = colors.surfaceDim,
        onSurfaceVariant = colors.onSurfaceVariant,
        inverseSurface = colors.inverseSurface,
        inverseOnSurface = colors.inverseOnSurface,
        outline = colors.outline,
        outlineVariant = colors.outlineVariant,
        error = colors.error,
        onError = colors.onError,
        errorContainer = colors.errorContainer,
        onErrorContainer = colors.onErrorContainer,
        scrim = colors.scrim,
        background = colors.surface,
        onBackground = colors.onSurface,
        surfaceVariant = colors.surfaceContainerHigh,
        surfaceTint = Color.Transparent,
        tertiary = colors.secondary,
        onTertiary = colors.onSecondary,
        tertiaryContainer = colors.secondaryContainer,
        onTertiaryContainer = colors.onSecondaryContainer,
    )
}

/** Keep M3 fallback controls and Oxygen controls on the same palette and host font. */
@Composable
internal fun ProvideOxygenTheme(
    colorScheme: ColorScheme = MaterialTheme.colorScheme,
    isDark: Boolean = LegadoTheme.isDark,
    useDefaultColors: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = remember(colorScheme, isDark, useDefaultColors) {
        if (useDefaultColors) {
            val defaults = if (isDark) io.oxygenx.theme.darkColorScheme() else io.oxygenx.theme.lightColorScheme()
            // Keep exact native extension roles; the host may still apply AMOLED surfaces.
            return@remember defaults.copy(
                surface = colorScheme.surface,
                surfaceContainer = colorScheme.surfaceContainer,
                surfaceContainerLow = colorScheme.surfaceContainerLow,
            )
        }
        // Transparent reading themes carry no luminance. Use the host's explicit mode
        // for extension-role synthesis, then restore the actual transparent surface.
        val source = if (colorScheme.surface.alpha == 0f) {
            colorScheme.copy(surface = if (isDark) Color.Black else Color.White)
        } else colorScheme
        source.toOxygenColorScheme().copy(surface = colorScheme.surface, isDark = isDark)
    }
    val materialTypography = MaterialTheme.typography
    val typography = remember(materialTypography) { materialTypography.toOxygenTypography() }

    OxygenTheme(darkTheme = isDark, colorScheme = colors, typography = typography) {
        ProvideMaterial3Content(content)
    }
}
