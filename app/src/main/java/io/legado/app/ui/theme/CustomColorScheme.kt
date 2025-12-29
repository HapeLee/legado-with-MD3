package io.legado.app.ui.theme

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.core.content.getSystemService
import com.materialkolor.Contrast
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme

internal class CustomColorScheme(
    context: Context,
    seed: Int,
    style: PaletteStyle,
) : BaseColorScheme() {
    private val custom = CustomCompatColorScheme(context, seed, style)

    override val darkScheme
        get() = custom.darkScheme

    override val lightScheme
        get() = custom.lightScheme
}

private class CustomCompatColorScheme(
    context: Context,
    seed: Int,
    style: PaletteStyle,
) : BaseColorScheme() {
    override val lightScheme = dynamicColorScheme(
        seedColor = Color(seed),
        isDark = false,
        isAmoled = false,
        style = style,
        contrastLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.getSystemService<UiModeManager>()?.contrast?.toDouble() ?: Contrast.Default.value
        } else {
            Contrast.Default.value
        },
    )
    override val darkScheme = dynamicColorScheme(
        seedColor = Color(seed),
        isDark = true,
        isAmoled = false,
        style = style,
        contrastLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.getSystemService<UiModeManager>()?.contrast?.toDouble() ?: Contrast.Default.value
        } else {
            Contrast.Default.value
        },
    )
}