package io.legado.app.ui.book.read

import android.graphics.drawable.ColorDrawable
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.core.graphics.toColorInt
import io.legado.app.data.repository.ReadPreferences
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ReadStyleResolver
import io.legado.app.ui.config.themeConfig.ThemeConfig
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ProvideThemeOverride
import io.legado.app.ui.theme.ThemeOverrideState
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.theme.buildThemeOverrideState
import io.legado.app.ui.theme.extractSeedColor
import io.legado.app.ui.theme.toSafeBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ReadBookColorTheme(
    styleConfig: ReadBookStyleConfig,
    preferences: ReadPreferences,
    content: @Composable () -> Unit,
) {
    ProvideThemeOverride(
        theme = rememberReadBookColorTheme(
            styleConfig = styleConfig,
            preferences = preferences,
        ),
        content = content
    )
}

@Composable
private fun rememberReadBookColorTheme(
    styleConfig: ReadBookStyleConfig,
    preferences: ReadPreferences,
): ThemeOverrideState? {
    val isAppDark = LegadoTheme.isDark
    val paletteStyle = preferences.readMenuPaletteStyle
    return when (preferences.readBarStyle) {
        1 -> rememberReadBackgroundTheme(styleConfig, isAppDark, paletteStyle)
        2 -> rememberCustomReadMenuTheme(
            styleConfig = styleConfig,
            preferences = preferences,
            isAppDark = isAppDark,
            paletteStyle = paletteStyle,
        )
        else -> null
    }
}

@Composable
private fun rememberReadBackgroundTheme(
    styleConfig: ReadBookStyleConfig,
    isAppDark: Boolean,
    paletteStyle: String,
): ThemeOverrideState? {
    val background = remember(styleConfig, isAppDark) {
        runCatching { ReadStyleResolver.currentBackground(ReadBookConfig.durConfig) }.getOrNull()
    } ?: return null
    var seedColor by remember(background) { mutableStateOf<Color?>(null) }

    LaunchedEffect(background, styleConfig, isAppDark) {
        seedColor = when (background.type) {
            0 -> background.value.toColorOrNull()
            else -> extractCurrentReadBackgroundSeed()
                ?: ReadBookConfig.bgMeanColor.takeIf { it != 0 }?.let(::Color)
        }
    }

    val sourceColor = seedColor ?: return null
    val surfaceColor = when (background.type) {
        0 -> background.value.toColorOrNull()
        else -> ReadBookConfig.bgMeanColor.takeIf { it != 0 }?.let(::Color)
    }
    return rememberReadThemeOverride(
        seedColor = sourceColor,
        backgroundColor = surfaceColor,
        containerColor = null,
        deriveDarkFromColor = false,
        paletteStyle = paletteStyle,
    )
}

@Composable
private fun rememberCustomReadMenuTheme(
    styleConfig: ReadBookStyleConfig,
    preferences: ReadPreferences,
    isAppDark: Boolean,
    paletteStyle: String,
): ThemeOverrideState {
    val menuBackgroundColor = remember(
        styleConfig,
        preferences.readMenuBgColor,
        preferences.readMenuBgColorNight,
        isAppDark,
    ) {
        Color(preferences.readMenuBackgroundColor(isAppDark))
    }
    val accentColor = remember(
        styleConfig,
        preferences.readMenuAccentColor,
        preferences.readMenuAccentColorNight,
        isAppDark,
    ) {
        Color(preferences.readMenuAccentColor(isAppDark))
    }
    val menuContainerColor = remember(
        styleConfig,
        preferences.readMenuContainerColor,
        preferences.readMenuContainerColorNight,
        preferences.readMenuBgColor,
        preferences.readMenuBgColorNight,
        isAppDark,
    ) {
        Color(preferences.readMenuContainerColor(isAppDark))
    }
    val useSeedOnly = preferences.readMenuColorMode == 0
    if (!useSeedOnly) {
        return rememberCustomReadMenuThemeOverride(
            accentColor = accentColor,
            menuBackgroundColor = menuBackgroundColor,
            menuContainerColor = menuContainerColor,
            isDark = isAppDark,
            paletteStyle = paletteStyle,
        )
    }

    return rememberReadThemeOverride(
        seedColor = accentColor,
        backgroundColor = null,
        containerColor = null,
        fallbackDark = isAppDark,
        deriveDarkFromColor = false,
        paletteStyle = paletteStyle,
    ) ?: buildReadThemeOverride(
        seedColor = accentColor,
        backgroundColor = null,
        containerColor = null,
        isDark = isAppDark,
        paletteStyle = paletteStyle,
    )
}

@Composable
private fun rememberCustomReadMenuThemeOverride(
    accentColor: Color,
    menuBackgroundColor: Color,
    menuContainerColor: Color,
    isDark: Boolean,
    paletteStyle: String,
): ThemeOverrideState {
    return remember(accentColor, menuBackgroundColor, menuContainerColor, isDark, paletteStyle) {
        buildReadThemeOverride(
            seedColor = accentColor,
            backgroundColor = null,
            containerColor = null,
            isDark = isDark,
            paletteStyle = paletteStyle,
        ).let { base ->
            base.copy(
                colorScheme = base.colorScheme.withCustomReadMenuColors(
                    accentColor = accentColor,
                    menuBackgroundColor = menuBackgroundColor,
                    menuContainerColor = menuContainerColor,
                )
            )
        }
    }
}

