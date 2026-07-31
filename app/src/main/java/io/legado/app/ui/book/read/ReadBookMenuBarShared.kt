package io.legado.app.ui.book.read

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import io.legado.app.constant.ReadMenuBlurStyle
import io.legado.app.help.config.ReadStyleResolver
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.reader.ReaderMenuPlacement
import io.legado.app.ui.widget.components.reader.ReaderMenuTintStyle
import io.legado.app.ui.widget.components.reader.readerMenuLiquidGlass
import io.legado.app.ui.widget.components.reader.readerMenuSurfaceBrush

internal val menuTextShadow = androidx.compose.ui.graphics.Shadow(
    color = Color.Black.copy(alpha = 0.12f),
    offset = Offset.Zero,
    blurRadius = 12f,
)

internal data class ReadMenuColors(
    val background: Color,
    val content: Color,
)

internal fun Int.toReaderMenuTintStyle(): ReaderMenuTintStyle {
    return if (this == ReadMenuBlurStyle.Progressive) {
        ReaderMenuTintStyle.Gradient
    } else {
        ReaderMenuTintStyle.Fill
    }
}

@Composable
internal fun Modifier.readMenuLiquidGlass(
    backdrop: Backdrop?,
    colors: ReadMenuColors,
    shape: Shape,
    useTopBarStyle: Boolean,
    useLens: Boolean,
    blurRadius: Dp? = null,
    interactive: Boolean = false,
    surfaceAlphaOverride: Int? = null,
    menuConfig: ReadMenuConfig,
): Modifier {
    val resolvedBlurRadius = blurRadius ?: menuConfig.readMenuBlurRadius.dp
    val blurAlpha = surfaceAlphaOverride ?: menuConfig.readMenuBlurAlpha
    val surfaceColor = readMenuTintColor(menuConfig) ?: colors.background
    val containerColor = surfaceColor.copy(
        alpha = (blurAlpha.coerceIn(0, 100) / 100f).coerceAtMost(0.6f)
    )
    val topBarSurfaceBrush = readerMenuSurfaceBrush(
        style = ReaderMenuTintStyle.Gradient,
        placement = ReaderMenuPlacement.Top,
        color = surfaceColor,
        alpha = containerColor.alpha,
    )

    val surfaceBrush = if (useTopBarStyle) {
        topBarSurfaceBrush
    } else {
        readerMenuSurfaceBrush(
            style = ReaderMenuTintStyle.Fill,
            placement = ReaderMenuPlacement.Bottom,
            color = surfaceColor,
            alpha = containerColor.alpha,
        )
    }
    return readerMenuLiquidGlass(
        backdrop = backdrop,
        shape = shape,
        surfaceBrush = surfaceBrush,
        blurRadius = resolvedBlurRadius,
        lensRadius = menuConfig.readMenuLensRadius.dp,
        useLens = useLens,
        interactive = interactive,
    )
}

@Composable
internal fun readMenuTintColor(menuConfig: ReadMenuConfig): Color? {
    return menuConfig.readMenuBlurColorNight
        .takeIf { it != 0 && ReadStyleResolver.isNightTheme() }
        ?.let(::Color)
        ?: menuConfig.readMenuBlurColor
            .takeIf { it != 0 && !ReadStyleResolver.isNightTheme() }
            ?.let(::Color)
        ?: menuConfig.readMenuBlurColor
            .takeIf { it != 0 }
            ?.let(::Color)
}

@Composable
internal fun readMenuBorderColor(menuConfig: ReadMenuConfig): Int {
    return (if (ReadStyleResolver.isNightTheme()) {
        menuConfig.readMenuBorderColorNight
    } else {
        menuConfig.readMenuBorderColor
    }).takeIf { it != 0 }
        ?: LegadoTheme.colorScheme.outlineVariant.hashCode()
}

@Composable
internal fun readMenuTextColor(menuConfig: ReadMenuConfig): Color {
    return Color(
        if (ReadStyleResolver.isNightTheme()) {
            menuConfig.readMenuTextColorNight
        } else {
            menuConfig.readMenuTextColor
        }
    ).takeUnless { it == Color.Unspecified || it.alpha == 0f }
        ?: LegadoTheme.colorScheme.onSurface
}
