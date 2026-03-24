package io.legado.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import io.legado.app.ui.theme.ThemeResolver.resolveColorSpecVersion

class CustomColorScheme(
    seed: Int,
    style: PaletteStyle,
    colorSpec: ThemeColorSpec = ThemeColorSpec.SPEC_2021,
) : BaseColorScheme() {

    private val contrastLevel: Double = ThemeResolver.resolveContrastLevel()

    private val specVersion = resolveColorSpecVersion(colorSpec)

    override val lightScheme: ColorScheme = dynamicColorScheme(
        seedColor = Color(seed),
        isDark = false,
        isAmoled = false,
        style = style,
        contrastLevel = contrastLevel,
        specVersion = specVersion
    )

    override val darkScheme: ColorScheme = dynamicColorScheme(
        seedColor = Color(seed),
        isDark = true,
        isAmoled = false,
        style = style,
        contrastLevel = contrastLevel,
        specVersion = specVersion
    )
}
