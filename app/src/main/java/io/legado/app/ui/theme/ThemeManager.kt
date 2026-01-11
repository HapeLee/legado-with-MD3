package io.legado.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.PaletteStyle
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.theme.colorScheme.AugustColorScheme
import io.legado.app.ui.theme.colorScheme.CarlottaColorScheme
import io.legado.app.ui.theme.colorScheme.ElinkColorScheme
import io.legado.app.ui.theme.colorScheme.GRColorScheme
import io.legado.app.ui.theme.colorScheme.KoharuColorScheme
import io.legado.app.ui.theme.colorScheme.LemonColorScheme
import io.legado.app.ui.theme.colorScheme.MujikaColorScheme
import io.legado.app.ui.theme.colorScheme.PhoebeColorScheme
import io.legado.app.ui.theme.colorScheme.SoraColorScheme
import io.legado.app.ui.theme.colorScheme.TransparentColorScheme
import io.legado.app.ui.theme.colorScheme.WHColorScheme
import io.legado.app.ui.theme.colorScheme.YuukaColorScheme

object ThemeManager {

    val colorSchemes: Map<AppThemeMode, BaseColorScheme> = mapOf(
        AppThemeMode.GR to GRColorScheme,
        AppThemeMode.Lemon to LemonColorScheme,
        AppThemeMode.WH to WHColorScheme,
        AppThemeMode.Elink to ElinkColorScheme,
        AppThemeMode.Sora to SoraColorScheme,
        AppThemeMode.August to AugustColorScheme,
        AppThemeMode.Carlotta to CarlottaColorScheme,
        AppThemeMode.Koharu to KoharuColorScheme,
        AppThemeMode.Yuuka to YuukaColorScheme,
        AppThemeMode.Phoebe to PhoebeColorScheme,
        AppThemeMode.Mujika to MujikaColorScheme,
        AppThemeMode.Transparent to TransparentColorScheme,
    )

    @Composable
    fun getColorScheme(
        mode: AppThemeMode,
        darkTheme: Boolean = isSystemInDarkTheme(),
        isAmoled: Boolean,
        isImageBg: Boolean,
        forceOpaque: Boolean = false
    ): ColorScheme {
        val context = LocalContext.current

        val actualMode = if (forceOpaque && mode == AppThemeMode.Transparent) {
            AppThemeMode.WH
        } else {
            mode
        }

        var scheme = when (actualMode) {
            AppThemeMode.Dynamic -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(
                        context
                    )
                } else GRColorScheme.getColorScheme(darkTheme)
            }

            AppThemeMode.CUSTOM -> {
                CustomColorScheme(context, context.primaryColor, PaletteStyle.Vibrant)
                    .getColorScheme(darkTheme)
            }

            else -> (colorSchemes[actualMode] ?: GRColorScheme).getColorScheme(darkTheme)
        }

        if (darkTheme && isAmoled) {
            scheme = scheme.copy(
                surface = Color.Black,
                background = Color.Black,
                surfaceContainerLow = Color(0xFF0A0A0A),
                surfaceContainer = Color(0xFF121212)
            )
        }

        return if (!forceOpaque && (isImageBg || actualMode == AppThemeMode.Transparent)) {
            scheme.copy(
                surface = Color.Transparent,
                background = Color.Transparent,
                surfaceContainerLow = Color.Transparent,
                surfaceContainer = Color.Transparent
            )
        } else {
            scheme
        }
    }

}