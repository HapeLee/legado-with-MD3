package io.legado.app.ui.theme

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme.composeEngine

@Composable
fun Modifier.adaptiveHorizontalPadding(): Modifier {
    val horizontal = if (ThemeResolver.isMiuixEngine(composeEngine)) 8.dp else 16.dp
    return this.padding(horizontal = horizontal)
}

@Composable
fun Modifier.adaptiveVerticalPadding(): Modifier {
    val horizontal = if (ThemeResolver.isMiuixEngine(composeEngine)) 12.dp else 8.dp
    return this.padding(horizontal = horizontal)
}
