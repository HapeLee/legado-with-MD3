package io.legado.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// 用户自定义的颜色集合
data class UserColorPalette(
    val primaryColor: Color,        // 用户定义的主题色
    val secondaryColor: Color,      // 用户定义的次要主题色
    val backgroundColor: Color,     // 用户定义的背景色
    val primaryFontColor: Color,    // 用户定义的主要字体色
    val secondaryFontColor: Color,  // 用户定义的次要字体色
    val labelContainerColor: Color  // 用户定义的标签容器色
)

// 将用户自定义颜色映射到 Material Theme 的 ColorScheme
fun generateColorScheme(userPalette: UserColorPalette, isDark: Boolean): androidx.compose.material3.ColorScheme {
    return if (isDark) {
        darkColorScheme(
            primary = userPalette.primaryColor,
            onPrimary = userPalette.primaryFontColor,
            primaryContainer = userPalette.labelContainerColor,
            onPrimaryContainer = userPalette.primaryFontColor,

            secondary = userPalette.secondaryColor,
            onSecondary = userPalette.secondaryFontColor,
            secondaryContainer = userPalette.labelContainerColor,
            onSecondaryContainer = userPalette.secondaryFontColor,

            tertiary = userPalette.secondaryColor,
            onTertiary = userPalette.secondaryFontColor,

            background = userPalette.backgroundColor,
            surface = userPalette.backgroundColor,
            onBackground = userPalette.primaryFontColor,
            onSurface = userPalette.primaryFontColor,
            surfaceVariant = userPalette.labelContainerColor,
            onSurfaceVariant = userPalette.secondaryFontColor,

            // 其他颜色
            error = Color(0xFFB3261E),
            onError = Color(0xFFFFFFFF),
            errorContainer = Color(0xFFF9DEDC),
            onErrorContainer = Color(0xFF410E0B),
            outline = Color(0xFF79747E),
            outlineVariant = Color(0xFFC4C7C5),
            scrim = Color(0xFF000000),

            // 表面颜色变体
            surfaceBright = userPalette.backgroundColor,
            surfaceDim = userPalette.backgroundColor,
            surfaceContainer = userPalette.backgroundColor,
            surfaceContainerHigh = userPalette.backgroundColor,
            surfaceContainerHighest = userPalette.backgroundColor,
            surfaceContainerLow = userPalette.labelContainerColor,
            surfaceContainerLowest = userPalette.backgroundColor,

            // 固定颜色
            primaryFixed = userPalette.primaryColor,
            primaryFixedDim = userPalette.primaryColor.copy(alpha = 0.8f),
            onPrimaryFixed = userPalette.primaryFontColor,
            onPrimaryFixedVariant = userPalette.primaryFontColor,
            secondaryFixed = userPalette.secondaryColor,
            secondaryFixedDim = userPalette.secondaryColor.copy(alpha = 0.8f),
            onSecondaryFixed = userPalette.secondaryFontColor,
            onSecondaryFixedVariant = userPalette.secondaryFontColor,
            tertiaryFixed = userPalette.secondaryColor,
            tertiaryFixedDim = userPalette.secondaryColor.copy(alpha = 0.8f),
            onTertiaryFixed = userPalette.secondaryFontColor,
            onTertiaryFixedVariant = userPalette.secondaryFontColor
        )
    } else {
        lightColorScheme(
            primary = userPalette.primaryColor,
            onPrimary = userPalette.primaryFontColor,
            primaryContainer = userPalette.labelContainerColor,
            onPrimaryContainer = userPalette.primaryFontColor,

            secondary = userPalette.secondaryColor,
            onSecondary = userPalette.secondaryFontColor,
            secondaryContainer = userPalette.labelContainerColor,
            onSecondaryContainer = userPalette.secondaryFontColor,

            tertiary = userPalette.secondaryColor,
            onTertiary = userPalette.secondaryFontColor,

            background = userPalette.backgroundColor,
            surface = userPalette.backgroundColor,
            onBackground = userPalette.primaryFontColor,
            onSurface = userPalette.primaryFontColor,
            surfaceVariant = userPalette.labelContainerColor,
            onSurfaceVariant = userPalette.secondaryFontColor,

            // 其他颜色
            error = Color(0xFFB3261E),
            onError = Color(0xFFFFFFFF),
            errorContainer = Color(0xFFF9DEDC),
            onErrorContainer = Color(0xFF410E0B),
            outline = Color(0xFF79747E),
            outlineVariant = Color(0xFFC4C7C5),
            scrim = Color(0xFF000000),

            // 表面颜色变体
            surfaceBright = userPalette.backgroundColor,
            surfaceDim = userPalette.backgroundColor,
            surfaceContainer = userPalette.backgroundColor,
            surfaceContainerHigh = userPalette.backgroundColor,
            surfaceContainerHighest = userPalette.backgroundColor,
            surfaceContainerLow = userPalette.labelContainerColor,
            surfaceContainerLowest = userPalette.backgroundColor,

            // 固定颜色
            primaryFixed = userPalette.primaryColor,
            primaryFixedDim = userPalette.primaryColor.copy(alpha = 0.8f),
            onPrimaryFixed = userPalette.primaryFontColor,
            onPrimaryFixedVariant = userPalette.primaryFontColor,
            secondaryFixed = userPalette.secondaryColor,
            secondaryFixedDim = userPalette.secondaryColor.copy(alpha = 0.8f),
            onSecondaryFixed = userPalette.secondaryFontColor,
            onSecondaryFixedVariant = userPalette.secondaryFontColor,
            tertiaryFixed = userPalette.secondaryColor,
            tertiaryFixedDim = userPalette.secondaryColor.copy(alpha = 0.8f),
            onTertiaryFixed = userPalette.secondaryFontColor,
            onTertiaryFixedVariant = userPalette.secondaryFontColor
        )
    }
}
