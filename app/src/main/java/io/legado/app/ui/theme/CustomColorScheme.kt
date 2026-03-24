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
import io.legado.app.ui.config.themeConfig.ThemeConfig

class CustomColorScheme(
    context: Context,
    seed: Int,
    style: PaletteStyle,
) : BaseColorScheme() {

    private val contrastLevel: Double = resolveContrastLevel(context)

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

    companion object {
        const val SOURCE_SYSTEM = "system"
        const val SOURCE_MANUAL = "manual"

        fun systemContrastLevel(context: Context): Double? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                return null
            }
            return context.getSystemService<UiModeManager>()
                ?.contrast
                ?.toDouble()
                ?.coerceIn(Contrast.Reduced.value, Contrast.High.value)
        }

        fun resolveContrastLevel(context: Context): Double {
            return when (ThemeConfig.customContrastSource) {
                SOURCE_MANUAL -> {
                    ThemeConfig.customContrastManual
                        .toDoubleOrNull()
                        ?.coerceIn(Contrast.Reduced.value, Contrast.High.value)
                        ?: Contrast.Default.value
                }

                else -> systemContrastLevel(context) ?: Contrast.Default.value
            }
        }
    }
}
