package io.legado.app.ui.widget.components.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import io.legado.app.ui.theme.hazeStyle.HazeLegado

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

    return hazeBlur(
        input = HazeInput.Sources(state),
        style = style.then {
            progressive(
                if (visualState.isProgressiveBlur) {
                    HazeProgressive.verticalGradient(
                        startIntensity = if (placement == ReaderMenuPlacement.Bottom) 0f else 1f,
                        endIntensity = if (placement == ReaderMenuPlacement.Bottom) 1f else 0f,
                    )
                } else {
                    null
                },
            )
        },
    )
}
