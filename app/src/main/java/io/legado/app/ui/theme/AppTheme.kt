package io.legado.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import io.legado.app.ui.config.themeConfig.ThemeConfig

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    
    // 1. 获取基础配置
    val appThemeMode = ThemeResolver.resolveThemeMode(ThemeConfig.appTheme)
    val isPureBlack = ThemeConfig.isPureBlack
    val paletteStyleValue = ThemeConfig.paletteStyle
    val materialVersion = ThemeConfig.materialVersion
    val composeEngine = ThemeConfig.composeEngine
    val customPrimary = ThemeConfig.cPrimary
    val customNightPrimary = ThemeConfig.cNPrimary
    val appFontPath = ThemeConfig.appFontPath

    // 2. 深度个性化配置
    val enableDeepPersonalization = ThemeConfig.enableDeepPersonalization
    val themeColor = ThemeConfig.themeColor
    val secondaryThemeColor = ThemeConfig.secondaryThemeColor
    val primaryTextColor = ThemeConfig.primaryTextColor
    val secondaryTextColor = ThemeConfig.secondaryTextColor
    val themeBackgroundColor = ThemeConfig.themeBackgroundColor
    val customLabelContainerColor = ThemeConfig.labelContainerColor

    // 3. 加载自定义字体
    val customFontFamily = rememberCustomFont(appFontPath)

    // 4. 解析配色方案 (Material 3 ColorScheme)
    val colorScheme = remember(
        context, appThemeMode, darkTheme, isPureBlack, customPrimary, customNightPrimary,
        enableDeepPersonalization, themeColor, secondaryThemeColor, primaryTextColor,
        secondaryTextColor, themeBackgroundColor, customLabelContainerColor,
        paletteStyleValue, materialVersion
    ) {
        if (enableDeepPersonalization &&
            (themeColor != 0 || secondaryThemeColor != 0 || primaryTextColor != 0 ||
             secondaryTextColor != 0 || themeBackgroundColor != 0 || customLabelContainerColor != 0)) {
            val userPalette = UserColorPalette(
                primaryColor = if (themeColor != 0) Color(themeColor) else Color(0xFF6750A4),
                secondaryColor = if (secondaryThemeColor != 0) Color(secondaryThemeColor) else Color(0xFF625B71),
                backgroundColor = if (themeBackgroundColor != 0) Color(themeBackgroundColor) else Color(0xFFFEF7FF),
                primaryFontColor = if (primaryTextColor != 0) Color(primaryTextColor) else Color(0xFF1C1B1F),
                secondaryFontColor = if (secondaryTextColor != 0) Color(secondaryTextColor) else Color(0xFF49454F),
                labelContainerColor = if (customLabelContainerColor != 0) Color(customLabelContainerColor) else Color(0xFFF7F2FA)
            )
            generateColorScheme(userPalette, darkTheme)
        } else {
            val customSeedColor = if (darkTheme) customNightPrimary else customPrimary
            ThemeEngine.getColorScheme(
                context = context,
                mode = appThemeMode,
                darkTheme = darkTheme,
                isAmoled = isPureBlack,
                paletteStyle = paletteStyleValue,
                materialVersion = materialVersion,
                customSeedColor = customSeedColor
            )
        }
    }

    // 5. 确定种子颜色
    val themeSeedColor = remember(appThemeMode, colorScheme.primary) {
        if (appThemeMode == AppThemeMode.Custom) {
            val seed = if (darkTheme) customNightPrimary else customPrimary
            if (seed != 0) Color(seed) else colorScheme.primary
        } else {
            colorScheme.primary
        }
    }

    // 6. 构造 Legado 主题模式数据
    val themeColors = remember(
        colorScheme, darkTheme, themeSeedColor, paletteStyleValue, composeEngine, appThemeMode
    ) {
        val paletteStyle = ThemeResolver.resolvePaletteStyle(paletteStyleValue)
        val colorSchemeMode = ThemeResolver.resolveColorSchemeMode(ThemeConfig.themeMode)
        LegadoThemeMode(
            colorScheme = colorScheme,
            isDark = darkTheme,
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
