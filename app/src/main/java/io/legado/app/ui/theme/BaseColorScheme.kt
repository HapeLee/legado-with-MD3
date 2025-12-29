package io.legado.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

abstract class BaseColorScheme {
    abstract val lightScheme: ColorScheme
    abstract val darkScheme: ColorScheme

    @Composable
    @ReadOnlyComposable
    fun getColorScheme(
        darkTheme: Boolean,
        isAmoled: Boolean,
    ): ColorScheme {
        var scheme = if (darkTheme) darkScheme else lightScheme
        if (darkTheme && isAmoled) {
            scheme = scheme.copy(
                background = Color.Black,
                surface = Color.Black,
            )
        }
        return scheme
    }
}
