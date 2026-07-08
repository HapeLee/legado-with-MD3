package io.legado.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import io.legado.app.ui.config.themeConfig.ThemeConfig
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import com.materialkolor.PaletteStyle

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    if (LocalInspectionMode.current) {
        AppThemePreview(darkTheme, content)
    } else {
        AppThemeActual(darkTheme, content)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppThemePreview(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()
    val themeColors = LegadoThemeMode(
        colorScheme = colorScheme,
        isDark = darkTheme,
        seedColor = Color.Unspecified,
        paletteStyle = PaletteStyle.TonalSpot,
        themeMode = if (darkTheme) ColorSchemeMode.Dark else ColorSchemeMode.Light,
        useDynamicColor = false,
        composeEngine = "material"
    )
    CompositionLocalProvider(
        LocalLegadoThemeColors provides themeColors
    ) {
        MaterialThemeWrapper(
            themeColors = themeColors,
            customFontFamily = null,
            content = content
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppThemeActual(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    
    // 1. 获取基础配置
    val appThemeMode = ThemeResolver.resolveThemeMode(ThemeConfig.appTheme)
    val themeModeValue = ThemeConfig.themeMode
    val effectiveDarkTheme = when (themeModeValue) {
        "1" -> false
        "2" -> true
        else -> darkTheme
    }
    val isPureBlack = ThemeConfig.isPureBlack
    val paletteStyleValue = ThemeConfig.paletteStyle
    val materialVersion = ThemeConfig.materialVersion
    val composeEngine = ThemeConfig.composeEngine
    val customPrimary = ThemeConfig.cPrimary
    val customNightPrimary = ThemeConfig.cNPrimary
    val appFontPath = ThemeConfig.appFontPath

    // 2. 深度个性化配置
    val enableDeepPersonalization = ThemeConfig.enableDeepPersonalization
    val customColors = ThemeConfig.customThemeColors(effectiveDarkTheme)

    // 3. 加载自定义字体
    val customFontFamily = rememberCustomFont(appFontPath)

    // 4. 解析配色方案 (Material 3 ColorScheme)
    val colorScheme = remember(
        context, appThemeMode, effectiveDarkTheme, isPureBlack, customPrimary, customNightPrimary,
        enableDeepPersonalization, customColors,
        paletteStyleValue, materialVersion
    ) {
        if (appThemeMode == AppThemeMode.Custom &&
            enableDeepPersonalization &&
            customColors.hasCustomColor
        ) {
            val userPalette = UserColorPalette(
                primaryColor = if (customColors.primary != 0) Color(customColors.primary) else Color(0xFF6750A4),
                secondaryColor = if (customColors.secondary != 0) Color(customColors.secondary) else Color(0xFF625B71),
                backgroundColor = if (customColors.background != 0) Color(customColors.background) else Color(0xFFFEF7FF),
                primaryFontColor = if (customColors.primaryText != 0) Color(customColors.primaryText) else Color(0xFF1C1B1F),
                secondaryFontColor = if (customColors.secondaryText != 0) Color(customColors.secondaryText) else Color(0xFF49454F),
                labelContainerColor = if (customColors.labelContainer != 0) Color(customColors.labelContainer) else Color(0xFFF7F2FA)
            )
            generateColorScheme(userPalette, effectiveDarkTheme)
        } else {
            val customSeedColor = if (effectiveDarkTheme) customNightPrimary else customPrimary
            ThemeEngine.getColorScheme(
                context = context,
                mode = appThemeMode,
                darkTheme = effectiveDarkTheme,
                isAmoled = isPureBlack,
                paletteStyle = paletteStyleValue,
                materialVersion = materialVersion,
                customSeedColor = customSeedColor
            )
        }
    }

    // 5. 确定种子颜色
    val themeSeedColor = remember(
        appThemeMode, colorScheme.primary, effectiveDarkTheme, customPrimary, customNightPrimary
    ) {
        if (appThemeMode == AppThemeMode.Custom) {
            val seed = if (effectiveDarkTheme) customNightPrimary else customPrimary
            if (seed != 0) Color(seed) else colorScheme.primary
        } else {
            colorScheme.primary
        }
    }

    // 6. 构造 Legado 主题模式数据
    val themeColors = remember(
        colorScheme, effectiveDarkTheme, themeSeedColor, paletteStyleValue, composeEngine,
        appThemeMode, themeModeValue
    ) {
        val paletteStyle = ThemeResolver.resolvePaletteStyle(paletteStyleValue)
        val colorSchemeMode = ThemeResolver.resolveColorSchemeMode(themeModeValue)
        LegadoThemeMode(
            colorScheme = colorScheme,
            isDark = effectiveDarkTheme,
            seedColor = themeSeedColor,
            paletteStyle = paletteStyle,
            themeMode = colorSchemeMode,
            useDynamicColor = appThemeMode == AppThemeMode.Dynamic,
            composeEngine = composeEngine
        )
    }

    // 7. 提供主题数据并根据引擎渲染
    CompositionLocalProvider(
        LocalLegadoThemeColors provides themeColors
    ) {
        if (ThemeResolver.isMiuixEngine(themeColors.composeEngine)) {
            MiuixThemeWrapper(
                themeColors = themeColors,
                customFontFamily = customFontFamily,
                content = content
            )
        } else {
            MaterialThemeWrapper(
                themeColors = themeColors,
                customFontFamily = customFontFamily,
                content = content
            )
        }
    }
}
