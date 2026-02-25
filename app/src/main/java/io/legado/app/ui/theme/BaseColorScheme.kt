package io.legado.app.ui.theme

import androidx.compose.material3.ColorScheme

abstract class BaseColorScheme {
    abstract val lightScheme: ColorScheme
    abstract val darkScheme: ColorScheme

    fun getColorScheme(darkTheme: Boolean): ColorScheme {
        return if (darkTheme) darkScheme else lightScheme
    }
}
