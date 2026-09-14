package io.legado.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
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
        mode: AppThemeMode,
        darkTheme: Boolean,
        isAmoled: Boolean,
        paletteStyle: String?,
        materialVersion: String? = null,
        forceOpaque: Boolean = false,
        customSeedColor: Int? = null,
        customContrast: String? = null,
    ): ColorScheme {
        val resolvedMode = resolveMode(mode = mode, forceOpaque = forceOpaque)
        val baseColorScheme = resolveBaseColorScheme(
            mode = resolvedMode,
            darkTheme = darkTheme,
            paletteStyle = paletteStyle,
            materialVersion = materialVersion,
            customSeedColor = customSeedColor,
            customContrast = customContrast,
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
        mode: AppThemeMode,
        darkTheme: Boolean,
        paletteStyle: String?,
        materialVersion: String?,
        customSeedColor: Int?,
        customContrast: String?,
    ): ColorScheme {
        if (mode == AppThemeMode.Dynamic) {
            return resolveDynamicColorScheme(darkTheme = darkTheme)
        }
        if (mode == AppThemeMode.Custom) {
            return resolveCustomColorScheme(
                seedColor = customSeedColor ?: ThemeSeedColors.primaryColor(),
                darkTheme = darkTheme,
                paletteStyle = paletteStyle,
                materialVersion = materialVersion,
                customContrast = customContrast,
            )
        }
        return (predefinedColorSchemes[mode] ?: GRColorScheme).getColorScheme(darkTheme)
    }

    /**
     * 动态取色的实际取色器来自 [DynamicColorSchemes]（宿主注入的窄契约）。
     *
     * Android 12 以下、以及 desktop/iOS 这类**根本没有系统调色板**的目标，
     * 提供方都返回 `null` ⇒ 统一回落到预定义配色 [GRColorScheme]。
     * 这与改造前 `Build.VERSION.SDK_INT < S` 分支的行为逐字等价——那个判断
     * 连同 `dynamicLight/DarkColorScheme(context)` 一起搬到了 Android 实现里
     * （见 `core/ui` 的 `installAndroidThemePlatform`）。
     */
    private fun resolveDynamicColorScheme(
        darkTheme: Boolean
    ): ColorScheme {
        return DynamicColorSchemes.colorScheme(darkTheme) ?: GRColorScheme.getColorScheme(darkTheme)
    }

    private fun resolveCustomColorScheme(
        seedColor: Int,
        darkTheme: Boolean,
        paletteStyle: String?,
        materialVersion: String?,
        customContrast: String?,
    ): ColorScheme {
        val style = resolvePaletteStyle(paletteStyle)
        val colorSpec = ThemeResolver.resolveColorSpecFromMaterialVersion(materialVersion)
        return CustomColorScheme(
            seed = seedColor,
            style = style,
            colorSpec = colorSpec,
            contrastLevel = ThemeResolver.resolveContrastLevel(
                customContrast ?: "Default"
            ),
        ).getColorScheme(darkTheme)
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
