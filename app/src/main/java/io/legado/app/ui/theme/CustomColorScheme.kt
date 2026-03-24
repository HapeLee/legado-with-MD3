package io.legado.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme

class CustomColorScheme(
    seed: Int,
    style: PaletteStyle,
) : BaseColorScheme() {

    private val contrastLevel: Double = ThemeResolver.resolveContrastLevel()

    override val lightScheme: ColorScheme = dynamicColorScheme(
        seedColor = Color(seed),
        isDark = false,
        isAmoled = false,
        style = style,
        contrastLevel = contrastLevel
    )

    override val darkScheme: ColorScheme = dynamicColorScheme(
        seedColor = Color(seed),
        isDark = true,
        isAmoled = false,
        style = style,
        contrastLevel = contrastLevel
    )
}
