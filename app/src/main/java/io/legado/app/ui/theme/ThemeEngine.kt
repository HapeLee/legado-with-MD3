package io.legado.app.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.theme.ThemeResolver.resolvePaletteStyle
import io.legado.app.ui.theme.colorScheme.AugustColorScheme
import io.legado.app.ui.theme.colorScheme.CarlottaColorScheme
import io.legado.app.ui.theme.colorScheme.ElinkColorScheme
import io.legado.app.ui.theme.colorScheme.GRColorScheme
import io.legado.app.ui.theme.colorScheme.KoharuColorScheme
import io.legado.app.ui.theme.colorScheme.LemonColorScheme
import io.legado.app.ui.theme.colorScheme.MujikaColorScheme
import io.legado.app.ui.theme.colorScheme.PhoebeColorScheme
import io.legado.app.ui.theme.colorScheme.SoraColorScheme
import io.legado.app.ui.theme.colorScheme.TransparentColorScheme
import io.legado.app.ui.theme.colorScheme.WHColorScheme
import io.legado.app.ui.theme.colorScheme.YuukaColorScheme

object ThemeEngine {

    private val predefinedColorSchemes: Map<AppThemeMode, BaseColorScheme> = mapOf(
        AppThemeMode.GR to GRColorScheme,
        AppThemeMode.Lemon to LemonColorScheme,
        AppThemeMode.WH to WHColorScheme,
        AppThemeMode.Elink to ElinkColorScheme,
        AppThemeMode.Sora to SoraColorScheme,
        AppThemeMode.August to AugustColorScheme,
        AppThemeMode.Carlotta to CarlottaColorScheme,
        AppThemeMode.Koharu to KoharuColorScheme,
        AppThemeMode.Yuuka to YuukaColorScheme,
        AppThemeMode.Phoebe to PhoebeColorScheme,
        AppThemeMode.Mujika to MujikaColorScheme,
        AppThemeMode.Transparent to TransparentColorScheme,
    )

    fun getColorScheme(
        context: Context,
        mode: AppThemeMode,
        darkTheme: Boolean,
        isAmoled: Boolean,
        paletteStyle: String?,
        materialVersion: String? = null,
        forceOpaque: Boolean = false,
        customSeedColor: Int? = null
    ): ColorScheme {
        val resolvedMode = resolveMode(mode = mode, forceOpaque = forceOpaque)
        val baseColorScheme = resolveBaseColorScheme(
            context = context,
            mode = resolvedMode,
            darkTheme = darkTheme,
            paletteStyle = paletteStyle,
            materialVersion = materialVersion,
            customSeedColor = customSeedColor
        )

        return baseColorScheme
            .applyAmoledIfNeeded(darkTheme = darkTheme, isAmoled = isAmoled)
            .applyTransparentIfNeeded(mode = resolvedMode, forceOpaque = forceOpaque)
    }

    private fun resolveMode(
        mode: AppThemeMode,
        forceOpaque: Boolean
    ): AppThemeMode {
        return if (forceOpaque && mode == AppThemeMode.Transparent) {
            AppThemeMode.WH
        } else {
            mode
        }
    }

    private fun resolveBaseColorScheme(
        context: Context,
        mode: AppThemeMode,
        darkTheme: Boolean,
        paletteStyle: String?,
        materialVersion: String?,
        customSeedColor: Int?
    ): ColorScheme {
        if (mode == AppThemeMode.Dynamic) {
            return resolveDynamicColorScheme(context = context, darkTheme = darkTheme)
        }
        if (mode == AppThemeMode.Custom) {
            return resolveCustomColorScheme(
                seedColor = customSeedColor ?: context.primaryColor,
                darkTheme = darkTheme,
                paletteStyle = paletteStyle,
                materialVersion = materialVersion
            )
        }
        return (predefinedColorSchemes[mode] ?: GRColorScheme).getColorScheme(darkTheme)
    }

    private fun resolveDynamicColorScheme(
        context: Context,
        darkTheme: Boolean
    ): ColorScheme {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return GRColorScheme.getColorScheme(darkTheme)
        }
        return if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }

    private fun resolveCustomColorScheme(
        seedColor: Int,
        darkTheme: Boolean,
        paletteStyle: String?,
        materialVersion: String?
    ): ColorScheme {
        val style = resolvePaletteStyle(paletteStyle)
        val colorSpec = ThemeResolver.resolveColorSpecFromMaterialVersion(materialVersion)
        return CustomColorScheme(seedColor, style, colorSpec).getColorScheme(darkTheme)
    }

    private fun ColorScheme.applyAmoledIfNeeded(
        darkTheme: Boolean,
        isAmoled: Boolean
    ): ColorScheme {
        if (!darkTheme || !isAmoled) return this
        return copy(
            surface = Color.Black,
            background = Color.Black,
            surfaceContainerLow = Color(0xFF0A0A0A),
            surfaceContainer = Color(0xFF121212)
        )
    }

    private fun ColorScheme.applyTransparentIfNeeded(
        mode: AppThemeMode,
        forceOpaque: Boolean
    ): ColorScheme {
        if (forceOpaque || mode != AppThemeMode.Transparent) return this
        return copy(
            surface = Color.Transparent,
            background = Color.Transparent,
            surfaceContainerLow = Color.Transparent,
            surfaceContainer = Color.Transparent,
        )
    }
}
