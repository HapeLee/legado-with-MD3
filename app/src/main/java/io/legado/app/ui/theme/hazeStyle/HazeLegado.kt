package io.legado.app.ui.theme.hazeStyle

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.ThemeResolver
import top.yukonga.miuix.kmp.theme.MiuixTheme

object HazeLegado {

    @Composable
    @ReadOnlyComposable
    fun ultraThin(
        containerColor: Color = if (ThemeResolver.isMiuixEngine(composeEngine)) MiuixTheme.colorScheme.surface else MaterialTheme.colorScheme.surface,
    ): HazeStyle = hazeLegado(
        containerColor = containerColor,
        lightAlpha = 0.35f,
        darkAlpha = 0.55f,
    )

    @Composable
    @ReadOnlyComposable
    fun regular(
        containerColor: Color = if (ThemeResolver.isMiuixEngine(composeEngine)) MiuixTheme.colorScheme.surface else MaterialTheme.colorScheme.surface,
    ): HazeStyle = hazeLegado(
        containerColor = containerColor,
        lightAlpha = 0.73f,
        darkAlpha = 0.8f,
    )

    private fun hazeLegado(
        containerColor: Color,
        lightAlpha: Float,
        darkAlpha: Float,
    ): HazeStyle = HazeStyle(
        blurRadius = 24.dp,
        backgroundColor = containerColor,
        tint = HazeTint(
            containerColor.copy(alpha = if (containerColor.luminance() >= 0.5) lightAlpha else darkAlpha),
        ),
    )
}