@Composable
private fun rememberReadThemeOverride(
    seedColor: Color,
    backgroundColor: Color?,
    containerColor: Color?,
    fallbackDark: Boolean = LegadoTheme.isDark,
    deriveDarkFromColor: Boolean = true,
    paletteStyle: String = "",
): ThemeOverrideState? {
    val isDark = remember(backgroundColor, containerColor, fallbackDark, deriveDarkFromColor) {
        if (deriveDarkFromColor) {
            (containerColor ?: backgroundColor)?.let { it.luminance() < 0.5f } ?: fallbackDark
        } else {
            fallbackDark
        }
    }
    return remember(seedColor, backgroundColor, containerColor, isDark, paletteStyle) {
        buildReadThemeOverride(
            seedColor = seedColor,
            backgroundColor = backgroundColor,
            containerColor = containerColor,
            isDark = isDark,
            paletteStyle = paletteStyle,
        )
    }
}

private fun buildReadThemeOverride(
    seedColor: Color,
    backgroundColor: Color?,
    containerColor: Color?,
    isDark: Boolean,
    paletteStyle: String = "",
): ThemeOverrideState {
    val colorSpec = ThemeResolver.resolveColorSpecFromMaterialVersion(ThemeConfig.materialVersion)
    val resolvedPaletteStyle = paletteStyle
        .takeIf { it.isNotBlank() }
        ?.let { ThemeResolver.resolvePaletteStyle(it) }
        ?: ThemeResolver.resolvePaletteStyle(ThemeConfig.paletteStyle)
    val base = buildThemeOverrideState(
        seedColor = seedColor,
        isDark = isDark,
        paletteStyle = resolvedPaletteStyle,
        colorSpec = colorSpec,
        usePureBlack = false,
    )
    return base.copy(
        colorScheme = base.colorScheme.withReadSurfaceColors(
            backgroundColor = backgroundColor,
            containerColor = containerColor
        )
    )
}

private fun ColorScheme.withReadSurfaceColors(
    backgroundColor: Color?,
    containerColor: Color?,
): ColorScheme {
    val resolvedBackground = backgroundColor ?: background
    val resolvedContainer = containerColor ?: surfaceContainer
    return copy(
        background = resolvedBackground,
        surface = resolvedBackground,
        surfaceDim = resolvedBackground,
        surfaceBright = resolvedBackground,
        surfaceContainerLowest = resolvedBackground,
        surfaceContainer = resolvedContainer,
    )
}

private fun ColorScheme.withCustomReadMenuColors(
    accentColor: Color,
    menuBackgroundColor: Color,
    menuContainerColor: Color,
): ColorScheme {
    return copy(
        primary = accentColor,
        onPrimary = accentColor.contrastContentColor(),
        surfaceTint = accentColor,
        surfaceContainerHigh = menuBackgroundColor,
        surfaceContainerLow = menuContainerColor,
    )
}

private fun Color.contrastContentColor(): Color {
    return if (luminance() > 0.5f) Color.Black else Color.White
}

private fun ReadPreferences.readMenuBackgroundColor(isDark: Boolean): Int {
    return if (isDark) {
        readMenuBgColorNight.takeIf { it != 0 } ?: ReadBookConfig.durConfig.menuBgColor(isNight = true)
    } else {
        readMenuBgColor.takeIf { it != 0 } ?: ReadBookConfig.durConfig.menuBgColor(isNight = false)
    }
}

private fun ReadPreferences.readMenuAccentColor(isDark: Boolean): Int {
    return if (isDark) {
        readMenuAccentColorNight.takeIf { it != 0 }
            ?: ReadBookConfig.durConfig.menuAccentColor(isNight = true)
    } else {
        readMenuAccentColor.takeIf { it != 0 }
            ?: ReadBookConfig.durConfig.menuAccentColor(isNight = false)
    }
}

private fun ReadPreferences.readMenuContainerColor(isDark: Boolean): Int {
    return if (isDark) {
        readMenuContainerColorNight.takeIf { it != 0 } ?: readMenuBackgroundColor(isDark = true)
    } else {
        readMenuContainerColor.takeIf { it != 0 } ?: readMenuBackgroundColor(isDark = false)
    }
}

private suspend fun extractCurrentReadBackgroundSeed(): Color? {
    return withContext(Dispatchers.Default) {
        runCatching {
            val drawable = ReadStyleResolver.currentBackgroundDrawable(
                config = ReadBookConfig.durConfig,
                width = 128,
                height = 128
            )
            if (drawable is ColorDrawable) {
                Color(drawable.color)
            } else {
                Color(drawable.toSafeBitmap(128).extractSeedColor())
            }
        }.getOrNull()
    }
}

private fun String.toColorOrNull(): Color? {
    return runCatching { Color(toColorInt()) }.getOrNull()
}
