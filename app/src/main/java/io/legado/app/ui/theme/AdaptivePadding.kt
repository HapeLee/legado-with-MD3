package io.legado.app.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme.composeEngine

@Composable
fun Modifier.adaptiveHorizontalPadding(): Modifier {
    val horizontal = if (ThemeResolver.isMiuixEngine(composeEngine)) 12.dp else 16.dp
    return this.padding(horizontal = horizontal)
}

@Composable
fun Modifier.adaptiveHorizontalPaddingTab(): Modifier {
    val start = if (ThemeResolver.isMiuixEngine(composeEngine)) 12.dp else 0.dp
    val end = if (ThemeResolver.isMiuixEngine(composeEngine)) 12.dp else 16.dp
    return this.padding(start = start, end = end)
}

@Composable
fun Modifier.adaptiveHorizontalPadding(
    vertical: Dp,
): Modifier {
    val horizontal = if (ThemeResolver.isMiuixEngine(composeEngine)) 12.dp else 16.dp
    return this.padding(horizontal = horizontal, vertical = vertical)
}

@Composable
fun Modifier.adaptiveVerticalPadding(): Modifier {
    val horizontal = if (ThemeResolver.isMiuixEngine(composeEngine)) 12.dp else 8.dp
    return this.padding(horizontal = horizontal)
}

@Composable
fun adaptiveContentPaddingOnlyVertical(
    top: Dp,
    bottom: Dp
): PaddingValues {
    val adjustedTop = if (ThemeResolver.isMiuixEngine(composeEngine)) top + 8.dp else top
    return PaddingValues(
        top = adjustedTop,
        bottom = bottom,
        start = 0.dp,
        end = 0.dp
    )
}

@Composable
fun adaptiveContentPadding(
    top: Dp,
    bottom: Dp
): PaddingValues {
    val horizontal = if (ThemeResolver.isMiuixEngine(composeEngine)) 12.dp else 16.dp
    val adjustedTop = if (ThemeResolver.isMiuixEngine(composeEngine)) top + 8.dp else top
    return PaddingValues(
        top = adjustedTop,
        bottom = bottom,
        start = horizontal,
        end = horizontal
    )
}

@Composable
fun adaptiveContentPadding(
    top: Dp,
    bottom: Dp,
    horizontal: Dp
): PaddingValues {
    val adjustedTop = if (ThemeResolver.isMiuixEngine(composeEngine)) top + 6.dp else top + 4.dp
    return PaddingValues(
        top = adjustedTop,
        bottom = bottom,
        start = horizontal,
        end = horizontal
    )
}

@Composable
fun adaptiveContentPaddingBookshelf(
    top: Dp,
    bottom: Dp,
    horizontal: Dp
): PaddingValues {
    val adjustedTop = if (ThemeResolver.isMiuixEngine(composeEngine)) top + 12.dp else top + 8.dp
    val horizontal = if (ThemeResolver.isMiuixEngine(composeEngine)) 12.dp + horizontal else 4.dp + horizontal
    return PaddingValues(
        top = adjustedTop,
        bottom = bottom,
        start = horizontal,
        end = horizontal
    )
}
