package io.legado.app.ui.widget.components.reader

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor

enum class ReaderMenuPlacement {
    Top,
    Bottom,
}

fun readerMenuSurfaceBrush(
    style: ReaderMenuTintStyle,
    placement: ReaderMenuPlacement,
    color: Color,
    alpha: Float,
): Brush {
    val opaque = color.copy(alpha = alpha.coerceIn(0f, 1f))
    if (style == ReaderMenuTintStyle.Fill) return SolidColor(opaque)

    val transparent = color.copy(alpha = 0f)
    return Brush.verticalGradient(
        colors = when (placement) {
            ReaderMenuPlacement.Top -> listOf(opaque, transparent)
            ReaderMenuPlacement.Bottom -> listOf(transparent, opaque)
        }
    )
}
