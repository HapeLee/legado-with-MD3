package io.legado.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

abstract class BaseColorScheme {
    abstract val lightScheme: ColorScheme
    abstract val darkScheme: ColorScheme

    @Composable
    @ReadOnlyComposable
    fun getColorScheme(darkTheme: Boolean): ColorScheme {
        return if (darkTheme) darkScheme else lightScheme
    }
}
