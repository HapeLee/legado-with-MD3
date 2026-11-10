package io.legado.app.ui.theme

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.itos.coui.material3.CouiMaterial3Style
import com.itos.coui.theme.CouiPresets
import com.itos.coui.theme.CouiTheme

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal fun couiDefaultColorScheme(isDark: Boolean): ColorScheme {
    val colors = if (isDark) CouiPresets.Dark else CouiPresets.Light
    val inverse = if (isDark) CouiPresets.Light else CouiPresets.Dark
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = colors.accent,
        onPrimary = colors.onAccent,
        primaryContainer = colors.container,
        onPrimaryContainer = colors.onSurface,
        inversePrimary = inverse.accent,
        secondary = colors.onSurfaceVariant,
        onSecondary = colors.surface,
        secondaryContainer = colors.container,
        onSecondaryContainer = colors.onSurface,
        tertiary = colors.accentOnSurface,
        onTertiary = colors.onAccent,
        tertiaryContainer = colors.surface,
        onTertiaryContainer = colors.onSurface,
        background = colors.background,
        onBackground = colors.onSurface,
        surface = colors.surface,
        onSurface = colors.onSurface,
        surfaceVariant = colors.container,
        onSurfaceVariant = colors.onSurfaceVariant,
        surfaceTint = Color.Transparent,
        inverseSurface = inverse.surface,
        inverseOnSurface = inverse.onSurface,
        outline = colors.onSurfaceVariant,
        outlineVariant = colors.outlineVariant,
        surfaceBright = colors.container,
        surfaceDim = colors.background,
        surfaceContainerLowest = colors.background,
        surfaceContainerLow = colors.card.compositeOver(colors.background),
        surfaceContainer = colors.background,
        surfaceContainerHigh = colors.container,
        surfaceContainerHighest = colors.container,
    )
}

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@Composable
internal fun ProvideCouiTheme(
    isDark: Boolean,
    useDefaultColors: Boolean = false,
    content: @Composable () -> Unit,
) {
    val style = remember { CouiMaterial3Style() }
    val defaults = style.themeDefaults(isDark)
    val colors = if (useDefaultColors) {
        val preset = if (isDark) CouiPresets.Dark else CouiPresets.Light
        // Keep the native menu and card roles, with the host's optional AMOLED surface.
        preset.copy(surface = MaterialTheme.colorScheme.surface)
    } else {
        defaults.colorScheme
    }
    CouiTheme(
        darkTheme = isDark,
        colorScheme = colors,
        typography = defaults.typography,
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.onSurface, content = content)
    }
}
