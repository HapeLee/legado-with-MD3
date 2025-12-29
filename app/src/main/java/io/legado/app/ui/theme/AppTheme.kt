package io.legado.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.PaletteStyle
import io.legado.app.help.config.AppConfig
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val appThemeMode by ThemeState.themeMode.collectAsState()
    val isAmoled = AppConfig.pureBlack

    val colorScheme = when (appThemeMode) {
        AppThemeMode.Dynamic -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                GRColorScheme.getColorScheme(darkTheme, isAmoled)
            }
        }
        AppThemeMode.CUSTOM -> {
            CustomColorScheme(
                context = context,
                seed = context.primaryColor,
                style = PaletteStyle.Vibrant,
            ).getColorScheme(darkTheme, isAmoled)
        }
        else -> {
            val scheme = colorSchemes.getOrDefault(appThemeMode, GRColorScheme)
            scheme.getColorScheme(darkTheme, isAmoled)
        }
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        motionScheme = MotionScheme.expressive(),
        shapes = Shapes(),
        content = content
    )
}

private val colorSchemes: Map<AppThemeMode, BaseColorScheme> = mapOf(
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
