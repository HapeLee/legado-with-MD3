package io.legado.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.theme.AppColorSchemes.AugustDarkColorScheme
import io.legado.app.ui.theme.AppColorSchemes.AugustLightColorScheme
import io.legado.app.ui.theme.AppColorSchemes.CarlottaDarkColorScheme
import io.legado.app.ui.theme.AppColorSchemes.CarlottaLightColorScheme
import io.legado.app.ui.theme.AppColorSchemes.ElinkDarkColorScheme
import io.legado.app.ui.theme.AppColorSchemes.ElinkLightColorScheme
import io.legado.app.ui.theme.AppColorSchemes.GRDarkColorScheme
import io.legado.app.ui.theme.AppColorSchemes.GRLightColorScheme
import io.legado.app.ui.theme.AppColorSchemes.KoharuDarkColorScheme
import io.legado.app.ui.theme.AppColorSchemes.KoharuLightColorScheme
import io.legado.app.ui.theme.AppColorSchemes.LemonDarkColorScheme
import io.legado.app.ui.theme.AppColorSchemes.LemonLightColorScheme
import io.legado.app.ui.theme.AppColorSchemes.MujikaDarkColorScheme
import io.legado.app.ui.theme.AppColorSchemes.MujikaLightColorScheme
import io.legado.app.ui.theme.AppColorSchemes.PhoebeDarkColorScheme
import io.legado.app.ui.theme.AppColorSchemes.PhoebeLightColorScheme
import io.legado.app.ui.theme.AppColorSchemes.SoraDarkColorScheme
import io.legado.app.ui.theme.AppColorSchemes.SoraLightColorScheme
import io.legado.app.ui.theme.AppColorSchemes.TransparentDarkColorScheme
import io.legado.app.ui.theme.AppColorSchemes.TransparentLightColorScheme
import io.legado.app.ui.theme.AppColorSchemes.WHDarkColorScheme
import io.legado.app.ui.theme.AppColorSchemes.WHLightColorScheme
import io.legado.app.ui.theme.AppColorSchemes.YuukaDarkColorScheme
import io.legado.app.ui.theme.AppColorSchemes.YuukaLightColorScheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val motionScheme = MotionScheme.expressive()
    val appThemeMode by ThemeState.themeMode.collectAsState()

    var colorScheme = when (appThemeMode) {
        AppThemeMode.Dynamic, AppThemeMode.ContentBased -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (darkTheme) darkColorScheme() else lightColorScheme()
            }
        }
        AppThemeMode.GR -> if (darkTheme) GRDarkColorScheme else GRLightColorScheme
        AppThemeMode.Lemon -> if (darkTheme) LemonDarkColorScheme else LemonLightColorScheme
        AppThemeMode.WH -> if (darkTheme) WHDarkColorScheme else WHLightColorScheme
        AppThemeMode.Elink -> if (darkTheme) ElinkDarkColorScheme else ElinkLightColorScheme
        AppThemeMode.Sora -> if (darkTheme) SoraDarkColorScheme else SoraLightColorScheme
        AppThemeMode.August -> if (darkTheme) AugustDarkColorScheme else AugustLightColorScheme
        AppThemeMode.Carlotta -> if (darkTheme) CarlottaDarkColorScheme else CarlottaLightColorScheme
        AppThemeMode.Koharu -> if (darkTheme) KoharuDarkColorScheme else KoharuLightColorScheme
        AppThemeMode.Yuuka -> if (darkTheme) YuukaDarkColorScheme else YuukaLightColorScheme
        AppThemeMode.Phoebe -> if (darkTheme) PhoebeDarkColorScheme else PhoebeLightColorScheme
        AppThemeMode.Mujika -> if (darkTheme) MujikaDarkColorScheme else MujikaLightColorScheme
        AppThemeMode.Transparent -> if (darkTheme) TransparentDarkColorScheme else TransparentLightColorScheme
    }

    if (darkTheme && AppConfig.pureBlack) {
        colorScheme = colorScheme.copy(
            background = Color.Black,
            surface = Color.Black
        )
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        motionScheme = motionScheme,
        shapes = AppShapes,
        content = content
    )
}

// Material3 Typography
val AppTypography = Typography()

// Material3 Shapes
val AppShapes = Shapes()
