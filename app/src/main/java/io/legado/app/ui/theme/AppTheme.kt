package io.legado.app.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Density
import com.materialkolor.PaletteStyle
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.domain.model.settings.customColors
import io.legado.app.utils.sysConfiguration
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppTheme(
    configuration: AppUiConfiguration,
    darkTheme: Boolean = configuration.isDarkTheme,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalAppUiConfiguration provides configuration) {
        if (LocalInspectionMode.current) {
            AppThemePreview(darkTheme, content)
        } else {
            AppThemeActual(configuration, darkTheme, content)
        }
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
        LocalLegadoThemeColors provides themeColors,
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
    configuration: AppUiConfiguration,
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val appShellSettings = configuration.appShell
    val themeSettings = configuration.theme
    val context = LocalContext.current
    
    // 1. 获取基础配置
    val appThemeMode = ThemeResolver.resolveThemeMode(themeSettings.appTheme)
    val effectiveDarkTheme = darkTheme
    val isPureBlack = themeSettings.isPureBlack
    val paletteStyleValue = themeSettings.paletteStyle
    val materialVersion = themeSettings.materialVersion
    val customContrast = themeSettings.customContrast
    val composeEngine = appShellSettings.composeEngine
    val customPrimary = themeSettings.customPrimary
    val customNightPrimary = themeSettings.customNightPrimary
    val appFontPath = themeSettings.appFontPath
    val currentDensity = LocalDensity.current
    val fontScale = (appShellSettings.fontScale / 10f)
        .takeIf { it in 0.8f..1.6f }
        ?: sysConfiguration.fontScale
    val appDensity = remember(currentDensity.density, fontScale) {
        Density(currentDensity.density, fontScale)
    }

    // 2. 深度个性化配置
    val enableDeepPersonalization = themeSettings.enableDeepPersonalization
    val customColors = themeSettings.customColors(effectiveDarkTheme)

    // 3. 加载自定义字体
    val customFontFamily = rememberCustomFont(appFontPath)

    // 4. 解析配色方案 (Material 3 ColorScheme)
    val colorScheme = remember(
        context, appThemeMode, effectiveDarkTheme, isPureBlack, customPrimary, customNightPrimary,
        enableDeepPersonalization, customColors,
        paletteStyleValue, materialVersion, customContrast,
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
                customSeedColor = customSeedColor,
                customContrast = customContrast,
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
    // themeMode 用 effectiveDarkTheme 归一化（System 已在上面解析成明确的深浅色），
    // 这样"跟随系统"和"深色"在系统深色下产生相等的 LegadoThemeMode，
    // staticCompositionLocalOf 不会触发全树重组
    val themeColors = remember(
        colorScheme, effectiveDarkTheme, themeSeedColor, paletteStyleValue, composeEngine,
        appThemeMode
    ) {
        val paletteStyle = ThemeResolver.resolvePaletteStyle(paletteStyleValue)
        val colorSchemeMode =
            if (effectiveDarkTheme) ColorSchemeMode.Dark else ColorSchemeMode.Light
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
        LocalLegadoThemeColors provides themeColors,
        LocalDensity provides appDensity,
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
