package io.legado.app.ui.theme

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.core.content.getSystemService
import com.materialkolor.Contrast
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme

class CustomColorScheme(
    context: Context,
    seed: Int,
    style: PaletteStyle,
) : BaseColorScheme() {

    private val contrastLevel: Double =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.getSystemService<UiModeManager>()
                ?.contrast
                ?.toDouble()
                ?: Contrast.Default.value
        } else {
            Contrast.Default.value
        }

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
