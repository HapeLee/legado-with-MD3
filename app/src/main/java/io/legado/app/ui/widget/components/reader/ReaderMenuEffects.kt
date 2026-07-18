package io.legado.app.ui.widget.components.reader

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import io.legado.app.ui.theme.hazeStyle.HazeLegado

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun Modifier.readerMenuHazeEffect(
    state: HazeState,
    visualState: ReaderMenuVisualState,
    placement: ReaderMenuPlacement,
    baseColor: Color,
    tintColor: Color?,
    blurRadius: Int,
    surfaceAlpha: Int,
): Modifier {
    val resolvedColor = tintColor
        .takeIf { visualState.useTint }
        ?: baseColor
    val style = HazeLegado.custom(
        containerColor = resolvedColor.copy(alpha = surfaceAlpha.coerceIn(0, 100) / 100f),
        blurRadius = blurRadius,
        blurAlpha = surfaceAlpha,
    )

    return hazeEffect(state = state, style = style) {
        progressive = if (visualState.isProgressiveBlur) {
            HazeProgressive.verticalGradient(
                startIntensity = if (placement == ReaderMenuPlacement.Bottom) 0f else 1f,
                endIntensity = if (placement == ReaderMenuPlacement.Bottom) 1f else 0f,
            )
        } else {
            null
        }
    }
}
