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
        blurAlpha = menuTintAlpha(surfaceAlpha),
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

/**
 * 着色层(tint)的不透明度上限。
 *
 * HazeStyle 的 tint 绘制在模糊内容之上，其 alpha 由「菜单不透明度」readMenuBlurAlpha 决定
 * （默认 100 → 1.0 完全不透明）。若填充样式（progressive = null）直接使用该值，tint 会
 * 完全遮住底层模糊，表现为「填充无模糊、渐变才有模糊」。封顶到半透明可保证模糊始终可见。
 */
internal const val MAX_MENU_TINT_ALPHA = 60

internal fun menuTintAlpha(blurAlpha: Int): Int =
    blurAlpha.coerceIn(0, MAX_MENU_TINT_ALPHA)
