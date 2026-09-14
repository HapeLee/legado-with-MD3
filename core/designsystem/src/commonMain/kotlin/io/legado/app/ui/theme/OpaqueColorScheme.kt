package io.legado.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.legado.app.domain.model.settings.hasBackgroundImage
import io.legado.app.ui.theme.ThemeEngine.getColorScheme

/**
 * 取一份**不透明**的配色方案，给「独立窗口」里的内容用。
 *
 * 弹窗（Popup / Dialog / ModalBottomSheet / DropdownMenu）各自持有独立 Composition owner，
 * 拿不到宿主 `AppTheme` 提供的 `LocalLegadoColorScheme`；而透明度主题（`Transparent`）下
 * 宿主配色本身是 `Color.Transparent`，直接透传到弹窗里会变成完全看不见。
 * 所以这里在 `Transparent` 时重新解析一份 `forceOpaque = true` 的等效配色。
 *
 * M1-3l：本文件随 [ThemeEngine] 一起进 `commonMain`，去掉了原先的 `LocalContext.current`。
 * 关键点：**这条路径不需要任何平台能力**——调用它时 `appThemeMode` 必然等于 `Transparent`
 * （否则走上面那个分支直接返回宿主配色），而 `forceOpaque = true` 会把 `Transparent`
 * 归一到 `WH`（见 `ThemeEngine.resolveMode`），落进「预定义配色」分支。
 * `Dynamic` / `Custom` 这两条会用到平台契约的分支在此**不可达**。
 */
@Composable
fun rememberOpaqueColorScheme(): ColorScheme {
    val themeSettings = LocalAppUiConfiguration.current.theme
    val currentTheme = LocalLegadoThemeColors.current
    val appThemeMode = ThemeResolver.resolveThemeMode(themeSettings.appTheme)
    val isDark = currentTheme.isDark
    val isPureBlack = themeSettings.isPureBlack
    val hasImageBg = themeSettings.hasBackgroundImage(isDark)
    val paletteStyle = themeSettings.paletteStyle
    val materialVersion = themeSettings.materialVersion
    val seedColorInt = currentTheme.seedColor
        .takeUnless { it == Color.Unspecified }
        ?.toArgb()

    return remember(
        currentTheme.colorScheme,
        appThemeMode,
        isDark,
        isPureBlack,
        hasImageBg,
        paletteStyle,
        materialVersion,
        seedColorInt
    ) {
        if (appThemeMode != AppThemeMode.Transparent) {
            currentTheme.colorScheme
        } else {
            getColorScheme(
                mode = appThemeMode,
                darkTheme = isDark,
                isAmoled = isPureBlack,
                paletteStyle = paletteStyle,
                materialVersion = materialVersion,
                forceOpaque = true,
                customSeedColor = seedColorInt
            )
        }
    }
}
