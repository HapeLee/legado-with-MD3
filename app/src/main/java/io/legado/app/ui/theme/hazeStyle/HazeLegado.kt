package io.legado.app.ui.theme.hazeStyle

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import io.legado.app.ui.config.themeConfig.ThemeConfig
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.ThemeResolver
import top.yukonga.miuix.kmp.theme.MiuixTheme

object HazeLegado {

    @Composable
    @ReadOnlyComposable
    fun ultraThinPlus(
        containerColor: Color = if (ThemeResolver.isMiuixEngine(composeEngine)) MiuixTheme.colorScheme.surface else MaterialTheme.colorScheme.surface,
    ): HazeStyle = hazeLegado(
        containerColor = containerColor,
        lightAlpha = 0.0f,
        darkAlpha = 0.0f,
    )

    @Composable
    @ReadOnlyComposable
    fun ultraThin(
        containerColor: Color = if (ThemeResolver.isMiuixEngine(composeEngine)) MiuixTheme.colorScheme.surface else MaterialTheme.colorScheme.surface,
    ): HazeStyle = hazeLegado(
        containerColor = containerColor,
        lightAlpha = ThemeConfig.topBarBlurAlpha / 100f * 0.35f / 0.73f,
        darkAlpha = ThemeConfig.topBarBlurAlpha / 100f * 0.55f / 0.8f,
    )

    @Composable
    @ReadOnlyComposable
    fun regular(
        containerColor: Color = if (ThemeResolver.isMiuixEngine(composeEngine)) MiuixTheme.colorScheme.surface else MaterialTheme.colorScheme.surface,
    ): HazeStyle = hazeLegado(
        containerColor = containerColor,
        lightAlpha = ThemeConfig.topBarBlurAlpha / 100f,
        darkAlpha = ThemeConfig.topBarBlurAlpha / 100f,
    )

    @Composable
    @ReadOnlyComposable
    fun custom(
        containerColor: Color = if (ThemeResolver.isMiuixEngine(composeEngine)) MiuixTheme.colorScheme.surface else MaterialTheme.colorScheme.surface,
        blurRadius: Int = ThemeConfig.topBarBlurRadius,
        blurAlpha: Int = ThemeConfig.topBarBlurAlpha,
    ): HazeStyle = hazeLegado(
        containerColor = containerColor,
        blurRadius = blurRadius,
        lightAlpha = blurAlpha / 100f,
        darkAlpha = blurAlpha / 100f,
    )

    private fun hazeLegado(
        containerColor: Color,
        blurRadius: Int = 24,
        lightAlpha: Float,
        darkAlpha: Float,
    ): HazeStyle = HazeStyle(
        blurRadius = blurRadius.dp,
        backgroundColor = containerColor,
        tint = HazeTint(
            containerColor.copy(alpha = if (containerColor.luminance() >= 0.5) lightAlpha else darkAlpha),
        ),
    )
}