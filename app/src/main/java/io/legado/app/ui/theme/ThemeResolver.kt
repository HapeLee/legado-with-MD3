package io.legado.app.ui.theme

import com.materialkolor.Contrast
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import io.legado.app.ui.config.themeConfig.ThemeConfig
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.ThemeColorSpec as MiuixThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle as MiuixPaletteStyle

object ThemeResolver {

    private const val COMPOSE_ENGINE_MIUIX = "miuix"
    private const val MATERIAL_VERSION_EXPRESSIVE = "material3Expressive"

    private val appThemeModes = mapOf(
        "0" to AppThemeMode.Dynamic,
        "1" to AppThemeMode.GR,
        "2" to AppThemeMode.Lemon,
        "3" to AppThemeMode.WH,
        "4" to AppThemeMode.Elink,
        "5" to AppThemeMode.Sora,
        "6" to AppThemeMode.August,
        "7" to AppThemeMode.Carlotta,
        "8" to AppThemeMode.Koharu,
        "9" to AppThemeMode.Yuuka,
        "10" to AppThemeMode.Phoebe,
        "11" to AppThemeMode.Mujika,
        "12" to AppThemeMode.Custom,
        "13" to AppThemeMode.Transparent,
    )

    private val materialPaletteStyles = mapOf(
        "tonalSpot" to PaletteStyle.TonalSpot,
        "neutral" to PaletteStyle.Neutral,
        "vibrant" to PaletteStyle.Vibrant,
        "expressive" to PaletteStyle.Expressive,
        "rainbow" to PaletteStyle.Rainbow,
        "fruitSalad" to PaletteStyle.FruitSalad,
        "monochrome" to PaletteStyle.Monochrome,
        "fidelity" to PaletteStyle.Fidelity,
        "content" to PaletteStyle.Content,
    )

    private val miuixPaletteStyles = mapOf(
        "tonalSpot" to MiuixPaletteStyle.TonalSpot,
        "neutral" to MiuixPaletteStyle.Neutral,
        "vibrant" to MiuixPaletteStyle.Vibrant,
        "expressive" to MiuixPaletteStyle.Expressive,
        "rainbow" to MiuixPaletteStyle.Rainbow,
        "fruitSalad" to MiuixPaletteStyle.FruitSalad,
        "monochrome" to MiuixPaletteStyle.Monochrome,
        "fidelity" to MiuixPaletteStyle.Fidelity,
        "content" to MiuixPaletteStyle.Content,
    )

    private val supportedSpec2025PaletteStyles = setOf(
        "tonalSpot",
        "neutral",
        "vibrant",
        "expressive"
    )

    fun resolveThemeMode(value: String): AppThemeMode {
        return appThemeModes[value] ?: AppThemeMode.Dynamic
    }

    fun resolvePaletteStyle(value: String?): PaletteStyle {
        return materialPaletteStyles[value] ?: PaletteStyle.TonalSpot
    }

    fun resolveContrastLevel(): Double {
        return runCatching { Contrast.valueOf(ThemeConfig.customContrast).value }
            .getOrDefault(Contrast.Default.value)
    }

    fun resolveColorSchemeMode(value: String): ColorSchemeMode {
        return when (value) {
            "1" -> ColorSchemeMode.Light
            "2" -> ColorSchemeMode.Dark
            else -> ColorSchemeMode.System
        }
    }

    fun resolveMiuixColorSchemeMode(
        value: String,
        useMonet: Boolean
    ): ColorSchemeMode {
        val baseMode = resolveColorSchemeMode(value)
        if (!useMonet) return baseMode

        return when (baseMode) {
            ColorSchemeMode.Light -> ColorSchemeMode.MonetLight
            ColorSchemeMode.Dark -> ColorSchemeMode.MonetDark
            else -> ColorSchemeMode.MonetSystem
        }
    }

    fun isMiuixEngine(composeEngine: String): Boolean {
        return composeEngine.equals(COMPOSE_ENGINE_MIUIX, ignoreCase = true)
    }

    fun resolveColorSpecVersion(colorSpec: ThemeColorSpec): ColorSpec.SpecVersion {
        return when (colorSpec) {
            ThemeColorSpec.SPEC_2025 -> ColorSpec.SpecVersion.SPEC_2025
            ThemeColorSpec.SPEC_2021 -> ColorSpec.SpecVersion.SPEC_2021
        }
    }

    fun resolveColorSpecFromMaterialVersion(value: String?): ThemeColorSpec {
        return if (value == MATERIAL_VERSION_EXPRESSIVE) {
            ThemeColorSpec.SPEC_2025
        } else {
            ThemeColorSpec.SPEC_2021
        }
    }

    fun resolveMiuixPaletteStyle(value: String?): MiuixPaletteStyle {
        return miuixPaletteStyles[value] ?: MiuixPaletteStyle.TonalSpot
    }

    fun resolveMiuixColorSpec(
        materialVersion: String?,
        paletteStyle: String?
    ): MiuixThemeColorSpec {
        val useSpec2025 = resolveColorSpecFromMaterialVersion(materialVersion) == ThemeColorSpec.SPEC_2025
        val supportsSpec2025 = paletteStyle in supportedSpec2025PaletteStyles

        return if (useSpec2025 && supportsSpec2025) {
            MiuixThemeColorSpec.Spec2025
        } else {
            MiuixThemeColorSpec.Spec2021
        }
    }
}
