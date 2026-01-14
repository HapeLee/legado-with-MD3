package io.legado.app.ui.theme

import com.materialkolor.PaletteStyle

object ThemeResolver {

    fun resolveThemeMode(value: String): AppThemeMode = when (value) {
        "0" -> AppThemeMode.Dynamic
        "1" -> AppThemeMode.GR
        "2" -> AppThemeMode.Lemon
        "3" -> AppThemeMode.WH
        "4" -> AppThemeMode.Elink
        "5" -> AppThemeMode.Sora
        "6" -> AppThemeMode.August
        "7" -> AppThemeMode.Carlotta
        "8" -> AppThemeMode.Koharu
        "9" -> AppThemeMode.Yuuka
        "10" -> AppThemeMode.Phoebe
        "11" -> AppThemeMode.Mujika
        "12" -> AppThemeMode.CUSTOM
        "13" -> AppThemeMode.Transparent
        else -> AppThemeMode.Dynamic
    }

    fun resolvePaletteStyle(value: String?): PaletteStyle {
        return when (value) {
            "tonalSpot" -> PaletteStyle.TonalSpot
            "neutral" -> PaletteStyle.Neutral
            "vibrant" -> PaletteStyle.Vibrant
            "expressive" -> PaletteStyle.Expressive
            "rainbow" -> PaletteStyle.Rainbow
            "fruitSalad" -> PaletteStyle.FruitSalad
            "monochrome" -> PaletteStyle.Monochrome
            "fidelity" -> PaletteStyle.Fidelity
            "content" -> PaletteStyle.Content
            else -> PaletteStyle.TonalSpot
        }
    }

}
